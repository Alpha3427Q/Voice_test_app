package com.projectalice.engine

import android.content.Context
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import com.projectalice.data.EngineRepository

class PorcupineWakeWordEngine(private val context: Context) : WakeWordEngine {
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false

    override fun startListening(onWakeWordDetected: () -> Unit) {
        if (isListening) return
        if (porcupineManager == null) {
            porcupineManager = createManager(onWakeWordDetected)
        }
        porcupineManager?.start()
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        porcupineManager?.stop()
        isListening = false
    }

    override fun release() {
        try {
            porcupineManager?.stop()
        } catch (_: Exception) {
            // No-op.
        }
        porcupineManager?.delete()
        porcupineManager = null
        isListening = false
    }

    private fun createManager(onWakeWordDetected: () -> Unit): PorcupineManager {
        val modelPath = "porcupine_params.pv"
        val keywordPath = "Hey-Alice_en_android_v4_0_0.ppn"

        return try {
            PorcupineManager.Builder()
                .setAccessKey(PICOVOICE_ACCESS_KEY)
                .setModelPath(modelPath)
                .setKeywordPath(keywordPath)
                .build(context) { keywordIndex ->
                    if (keywordIndex == 0) {
                        EngineRepository.setOverlayTriggered(true)
                        onWakeWordDetected()
                    }
                }
        } catch (error: PorcupineException) {
            throw IllegalStateException("Failed to initialize PorcupineManager", error)
        }
    }
}

private const val PICOVOICE_ACCESS_KEY = "x+KEpi1bH3UY0yAIA+XEibxmRLUyhueOQi4k8KoEbsRA6Ivnxy/b7A=="
