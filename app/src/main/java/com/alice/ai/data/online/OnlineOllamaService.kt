package com.alice.ai.data.online

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.net.SocketTimeoutException

sealed class OllamaConnectionResult {
    data object Success : OllamaConnectionResult()
    data object InvalidApiKey : OllamaConnectionResult()
    data object InvalidOllamaUrl : OllamaConnectionResult()
    data object ServerUnreachable : OllamaConnectionResult()
    data object InvalidEndpoint : OllamaConnectionResult()
    data object NoModelsReturned : OllamaConnectionResult()
    data class Failure(val message: String) : OllamaConnectionResult()
}

sealed class OllamaChatStreamResult {
    data object Success : OllamaChatStreamResult()
    data class Failure(val message: String) : OllamaChatStreamResult()
}

data class OllamaStreamChunk(
    val message: OllamaMessage? = null,
    val response: String? = null,
    val done: Boolean? = null,
    val error: String? = null
)

class OnlineOllamaService(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "OnlineOllamaService"
        private const val MAX_STREAM_RETRIES = 2
        private const val STREAM_RETRY_DELAY_MS = 600L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun validateConnection(
        baseUrl: String,
        apiKey: String
    ): OllamaConnectionResult = withContext(Dispatchers.IO) {
        val request = buildGetRequest(baseUrl = baseUrl, endpoint = "api/tags", apiKey = apiKey)
            ?: return@withContext OllamaConnectionResult.InvalidOllamaUrl

        return@withContext executeConnectionRequest(request)
    }

    suspend fun fetchModelNames(
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val request = buildGetRequest(baseUrl = baseUrl, endpoint = "api/tags", apiKey = apiKey)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid endpoint"))

        return@withContext executeModelFetchRequest(request)
    }

    suspend fun streamChatResponse(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<OllamaMessage>,
        onDelta: (String) -> Unit
    ): OllamaChatStreamResult = withContext(Dispatchers.IO) {
        val normalizedModel = model.trim()
        if (normalizedModel.isBlank()) {
            return@withContext OllamaChatStreamResult.Failure("Invalid endpoint")
        }

        var lastFailure: String = "Online request failed"
        var attempt = 0
        while (attempt < MAX_STREAM_RETRIES) {
            currentCoroutineContext().ensureActive()
            val request = buildChatRequest(
                baseUrl = baseUrl,
                apiKey = apiKey,
                requestBody = OllamaChatRequest(
                    model = normalizedModel,
                    messages = messages,
                    stream = true
                )
            ) ?: return@withContext OllamaChatStreamResult.Failure("Invalid endpoint")

            when (val result = executeStreamRequest(request = request, onDelta = onDelta)) {
                OllamaChatStreamResult.Success -> return@withContext OllamaChatStreamResult.Success
                is OllamaChatStreamResult.Failure -> {
                    lastFailure = result.message
                    val shouldRetry = attempt < MAX_STREAM_RETRIES - 1 && isRetriableFailure(result.message)
                    if (!shouldRetry) {
                        return@withContext result
                    }
                    Log.w(TAG, "Retrying stream request after failure: ${result.message}")
                    delay(STREAM_RETRY_DELAY_MS)
                }
            }
            attempt += 1
        }

        return@withContext OllamaChatStreamResult.Failure(lastFailure)
    }

    private suspend fun executeStreamRequest(
        request: Request,
        onDelta: (String) -> Unit
    ): OllamaChatStreamResult {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use when (response.code) {
                        401, 403 -> OllamaChatStreamResult.Failure("Invalid API key")
                        404 -> OllamaChatStreamResult.Failure("Invalid endpoint")
                        else -> OllamaChatStreamResult.Failure("Online request failed (${response.code})")
                    }
                }

                val body = response.body ?: return@use OllamaChatStreamResult.Failure("Online request failed")
                val source = body.source()
                val transferBuffer = Buffer()
                val pendingLineBuffer = StringBuilder()
                var emitted = false
                var streamError: String? = null
                var lastRawLine: String? = null
                var done = false

                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val readCount = source.read(transferBuffer, 8_192)
                    if (readCount <= 0L) {
                        continue
                    }
                    pendingLineBuffer.append(transferBuffer.readUtf8())

                    var newlineIndex = pendingLineBuffer.indexOf("\n")
                    while (newlineIndex >= 0) {
                        val rawLine = pendingLineBuffer.substring(0, newlineIndex)
                        pendingLineBuffer.delete(0, newlineIndex + 1)
                        if (rawLine == lastRawLine) {
                            newlineIndex = pendingLineBuffer.indexOf("\n")
                            continue
                        }
                        lastRawLine = rawLine
                        val parseOutcome = handleStreamLine(
                            rawLine = rawLine,
                            onDelta = { delta ->
                                emitted = true
                                onDelta(delta)
                            }
                        )
                        if (parseOutcome is StreamLineOutcome.Error) {
                            streamError = parseOutcome.message
                            done = true
                            break
                        }
                        if (parseOutcome is StreamLineOutcome.Done) {
                            done = true
                            break
                        }
                        newlineIndex = pendingLineBuffer.indexOf("\n")
                    }

                    if (done) {
                        break
                    }
                }

                if (!done && pendingLineBuffer.isNotBlank()) {
                    val tailLine = pendingLineBuffer.toString()
                    if (tailLine != lastRawLine) {
                        when (
                            val parseOutcome = handleStreamLine(
                                rawLine = tailLine,
                                onDelta = { delta ->
                                    emitted = true
                                    onDelta(delta)
                                }
                            )
                        ) {
                            is StreamLineOutcome.Error -> streamError = parseOutcome.message
                            StreamLineOutcome.Done -> done = true
                            StreamLineOutcome.Skip -> Unit
                        }
                    }
                }

                if (streamError != null) {
                    return@use OllamaChatStreamResult.Failure(streamError ?: "Online request failed")
                }

                if (!emitted) {
                    OllamaChatStreamResult.Failure("Online engine returned an empty response")
                } else {
                    OllamaChatStreamResult.Success
                }
            }
        } catch (_: SocketTimeoutException) {
            OllamaChatStreamResult.Failure("Server unreachable")
        } catch (_: IOException) {
            OllamaChatStreamResult.Failure("Server unreachable")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            OllamaChatStreamResult.Failure(error.message ?: "Online request failed")
        }
    }

    private fun handleStreamLine(
        rawLine: String,
        onDelta: (String) -> Unit
    ): StreamLineOutcome {
        val line = rawLine.trim()
        if (line.isBlank()) {
            return StreamLineOutcome.Skip
        }

        val normalized = if (line.startsWith("data:", ignoreCase = true)) {
            line.removePrefix("data:").trim()
        } else {
            line
        }
        if (normalized.equals("[DONE]", ignoreCase = true)) {
            return StreamLineOutcome.Done
        }

        val chunk = parseChunk(normalized) ?: return StreamLineOutcome.Skip
        val error = chunk.error?.trim().orEmpty()
        if (error.isNotEmpty()) {
            return StreamLineOutcome.Error(error)
        }

        val delta = chunk.message?.content.orEmpty().ifBlank {
            chunk.response.orEmpty()
        }
        if (delta.isNotEmpty()) {
            onDelta(delta)
        }
        return if (chunk.done == true) {
            StreamLineOutcome.Done
        } else {
            StreamLineOutcome.Skip
        }
    }

    private fun executeConnectionRequest(request: Request): OllamaConnectionResult {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val models = parseModelsFromBody(response.body?.string().orEmpty())
                        if (models.isEmpty()) {
                            OllamaConnectionResult.NoModelsReturned
                        } else {
                            OllamaConnectionResult.Success
                        }
                    }

                    response.code == 401 || response.code == 403 -> OllamaConnectionResult.InvalidApiKey
                    response.code == 404 -> OllamaConnectionResult.InvalidOllamaUrl
                    else -> OllamaConnectionResult.Failure("Invalid endpoint")
                }
            }
        } catch (_: SocketTimeoutException) {
            OllamaConnectionResult.ServerUnreachable
        } catch (_: IOException) {
            OllamaConnectionResult.ServerUnreachable
        } catch (error: Throwable) {
            OllamaConnectionResult.Failure(error.message ?: "Server unreachable")
        }
    }

    private fun executeModelFetchRequest(request: Request): Result<List<String>> {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val models = parseModelsFromBody(response.body?.string().orEmpty())
                        if (models.isEmpty()) {
                            Result.failure(IllegalStateException("No models returned"))
                        } else {
                            Result.success(models)
                        }
                    }

                    response.code == 401 || response.code == 403 -> {
                        Result.failure(IllegalStateException("Invalid API key"))
                    }

                    response.code == 404 -> {
                        Result.failure(IllegalStateException("Invalid endpoint"))
                    }

                    else -> {
                        Result.failure(IllegalStateException("Invalid endpoint"))
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            Result.failure(IllegalStateException("Server unreachable"))
        } catch (_: IOException) {
            Result.failure(IllegalStateException("Server unreachable"))
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Invalid endpoint"))
        }
    }

    private fun parseModelsFromBody(body: String): List<String> {
        return runCatching {
            gson.fromJson(body, OllamaTagsResponse::class.java)
                ?.models
                .orEmpty()
                .map { it.name.trim() }
                .filter { it.isNotBlank() }
        }.getOrElse {
            emptyList()
        }
    }

    private fun parseChunk(line: String): OllamaStreamChunk? {
        return try {
            gson.fromJson(line, OllamaStreamChunk::class.java)
        } catch (_: JsonSyntaxException) {
            Log.w(TAG, "Skipping malformed chunk")
            null
        }
    }

    private fun buildGetRequest(baseUrl: String, endpoint: String, apiKey: String): Request? {
        val normalizedBase = normalizeBaseUrl(baseUrl) ?: return null
        val requestBuilder = Request.Builder()
            .url(normalizedBase + endpoint)
            .get()
        addAuthHeader(requestBuilder, apiKey)
        return requestBuilder.build()
    }

    private fun buildChatRequest(
        baseUrl: String,
        apiKey: String,
        requestBody: OllamaChatRequest
    ): Request? {
        val normalizedBase = normalizeBaseUrl(baseUrl) ?: return null
        val body = gson.toJson(requestBody).toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(normalizedBase + "api/chat")
            .post(body)
        addAuthHeader(requestBuilder, apiKey)
        return requestBuilder.build()
    }

    private fun addAuthHeader(requestBuilder: Request.Builder, apiKey: String) {
        val normalizedKey = apiKey.trim()
        if (normalizedKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $normalizedKey")
            Log.d(TAG, "Applying Authorization header: ***")
        } else {
            Log.d(TAG, "Applying Authorization header: none")
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }

    private fun isRetriableFailure(message: String): Boolean {
        val normalized = message.lowercase()
        return "timeout" in normalized ||
            "unreachable" in normalized ||
            "connection" in normalized
    }
}

private sealed class StreamLineOutcome {
    data object Skip : StreamLineOutcome()
    data object Done : StreamLineOutcome()
    data class Error(val message: String) : StreamLineOutcome()
}
