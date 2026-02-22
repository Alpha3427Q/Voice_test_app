package com.projectalice.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectalice.data.AppSettings
import com.projectalice.data.EngineRepository
import com.projectalice.data.EngineUiState
import com.projectalice.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(
        recordAudio = false,
        overlay = false,
        readPhoneState = false,
        usageStats = false,
        batteryOptimizationExempt = false
    ),
    val engine: EngineUiState = EngineUiState()
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val permissionState = MutableStateFlow(
        PermissionSnapshot(
            recordAudio = false,
            overlay = false,
            readPhoneState = false,
            usageStats = false,
            batteryOptimizationExempt = false
        )
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        permissionState,
        EngineRepository.state
    ) { settings, permissions, engine ->
        SettingsUiState(
            settings = settings,
            permissions = permissions,
            engine = engine
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun updatePermissionSnapshot(snapshot: PermissionSnapshot) {
        permissionState.value = snapshot
    }

    fun setBackgroundListeningEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundListeningEnabled(value) }
    }

    fun setAssistantUrl(value: String) {
        viewModelScope.launch { settingsRepository.setAssistantUrl(value) }
    }

    fun setTtsUrl(value: String) {
        viewModelScope.launch { settingsRepository.setTtsUrl(value) }
    }

    fun setN8nWebhookUrl(value: String) {
        viewModelScope.launch { settingsRepository.setN8nWebhookUrl(value) }
    }

    fun setOllamaUrl(value: String) {
        viewModelScope.launch { settingsRepository.setOllamaUrl(value) }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        return SettingsViewModel(SettingsRepository(context.applicationContext)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
