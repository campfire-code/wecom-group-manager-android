package org.yameida.worktool.autoreply

/**
 * 篝火新增：自动回复设置。全部保存在本机，默认关闭。
 * 多行文本字段（群聊、触发关键词、规则）按「一行一条」解析。
 */
data class AutoReplyConfig(
    /** 开启 AI 回复（默认关闭） */
    val aiEnabled: Boolean = false,
    /** 开启关键词规则回复（默认关闭） */
    val rulesEnabled: Boolean = false,
    /** 生效的群聊名称，一行一个，完全匹配（忽略末尾的人数括号） */
    val groupsText: String = "",
    /** 私聊也生效 */
    val includePrivate: Boolean = false,
    /** 群里只有 @机器人 才触发 AI */
    val requireAt: Boolean = true,
    /** 额外的 AI 触发关键词，一行一个，消息包含任一个即触发 */
    val triggerKeywordsText: String = "",
    /** 在群里回复时 @提问人 */
    val atSender: Boolean = false,
    /** 大模型接口地址（兼容 OpenAI 格式），如 https://你的服务/v1 */
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val perMinuteLimit: Int = 5,
    val perDayLimit: Int = 200,
    /** 规则表，一行一条：关键词=>回复 */
    val rulesText: String = "",
    /** 单条回复最长字数，超出截断 */
    val maxReplyChars: Int = 500
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "你是群聊里的助手，用简体中文回答，语气友好，尽量简短，不超过 200 字。"
    }
}
