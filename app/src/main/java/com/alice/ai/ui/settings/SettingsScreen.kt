package com.alice.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    ollamaServerUrl: String,
    ttsApiUrl: String,
    ggufModelPath: String,
    onOllamaServerUrlChange: (String) -> Unit,
    onTtsApiUrlChange: (String) -> Unit,
    onAddModelClick: () -> Unit,
    onRemoveModelClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Online Engine",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = ollamaServerUrl,
            onValueChange = onOllamaServerUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ollama Server URL") },
            singleLine = true
        )

        Text(
            text = "Audio",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = ttsApiUrl,
            onValueChange = onTtsApiUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("TTS API URL") },
            singleLine = true
        )

        Text(
            text = "Offline Engine (GGUF)",
            style = MaterialTheme.typography.titleMedium
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Selected Model Path",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = ggufModelPath.ifBlank { "No local model selected" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Uses Android file picker with persisted read access for API 33+.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAddModelClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add Model")
            }
            Button(
                onClick = onRemoveModelClick,
                modifier = Modifier.weight(1f),
                enabled = ggufModelPath.isNotBlank()
            ) {
                Text("Remove Model")
            }
        }

        Button(
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Save")
        }
    }
}
