package com.alice.ai.data.offline

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class OfflineEngineError(val shortReason: String) {
    data object ModelNotFound : OfflineEngineError("model file not found")
    data object PermissionDenied : OfflineEngineError("storage permission denied")
    data object OutOfMemory : OfflineEngineError("insufficient memory")
    data class LoadFailed(private val detail: String) : OfflineEngineError(detail)
}

sealed class OfflineEngineResult {
    data object Success : OfflineEngineResult()
    data class Failure(val error: OfflineEngineError) : OfflineEngineResult()
}

object OfflineEngineManager {
    private const val TAG = "OfflineEngineManager"

    fun isModelLoaded(modelIdOrPath: String): Boolean {
        val normalizedPath = modelIdOrPath.trim()
        if (normalizedPath.isBlank()) {
            return false
        }
        return LlamaJniBridge.nativeIsModelLoaded() &&
            LlamaJniBridge.getActiveModelPath() == normalizedPath
    }

    suspend fun loadModel(modelIdOrPath: String): OfflineEngineResult = withContext(Dispatchers.IO) {
        val normalizedPath = modelIdOrPath.trim()
        if (normalizedPath.isBlank()) {
            return@withContext OfflineEngineResult.Failure(
                OfflineEngineError.LoadFailed("empty model path")
            )
        }

        if (isModelLoaded(normalizedPath)) {
            Log.i(TAG, "Model already loaded (${modelLabel(normalizedPath)})")
            return@withContext OfflineEngineResult.Success
        }

        try {
            val loaded = LlamaJniBridge.loadModel(normalizedPath)
            if (loaded && isModelLoaded(normalizedPath)) {
                Log.i(TAG, "Offline model loaded (${modelLabel(normalizedPath)})")
                OfflineEngineResult.Success
            } else {
                val error = classifyLoadFailure(normalizedPath)
                Log.e(TAG, "Offline model load failed (${modelLabel(normalizedPath)}): ${error.shortReason}")
                OfflineEngineResult.Failure(error)
            }
        } catch (_: SecurityException) {
            Log.e(TAG, "Offline model load failed (${modelLabel(normalizedPath)}): permission denied")
            OfflineEngineResult.Failure(OfflineEngineError.PermissionDenied)
        } catch (_: OutOfMemoryError) {
            Log.e(TAG, "Offline model load failed (${modelLabel(normalizedPath)}): out of memory")
            OfflineEngineResult.Failure(OfflineEngineError.OutOfMemory)
        } catch (error: Throwable) {
            Log.e(TAG, "Offline model load failed (${modelLabel(normalizedPath)}): ${error.message}")
            OfflineEngineResult.Failure(
                OfflineEngineError.LoadFailed(
                    error.message?.takeIf { it.isNotBlank() } ?: "load failed"
                )
            )
        }
    }

    suspend fun unloadModel(modelIdOrPath: String? = null): OfflineEngineResult = withContext(Dispatchers.IO) {
        val activePath = LlamaJniBridge.getActiveModelPath().orEmpty()
        val normalizedTarget = modelIdOrPath?.trim().orEmpty()
        if (normalizedTarget.isNotBlank() && activePath.isNotBlank() && normalizedTarget != activePath) {
            return@withContext OfflineEngineResult.Success
        }

        if (!LlamaJniBridge.nativeIsModelLoaded()) {
            return@withContext OfflineEngineResult.Success
        }

        return@withContext try {
            Log.i(TAG, "Unloading offline model (${modelLabel(activePath)})")
            LlamaJniBridge.unloadModel()
            if (LlamaJniBridge.nativeIsModelLoaded()) {
                Log.e(TAG, "Offline model unload failed (${modelLabel(activePath)})")
                OfflineEngineResult.Failure(OfflineEngineError.LoadFailed("failed to unload model"))
            } else {
                Log.i(TAG, "Offline model unloaded (${modelLabel(activePath)})")
                OfflineEngineResult.Success
            }
        } catch (_: OutOfMemoryError) {
            OfflineEngineResult.Failure(OfflineEngineError.OutOfMemory)
        } catch (error: Throwable) {
            Log.e(TAG, "Offline model unload failed (${modelLabel(activePath)}): ${error.message}")
            OfflineEngineResult.Failure(
                OfflineEngineError.LoadFailed(
                    error.message?.takeIf { it.isNotBlank() } ?: "unload failed"
                )
            )
        }
    }

    private fun classifyLoadFailure(modelPath: String): OfflineEngineError {
        val uri = Uri.parse(modelPath)
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme == "content") {
            return OfflineEngineError.PermissionDenied
        }

        val filePath = when {
            scheme == "file" -> uri.path.orEmpty()
            scheme.isBlank() -> modelPath
            else -> ""
        }
        if (filePath.isNotBlank() && !File(filePath).exists()) {
            return OfflineEngineError.ModelNotFound
        }

        return OfflineEngineError.LoadFailed("failed to load model")
    }

    private fun modelLabel(modelPath: String): String {
        if (modelPath.isBlank()) {
            return "none"
        }
        val uri = Uri.parse(modelPath)
        val segment = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        return segment.ifBlank { "model" }
    }
}
