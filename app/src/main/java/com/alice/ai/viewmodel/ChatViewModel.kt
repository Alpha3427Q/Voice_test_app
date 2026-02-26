package com.alice.ai.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alice.ai.data.local.AliceDatabase
import com.alice.ai.data.local.ChatStorageRepository
import com.alice.ai.data.local.MessageEntity
import com.alice.ai.data.model.ModelPathResult
import com.alice.ai.data.model.ModelRepository
import com.alice.ai.data.offline.LlamaJniBridge
import com.alice.ai.data.offline.OfflineLlamaEngine
import com.alice.ai.data.offline.OfflineLlamaError
import com.alice.ai.data.offline.OfflineLlamaGenerateResult
import com.alice.ai.data.offline.OfflineLlamaResult
import com.alice.ai.data.online.OllamaChatStreamResult
import com.alice.ai.data.online.OllamaConnectionResult
import com.alice.ai.data.online.OllamaMessage
import com.alice.ai.data.online.OnlineOllamaService
import com.alice.ai.data.settings.SettingsRepository
import com.alice.ai.ui.chat.ChatMessage
import com.alice.ai.ui.chat.ChatRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "ChatViewModel"
private const val OFFLINE_PREFIX = "Offline: "
private const val LEGACY_ONLINE_PREFIX = "Online: "
private const val DEFAULT_ONLINE_MODEL = "llama3"
private const val CONTEXT_WINDOW_SIZE = 7
private const val MAX_STORED_MESSAGES = 200
private const val OLLAMA_TIMEOUT_MINUTES = 45L

private const val SYSTEM_PROMPT_TEMPLATE =
    "Your name is Alice an AI assistant created by Adwaith also known as Aromal and Alpha. " +
        "Current time is [DYNAMIC_TIME] and date is [DYNAMIC_DATE] and day is [DYNAMIC_DAY]."

enum class EngineMode {
    Online,
    Offline
}

sealed class OnlineSettingsValidationResult {
    data object Success : OnlineSettingsValidationResult()
    data class Failure(val message: String) : OnlineSettingsValidationResult()
}

data class ChatSessionUi(
    val id: String,
    val title: String,
    val createdAt: Long
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val activeContext: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val ollamaServerUrl: String = "",
    val ollamaApiKey: String = "",
    val offlineModelPath: String = "",
    val resolvedOfflineModelPath: String = "",
    val availableModels: List<String> = listOf(DEFAULT_ONLINE_MODEL),
    val selectedModel: String = DEFAULT_ONLINE_MODEL,
    val mode: EngineMode = EngineMode.Online,
    val isGenerating: Boolean = false,
    val isWaitingForResponse: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val currentSessionId: String = "",
    val sessions: List<ChatSessionUi> = emptyList()
)

