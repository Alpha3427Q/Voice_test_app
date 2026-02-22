package com.alice.ai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alice.ai.data.offline.LlamaJniBridge
import com.alice.ai.data.offline.OfflineEngineManager
import com.alice.ai.data.offline.OfflineEngineResult
import com.alice.ai.data.online.OllamaApi
import com.alice.ai.data.online.OllamaApiFactory
import com.alice.ai.data.online.OllamaChatRequest
import com.alice.ai.data.online.OllamaMessage
import com.alice.ai.data.settings.SettingsRepository
import com.alice.ai.ui.chat.ChatMessage
import com.alice.ai.ui.chat.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "ChatViewModel"
private const val ONLINE_PREFIX = "Online: "
private const val OFFLINE_PREFIX = "Offline: "
private const val DEFAULT_ONLINE_MODEL = "llama3"
private const val CONTEXT_WINDOW_SIZE = 7
private const val MAX_STORED_MESSAGES = 200

private const val SYSTEM_PROMPT_TEMPLATE =
    "Your name is Alice an AI assistant created by Adwaith also known as Aromal and Alpha. " +
        "Current time is [DYNAMIC_TIME] and date is [DYNAMIC_DATE] and day is [DYNAMIC_DAY]."

enum class EngineMode {
    Online,
    Offline
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val activeContext: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val ollamaServerUrl: String = "",
    val ollamaApiKey: String = "",
    val offlineModelPath: String = "",
    val availableModels: List<String> = listOf("$ONLINE_PREFIX$DEFAULT_ONLINE_MODEL"),
    val selectedModel: String = "$ONLINE_PREFIX$DEFAULT_ONLINE_MODEL",
    val mode: EngineMode = EngineMode.Online,
    val isGenerating: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var ollamaApi: OllamaApi? = null
    private var onlineModelNames: List<String> = listOf(DEFAULT_ONLINE_MODEL)
    private var settingsRepository: SettingsRepository? = null
    private var statusMessageJob: Job? = null

    fun attachSettingsRepository(repository: SettingsRepository) {
        settingsRepository = repository
        val currentUrl = _uiState.value.ollamaServerUrl
        if (currentUrl.isNotBlank()) {
            rebuildOllamaApi(currentUrl)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun updateOllamaApiKey(apiKey: String) {
        _uiState.update { it.copy(ollamaApiKey = apiKey.trim(), errorMessage = null) }
        val currentUrl = _uiState.value.ollamaServerUrl
        if (currentUrl.isNotBlank()) {
            rebuildOllamaApi(currentUrl)
        }
    }

    fun updateOllamaServerUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            ollamaApi = null
            onlineModelNames = listOf(DEFAULT_ONLINE_MODEL)
            _uiState.update { state ->
                val mergedModels = mergeModelOptions(onlineModelNames, state.offlineModelPath)
                val modelToSelect = when {
                    state.selectedModel in mergedModels -> state.selectedModel
                    mergedModels.isNotEmpty() -> mergedModels.first()
                    else -> "$ONLINE_PREFIX$DEFAULT_ONLINE_MODEL"
                }
                state.copy(
                    ollamaServerUrl = "",
                    availableModels = mergedModels,
                    selectedModel = modelToSelect,
                    mode = if (modelToSelect.startsWith(OFFLINE_PREFIX)) EngineMode.Offline else EngineMode.Online,
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update { it.copy(ollamaServerUrl = trimmed, errorMessage = null) }
        rebuildOllamaApi(trimmed)
    }

    fun updateOfflineModelPath(path: String) {
        val trimmed = path.trim()
        val previousPath = _uiState.value.offlineModelPath
        _uiState.update { state ->
            val mergedModels = mergeModelOptions(onlineModelNames, trimmed)
            val offlineEntry = mergedModels.lastOrNull { it.startsWith(OFFLINE_PREFIX) }
            val modelToSelect = when {
                trimmed.isBlank() && state.mode == EngineMode.Offline -> mergedModels.first()
                state.mode == EngineMode.Offline && offlineEntry != null -> offlineEntry
                state.selectedModel in mergedModels -> state.selectedModel
                else -> mergedModels.first()
            }
            state.copy(
                offlineModelPath = trimmed,
                availableModels = mergedModels,
                selectedModel = modelToSelect,
                mode = if (modelToSelect.startsWith(OFFLINE_PREFIX)) EngineMode.Offline else EngineMode.Online
            )
        }

        if (trimmed.isBlank() || (previousPath.isNotBlank() && previousPath != trimmed)) {
            viewModelScope.launch {
                unloadOfflineModelWithStatus()
            }
        }
    }

    fun selectModel(model: String) {
        val state = _uiState.value
        if (model == state.selectedModel) {
            return
        }

        if (model.startsWith(OFFLINE_PREFIX)) {
            val modelPath = state.offlineModelPath
            if (modelPath.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Select a GGUF file in Settings before using Offline mode")
                }
                return
            }
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    mode = EngineMode.Offline,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    mode = EngineMode.Online,
                    errorMessage = null
                )
            }
            viewModelScope.launch {
                unloadOfflineModelWithStatus()
            }
        }
    }

