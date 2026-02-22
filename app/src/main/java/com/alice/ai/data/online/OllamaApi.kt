package com.alice.ai.data.online

import com.google.gson.annotations.SerializedName

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
