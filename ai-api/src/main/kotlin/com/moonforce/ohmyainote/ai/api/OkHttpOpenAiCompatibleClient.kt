package com.moonforce.ohmyainote.ai.api

import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpOpenAiCompatibleClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) : OpenAiCompatibleClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun askAboutImage(
        jpeg: ByteArray,
        question: String,
        settings: ResolvedAiSettings,
    ): AiAnswer = withContext(Dispatchers.IO) {
        val resolved = settings.normalized()
        val body = PromptBuilder.build(jpeg, question, resolved.model)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${resolved.baseUrl}/chat/completions")
            .header("Authorization", "Bearer ${resolved.apiKey}")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        val mark = TimeSource.Monotonic.markNow()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiApiException("AI request failed with HTTP ${response.code}", response.code)
                }
                val responseText = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(responseText).jsonObject }
                    .getOrElse { throw AiApiException("AI response is not valid JSON", response.code, it) }
                val answer = runCatching {
                    root.getValue("choices").jsonArray.first().jsonObject
                        .getValue("message").jsonObject
                        .getValue("content").jsonPrimitive.content
                }.getOrElse { throw AiApiException("AI response has no text answer", response.code, it) }
                require(answer.isNotBlank()) { "AI response is blank" }
                AiAnswer(
                    text = answer,
                    model = root["model"]?.jsonPrimitive?.contentOrNull ?: resolved.model,
                    latencyMs = mark.elapsedNow().inWholeMilliseconds,
                )
            }
        } catch (failure: AiApiException) {
            throw failure
        } catch (failure: Exception) {
            throw AiApiException("AI request failed: ${failure.javaClass.simpleName}", cause = failure)
        }
    }
}
