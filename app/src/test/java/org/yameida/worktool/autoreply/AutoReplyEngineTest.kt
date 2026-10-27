package org.yameida.worktool.autoreply

import org.junit.Assert.*
import org.junit.Test

class AutoReplyEngineTest {
    private var now = 1_000_000_000_000L
    private val transport = FakeTransport(HttpResult(200, """{"choices":[{"message":{"content":"AI答复"}}]}"""))
    private var config = AutoReplyConfig(
        aiEnabled = true, rulesEnabled = true, groupsText = "产品交流群",
        apiUrl = "https://api.example.com/v1", model = "m1",
        rulesText = "价格=>示例价格回复"
    )

    private fun engine() = AutoReplyEngine({ config }, ChatClient(transport), ReplyLimiter({ now }, { 0 }))

    private fun group(text: String, room: String = "产品交流群(8)", sender: String? = "小王") =
        IncomingMessage(room, true, sender, text, "小助手")

    @Test fun defaultConfigNeverReplies() {
        config = AutoReplyConfig(groupsText = "产品交流群")
        assertNull(engine().handle(group("@小助手 你好")))
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun onlyConfiguredGroups() {
        val e = engine()
        assertNull(e.handle(group("@小助手 你好", room = "别的群")))
        assertNull(e.handle(IncomingMessage("小李", false, "小李", "@小助手 你好", "小助手")))
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun privateChatWorksOnlyWhenEnabled() {
        config = config.copy(includePrivate = true)
        val r = engine().handle(IncomingMessage("小李", false, "小李", "你好", "小助手"))
        assertEquals(ReplySource.AI, r?.source)
        assertNull(r?.atName)
    }

    @Test fun ruleBeatsAiAndNeedsNoAt() {
        val r = engine().handle(group("请问价格是多少"))
        assertEquals(ReplySource.RULE, r?.source)
        assertEquals("示例价格回复", r?.text)
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun aiNeedsAtByDefaultInGroup() {
        val e = engine()
        assertNull(e.handle(group("大家好")))
        assertTrue(transport.requests.isEmpty())
        val r = e.handle(group("@小助手\u2005今天天气怎么样"))
        assertEquals("AI答复", r?.text)
        assertTrue(transport.requests[0].body.contains("今天天气怎么样"))
        assertFalse(transport.requests[0].body.contains("@小助手"))
    }

    @Test fun keywordTriggerAndNoAtRequirement() {
        config = config.copy(requireAt = true, triggerKeywordsText = "机器人")
        assertEquals("AI答复", engine().handle(group("机器人 在吗"))?.text)
        config = config.copy(requireAt = false, triggerKeywordsText = "")
        assertEquals("AI答复", engine().handle(group("随便聊聊"))?.text)
    }

    @Test fun rulesOffFallsThroughToAi() {
        config = config.copy(rulesEnabled = false)
        assertEquals(ReplySource.AI, engine().handle(group("@小助手 价格"))?.source)
    }

    @Test fun aiDisabledStillAllowsRules() {
        config = config.copy(aiEnabled = false)
        val e = engine()
        assertNull(e.handle(group("@小助手 天气")))
        assertEquals(ReplySource.RULE, e.handle(group("价格呢"))?.source)
    }

    @Test fun minuteLimitAppliesAcrossSources() {
        config = config.copy(perMinuteLimit = 2, requireAt = false)
        val e = engine()
        assertNotNull(e.handle(group("价格1")))
        assertNotNull(e.handle(group("聊聊2")))
        assertNull(e.handle(group("聊聊3")))
        assertTrue(e.lastError.contains("上限"))
        assertEquals(1, transport.requests.size)
        now += 61_000
        assertNotNull(e.handle(group("聊聊4")))
    }

    @Test fun sameMessageHandledOnceUntilRoomMovesOn() {
        config = config.copy(requireAt = false)
        val e = engine()
        assertNotNull(e.handle(group("你好")))
        assertNull(e.handle(group("你好")))
        e.roomMovedOn("产品交流群(8)")
        assertNotNull(e.handle(group("你好")))
        assertEquals(2, transport.requests.size)
    }

    @Test fun aiFailureGivesNoReplyAndExplainsWithoutKey() {
        config = config.copy(apiKey = "secret-abc", requireAt = false)
        transport.result = HttpResult(500, "oops secret-abc")
        val e = engine()
        assertNull(e.handle(group("你好")))
        assertTrue(e.lastError.contains("500"))
        assertFalse(e.lastError.contains("secret-abc"))
    }

    @Test fun missingApiSettingsGivesNoRequest() {
        config = config.copy(apiUrl = "", requireAt = false)
        val e = engine()
        assertNull(e.handle(group("你好")))
        assertTrue(transport.requests.isEmpty())
        assertTrue(e.lastError.isNotEmpty())
    }

    @Test fun atSenderAndTruncation() {
        config = config.copy(atSender = true, requireAt = false, maxReplyChars = 3)
        transport.result = HttpResult(200, """{"choices":[{"message":{"content":"12345"}}]}""")
        val r = engine().handle(group("你好"))
        assertEquals("小王", r?.atName)
        assertEquals("123…", r?.text)
    }

    @Test fun configJsonRoundTripAndDefaults() {
        val gson = com.google.gson.Gson()
        val c = gson.fromJson("{}", AutoReplyConfig::class.java)
        assertFalse(c.aiEnabled)
        assertFalse(c.rulesEnabled)
        assertTrue(c.requireAt)
        assertEquals(5, c.perMinuteLimit)
        val back = gson.fromJson(gson.toJson(config), AutoReplyConfig::class.java)
        assertEquals(config, back)
    }
}
