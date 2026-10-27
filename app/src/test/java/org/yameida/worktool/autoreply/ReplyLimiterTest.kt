package org.yameida.worktool.autoreply

import org.junit.Assert.*
import org.junit.Test

class ReplyLimiterTest {
    private var now = 1_000_000_000_000L
    private fun limiter(store: DayCounterStore = MemoryDayCounterStore(), offset: Int = 0) =
        ReplyLimiter({ now }, { offset }, store)

    @Test fun perMinuteSlidingWindow() {
        val l = limiter()
        repeat(3) { assertTrue(l.tryAcquire(3, 100)) }
        assertFalse(l.tryAcquire(3, 100))
        now += 59_000
        assertFalse(l.tryAcquire(3, 100))
        now += 1_001
        assertTrue(l.tryAcquire(3, 100))
    }

    @Test fun perDayResetsNextCalendarDay() {
        val l = limiter()
        now = 86_400_000L * 100 + 3_600_000 // 第 100 天 01:00
        repeat(2) { assertTrue(l.tryAcquire(0, 2)) ; now += 61_000 }
        assertFalse(l.tryAcquire(0, 2))
        assertEquals(2, l.todayCount())
        now = 86_400_000L * 101 + 1_000
        assertTrue(l.tryAcquire(0, 2))
        assertEquals(1, l.todayCount())
    }

    @Test fun dayBoundaryUsesLocalOffset() {
        val eightHours = 8 * 3_600_000
        val l = limiter(offset = eightHours)
        now = 86_400_000L * 100 + 15 * 3_600_000 // UTC 15:00 = 本地 23:00
        assertTrue(l.tryAcquire(0, 1))
        now += 2 * 3_600_000 // 本地次日 01:00
        assertTrue(l.tryAcquire(0, 1))
    }

    @Test fun dayCountSurvivesNewInstance() {
        val store = MemoryDayCounterStore()
        now = 86_400_000L * 100
        assertTrue(limiter(store).tryAcquire(0, 1))
        assertFalse(limiter(store).tryAcquire(0, 1))
    }

    @Test fun zeroMeansUnlimited() {
        val l = limiter()
        repeat(50) { assertTrue(l.tryAcquire(0, 0)) }
    }
}
