package com.projectalice.overlay

import androidx.lifecycle.ViewModel
import com.projectalice.data.EngineRepository
import com.projectalice.data.EngineUiState
import kotlinx.coroutines.flow.StateFlow

class OverlayViewModel : ViewModel() {
    val uiState: StateFlow<EngineUiState> = EngineRepository.state
}
