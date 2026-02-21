package com.projectalice.engine

interface WakeWordEngine {
    fun startListening(onWakeWordDetected: () -> Unit)
    fun stopListening()
    fun release()
}
