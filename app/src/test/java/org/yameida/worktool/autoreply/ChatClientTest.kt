package org.yameida.worktool.autoreply

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class FakeTransport(var result: HttpResult? = null, var error: Exception? = null) : HttpTransport {
    val requests = arrayListOf<HttpRequest>()
    override fun post(request: HttpRequest): HttpResult {
        requests.add(request)
        error?.let { throw it }
        return result!!
    }
}

class ChatClientTest {
    private val config = AutoReplyConfig(
        apiUrl = "https://api.example.com/v1/", apiKey = "test-key-123", model = "m1", systemPrompt = "你是助手"
    )

    private fun ok(content: String) = HttpResult(
        200, """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""
    )

    @Test fun endpointNormalization() {
        assertEquals("https://a.com/v1/chat/completions", ChatClient.endpoint("https://a.com/v1/"))
        assertEquals("https://a.com/v1/chat/completions", ChatClient.endpoint(" https://a.com/v1/chat/completions "))
        assertEquals("https://a.com/chat/completions", ChatClient.endpoint("https://a.com"))
    }

    @Test fun requestContainsModelMessagesAndBearer() {
        val t = FakeTransport(ok("好"))
        val req = ChatClient(t).buildRequest(config, "你好\"引号\"")
        assertEquals("https://api.example.com/v1/chat/completions", req.url)
        assertEquals("Bearer test-key-123", req.headers["Authorization"])
        val body = JsonParser().parse(req.body).asJsonObject
        assertEquals("m1", body["model"].asString)
        assertFalse(body["stream"].asBoolean)
        val msgs = body.getAsJsonArray("messages")
        assertEquals(2, msgs.size())
        assertEquals("system", msgs[0].asJsonObject["role"].asString)
        assertEquals("你是助手", msgs[0].asJsonObject["content"].asString)
        assertEquals("你好\"引号\"", msgs[1].asJsonObject["content"].asString)
    }

    @Test fun noKeyNoAuthHeaderAndNoSystemMessageWhenBlank() {
        val req = ChatClient(FakeTransport()).buildRequest(config.copy(apiKey = "", systemPrompt = " "), "hi")
        assertFalse(req.headers.containsKey("Authorization"))
        assertEquals(1, JsonParser().parse(req.body).asJsonObject.getAsJsonArray("messages").size())
    }

    @Test fun parsesReplyAndTrims() {
        assertEquals("好的", ChatClient(FakeTransport(ok("  好的\\n"))).chat(config, "hi"))
    }

    @Test fun httpErrorDoesNotLeakKeyOrBody() {
        val t = FakeTransport(HttpResult(401, """{"error":"bad key test-key-123"}"""))
        val e = assertThrows(ChatException::class.java) { ChatClient(t).chat(config, "hi") }
        assertTrue(e.message!!.contains("401"))
        assertFalse(e.message!!.contains("test-key-123"))
    }

    @Test fun badJsonAndEmptyContent() {
        assertThrows(ChatException::class.java) { ChatClient(FakeTransport(HttpResult(200, "<html>"))).chat(config, "hi") }
        assertThrows(ChatException::class.java) { ChatClient(FakeTransport(ok("  "))).chat(config, "hi") }
    }

    @Test fun networkFailureWrapped() {
        val t = FakeTransport(error = java.io.IOException("连接 test-key-123 失败"))
        val e = assertThrows(ChatException::class.java) { ChatClient(t).chat(config, "hi") }
        assertFalse(e.message!!.contains("test-key-123"))
    }
}
