package com.projectalice.ui.n8n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun N8nScreen(
    modifier: Modifier = Modifier
) {
    var webhookUrl by remember { mutableStateOf("") }
    var authToken by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "N8N Automation Dashboard",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = webhookUrl,
            onValueChange = { webhookUrl = it },
            label = { Text("N8N Webhook URL") },
            singleLine = true
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = authToken,
            onValueChange = { authToken = it },
            label = { Text("Auth Token") },
            singleLine = true
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            onClick = {
                status = if (webhookUrl.isNotBlank() && authToken.isNotBlank()) {
                    "Connection test queued"
                } else {
                    "Enter webhook URL and auth token"
                }
            }
        ) {
            Text("Test Connection")
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