class ChatViewModel(
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isWaitingForResponse = MutableStateFlow(false)
    val isWaitingForResponse: StateFlow<Boolean> = _isWaitingForResponse.asStateFlow()

    private var settingsRepository: SettingsRepository? = null
    private var modelRepository: ModelRepository? = null
    private var chatStorageRepository: ChatStorageRepository? = null

    private val onlineOllamaService = OnlineOllamaService(
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(OLLAMA_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .readTimeout(OLLAMA_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(OLLAMA_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .callTimeout(OLLAMA_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    )
    private val offlineLlamaEngine = OfflineLlamaEngine()

    private var statusMessageJob: Job? = null
    private var generationJob: Job? = null
    private var onlineModelNames: List<String> = listOf(DEFAULT_ONLINE_MODEL)

    fun attachAppContext(context: Context) {
        if (modelRepository == null) {
            modelRepository = ModelRepository(context.applicationContext)
        }
        if (chatStorageRepository == null) {
            val database = AliceDatabase.getInstance(context.applicationContext)
            chatStorageRepository = ChatStorageRepository(database.chatDao())
            viewModelScope.launch {
                loadOrCreateInitialSession()
            }
        }
    }

    fun attachSettingsRepository(repository: SettingsRepository) {
        settingsRepository = repository
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun updateOllamaApiKey(apiKey: String) {
        _uiState.update { it.copy(ollamaApiKey = apiKey.trim(), errorMessage = null) }
    }

    fun updateOllamaServerUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            onlineModelNames = listOf(DEFAULT_ONLINE_MODEL)
            _uiState.update { state ->
                val mergedModels = mergeModelOptions(onlineModelNames, state.offlineModelPath)
                val selected = selectFallbackModel(
                    currentSelected = state.selectedModel,
                    mode = state.mode,
                    mergedModels = mergedModels
                )
                settingsRepository?.saveSelectedModel(selected)
                state.copy(
                    ollamaServerUrl = "",
                    availableModels = mergedModels,
                    selectedModel = selected,
                    mode = if (selected.startsWith(OFFLINE_PREFIX)) EngineMode.Offline else EngineMode.Online,
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update {
            val normalizedSelected = normalizeLegacyOnlineLabel(it.selectedModel)
            it.copy(
                ollamaServerUrl = trimmed,
                selectedModel = normalizedSelected,
                errorMessage = null
            )
        }
    }

    fun updateOfflineModelPath(path: String) {
        val trimmed = path.trim()
        val previousPath = _uiState.value.offlineModelPath

        _uiState.update { state ->
            val mergedModels = mergeModelOptions(onlineModelNames, trimmed)
            val fallback = selectFallbackModel(
                currentSelected = state.selectedModel,
                mode = state.mode,
                mergedModels = mergedModels
            )
            settingsRepository?.saveSelectedModel(fallback)
            settingsRepository?.saveOfflineGgufUri(trimmed)
            state.copy(
                offlineModelPath = trimmed,
                resolvedOfflineModelPath = if (trimmed == previousPath) {
                    state.resolvedOfflineModelPath
                } else {
                    ""
                },
                availableModels = mergedModels,
                selectedModel = fallback,
                mode = if (fallback.startsWith(OFFLINE_PREFIX)) EngineMode.Offline else EngineMode.Online
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
        val normalizedModel = normalizeLegacyOnlineLabel(model)
        if (normalizedModel == state.selectedModel) {
            return
        }

        if (normalizedModel.startsWith(OFFLINE_PREFIX)) {
            if (state.offlineModelPath.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Model file not found") }
                return
            }

            settingsRepository?.saveSelectedModel(normalizedModel)
            _uiState.update {
                it.copy(
                    selectedModel = normalizedModel,
                    mode = EngineMode.Offline,
                    errorMessage = null
                )
            }

            viewModelScope.launch {
                when (val prepared = prepareAndLoadOfflineModel()) {
                    is OfflinePreparationResult.Success -> Unit
                    is OfflinePreparationResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = prepared.message) }
                    }
                }
            }
        } else {
            settingsRepository?.saveSelectedModel(normalizedModel)
            _uiState.update {
                it.copy(
                    selectedModel = normalizedModel,
                    mode = EngineMode.Online,
                    errorMessage = null
                )
            }

            viewModelScope.launch {
                unloadOfflineModelWithStatus()
                refreshOnlineModels()
            }
        }
    }

    fun onModelDropdownOpened() {
        val state = _uiState.value
        if (state.mode == EngineMode.Online && state.ollamaServerUrl.isNotBlank()) {
            refreshOnlineModels()
        }
    }

    fun refreshOnlineModels() {
        val state = _uiState.value
        val url = state.ollamaServerUrl.trim()
        if (url.isBlank()) {
            return
        }

        viewModelScope.launch {
            val result = onlineOllamaService.fetchModelNames(
                url,
                resolveOllamaApiKey(state.ollamaApiKey)
            )
            val fetchedModels = result.getOrNull()
            if (fetchedModels != null) {
                onlineModelNames = fetchedModels
                _uiState.update { current ->
                    val mergedModels = mergeModelOptions(onlineModelNames, current.offlineModelPath)
                    val selected = selectFallbackModel(
                        currentSelected = current.selectedModel,
                        mode = current.mode,
                        mergedModels = mergedModels
                    )
                    settingsRepository?.saveSelectedModel(selected)
                    current.copy(
                        availableModels = mergedModels,
                        selectedModel = selected,
                        mode = if (selected.startsWith(OFFLINE_PREFIX)) EngineMode.Offline else EngineMode.Online,
                        errorMessage = null
                    )
                }
            } else {
                val message = result.exceptionOrNull()?.message ?: "Invalid endpoint"
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    suspend fun validateOnlineSettings(
        ollamaUrl: String,
        ollamaApiKey: String
    ): OnlineSettingsValidationResult {
        val normalizedUrl = ollamaUrl.trim()
        if (normalizedUrl.isBlank()) {
            return OnlineSettingsValidationResult.Success
        }

        return when (onlineOllamaService.validateConnection(normalizedUrl, ollamaApiKey.trim())) {
            OllamaConnectionResult.Success -> {
                _uiState.update { it.copy(errorMessage = null) }
                setTransientStatus("Connection successful")
                OnlineSettingsValidationResult.Success
            }

            OllamaConnectionResult.InvalidApiKey -> {
                _uiState.update { it.copy(errorMessage = "Invalid API key") }
                OnlineSettingsValidationResult.Failure("Invalid API key")
            }

            OllamaConnectionResult.InvalidOllamaUrl,
            OllamaConnectionResult.InvalidEndpoint -> {
                _uiState.update { it.copy(errorMessage = "Invalid Ollama URL") }
                OnlineSettingsValidationResult.Failure("Invalid Ollama URL")
            }

            OllamaConnectionResult.ServerUnreachable -> {
                _uiState.update { it.copy(errorMessage = "Server unreachable") }
                OnlineSettingsValidationResult.Failure("Server unreachable")
            }

            OllamaConnectionResult.NoModelsReturned -> {
                _uiState.update { it.copy(errorMessage = "No models returned") }
                OnlineSettingsValidationResult.Failure("No models returned")
            }

            is OllamaConnectionResult.Failure -> {
                _uiState.update { it.copy(errorMessage = "Invalid endpoint") }
                OnlineSettingsValidationResult.Failure("Invalid endpoint")
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val userText = state.inputText.trim()
        if (userText.isBlank() || state.isGenerating) {
            return
        }
        if (state.currentSessionId.isBlank()) {
            viewModelScope.launch {
                loadOrCreateInitialSession()
            }
            _uiState.update { it.copy(errorMessage = "Chat session is initializing. Try again.") }
            return
        }

        val userMessage = ChatMessage(role = ChatRole.User, content = userText)
        _uiState.update { current ->
            val updatedMessages = (current.messages + userMessage).takeLast(MAX_STORED_MESSAGES)
            current.copy(
                messages = updatedMessages,
                activeContext = updatedMessages.takeLast(CONTEXT_WINDOW_SIZE),
                inputText = "",
                isGenerating = true,
                isWaitingForResponse = true,
                errorMessage = null
            )
        }
        _isWaitingForResponse.value = true

        viewModelScope.launch {
            persistMessage(
                sessionId = _uiState.value.currentSessionId,
                message = userMessage
            )
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val stateAtStart = _uiState.value
            val contextWindow = stateAtStart.messages.takeLast(CONTEXT_WINDOW_SIZE)
            val systemPrompt = buildDynamicSystemPrompt()

            when (stateAtStart.mode) {
                EngineMode.Online -> generateOnlineReplyStreaming(
                    stateAtStart = stateAtStart,
                    contextWindow = contextWindow,
                    systemPrompt = systemPrompt
                )

                EngineMode.Offline -> generateOfflineReply(
                    contextWindow = contextWindow,
                    systemPrompt = systemPrompt
                )
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, isWaitingForResponse = false) }
        _isWaitingForResponse.value = false
    }

    fun deleteUserMessage(messageId: String) {
        _uiState.update { state ->
            val remaining = state.messages.filterNot { it.id == messageId }
            state.copy(
                messages = remaining,
                activeContext = remaining.takeLast(CONTEXT_WINDOW_SIZE)
            )
        }

        viewModelScope.launch {
            chatStorageRepository?.deleteMessage(messageId)
        }
    }

    fun startNewChat() {
        val state = _uiState.value
        if (state.isGenerating) {
            setTransientStatus("Wait for current response to finish")
            return
        }
        viewModelScope.launch {
            val repository = chatStorageRepository ?: return@launch
            val session = repository.createSession()
            val sessions = repository.listSessions().map { info ->
                ChatSessionUi(id = info.id, title = info.title, createdAt = info.createdAt)
            }
            _uiState.update {
                it.copy(
                    currentSessionId = session.id,
                    messages = emptyList(),
                    activeContext = emptyList(),
                    sessions = sessions,
                    errorMessage = null
                )
            }
        }
    }

    fun openChatSession(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }
        val state = _uiState.value
        if (state.isGenerating) {
            setTransientStatus("Wait for current response to finish")
            return
        }
        viewModelScope.launch {
            val repository = chatStorageRepository ?: return@launch
            val session = repository.ensureSession(sessionId)
            val messages = repository.getMessages(session.id)
                .map { it.toChatMessage() }
                .takeLast(MAX_STORED_MESSAGES)
            val sessions = repository.listSessions().map { info ->
                ChatSessionUi(id = info.id, title = info.title, createdAt = info.createdAt)
            }
            _uiState.update {
                it.copy(
                    currentSessionId = session.id,
                    messages = messages,
                    activeContext = messages.takeLast(CONTEXT_WINDOW_SIZE),
                    sessions = sessions,
                    errorMessage = null
                )
            }
        }
    }

    fun deleteChatSession(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val repository = chatStorageRepository ?: return@launch
            repository.deleteSession(sessionId)

            val sessions = repository.listSessions()
            if (sessions.isEmpty()) {
                val created = repository.createSession()
                _uiState.update {
                    it.copy(
                        currentSessionId = created.id,
                        messages = emptyList(),
                        activeContext = emptyList(),
                        sessions = listOf(
                            ChatSessionUi(
                                id = created.id,
                                title = created.title,
                                createdAt = created.createdAt
                            )
                        )
                    )
                }
                return@launch
            }

            val current = _uiState.value.currentSessionId
            val keepCurrent = sessions.any { it.id == current }
            val nextSession = if (keepCurrent) {
                sessions.first { it.id == current }
            } else {
                sessions.first()
            }
            val nextMessages = repository.getMessages(nextSession.id)
                .map { it.toChatMessage() }
                .takeLast(MAX_STORED_MESSAGES)
            _uiState.update {
                it.copy(
                    currentSessionId = nextSession.id,
                    messages = nextMessages,
                    activeContext = nextMessages.takeLast(CONTEXT_WINDOW_SIZE),
                    sessions = sessions.map { info ->
                        ChatSessionUi(id = info.id, title = info.title, createdAt = info.createdAt)
                    }
                )
            }
        }
    }

    override fun onCleared() {
        statusMessageJob?.cancel()
        generationJob?.cancel()
        runCatching { LlamaJniBridge.unloadModel() }
        super.onCleared()
    }

    private suspend fun loadOrCreateInitialSession() {
        val repository = chatStorageRepository ?: return
        val session = repository.ensureSession(_uiState.value.currentSessionId.ifBlank { null })
        val messages = repository.getMessages(session.id)
            .map { it.toChatMessage() }
            .takeLast(MAX_STORED_MESSAGES)
        val sessions = repository.listSessions().map { info ->
            ChatSessionUi(id = info.id, title = info.title, createdAt = info.createdAt)
        }
        _uiState.update {
            it.copy(
                currentSessionId = session.id,
                messages = messages,
                activeContext = messages.takeLast(CONTEXT_WINDOW_SIZE),
                sessions = sessions
            )
        }
    }

    private suspend fun generateOnlineReplyStreaming(
        stateAtStart: ChatUiState,
        contextWindow: List<ChatMessage>,
        systemPrompt: String
    ) {
        val url = stateAtStart.ollamaServerUrl.trim()
        if (url.isBlank()) {
            finishGenerationWithError("Invalid endpoint")
            return
        }

        val selectedModel = normalizeLegacyOnlineLabel(stateAtStart.selectedModel)
        if (selectedModel.isBlank() || selectedModel.startsWith(OFFLINE_PREFIX)) {
            finishGenerationWithError("No models returned")
            return
        }

        val requestMessages = buildList {
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

        val assistantMessage = ChatMessage(role = ChatRole.Assistant, content = "")
        _uiState.update { current ->
            val updatedMessages = (current.messages + assistantMessage).takeLast(MAX_STORED_MESSAGES)
            current.copy(
                messages = updatedMessages,
                activeContext = updatedMessages.takeLast(CONTEXT_WINDOW_SIZE)
            )
        }

        val assistantMessageId = assistantMessage.id
        var emittedAnyChunk = false

        when (
            val streamResult = onlineOllamaService.streamChatResponse(
                baseUrl = url,
                apiKey = resolveOllamaApiKey(stateAtStart.ollamaApiKey),
                model = selectedModel,
                messages = requestMessages,
                onDelta = { delta ->
                    if (!emittedAnyChunk) {
                        emittedAnyChunk = true
                        setWaitingForResponse(false)
                    }
                    appendAssistantChunk(assistantMessageId, delta)
                }
            )
        ) {
            OllamaChatStreamResult.Success -> {
                val finalText = _uiState.value.messages.firstOrNull { it.id == assistantMessageId }
                    ?.content
                    .orEmpty()
                    .trim()
                if (!emittedAnyChunk || finalText.isBlank()) {
                    removeMessageById(assistantMessageId)
                    finishGenerationWithError("Online engine returned an empty response")
                } else {
                    persistMessage(
                        sessionId = _uiState.value.currentSessionId,
                        message = ChatMessage(
                            id = assistantMessageId,
                            role = ChatRole.Assistant,
                            content = finalText
                        )
                    )
                    finishGeneration()
                }
            }

            is OllamaChatStreamResult.Failure -> {
                if (!emittedAnyChunk) {
                    removeMessageById(assistantMessageId)
                } else {
                    val partialText = _uiState.value.messages.firstOrNull { it.id == assistantMessageId }
                        ?.content
                        .orEmpty()
                        .trim()
                    if (partialText.isNotBlank()) {
                        persistMessage(
                            sessionId = _uiState.value.currentSessionId,
                            message = ChatMessage(
                                id = assistantMessageId,
                                role = ChatRole.Assistant,
                                content = partialText
                            )
                        )
                    }
                }
                finishGenerationWithError(streamResult.message)
            }
        }
    }

    private suspend fun generateOfflineReply(
        contextWindow: List<ChatMessage>,
        systemPrompt: String
    ) {
        val preparation = prepareAndLoadOfflineModel()
        if (preparation is OfflinePreparationResult.Failure) {
            setWaitingForResponse(false)
            appendAssistantMessage(
                "Offline model not loaded: ${preparation.message}. Tap Settings \u2192 Offline Engine to select or re-add the model."
            )
            finishGeneration()
            return
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

        val result = offlineLlamaEngine.generateText(
            prompt = prompt,
            maxTokens = 512,
            temperature = 0.7f
        )
        setWaitingForResponse(false)

        when (result) {
            is OfflineLlamaGenerateResult.Success -> {
                val output = result.text.trim()
                if (output.isBlank()) {
                    appendAssistantMessage("Offline engine error: no output produced (see logs).")
                } else {
                    appendAssistantMessage(output)
                }
            }

            is OfflineLlamaGenerateResult.Failure -> {
                val message = when (result.error) {
                    OfflineLlamaError.ModelFileNotFound -> "Model file not found"
                    OfflineLlamaError.PermissionDenied -> "Permission denied"
                    OfflineLlamaError.OutOfMemory -> "Out of memory"
                    OfflineLlamaError.ModelLoadFailure -> "Model load failure"
                    OfflineLlamaError.NativeLibraryMissing -> "Native library missing"
                    OfflineLlamaError.ModelNotLoaded -> "Model not loaded"
                    OfflineLlamaError.NoOutput -> "Offline engine error: no output produced (see logs)."
                    is OfflineLlamaError.Unknown -> result.error.message
                }

                val friendlyMessage = if (result.error == OfflineLlamaError.NoOutput) {
                    message
                } else {
                    "Offline model not loaded: $message. Tap Settings \u2192 Offline Engine to select or re-add the model."
                }
                appendAssistantMessage(friendlyMessage)
            }
        }

        finishGeneration()
    }

    private suspend fun prepareAndLoadOfflineModel(): OfflinePreparationResult {
        val state = _uiState.value
        val offlineModelPath = state.offlineModelPath.trim()
        if (offlineModelPath.isBlank()) {
            return OfflinePreparationResult.Failure("Model file not found")
        }

        val repository = modelRepository
            ?: return OfflinePreparationResult.Failure("Model load failure")

        val resolvedPath = if (state.resolvedOfflineModelPath.isNotBlank()) {
            state.resolvedOfflineModelPath
        } else {
            when (val pathResult = repository.resolveOfflineModelPath(offlineModelPath)) {
                is ModelPathResult.Success -> {
                    _uiState.update { it.copy(resolvedOfflineModelPath = pathResult.filePath) }
                    pathResult.filePath
                }

                is ModelPathResult.Failure -> {
                    return OfflinePreparationResult.Failure(pathResult.detail)
                }
            }
        }

        if (offlineLlamaEngine.isModelLoaded(resolvedPath)) {
            return OfflinePreparationResult.Success
        }

        setStatus("Loading offline model...")
        return when (val load = offlineLlamaEngine.loadModel(resolvedPath)) {
            OfflineLlamaResult.Success -> {
                setTransientStatus("Offline model loaded")
                OfflinePreparationResult.Success
            }

            is OfflineLlamaResult.Failure -> {
                clearStatus()
                val message = when (load.error) {
                    OfflineLlamaError.ModelFileNotFound -> "Model file not found"
                    OfflineLlamaError.PermissionDenied -> "Permission denied"
                    OfflineLlamaError.OutOfMemory -> "Out of memory"
                    OfflineLlamaError.ModelLoadFailure -> "Model load failure"
                    OfflineLlamaError.NativeLibraryMissing -> "Native library missing"
                    OfflineLlamaError.ModelNotLoaded -> "Model not loaded"
                    OfflineLlamaError.NoOutput -> "Offline engine error: no output produced (see logs)."
                    is OfflineLlamaError.Unknown -> load.error.message
                }
                Log.e(TAG, "Offline load failed: $message")
                OfflinePreparationResult.Failure(message)
            }
        }
    }

    private suspend fun unloadOfflineModelWithStatus() {
        if (!LlamaJniBridge.isModelLoadedSafe()) {
            return
        }

        val targetPath = _uiState.value.resolvedOfflineModelPath.ifBlank { null }
        setStatus("Unloading offline model...")
        when (val unload = offlineLlamaEngine.unloadModel(targetPath)) {
            OfflineLlamaResult.Success -> {
                setTransientStatus("Offline model unloaded")
            }

            is OfflineLlamaResult.Failure -> {
                clearStatus()
                val message = when (unload.error) {
                    OfflineLlamaError.ModelFileNotFound -> "Model file not found"
                    OfflineLlamaError.PermissionDenied -> "Permission denied"
                    OfflineLlamaError.OutOfMemory -> "Out of memory"
                    OfflineLlamaError.ModelLoadFailure -> "Model load failure"
                    OfflineLlamaError.NativeLibraryMissing -> "Native library missing"
                    OfflineLlamaError.ModelNotLoaded -> "Model not loaded"
                    OfflineLlamaError.NoOutput -> "Offline engine error: no output produced (see logs)."
                    is OfflineLlamaError.Unknown -> unload.error.message
                }
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    private fun appendAssistantChunk(messageId: String, delta: String) {
        if (delta.isBlank()) {
            return
        }
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val index = updatedMessages.indexOfLast { it.id == messageId }
            if (index >= 0) {
                val current = updatedMessages[index]
                updatedMessages[index] = current.copy(content = current.content + delta)
            }
            val finalMessages = updatedMessages.takeLast(MAX_STORED_MESSAGES)
            state.copy(
                messages = finalMessages,
                activeContext = finalMessages.takeLast(CONTEXT_WINDOW_SIZE)
            )
        }
    }

    private fun appendAssistantMessage(content: String) {
        val message = ChatMessage(role = ChatRole.Assistant, content = content)
        _uiState.update { state ->
            val updatedMessages = (state.messages + message).takeLast(MAX_STORED_MESSAGES)
            state.copy(
                messages = updatedMessages,
                activeContext = updatedMessages.takeLast(CONTEXT_WINDOW_SIZE)
            )
        }
        persistMessage(
            sessionId = _uiState.value.currentSessionId,
            message = message
        )
    }

    private fun removeMessageById(messageId: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.filterNot { it.id == messageId }
            state.copy(
                messages = updatedMessages,
                activeContext = updatedMessages.takeLast(CONTEXT_WINDOW_SIZE)
            )
        }
    }

    private fun finishGeneration() {
        _uiState.update { it.copy(isGenerating = false, isWaitingForResponse = false) }
        _isWaitingForResponse.value = false
    }

    private fun finishGenerationWithError(message: String) {
        _uiState.update {
            it.copy(
                isGenerating = false,
                isWaitingForResponse = false,
                errorMessage = message
            )
        }
        _isWaitingForResponse.value = false
    }

    private fun setWaitingForResponse(enabled: Boolean) {
        _isWaitingForResponse.value = enabled
        _uiState.update { it.copy(isWaitingForResponse = enabled) }
    }

    private fun resolveOllamaApiKey(preferred: String): String {
        val stateKey = preferred.trim()
        if (stateKey.isNotBlank()) {
            return stateKey
        }
        return settingsRepository?.ollamaApiKey?.trim().orEmpty()
    }

    private fun mergeModelOptions(
        onlineNames: List<String>,
        offlinePath: String
    ): List<String> {
        val normalizedOnline = onlineNames
            .map { normalizeLegacyOnlineLabel(it) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(DEFAULT_ONLINE_MODEL) }

        val offlineEntry = offlinePath
            .takeIf { it.isNotBlank() }
            ?.let { path ->
                val label = path.substringAfterLast('/').substringBefore('?').ifBlank { "Local GGUF" }
                "$OFFLINE_PREFIX$label"
            }

        return buildList {
            addAll(normalizedOnline)
            if (offlineEntry != null) {
                add(offlineEntry)
            }
        }.distinct()
    }

    private fun selectFallbackModel(
        currentSelected: String,
        mode: EngineMode,
        mergedModels: List<String>
    ): String {
        val normalizedCurrent = normalizeLegacyOnlineLabel(currentSelected)
        if (normalizedCurrent in mergedModels) {
            return normalizedCurrent
        }

        if (mode == EngineMode.Offline) {
            val offlineEntry = mergedModels.lastOrNull { it.startsWith(OFFLINE_PREFIX) }
            if (offlineEntry != null) {
                return offlineEntry
            }
        }

        return mergedModels.firstOrNull().orEmpty().ifBlank { DEFAULT_ONLINE_MODEL }
    }

    private fun normalizeLegacyOnlineLabel(modelName: String): String {
        return modelName.removePrefix(LEGACY_ONLINE_PREFIX).trim()
    }

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

    private fun persistMessage(sessionId: String, message: ChatMessage) {
        if (sessionId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val repository = chatStorageRepository ?: return@launch
            repository.saveMessage(
                sessionId = sessionId,
                messageId = message.id,
                role = if (message.role == ChatRole.User) "user" else "assistant",
                content = message.content
            )
            if (message.role == ChatRole.User) {
                repository.updateSessionTitleFromMessage(sessionId, message.content)
            }
            val sessions = repository.listSessions().map { info ->
                ChatSessionUi(id = info.id, title = info.title, createdAt = info.createdAt)
            }
            _uiState.update { it.copy(sessions = sessions) }
        }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            role = if (role.equals("user", ignoreCase = true)) ChatRole.User else ChatRole.Assistant,
            content = content
        )
    }

    private sealed class OfflinePreparationResult {
        data object Success : OfflinePreparationResult()
        data class Failure(val message: String) : OfflinePreparationResult()
    }
}
