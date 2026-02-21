package com.projectalice.data

data class AppSettings(
    val backgroundListeningEnabled: Boolean = true,
    val assistantUrl: String = ASSISTANT_URL_PLACEHOLDER,
    val ttsUrl: String = TTS_URL_PLACEHOLDER,
    val n8nWebhookUrl: String = N8N_WEBHOOK_URL_PLACEHOLDER,
    val ollamaUrl: String = OLLAMA_URL_PLACEHOLDER
)

const val ASSISTANT_URL_PLACEHOLDER = "https://assistant.example.com/stream"
const val TTS_URL_PLACEHOLDER = "https://tts.example.com/speak"
const val N8N_WEBHOOK_URL_PLACEHOLDER = "https://n8n.example.com/webhook/alice"
const val OLLAMA_URL_PLACEHOLDER = "http://127.0.0.1:11434/api/chat"
