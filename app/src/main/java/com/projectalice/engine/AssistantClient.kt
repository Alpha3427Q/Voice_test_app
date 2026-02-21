package com.projectalice.engine

import kotlinx.coroutines.delay

class AssistantClient {
    suspend fun streamAssistantResponse(
        assistantUrl: String,
        prompt: String,
        onChunk: (String) -> Unit
    ): AssistantResponse {
        val chunks = listOf(
            "Sending to $assistantUrl",
            " | Prompt: $prompt",
            " | Response stream placeholder"
        )
        val streamed = StringBuilder()
        for (chunk in chunks) {
            delay(180)
            streamed.append(chunk)
            onChunk(streamed.toString())
        }
        return AssistantResponse(
            text = streamed.toString(),
            audioUrl = null
        )
    }
}

data class AssistantResponse(
    val text: String,
    val audioUrl: String?
)
