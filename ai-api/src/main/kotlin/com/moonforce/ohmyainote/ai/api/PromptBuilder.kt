package com.moonforce.ohmyainote.ai.api

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PromptBuilder {
    const val SYSTEM_PROMPT =
        "You are a study assistant. The image is a crop from the user's handwritten notebook or slide. " +
            "Answer the user's question about this region. Be concise. Reply in the same language as the question."

    private val json = Json { encodeDefaults = true }

    fun build(jpeg: ByteArray, question: String, model: String): String {
        require(jpeg.isNotEmpty()) { "Selection image is empty" }
        require(question.isNotBlank()) { "Question is blank" }
        require(model.isNotBlank()) { "Model is blank" }
        val imageUrl = "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(jpeg)}"
        val payload = buildJsonObject {
            put("model", model.trim())
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(message("system", JsonPrimitive(SYSTEM_PROMPT)))
                add(
                    message(
                        "user",
                        buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", question.trim()) })
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", imageUrl) })
                                }
                            )
                        },
                    )
                )
            })
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun message(role: String, content: kotlinx.serialization.json.JsonElement) = buildJsonObject {
        put("role", role)
        put("content", content)
    }
}
