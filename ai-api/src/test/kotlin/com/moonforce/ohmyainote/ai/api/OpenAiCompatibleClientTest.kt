package com.moonforce.ohmyainote.ai.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {
    @Test
    fun sendsOfficialMultimodalShapeAndParsesAnswer() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"model":"local-vlm","choices":[{"message":{"content":"结论是 42"}}]}"""),
            )
            val client = OkHttpOpenAiCompatibleClient(OkHttpClient())

            val answer = client.askAboutImage(
                jpeg = byteArrayOf(1, 2, 3, 4),
                question = "结论是什么？",
                settings = ResolvedAiSettings(server.url("/v1/").toString(), "vision-model", "secret-key"),
            )

            val request = server.takeRequest()
            val body = request.body.readUtf8()
            val root = Json.parseToJsonElement(body).jsonObject
            val userContent = root["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer secret-key", request.getHeader("Authorization"))
            assertEquals(2048, root["max_tokens"]!!.jsonPrimitive.content.toInt())
            assertEquals("text", userContent[0].jsonObject["type"]!!.jsonPrimitive.content)
            assertTrue(
                userContent[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
                    .startsWith("data:image/jpeg;base64,"),
            )
            assertEquals("结论是 42", answer.text)
            assertEquals("local-vlm", answer.model)
            assertFalse(body.contains("secret-key"))
        }
    }
}