    fun refreshOnlineModels() {
        val api = ollamaApi ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.fetchModels() } }
                .onSuccess { response ->
                    val fetched = response.models.map { it.name }.filter { it.isNotBlank() }
                    onlineModelNames = if (fetched.isEmpty()) {
                        listOf(DEFAULT_ONLINE_MODEL)
                    } else {
                        fetched
                    }
                    _uiState.update { state ->
                        val mergedModels = mergeModelOptions(onlineModelNames, state.offlineModelPath)
                        val modelToSelect = when {
                            state.selectedModel in mergedModels -> state.selectedModel
                            mergedModels.isNotEmpty() -> mergedModels.first()
                            else -> "$ONLINE_PREFIX$DEFAULT_ONLINE_MODEL"
                        }
                        state.copy(
                            availableModels = mergedModels,
                            selectedModel = modelToSelect,
                            mode = if (modelToSelect.startsWith(OFFLINE_PREFIX)) {
                                EngineMode.Offline
                            } else {
                                EngineMode.Online
                            },
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Failed to load online models")
                    }
                }
        }
    }

    fun sendMessage() {
        val userText = _uiState.value.inputText.trim()
        if (userText.isBlank() || _uiState.value.isGenerating) {
            return
        }

        val userMessage = ChatMessage(role = ChatRole.User, content = userText)
        _uiState.update { state ->
            val updatedMessages = (state.messages + userMessage).takeLast(MAX_STORED_MESSAGES)
            state.copy(
                messages = updatedMessages,
                activeContext = updatedMessages.takeLast(CONTEXT_WINDOW_SIZE),
                inputText = "",
                isGenerating = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val stateAtGenerationStart = _uiState.value
            val contextWindow = stateAtGenerationStart.messages.takeLast(CONTEXT_WINDOW_SIZE)
            val systemPrompt = buildDynamicSystemPrompt()

            runCatching {
                when (stateAtGenerationStart.mode) {
                    EngineMode.Online -> generateOnlineReply(
                        modelDisplayName = stateAtGenerationStart.selectedModel,
                        contextWindow = contextWindow,
                        systemPrompt = systemPrompt
                    )

                    EngineMode.Offline -> generateOfflineReply(
                        offlineModelPath = stateAtGenerationStart.offlineModelPath,
                        contextWindow = contextWindow,
                        systemPrompt = systemPrompt
                    )
                }
            }.onSuccess { assistantText ->
                val assistantMessage = ChatMessage(role = ChatRole.Assistant, content = assistantText)
                _uiState.update { state ->
                    val updatedMessages = (state.messages + assistantMessage).takeLast(MAX_STORED_MESSAGES)
                    state.copy(
                        messages = updatedMessages,
                        activeContext = updatedMessages.takeLast(CONTEXT_WINDOW_SIZE),
                        isGenerating = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        errorMessage = error.message ?: "Generation failed"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        statusMessageJob?.cancel()
        LlamaJniBridge.unloadModel()
        super.onCleared()
    }

    private fun rebuildOllamaApi(baseUrl: String) {
        ollamaApi = runCatching {
            OllamaApiFactory.create(
                baseUrl = baseUrl,
                okHttpClient = httpClient,
                apiKeyProvider = { resolveOllamaApiKey() }
            )
        }.onFailure { error ->
            _uiState.update {
                it.copy(errorMessage = error.message ?: "Invalid Ollama server URL")
            }
        }.getOrNull()
    }

    private fun resolveOllamaApiKey(): String {
        val storedKey = settingsRepository?.ollamaApiKey?.trim().orEmpty()
        return storedKey.ifBlank { _uiState.value.ollamaApiKey.trim() }
    }

    private suspend fun generateOnlineReply(
        modelDisplayName: String,
        contextWindow: List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val api = ollamaApi ?: error("Ollama API is not configured")
        val modelName = modelDisplayName.removePrefix(ONLINE_PREFIX).ifBlank { DEFAULT_ONLINE_MODEL }

        val requestMessages = buildList {
            // The dynamic system prompt is silently prepended every generation.
            add(OllamaMessage(role = "system", content = systemPrompt))
            contextWindow.forEach { message ->
                add(
                    OllamaMessage(
                        role = if (message.role == ChatRole.User) "user" else "assistant",
                        content = message.content
                    )
                )
            }
        }

        val response = api.chat(
            OllamaChatRequest(
                model = modelName,
                messages = requestMessages,
                stream = false
            )
        )

        response.message?.content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: response.response?.trim()?.takeIf { it.isNotBlank() }
            ?: error("Online engine returned an empty response")
    }

    private suspend fun generateOfflineReply(
        offlineModelPath: String,
        contextWindow: List<ChatMessage>,
        systemPrompt: String
    ): String {
        if (offlineModelPath.isBlank()) {
            return offlineModelNotLoadedMessage("no GGUF model selected")
        }

        if (!OfflineEngineManager.isModelLoaded(offlineModelPath)) {
            setStatus("Loading offline model...")
            when (val loadResult = OfflineEngineManager.loadModel(offlineModelPath)) {
                OfflineEngineResult.Success -> {
                    setTransientStatus("Offline model loaded")
                }

                is OfflineEngineResult.Failure -> {
                    val reason = loadResult.error.shortReason
                    Log.e(TAG, "Offline model load failed: $reason")
                    clearStatus()
                    return offlineModelNotLoadedMessage(reason)
                }
            }
        }

        val contextText = contextWindow.joinToString(separator = "\n") { message ->
            val speaker = if (message.role == ChatRole.User) "User" else "Alice"
            "$speaker: ${message.content}"
        }
        val prompt = buildString {
            append(systemPrompt)
            append("\n\n")
            append(contextText)
            append("\nAlice:")
        }

        val response = withContext(Dispatchers.IO) {
            LlamaJniBridge.generateText(
                prompt = prompt,
                maxTokens = 512,
                temperature = 0.7f
            )
        }.trim()

        if (response.equals("Offline model is not loaded.", ignoreCase = true)) {
            Log.e(TAG, "Offline generation failed: native runtime reported model not loaded")
            return offlineModelNotLoadedMessage("runtime reported model is not loaded")
        }

        if (response.isBlank() || response.startsWith("Offline engine stub response")) {
            Log.e(TAG, "Offline generation failed: empty or stub output")
            return "Offline engine error: no output produced (see logs)."
        }

        return response
    }

    private suspend fun unloadOfflineModelWithStatus() {
        if (!LlamaJniBridge.nativeIsModelLoaded()) {
            return
        }

        setStatus("Unloading offline model...")
        when (val unloadResult = OfflineEngineManager.unloadModel()) {
            OfflineEngineResult.Success -> {
                setTransientStatus("Offline model unloaded")
            }

            is OfflineEngineResult.Failure -> {
                clearStatus()
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to unload offline model: ${unloadResult.error.shortReason}"
                    )
                }
            }
        }
    }

    private fun offlineModelNotLoadedMessage(reason: String): String =
        "Offline model not loaded: $reason. Tap Settings → Offline Engine to select or re-add the model."

    private fun setStatus(message: String) {
        statusMessageJob?.cancel()
        _uiState.update { it.copy(statusMessage = message) }
    }

    private fun setTransientStatus(message: String, durationMs: Long = 1500L) {
        statusMessageJob?.cancel()
        _uiState.update { it.copy(statusMessage = message) }
        statusMessageJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { state ->
                if (state.statusMessage == message) {
                    state.copy(statusMessage = null)
                } else {
                    state
                }
            }
        }
    }

    private fun clearStatus() {
        statusMessageJob?.cancel()
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun mergeModelOptions(
        onlineNames: List<String>,
        offlinePath: String
    ): List<String> {
        val onlineEntries = onlineNames
            .ifEmpty { listOf(DEFAULT_ONLINE_MODEL) }
            .map { "$ONLINE_PREFIX$it" }
        val offlineEntry = offlinePath
            .takeIf { it.isNotBlank() }
            ?.let { path ->
                val label = path.substringAfterLast('/').substringBefore('?').ifBlank { "Local GGUF" }
                "$OFFLINE_PREFIX$label"
            }
        return buildList {
            addAll(onlineEntries)
            if (offlineEntry != null) {
                add(offlineEntry)
            }
        }
    }

    private fun buildDynamicSystemPrompt(): String {
        val now = LocalDateTime.now(clock)
        val dynamicTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US))
        val dynamicDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
        val dynamicDay = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))
        return SYSTEM_PROMPT_TEMPLATE
            .replace("[DYNAMIC_TIME]", dynamicTime)
            .replace("[DYNAMIC_DATE]", dynamicDate)
            .replace("[DYNAMIC_DAY]", dynamicDay)
    }
}
