package com.alice.ai

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
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
import com.alice.ai.ui.chat.ChatScreen
import com.alice.ai.ui.settings.SettingsScreen
import com.alice.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val PREFS_NAME = "alice_settings"
private const val KEY_OLLAMA_URL = "ollama_server_url"
private const val KEY_TTS_URL = "tts_api_url"
private const val KEY_GGUF_URI = "offline_gguf_uri"
private const val KEY_SELECTED_MODEL = "selected_model"
private const val DEFAULT_TTS_URL = "https://lonekirito-asuna3456.hf.space/speak"

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
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val httpClient = remember { OkHttpClient() }
    val mediaPlayerState = remember { mutableStateOf<MediaPlayer?>(null) }
    val uiState by chatViewModel.uiState.collectAsState()

    var ttsApiUrl by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_TTS_URL, DEFAULT_TTS_URL) ?: DEFAULT_TTS_URL)
    }

    val initialOllamaUrl = remember { prefs.getString(KEY_OLLAMA_URL, "") ?: "" }
    val initialOfflineModel = remember { prefs.getString(KEY_GGUF_URI, "") ?: "" }
    val initialSelectedModel = remember { prefs.getString(KEY_SELECTED_MODEL, "") ?: "" }
    var ollamaServerUrlDraft by rememberSaveable {
        mutableStateOf(initialOllamaUrl)
    }
    var hasRestoredSelection by rememberSaveable {
        mutableStateOf(initialSelectedModel.isBlank())
    }

    LaunchedEffect(Unit) {
        chatViewModel.updateOllamaServerUrl(initialOllamaUrl)
        chatViewModel.updateOfflineModelPath(initialOfflineModel)
        if (initialOllamaUrl.isNotBlank()) {
            chatViewModel.refreshOnlineModels()
        }
    }

    LaunchedEffect(initialSelectedModel, uiState.availableModels, hasRestoredSelection) {
        if (hasRestoredSelection || uiState.availableModels.isEmpty()) {
            return@LaunchedEffect
        }
        if (initialSelectedModel in uiState.availableModels) {
            chatViewModel.selectModel(initialSelectedModel)
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
            mediaPlayerState.value?.release()
            mediaPlayerState.value = null
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
                    activeContextCount = uiState.activeContext.size,
                    errorMessage = uiState.errorMessage,
                    onInputTextChange = chatViewModel::onInputChanged,
                    onModelSelected = chatViewModel::selectModel,
                    onUploadClick = { uploadLauncher.launch("*/*") },
                    onSendClick = chatViewModel::sendMessage,
                    onClearError = chatViewModel::clearError,
                    onReadAloud = { text ->
                        val safeText = text.trim()
                        if (safeText.isNotEmpty()) {
                            val endpoint = ttsApiUrl.ifBlank { DEFAULT_TTS_URL }
                            scope.launch {
                                try {
                                    val wavFile = withContext(Dispatchers.IO) {
                                        downloadTtsWav(
                                            client = httpClient,
                                            endpoint = endpoint,
                                            text = safeText,
                                            cacheDir = context.cacheDir
                                        )
                                    }
                                    mediaPlayerState.value?.release()
                                    mediaPlayerState.value = MediaPlayer().apply {
                                        setDataSource(wavFile.absolutePath)
                                        setOnCompletionListener { player ->
                                            player.release()
                                            mediaPlayerState.value = null
                                            wavFile.delete()
                                        }
                                        setOnErrorListener { player, _, _ ->
                                            player.release()
                                            mediaPlayerState.value = null
                                            wavFile.delete()
                                            true
                                        }
                                        prepare()
                                        start()
                                    }
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Read aloud failed: ${error.message ?: "Unknown error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }

            composable(AliceDestination.Settings.route) {
                SettingsScreen(
                    ollamaServerUrl = ollamaServerUrlDraft,
                    ttsApiUrl = ttsApiUrl,
                    ggufModelPath = uiState.offlineModelPath,
                    onOllamaServerUrlChange = { ollamaServerUrlDraft = it },
                    onTtsApiUrlChange = { ttsApiUrl = it },
                    onAddModelClick = { storagePermissionLauncher.launch(STORAGE_PERMISSIONS) },
                    onRemoveModelClick = {
                        chatViewModel.updateOfflineModelPath("")
                        Toast.makeText(context, "Offline model removed and unloaded", Toast.LENGTH_SHORT)
                            .show()
                    },
                    onSaveClick = {
                        chatViewModel.updateOllamaServerUrl(ollamaServerUrlDraft)
                        if (ollamaServerUrlDraft.isNotBlank()) {
                            chatViewModel.refreshOnlineModels()
                        }
                        prefs.edit()
                            .putString(KEY_OLLAMA_URL, ollamaServerUrlDraft.trim())
                            .putString(KEY_TTS_URL, ttsApiUrl.trim())
                            .putString(KEY_GGUF_URI, uiState.offlineModelPath)
                            .putString(KEY_SELECTED_MODEL, uiState.selectedModel)
                            .apply()
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
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
    primary = Color(0xFF88C9FF),
    onPrimary = Color(0xFF003351),
    primaryContainer = Color(0xFF004A73),
    onPrimaryContainer = Color(0xFFD1E8FF),
    secondary = Color(0xFFB8C8D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF374856),
    onSecondaryContainer = Color(0xFFD3E4F5),
    background = Color(0xFF090F14),
    onBackground = Color(0xFFDDE5ED),
    surface = Color(0xFF111A22),
    onSurface = Color(0xFFD4DEE7),
    surfaceVariant = Color(0xFF1C2833),
    onSurfaceVariant = Color(0xFFBCC8D3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)
