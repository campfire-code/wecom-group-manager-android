package org.yameida.worktool.autoreply

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

data class HttpRequest(val url: String, val headers: Map<String, String>, val body: String, val timeoutMs: Int)
data class HttpResult(val code: Int, val body: String)

/** 网络传输，单元测试里用假实现替换。 */
interface HttpTransport {
    fun post(request: HttpRequest): HttpResult
}

class ChatException(message: String) : Exception(message)

/** 调用兼容 OpenAI 格式的 /chat/completions 接口。密钥只放在请求头里，不进异常信息和日志。 */
class ChatClient(private val transport: HttpTransport, private val timeoutMs: Int = 20_000) {

    companion object {
        fun endpoint(apiUrl: String): String {
            val base = apiUrl.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
    }

    fun buildRequest(config: AutoReplyConfig, userText: String): HttpRequest {
        val messages = JsonArray()
        if (config.systemPrompt.isNotBlank()) {
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", config.systemPrompt.trim())
            })
        }
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", userText)
        })
        val body = JsonObject().apply {
            addProperty("model", config.model.trim())
            add("messages", messages)
            addProperty("stream", false)
        }
        val headers = linkedMapOf("Content-Type" to "application/json")
        if (config.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${config.apiKey.trim()}"
        return HttpRequest(endpoint(config.apiUrl), headers, body.toString(), timeoutMs)
    }

    fun parseReply(result: HttpResult): String {
        if (result.code !in 200..299) throw ChatException("接口返回 HTTP ${result.code}")
        val content = try {
            JsonParser().parse(result.body).asJsonObject
                .getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString
        } catch (e: Exception) {
            throw ChatException("接口返回的内容无法解析，请确认接口兼容 OpenAI 格式")
        }
        val text = content.trim()
        if (text.isEmpty()) throw ChatException("模型返回了空内容")
        return text
    }

    fun chat(config: AutoReplyConfig, userText: String): String {
        val response = try {
            transport.post(buildRequest(config, userText))
        } catch (e: ChatException) {
            throw e
        } catch (e: Exception) {
            throw ChatException("请求失败：${e.javaClass.simpleName}")
        }
        return parseReply(response)
    }
}

/** 真实传输：系统自带 HttpURLConnection，使用系统默认的证书校验。 */
class UrlConnectionTransport : HttpTransport {
    override fun post(request: HttpRequest): HttpResult {
        val url = URL(request.url)
        if (url.protocol != "https" && url.protocol != "http") throw ChatException("接口地址必须以 http:// 或 https:// 开头")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = request.timeoutMs
            conn.readTimeout = request.timeoutMs
            conn.doOutput = true
            request.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }
}
