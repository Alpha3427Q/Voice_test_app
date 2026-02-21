package com.projectalice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alice_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val backgroundListeningEnabled = booleanPreferencesKey("background_listening_enabled")
        val assistantUrl = stringPreferencesKey("assistant_url")
        val ttsUrl = stringPreferencesKey("tts_url")
        val n8nWebhookUrl = stringPreferencesKey("n8n_webhook_url")
        val ollamaUrl = stringPreferencesKey("ollama_url")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            backgroundListeningEnabled = preferences[Keys.backgroundListeningEnabled] ?: true,
            assistantUrl = preferences[Keys.assistantUrl] ?: ASSISTANT_URL_PLACEHOLDER,
            ttsUrl = preferences[Keys.ttsUrl] ?: TTS_URL_PLACEHOLDER,
            n8nWebhookUrl = preferences[Keys.n8nWebhookUrl] ?: N8N_WEBHOOK_URL_PLACEHOLDER,
            ollamaUrl = preferences[Keys.ollamaUrl] ?: OLLAMA_URL_PLACEHOLDER
        )
    }

    suspend fun setBackgroundListeningEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.backgroundListeningEnabled] = value }
    }

    suspend fun setAssistantUrl(value: String) {
        context.dataStore.edit { it[Keys.assistantUrl] = value }
    }

    suspend fun setTtsUrl(value: String) {
        context.dataStore.edit { it[Keys.ttsUrl] = value }
    }

    suspend fun setN8nWebhookUrl(value: String) {
        context.dataStore.edit { it[Keys.n8nWebhookUrl] = value }
    }

    suspend fun setOllamaUrl(value: String) {
        context.dataStore.edit { it[Keys.ollamaUrl] = value }
    }
}
