package com.alice.ai.data.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class ModelPathResult {
    data class Success(
        val filePath: String,
        val copiedFromSaf: Boolean
    ) : ModelPathResult()

    data class Failure(
        val error: ModelPathError,
        val detail: String
    ) : ModelPathResult()
}

enum class ModelPathError {
    ModelFileNotFound,
    PermissionDenied,
    CopyFailed
}

class ModelRepository(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "ModelRepository"
    }

    private val modelsDir: File
        get() = File(appContext.filesDir, "models")

    suspend fun resolveOfflineModelPath(pathOrUri: String): ModelPathResult = withContext(Dispatchers.IO) {
        val source = pathOrUri.trim()
        if (source.isBlank()) {
            return@withContext ModelPathResult.Failure(
                ModelPathError.ModelFileNotFound,
                "Model file not found"
            )
        }

        return@withContext when {
            source.startsWith("content://") -> copySafModelToInternal(source)
            source.startsWith("file://") -> {
                val filePath = Uri.parse(source).path.orEmpty()
                validateFilePath(filePath)
            }

            else -> validateFilePath(source)
        }
    }

    private fun copySafModelToInternal(contentUri: String): ModelPathResult {
        val uri = Uri.parse(contentUri)
        val inputStream = try {
            appContext.contentResolver.openInputStream(uri)
        } catch (_: SecurityException) {
            Log.e(TAG, "Permission denied while opening model uri")
            return ModelPathResult.Failure(
                ModelPathError.PermissionDenied,
                "Permission denied"
            )
        } catch (_: Throwable) {
            Log.e(TAG, "Failed to open model uri")
            return ModelPathResult.Failure(
                ModelPathError.CopyFailed,
                "Model load failure"
            )
        }

        if (inputStream == null) {
            return ModelPathResult.Failure(
                ModelPathError.ModelFileNotFound,
                "Model file not found"
            )
        }

        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            inputStream.close()
            Log.e(TAG, "Failed to create models directory at ${modelsDir.absolutePath}")
            return ModelPathResult.Failure(
                ModelPathError.CopyFailed,
                "Model load failure"
            )
        }

        val targetFile = File(
            modelsDir,
            buildTargetFileName(uri)
        )
        val tempFile = File(targetFile.absolutePath + ".tmp")

        return try {
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() <= 0L) {
                tempFile.delete()
                Log.e(TAG, "Copied model file is empty")
                return ModelPathResult.Failure(
                    ModelPathError.CopyFailed,
                    "Model load failure"
                )
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                Log.e(TAG, "Failed to move copied model into final location")
                return ModelPathResult.Failure(
                    ModelPathError.CopyFailed,
                    "Model load failure"
                )
            }

            ModelPathResult.Success(
                filePath = targetFile.absolutePath,
                copiedFromSaf = true
            )
        } catch (_: SecurityException) {
            tempFile.delete()
            Log.e(TAG, "Permission denied while copying model data")
            ModelPathResult.Failure(
                ModelPathError.PermissionDenied,
                "Permission denied"
            )
        } catch (_: Throwable) {
            tempFile.delete()
            Log.e(TAG, "Unexpected error while copying model data")
            ModelPathResult.Failure(
                ModelPathError.CopyFailed,
                "Model load failure"
            )
        }
    }

    private fun validateFilePath(filePath: String): ModelPathResult {
        if (filePath.isBlank()) {
            return ModelPathResult.Failure(
                ModelPathError.ModelFileNotFound,
                "Model file not found"
            )
        }
        val modelFile = File(filePath)
        if (!modelFile.exists() || !modelFile.isFile) {
            Log.e(TAG, "Model file not found at $filePath")
            return ModelPathResult.Failure(
                ModelPathError.ModelFileNotFound,
                "Model file not found"
            )
        }

        return ModelPathResult.Success(
            filePath = modelFile.absolutePath,
            copiedFromSaf = false
        )
    }

    private fun buildTargetFileName(uri: Uri): String {
        val displayName = queryDisplayName(uri)
            .orEmpty()
            .substringAfterLast('/')
            .substringAfterLast(':' )
            .ifBlank { "offline_model.gguf" }

        val safeName = displayName
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char == '.' || char == '_' || char == '-' -> char
                    else -> '_'
                }
            }
            .joinToString(separator = "")
            .ifBlank { "offline_model.gguf" }

        val hasExt = safeName.contains('.')
        val withExt = if (hasExt) safeName else "$safeName.gguf"
        val suffix = Integer.toHexString(uri.toString().hashCode())
        return withExt.substringBeforeLast('.') + "_" + suffix + "." + withExt.substringAfterLast('.')
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) {
                    null
                } else {
                    cursor.getString(index)
                }
            }
        }.getOrNull()
    }
}
