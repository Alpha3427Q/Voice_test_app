package com.alice.ai.data.settings

import android.content.SharedPreferences

data class StoredSettings(
    val ollamaServerUrl: String,
    val ollamaApiKey: String,
    val ttsApiUrl: String,
    val offlineGgufUri: String,
    val selectedModel: String
)

class SettingsRepository(
    private val sharedPreferences: SharedPreferences
) {
    fun getOllamaServerUrl(): String =
        sharedPreferences.getString(KEY_OLLAMA_URL, "").orEmpty()

    fun getOllamaApiKey(): String =
        sharedPreferences.getString(KEY_OLLAMA_API_KEY, "").orEmpty()

    val ollamaApiKey: String
        get() = getOllamaApiKey()

    fun getTtsApiUrl(defaultValue: String): String =
        sharedPreferences.getString(KEY_TTS_URL, defaultValue).orEmpty().ifBlank { defaultValue }

    fun getOfflineGgufUri(): String =
        sharedPreferences.getString(KEY_GGUF_URI, "").orEmpty()

    fun getSelectedModel(): String =
        sharedPreferences.getString(KEY_SELECTED_MODEL, "").orEmpty()

    fun save(settings: StoredSettings) {
        sharedPreferences.edit()
            .putString(KEY_OLLAMA_URL, settings.ollamaServerUrl)
            .putString(KEY_OLLAMA_API_KEY, settings.ollamaApiKey)
            .putString(KEY_TTS_URL, settings.ttsApiUrl)
            .putString(KEY_GGUF_URI, settings.offlineGgufUri)
            .putString(KEY_SELECTED_MODEL, settings.selectedModel)
            .apply()
    }

    companion object {
        private const val KEY_OLLAMA_URL = "ollama_server_url"
        private const val KEY_OLLAMA_API_KEY = "ollama_api_key"
        private const val KEY_TTS_URL = "tts_api_url"
        private const val KEY_GGUF_URI = "offline_gguf_uri"
        private const val KEY_SELECTED_MODEL = "selected_model"
    }
}
