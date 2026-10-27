package org.yameida.worktool.autoreply

/** 保存「今天已回复多少条」，进程重启后不丢。 */
interface DayCounterStore {
    fun get(): Pair<Long, Int>
    fun set(day: Long, count: Int)
}

class MemoryDayCounterStore : DayCounterStore {
    private var day = -1L
    private var count = 0
    override fun get() = Pair(day, count)
    override fun set(day: Long, count: Int) {
        this.day = day
        this.count = count
    }
}

/**
 * 回复上限：每分钟（滑动 60 秒）和每天（按本地日历日）。
 * tryAcquire 成功即记一次；限额 <= 0 视为不限。
 */
class ReplyLimiter(
    private val clock: () -> Long,
    private val zoneOffsetMs: (Long) -> Int,
    private val store: DayCounterStore = MemoryDayCounterStore()
) {
    private val recent = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(perMinute: Int, perDay: Int): Boolean {
        val now = clock()
        while (recent.isNotEmpty() && now - recent.first() >= 60_000L) recent.removeFirst()
        val day = Math.floorDiv(now + zoneOffsetMs(now), 86_400_000L)
        val saved = store.get()
        val todayCount = if (saved.first == day) saved.second else 0
        if (perMinute > 0 && recent.size >= perMinute) return false
        if (perDay > 0 && todayCount >= perDay) return false
        recent.addLast(now)
        store.set(day, todayCount + 1)
        return true
    }

    @Synchronized
    fun todayCount(): Int {
        val now = clock()
        val day = Math.floorDiv(now + zoneOffsetMs(now), 86_400_000L)
        val saved = store.get()
        return if (saved.first == day) saved.second else 0
    }
}
