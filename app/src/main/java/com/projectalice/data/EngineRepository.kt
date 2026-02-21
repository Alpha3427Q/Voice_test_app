package com.projectalice.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EngineRepository {
    private val _state = MutableStateFlow(EngineUiState())
    val state: StateFlow<EngineUiState> = _state.asStateFlow()

    fun setListening(listening: Boolean) {
        _state.value = _state.value.copy(isListening = listening)
    }

    fun setProcessing(processing: Boolean) {
        _state.value = _state.value.copy(isProcessing = processing)
    }

    fun setLiveTranscription(text: String) {
        _state.value = _state.value.copy(liveTranscription = text)
    }

    fun setAssistantStream(text: String) {
        _state.value = _state.value.copy(assistantStreamingText = text)
    }

    fun setMicBlocked(blocked: Boolean) {
        _state.value = _state.value.copy(isMicBlocked = blocked)
    }

    fun clearTransientText() {
        _state.value = _state.value.copy(liveTranscription = "", assistantStreamingText = "")
    }
}

data class EngineUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isMicBlocked: Boolean = false,
    val liveTranscription: String = "",
    val assistantStreamingText: String = ""
)
