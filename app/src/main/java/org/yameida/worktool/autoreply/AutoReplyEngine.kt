package org.yameida.worktool.autoreply

/** 一条待判断的收到的消息（已确定是别人发的文字消息）。 */
data class IncomingMessage(
    val roomTitle: String,
    val isGroup: Boolean,
    val senderName: String?,
    val text: String,
    val myName: String
)

enum class ReplySource { RULE, AI }

data class AutoReply(val text: String, val source: ReplySource, val atName: String?)

/**
 * 自动回复决策：生效群聊 → 关键词规则（优先）→ AI 回复。
 * 没有回复时返回 null；失败原因放在 lastError，供界面和日志使用（不含密钥）。
 */
class AutoReplyEngine(
    private val configProvider: () -> AutoReplyConfig,
    private val chatClient: ChatClient,
    private val limiter: ReplyLimiter
) {
    @Volatile
    var lastError: String = ""
        private set

    private val handled = HashMap<String, String>()

    /** 看到房间里最后一条是自己发的，说明上一条已处理完，之后别人再发同样的话要重新响应。 */
    @Synchronized
    fun roomMovedOn(roomTitle: String) {
        handled.remove(Rules.normalizeRoom(roomTitle))
    }

    fun handle(msg: IncomingMessage): AutoReply? {
        val config = configProvider()
        if (!config.aiEnabled && !config.rulesEnabled) return null
        if (!scopeAllowed(config, msg)) return null
        val text = msg.text.trim()
        if (text.isEmpty()) return null
        if (!markHandled(msg)) return null

        val at = if (config.atSender && msg.isGroup && !msg.senderName.isNullOrBlank()) msg.senderName else null
        val clean = Rules.stripAt(text, msg.myName)

        if (config.rulesEnabled) {
            val rule = Rules.match(Rules.parse(config.rulesText).rules, clean)
            if (rule != null) {
                if (!limiter.tryAcquire(config.perMinuteLimit, config.perDayLimit)) {
                    lastError = "已达到回复上限，本条不回复"
                    return null
                }
                return AutoReply(cut(rule.reply, config), ReplySource.RULE, at)
            }
        }

        if (config.aiEnabled && aiTriggered(config, msg, text, clean)) {
            if (config.apiUrl.isBlank() || config.model.isBlank()) {
                lastError = "AI 回复已开启，但接口地址或模型名没填"
                return null
            }
            if (!limiter.tryAcquire(config.perMinuteLimit, config.perDayLimit)) {
                lastError = "已达到回复上限，本条不回复"
                return null
            }
            return try {
                val reply = chatClient.chat(config, clean)
                lastError = ""
                AutoReply(cut(reply, config), ReplySource.AI, at)
            } catch (e: ChatException) {
                lastError = e.message ?: "AI 请求失败"
                null
            }
        }
        return null
    }

    private fun scopeAllowed(config: AutoReplyConfig, msg: IncomingMessage): Boolean =
        if (msg.isGroup) Rules.roomAllowed(msg.roomTitle, config.groupsText) else config.includePrivate

    private fun aiTriggered(config: AutoReplyConfig, msg: IncomingMessage, raw: String, clean: String): Boolean {
        if (!msg.isGroup) return true
        val keywords = Rules.lines(config.triggerKeywordsText)
        if (!config.requireAt && keywords.isEmpty()) return true
        if (config.requireAt && Rules.hasAt(raw, msg.myName)) return true
        return keywords.any { raw.contains(it, ignoreCase = true) }
    }

    /** 同一房间里同一条消息只处理一次（发送失败时也不会每轮重试）。 */
    @Synchronized
    private fun markHandled(msg: IncomingMessage): Boolean {
        val room = Rules.normalizeRoom(msg.roomTitle)
        val key = "${msg.senderName}|${msg.text.trim()}"
        if (handled[room] == key) return false
        handled[room] = key
        return true
    }

    private fun cut(text: String, config: AutoReplyConfig): String {
        val max = if (config.maxReplyChars > 0) config.maxReplyChars else 500
        return if (text.length > max) text.substring(0, max) + "…" else text
    }
}
