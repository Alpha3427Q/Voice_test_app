package com.projectalice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = this[APPLICATION_KEY]
                    ?.applicationContext
                    ?: error("Application is required")
                SettingsViewModel(SettingsRepository(appContext))
            }
        }
    }
}
