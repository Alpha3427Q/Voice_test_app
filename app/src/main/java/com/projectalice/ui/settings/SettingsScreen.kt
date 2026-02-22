package com.projectalice.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackgroundListeningChanged: (Boolean) -> Unit,
    onAssistantUrlChanged: (String) -> Unit,
    onTtsUrlChanged: (String) -> Unit,
    onN8nUrlChanged: (String) -> Unit,
    onOllamaUrlChanged: (String) -> Unit,
    onPermissionPromptClick: (PermissionPrompt) -> Unit,
    onStartEngineClick: () -> Unit,
    onStopEngineClick: () -> Unit,
    onStartOverlayClick: () -> Unit,
    onStopOverlayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "Alice Settings", style = MaterialTheme.typography.headlineSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Background Listening")
            Switch(
                checked = state.settings.backgroundListeningEnabled,
                onCheckedChange = onBackgroundListeningChanged
            )
        }

        OutlinedTextField(
            value = state.settings.assistantUrl,
            onValueChange = onAssistantUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Assistant URL") },
            singleLine = true
        )

        OutlinedTextField(
            value = state.settings.ttsUrl,
            onValueChange = onTtsUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("TTS URL") },
            singleLine = true
        )

        OutlinedTextField(
            value = state.settings.n8nWebhookUrl,
            onValueChange = onN8nUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("N8N Webhook URL") },
            singleLine = true
        )

        OutlinedTextField(
            value = state.settings.ollamaUrl,
            onValueChange = onOllamaUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ollama URL") },
            singleLine = true
        )

        HardwareAccelerationCard(vulkanSupported = state.isVulkanSupported)

        Text(text = "Permissions", style = MaterialTheme.typography.titleMedium)
        PermissionRow("Record Audio", state.permissions.recordAudio, PermissionPrompt.RECORD_AUDIO, onPermissionPromptClick)
        PermissionRow("Read Phone State", state.permissions.readPhoneState, PermissionPrompt.READ_PHONE_STATE, onPermissionPromptClick)
        PermissionRow("Overlay", state.permissions.overlay, PermissionPrompt.OVERLAY, onPermissionPromptClick)
        PermissionRow("Usage Stats", state.permissions.usageStats, PermissionPrompt.USAGE_STATS, onPermissionPromptClick)
        PermissionRow("Battery Exemption", state.permissions.batteryOptimizationExempt, PermissionPrompt.BATTERY_OPTIMIZATION, onPermissionPromptClick)

        Text(text = "Engine", style = MaterialTheme.typography.titleMedium)
        Text("Listening: ${state.engine.isListening}")
        Text("Processing: ${state.engine.isProcessing}")
        Text("Mic Blocked: ${state.engine.isMicBlocked}")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStartEngineClick) { Text("Start Engine") }
            Button(onClick = onStopEngineClick) { Text("Stop Engine") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStartOverlayClick) { Text("Start Overlay") }
            Button(onClick = onStopOverlayClick) { Text("Stop Overlay") }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HardwareAccelerationCard(vulkanSupported: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Hardware Acceleration",
                style = MaterialTheme.typography.titleMedium
            )

            Surface(
                color = if (vulkanSupported) Color(0xFF2E7D32) else Color(0xFFC62828),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = if (vulkanSupported) {
                        "CPU + GPU (Vulkan Supported)"
                    } else {
                        "CPU Only"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    prompt: PermissionPrompt,
    onPromptClick: (PermissionPrompt) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ${if (granted) "Granted" else "Missing"}")
        if (!granted) {
            Button(onClick = { onPromptClick(prompt) }) {
                Text("Grant")
            }
        }
    }
}
