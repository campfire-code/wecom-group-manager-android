package org.yameida.worktool.autoreply

/** 一条关键词规则：消息包含任一关键词（或完全等于，exact=true）就回复固定内容。 */
data class KeywordRule(val keywords: List<String>, val reply: String, val exact: Boolean)

data class RuleParseResult(val rules: List<KeywordRule>, val invalidLines: List<Int>)

object Rules {

    private val spaceRegex = "[\\s\\u2005\\u00a0\\u3000]+".toRegex()
    private val roomSuffixRegex = "(…)?(\\(\\d+\\)|（\\d+）)?\\s*$".toRegex()

    /** 把多行文本拆成非空、去重的行；以 # 开头的行视为注释。 */
    fun lines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.distinct()

    /**
     * 解析规则表。格式：`关键词=>回复`。
     * - 多个关键词用 | 分隔，命中任一个即可；
     * - 关键词以 = 开头表示整句完全匹配（如 `=你好=>你好呀`）；
     * - 回复里的 \n 会换成换行；
     * - 空行和 # 开头的行跳过；缺少 => 或关键词、回复为空的行记入 invalidLines（行号从 1 开始）。
     */
    fun parse(text: String): RuleParseResult {
        val rules = arrayListOf<KeywordRule>()
        val invalid = arrayListOf<Int>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val sep = line.indexOf("=>")
            if (sep < 0) {
                invalid.add(index + 1)
                return@forEachIndexed
            }
            var keyPart = line.substring(0, sep).trim()
            val reply = line.substring(sep + 2).trim().replace("\\n", "\n")
            val exact = keyPart.startsWith("=")
            if (exact) keyPart = keyPart.substring(1).trim()
            val keywords = keyPart.split("|", "｜").map { it.trim() }.filter { it.isNotEmpty() }
            if (keywords.isEmpty() || reply.isEmpty()) {
                invalid.add(index + 1)
                return@forEachIndexed
            }
            rules.add(KeywordRule(keywords, reply, exact))
        }
        return RuleParseResult(rules, invalid)
    }

    /** 按书写顺序取第一条命中的规则，忽略大小写。 */
    fun match(rules: List<KeywordRule>, message: String): KeywordRule? {
        val msg = message.trim()
        if (msg.isEmpty()) return null
        return rules.firstOrNull { rule ->
            rule.keywords.any { k ->
                if (rule.exact) msg.equals(k, ignoreCase = true) else msg.contains(k, ignoreCase = true)
            }
        }
    }

    /** 群名比较前去掉末尾的「(人数)」「…」和空白。 */
    fun normalizeRoom(name: String): String = name.trim().replace(roomSuffixRegex, "").trim().lowercase()

    fun roomAllowed(title: String, groupsText: String): Boolean {
        val target = normalizeRoom(title)
        if (target.isEmpty()) return false
        return lines(groupsText).any { normalizeRoom(it) == target }
    }

    /** 去掉消息里的 @机器人 并整理空白。 */
    fun stripAt(text: String, myName: String): String {
        if (myName.isBlank()) return text.trim()
        return text.replace("@$myName", " ").replace(spaceRegex, " ").trim()
    }

    fun hasAt(text: String, myName: String): Boolean = myName.isNotBlank() && text.contains("@$myName")
}
