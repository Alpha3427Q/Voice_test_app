package com.projectalice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectalice.engine.AliceEngineService
import com.projectalice.overlay.OverlayService
import com.projectalice.ui.settings.PermissionGatekeeper
import com.projectalice.ui.settings.PermissionPrompt
import com.projectalice.ui.settings.SettingsScreen
import com.projectalice.ui.settings.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    SettingsRoute(viewModel)
                }
            }
        }
    }
}

@Composable
private fun SettingsRoute(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
