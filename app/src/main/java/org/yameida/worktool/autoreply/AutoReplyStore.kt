package org.yameida.worktool.autoreply

import com.blankj.utilcode.util.SPUtils
import com.google.gson.Gson
import java.util.TimeZone

/** 自动回复设置与回复计数的本机存储（SharedPreferences，不上传）。 */
object AutoReplyStore {
    private const val SP_NAME = "autoReply"
    private val gson = Gson()

    fun load(): AutoReplyConfig {
        val json = SPUtils.getInstance(SP_NAME).getString("config", "")
        if (json.isBlank()) return AutoReplyConfig()
        return try {
            gson.fromJson(json, AutoReplyConfig::class.java) ?: AutoReplyConfig()
        } catch (e: Exception) {
            AutoReplyConfig()
        }
    }

    fun save(config: AutoReplyConfig) {
        SPUtils.getInstance(SP_NAME).put("config", gson.toJson(config))
    }

    private object DayStore : DayCounterStore {
        override fun get(): Pair<Long, Int> {
            val sp = SPUtils.getInstance(SP_NAME)
            return Pair(sp.getLong("day", -1L), sp.getInt("dayCount", 0))
        }

        override fun set(day: Long, count: Int) {
            val sp = SPUtils.getInstance(SP_NAME)
            sp.put("day", day)
            sp.put("dayCount", count)
        }
    }

    val limiter = ReplyLimiter(
        clock = { System.currentTimeMillis() },
        zoneOffsetMs = { TimeZone.getDefault().getOffset(it) },
        store = DayStore
    )

    val engine = AutoReplyEngine({ load() }, ChatClient(UrlConnectionTransport()), limiter)
}
