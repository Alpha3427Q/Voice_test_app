package com.alice.ai

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alice.ai.data.settings.SettingsRepository
import com.alice.ai.data.settings.StoredSettings
import com.alice.ai.ui.chat.AudioPlayerBottomSheet
import com.alice.ai.ui.chat.ChatScreen
import com.alice.ai.ui.history.ChatHistoryScreen
import com.alice.ai.ui.settings.SettingsScreen
import com.alice.ai.viewmodel.ChatViewModel
import com.alice.ai.viewmodel.OnlineSettingsValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "alice_settings"
private const val DEFAULT_TTS_URL = "https://lonekirito-asuna3456.hf.space/speak"
private const val NETWORK_TIMEOUT_MINUTES = 45L
private const val HISTORY_ROUTE = "history"

private val STORAGE_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_AUDIO
)

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            AliceDarkTheme {
                AliceApp(
                    prefs = prefs,
                    chatViewModel = chatViewModel
                )
            }
        }
    }
}

private enum class AliceDestination(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
    Settings("settings", "Settings", Icons.Outlined.Settings)
}

@Composable
private fun AliceApp(
    prefs: SharedPreferences,
    chatViewModel: ChatViewModel
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(prefs) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(NETWORK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .readTimeout(NETWORK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(NETWORK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .callTimeout(NETWORK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }
    val uiState by chatViewModel.uiState.collectAsState()

    var ttsApiUrl by rememberSaveable {
        mutableStateOf(settingsRepository.getTtsApiUrl(DEFAULT_TTS_URL))
    }
    var currentTtsFile by remember { mutableStateOf<File?>(null) }

    val initialOllamaUrl = remember { settingsRepository.getOllamaServerUrl() }
    val initialOllamaApiKey = remember { settingsRepository.ollamaApiKey }
    val initialOfflineModel = remember { settingsRepository.getOfflineGgufUri() }
    val initialSelectedModel = remember { settingsRepository.getSelectedModel() }
    val normalizedInitialSelectedModel = remember {
        initialSelectedModel.removePrefix("Online: ").trim()
    }
    var ollamaServerUrlDraft by rememberSaveable { mutableStateOf(initialOllamaUrl) }
    var ollamaApiKeyDraft by rememberSaveable { mutableStateOf(initialOllamaApiKey) }
    var hasRestoredSelection by rememberSaveable {
        mutableStateOf(initialSelectedModel.isBlank())
    }

    LaunchedEffect(Unit) {
        chatViewModel.attachAppContext(context)
        chatViewModel.attachSettingsRepository(settingsRepository)
        chatViewModel.updateOllamaServerUrl(initialOllamaUrl)
        chatViewModel.updateOllamaApiKey(initialOllamaApiKey)
        chatViewModel.updateOfflineModelPath(initialOfflineModel)
        if (initialOllamaUrl.isNotBlank()) {
            chatViewModel.refreshOnlineModels()
        }
    }

    LaunchedEffect(normalizedInitialSelectedModel, uiState.availableModels, hasRestoredSelection) {
        if (hasRestoredSelection || uiState.availableModels.isEmpty()) {
            return@LaunchedEffect
        }
        if (
            normalizedInitialSelectedModel in uiState.availableModels &&
            !normalizedInitialSelectedModel.startsWith("Offline: ")
        ) {
            chatViewModel.selectModel(normalizedInitialSelectedModel)
        }
        hasRestoredSelection = true
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not allow persisted permissions; keep URI anyway.
        }
        chatViewModel.updateOfflineModelPath(uri.toString())
        Toast.makeText(context, "Offline model selected", Toast.LENGTH_SHORT).show()
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            modelPickerLauncher.launch(arrayOf("*/*"))
        } else {
            Toast.makeText(context, "Storage permissions are required to import GGUF models", Toast.LENGTH_SHORT)
                .show()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Selected file: ${uri.lastPathSegment ?: uri}", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentTtsFile?.delete()
        }
    }

    val destinations = remember { AliceDestination.entries.toList() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AliceDestination.Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(AliceDestination.Chat.route) {
                ChatScreen(
                    messages = uiState.messages,
                    inputText = uiState.inputText,
                    selectedModel = uiState.selectedModel,
                    availableModels = uiState.availableModels,
                    isGenerating = uiState.isGenerating,
                    isWaitingForResponse = uiState.isWaitingForResponse,
                    activeContextCount = uiState.activeContext.size,
                    statusMessage = uiState.statusMessage,
                    errorMessage = uiState.errorMessage,
                    onInputTextChange = chatViewModel::onInputChanged,
                    onModelSelected = chatViewModel::selectModel,
                    onModelDropdownOpened = chatViewModel::onModelDropdownOpened,
                    onUploadClick = { uploadLauncher.launch("*/*") },
                    onSendClick = chatViewModel::sendMessage,
                    onClearError = chatViewModel::clearError,
                    onReadAloud = { text ->
                        val safeText = text.trim()
                        if (safeText.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val endpoint = ttsApiUrl.ifBlank { DEFAULT_TTS_URL }
                                    val wavFile = withContext(Dispatchers.IO) {
                                        downloadTtsWav(
                                            client = httpClient,
                                            endpoint = endpoint,
                                            text = safeText,
                                            cacheDir = context.cacheDir
                                        )
                                    }
                                    currentTtsFile?.delete()
                                    currentTtsFile = wavFile
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Read aloud failed: ${error.message ?: "Unknown error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    onDeleteUserMessage = chatViewModel::deleteUserMessage,
                    onShareMessage = { message ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share message"))
                    },
                    onHistoryClick = { navController.navigate(HISTORY_ROUTE) },
                    onNewChatClick = chatViewModel::startNewChat
                )
            }

            composable(HISTORY_ROUTE) {
                ChatHistoryScreen(
                    sessions = uiState.sessions.map { session ->
                        com.alice.ai.data.local.ChatSessionSummary(
                            id = session.id,
                            title = session.title,
                            createdAt = session.createdAt
                        )
                    },
                    onBack = { navController.popBackStack() },
                    onOpenSession = { sessionId ->
                        chatViewModel.openChatSession(sessionId)
                        navController.popBackStack()
                    },
                    onDeleteSession = chatViewModel::deleteChatSession
                )
            }

            composable(AliceDestination.Settings.route) {
                SettingsScreen(
                    ollamaServerUrl = ollamaServerUrlDraft,
                    ollamaApiKey = ollamaApiKeyDraft,
                    ttsApiUrl = ttsApiUrl,
                    ggufModelPath = uiState.offlineModelPath,
                    onOllamaServerUrlChange = { ollamaServerUrlDraft = it },
                    onOllamaApiKeyChange = { ollamaApiKeyDraft = it },
                    onTtsApiUrlChange = { ttsApiUrl = it },
                    onAddModelClick = { storagePermissionLauncher.launch(STORAGE_PERMISSIONS) },
                    onRemoveModelClick = {
                        chatViewModel.updateOfflineModelPath("")
                        Toast.makeText(context, "Offline model removed and unloaded", Toast.LENGTH_SHORT)
                            .show()
                    },
                    onSaveClick = {
                        val trimmedOllamaUrl = ollamaServerUrlDraft.trim()
                        val trimmedApiKey = ollamaApiKeyDraft.trim()
                        val trimmedTtsUrl = ttsApiUrl.trim()

                        scope.launch {
                            when (
                                val validation = chatViewModel.validateOnlineSettings(
                                    ollamaUrl = trimmedOllamaUrl,
                                    ollamaApiKey = trimmedApiKey
                                )
                            ) {
                                OnlineSettingsValidationResult.Success -> {
                                    settingsRepository.save(
                                        StoredSettings(
                                            ollamaServerUrl = trimmedOllamaUrl,
                                            ollamaApiKey = trimmedApiKey,
                                            ttsApiUrl = trimmedTtsUrl,
                                            offlineGgufUri = uiState.offlineModelPath,
                                            selectedModel = uiState.selectedModel
                                        )
                                    )
                                    chatViewModel.updateOllamaApiKey(trimmedApiKey)
                                    chatViewModel.updateOllamaServerUrl(trimmedOllamaUrl)
                                    if (trimmedOllamaUrl.isNotBlank()) {
                                        chatViewModel.refreshOnlineModels()
                                    }
                                    val successMessage = if (trimmedOllamaUrl.isBlank()) {
                                        "Settings saved"
                                    } else {
                                        "Connection successful"
                                    }
                                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                                }

                                is OnlineSettingsValidationResult.Failure -> {
                                    Toast.makeText(context, validation.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    currentTtsFile?.let { file ->
        AudioPlayerBottomSheet(
            mediaUrl = Uri.fromFile(file).toString(),
            onDismissRequest = {
                file.delete()
                if (currentTtsFile == file) {
                    currentTtsFile = null
                }
            }
        )
    }
}

private fun downloadTtsWav(
    client: OkHttpClient,
    endpoint: String,
    text: String,
    cacheDir: File
): File {
    val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
    val request = Request.Builder()
        .url("${endpoint.trim()}?text=$encodedText")
        .get()
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error("TTS request failed (${response.code})")
        }
        val body = response.body ?: error("TTS response was empty")
        val outputFile = File(cacheDir, "alice_tts_${System.currentTimeMillis()}.wav")
        outputFile.writeBytes(body.bytes())
        return outputFile
    }
}

@Composable
private fun AliceDarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = aliceDarkColorScheme(),
        content = content
    )
}

private fun aliceDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF7F5AF0),
    onPrimary = Color(0xFFEBE4FF),
    primaryContainer = Color(0xFF2A1E52),
    onPrimaryContainer = Color(0xFFE1D7FF),
    secondary = Color(0xFF2CB1FF),
    onSecondary = Color(0xFF001E32),
    secondaryContainer = Color(0xFF123C5E),
    onSecondaryContainer = Color(0xFFD1EEFF),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE6EBFF),
    surface = Color(0xFF101624),
    onSurface = Color(0xFFE3E8FA),
    surfaceVariant = Color(0xFF1A2338),
    onSurfaceVariant = Color(0xFFB9C4DE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)
