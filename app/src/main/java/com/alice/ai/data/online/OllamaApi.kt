package com.alice.ai.data.online

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
    fun create(
        baseUrl: String,
        okHttpClient: OkHttpClient? = null
    ): OllamaApi {
        require(baseUrl.isNotBlank()) { "Ollama base URL cannot be blank" }
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofitBuilder = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .addConverterFactory(GsonConverterFactory.create())

        if (okHttpClient != null) {
            retrofitBuilder.client(okHttpClient)
        }

        return retrofitBuilder
            .build()
            .create(OllamaApi::class.java)
    }
}
