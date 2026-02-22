package com.alice.ai.data.online

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class OllamaMessage(
    val role: String,
    val content: String
)

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

data class OllamaChatResponse(
    val model: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val message: OllamaMessage? = null,
    val response: String? = null,
    val done: Boolean? = null
)

data class OllamaModelInfo(
    val name: String
)

data class OllamaTagsResponse(
    val models: List<OllamaModelInfo> = emptyList()
)

interface OllamaApi {
    @POST("api/chat")
    suspend fun chat(
        @Body request: OllamaChatRequest
    ): OllamaChatResponse

    @GET("api/tags")
    suspend fun fetchModels(): OllamaTagsResponse
}

object OllamaApiFactory {
    private const val TAG = "OllamaApiFactory"

    fun create(
        baseUrl: String,
        okHttpClient: OkHttpClient? = null,
        apiKeyProvider: (() -> String)? = null
    ): OllamaApi {
        require(baseUrl.isNotBlank()) { "Ollama base URL cannot be blank" }
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val baseClient = okHttpClient ?: OkHttpClient()
        val client = if (apiKeyProvider != null) {
            baseClient.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val apiKey = apiKeyProvider.invoke().trim()
                    val requestBuilder = request.newBuilder()
                    if (apiKey.isNotEmpty()) {
                        requestBuilder.header("Authorization", "Bearer $apiKey")
                    }
                    Log.d(
                        TAG,
                        "Ollama request ${request.method} ${request.url.encodedPath} auth=" +
                            if (apiKey.isNotEmpty()) "***" else "none"
                    )
                    chain.proceed(requestBuilder.build())
                }
                .build()
        } else {
            baseClient
        }

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OllamaApi::class.java)
    }
}
