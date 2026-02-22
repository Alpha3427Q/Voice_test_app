package com.alice.ai.data.offline

object LlamaJniBridge {
    init {
        System.loadLibrary("native-lib")
    }

    @Volatile
    private var activeModelPath: String? = null

    @Synchronized
    fun loadModel(modelPath: String): Boolean {
        if (modelPath.isBlank()) {
            return false
        }
        if (nativeIsModelLoaded() && activeModelPath == modelPath) {
            return true
        }
        if (nativeIsModelLoaded()) {
            nativeUnloadModel()
        }
        val loaded = nativeLoadModel(modelPath)
        activeModelPath = if (loaded) modelPath else null
        return loaded
    }

    @Synchronized
    fun unloadModel() {
        if (nativeIsModelLoaded()) {
            nativeUnloadModel()
        }
        activeModelPath = null
    }

    @Synchronized
    fun generateText(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String {
        return nativeGenerateText(prompt, maxTokens, temperature)
    }

    fun getActiveModelPath(): String? = activeModelPath

    external fun nativeLoadModel(modelPath: String): Boolean
    external fun nativeUnloadModel()
    external fun nativeGenerateText(prompt: String, maxTokens: Int, temperature: Float): String
    external fun nativeIsModelLoaded(): Boolean
}
