package com.projectalice

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.projectalice.engine.AliceEngineService
import com.projectalice.overlay.OverlayService
import com.projectalice.ui.ai.AiScreen
import com.projectalice.ui.navigation.AppTab
import com.projectalice.ui.n8n.N8nScreen
import com.projectalice.ui.settings.PermissionGatekeeper
import com.projectalice.ui.settings.PermissionPrompt
import com.projectalice.ui.settings.SettingsScreen
import com.projectalice.ui.settings.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.provideFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainScaffold(viewModel)
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(viewModel: SettingsViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.AI) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { androidx.compose.material3.Text(tab.label) },
                        icon = {}
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.AI -> AiScreen(modifier = Modifier.padding(innerPadding))

            AppTab.N8N -> N8nScreen(modifier = Modifier.padding(innerPadding))

            AppTab.SETTINGS -> SettingsRoute(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun SettingsRoute(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val state by viewModel.uiState.collectAsState()

    var promptedRuntime by remember { mutableStateOf(false) }
    var promptedOverlay by remember { mutableStateOf(false) }
    var promptedUsageStats by remember { mutableStateOf(false) }
    var promptedBattery by remember { mutableStateOf(false) }
    var engineAutoStarted by remember { mutableStateOf(false) }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.updatePermissionSnapshot(PermissionGatekeeper.snapshot(context))
    }

    fun refreshPermissions() {
        viewModel.updatePermissionSnapshot(PermissionGatekeeper.snapshot(context))
        viewModel.refreshHardwareAcceleration(context)
    }

    fun promptPermission(prompt: PermissionPrompt) {
        when (prompt) {
            PermissionPrompt.RECORD_AUDIO,
            PermissionPrompt.READ_PHONE_STATE -> {
                val permissions = mutableListOf<String>()
                if (!state.permissions.recordAudio) permissions += Manifest.permission.RECORD_AUDIO
                if (!state.permissions.readPhoneState) permissions += Manifest.permission.READ_PHONE_STATE
                if (permissions.isNotEmpty()) {
                    runtimePermissionLauncher.launch(permissions.toTypedArray())
                }
            }

            PermissionPrompt.OVERLAY,
            PermissionPrompt.USAGE_STATS,
            PermissionPrompt.BATTERY_OPTIMIZATION -> {
                val intent = PermissionGatekeeper.buildIntent(context, prompt)
                if (intent != null && activity != null) {
                    activity.startActivity(intent)
                }
            }
        }
    }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    LaunchedEffect(state.permissions) {
        if (!state.permissions.recordAudio || !state.permissions.readPhoneState) {
            if (!promptedRuntime) {
                promptedRuntime = true
                promptPermission(PermissionPrompt.RECORD_AUDIO)
            }
        }

        if (!state.permissions.overlay && !promptedOverlay) {
            promptedOverlay = true
            promptPermission(PermissionPrompt.OVERLAY)
        }

        if (!state.permissions.usageStats && !promptedUsageStats) {
            promptedUsageStats = true
            promptPermission(PermissionPrompt.USAGE_STATS)
        }

        if (!state.permissions.batteryOptimizationExempt && !promptedBattery) {
            promptedBattery = true
            promptPermission(PermissionPrompt.BATTERY_OPTIMIZATION)
        }
    }

    LaunchedEffect(state.settings.backgroundListeningEnabled, state.permissions.allGranted) {
        if (state.settings.backgroundListeningEnabled && state.permissions.allGranted && !engineAutoStarted) {
            engineAutoStarted = true
            ContextCompat.startForegroundService(
                context,
                Intent(context, AliceEngineService::class.java).setAction(ACTION_START_ENGINE)
            )
        }
        if (!state.settings.backgroundListeningEnabled) {
            engineAutoStarted = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            onBackgroundListeningChanged = {
                viewModel.setBackgroundListeningEnabled(it)
                if (it && state.permissions.allGranted) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AliceEngineService::class.java).setAction(ACTION_START_ENGINE)
                    )
                }
                if (!it) {
                    context.startService(
                        Intent(context, AliceEngineService::class.java).setAction(ACTION_STOP_ENGINE)
                    )
                }
            },
            onAssistantUrlChanged = viewModel::setAssistantUrl,
            onTtsUrlChanged = viewModel::setTtsUrl,
            onN8nUrlChanged = viewModel::setN8nWebhookUrl,
            onOllamaUrlChanged = viewModel::setOllamaUrl,
            onPermissionPromptClick = { promptPermission(it) },
            onStartEngineClick = {
                refreshPermissions()
                if (state.permissions.allGranted) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AliceEngineService::class.java).setAction(ACTION_START_ENGINE)
                    )
                }
            },
            onStopEngineClick = {
                context.startService(Intent(context, AliceEngineService::class.java).setAction(ACTION_STOP_ENGINE))
            },
            onStartOverlayClick = {
                if (state.permissions.overlay) {
                    val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START_OVERLAY)
                    context.startService(intent)
                } else {
                    promptPermission(PermissionPrompt.OVERLAY)
                }
            },
            onStopOverlayClick = {
                context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP_OVERLAY))
            }
        )
    }
}
