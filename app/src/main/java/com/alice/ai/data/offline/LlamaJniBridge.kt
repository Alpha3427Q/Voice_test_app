package com.alice.ai.data.offline

object LlamaJniBridge {
    private val libraryLoaded: Boolean = runCatching {
        System.loadLibrary("native-lib")
        true
    }.getOrElse { false }

    @Volatile
    private var activeModelPath: String? = null

    fun isLibraryLoaded(): Boolean = libraryLoaded

    @Synchronized
    fun loadModel(modelPath: String): Boolean {
        if (!libraryLoaded) {
            activeModelPath = null
            return false
        }
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
        if (!libraryLoaded) {
            activeModelPath = null
            return
        }
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
        if (!libraryLoaded) {
            return ""
        }
        return nativeGenerateText(prompt, maxTokens, temperature)
    }

    fun getActiveModelPath(): String? = activeModelPath

    fun isModelLoadedSafe(): Boolean = libraryLoaded && nativeIsModelLoaded()

    external fun nativeLoadModel(modelPath: String): Boolean
    external fun nativeUnloadModel()
    external fun nativeGenerateText(prompt: String, maxTokens: Int, temperature: Float): String
    external fun nativeIsModelLoaded(): Boolean
}
