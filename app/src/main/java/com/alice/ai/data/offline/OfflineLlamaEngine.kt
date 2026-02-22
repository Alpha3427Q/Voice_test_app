package com.alice.ai.data.offline

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class OfflineLlamaResult {
    data object Success : OfflineLlamaResult()
    data class Failure(val error: OfflineLlamaError) : OfflineLlamaResult()
}

sealed class OfflineLlamaGenerateResult {
    data class Success(val text: String) : OfflineLlamaGenerateResult()
    data class Failure(val error: OfflineLlamaError) : OfflineLlamaGenerateResult()
}

sealed class OfflineLlamaError(val message: String) {
    data object ModelFileNotFound : OfflineLlamaError("Model file not found")
    data object ModelLoadFailure : OfflineLlamaError("Model load failure")
    data object NativeLibraryMissing : OfflineLlamaError("Native library missing")
    data object ModelNotLoaded : OfflineLlamaError("Model load failure")
    data class Unknown(private val reason: String) : OfflineLlamaError(reason)
}

class OfflineLlamaEngine {
    companion object {
        private const val TAG = "OfflineLlamaEngine"
    }

    fun isModelLoaded(modelPath: String): Boolean {
        val normalized = modelPath.trim()
        if (normalized.isBlank()) {
            return false
        }
        if (!LlamaJniBridge.isLibraryLoaded()) {
            return false
        }
        return LlamaJniBridge.isModelLoadedSafe() && LlamaJniBridge.getActiveModelPath() == normalized
    }

    suspend fun loadModel(modelPath: String): OfflineLlamaResult = withContext(Dispatchers.IO) {
        val normalized = modelPath.trim()
        if (normalized.isBlank()) {
            return@withContext OfflineLlamaResult.Failure(OfflineLlamaError.ModelFileNotFound)
        }

        if (!LlamaJniBridge.isLibraryLoaded()) {
            Log.e(TAG, "Native library missing while loading model")
            return@withContext OfflineLlamaResult.Failure(OfflineLlamaError.NativeLibraryMissing)
        }

        if (!File(normalized).exists()) {
            Log.e(TAG, "Model file not found: $normalized")
            return@withContext OfflineLlamaResult.Failure(OfflineLlamaError.ModelFileNotFound)
        }

        if (isModelLoaded(normalized)) {
            return@withContext OfflineLlamaResult.Success
        }

        return@withContext runCatching {
            if (LlamaJniBridge.loadModel(normalized)) {
                Log.i(TAG, "Offline model loaded")
                OfflineLlamaResult.Success
            } else {
                Log.e(TAG, "Model load failure for path: $normalized")
                OfflineLlamaResult.Failure(OfflineLlamaError.ModelLoadFailure)
            }
        }.getOrElse { throwable ->
            Log.e(TAG, "Model load failure: ${throwable.message}")
            OfflineLlamaResult.Failure(
                OfflineLlamaError.Unknown(throwable.message ?: OfflineLlamaError.ModelLoadFailure.message)
            )
        }
    }

    suspend fun unloadModel(): OfflineLlamaResult = withContext(Dispatchers.IO) {
        if (!LlamaJniBridge.isLibraryLoaded()) {
            return@withContext OfflineLlamaResult.Failure(OfflineLlamaError.NativeLibraryMissing)
        }

        return@withContext runCatching {
            LlamaJniBridge.unloadModel()
            OfflineLlamaResult.Success
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to unload model: ${throwable.message}")
            OfflineLlamaResult.Failure(
                OfflineLlamaError.Unknown(throwable.message ?: OfflineLlamaError.ModelLoadFailure.message)
            )
        }
    }

    suspend fun generateText(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): OfflineLlamaGenerateResult = withContext(Dispatchers.IO) {
        if (!LlamaJniBridge.isLibraryLoaded()) {
            return@withContext OfflineLlamaGenerateResult.Failure(OfflineLlamaError.NativeLibraryMissing)
        }
        if (!LlamaJniBridge.isModelLoadedSafe()) {
            return@withContext OfflineLlamaGenerateResult.Failure(OfflineLlamaError.ModelNotLoaded)
        }

        return@withContext runCatching {
            val output = LlamaJniBridge.generateText(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature
            ).trim()

            if (output.isBlank()) {
                OfflineLlamaGenerateResult.Failure(OfflineLlamaError.ModelLoadFailure)
            } else {
                OfflineLlamaGenerateResult.Success(output)
            }
        }.getOrElse { throwable ->
            Log.e(TAG, "Native inference failure: ${throwable.message}")
            OfflineLlamaGenerateResult.Failure(
                OfflineLlamaError.Unknown(throwable.message ?: OfflineLlamaError.ModelLoadFailure.message)
            )
        }
    }
}
