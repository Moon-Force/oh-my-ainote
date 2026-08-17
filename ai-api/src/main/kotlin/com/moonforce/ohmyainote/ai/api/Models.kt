package com.moonforce.ohmyainote.ai.api

data class AiSettings(
    val baseUrl: String,
    val model: String,
    val hasApiKey: Boolean,
)

data class ResolvedAiSettings(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    fun normalized(): ResolvedAiSettings {
        require(baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()) { "AI settings are incomplete" }
        return copy(baseUrl = baseUrl.trim().trimEnd('/'), model = model.trim())
    }
}

data class AiAnswer(
    val text: String,
    val model: String,
    val latencyMs: Long,
)

interface OpenAiCompatibleClient {
    suspend fun askAboutImage(
        jpeg: ByteArray,
        question: String,
        settings: ResolvedAiSettings,
    ): AiAnswer
}

class AiApiException(message: String, val statusCode: Int? = null, cause: Throwable? = null) :
    IOException(message, cause)

private typealias IOException = java.io.IOException
