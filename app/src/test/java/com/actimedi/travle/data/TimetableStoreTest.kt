package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오프라인 동작의 근거가 되는 셈.
 *
 * 여행자는 로밍이 끊긴 채로 지하철에 있는 경우가 많다. 낡았다고 버리면 정작
 * 필요한 순간에 아무것도 못 보여주므로, '성한가'와 '쓸 것인가'는 다른 판단이다.
 */
class TimetableStoreTest {

    @Test
    fun `받은 지 얼마 안 된 자료는 성하다`() {
        val cache = TimetableCache(savedOn = "2026-08-01")
        assertFalse(cache.isStale("2026-08-01"))
        assertFalse(cache.isStale("2026-08-13"))
    }

    @Test
    fun `보름이 지나면 낡은 것으로 본다`() {
        val cache = TimetableCache(savedOn = "2026-08-01")
        assertTrue(cache.isStale("2026-08-15"))
        assertTrue(cache.isStale("2026-09-01"))
    }

    @Test
    fun `받은 적이 없거나 날짜가 깨졌으면 낡은 것으로 본다`() {
        assertTrue(TimetableCache().isStale("2026-08-12"))
        assertTrue(TimetableCache(savedOn = "어제").isStale("2026-08-12"))
    }

    @Test
    fun `달과 해를 건너뛰어도 간격이 맞는다`() {
        // 월말·월초, 그리고 해를 넘기는 자리에서 통산일 셈이 어긋나기 쉽다.
        assertEquals(1, TimetableCache.daysBetween("2026-01-31", "2026-02-01"))
        assertEquals(1, TimetableCache.daysBetween("2026-12-31", "2027-01-01"))
        assertEquals(365, TimetableCache.daysBetween("2026-03-01", "2027-03-01"))
        // 2028년은 윤년이다.
        assertEquals(366, TimetableCache.daysBetween("2028-01-01", "2029-01-01"))
    }

    @Test
    fun `저장한 그대로 돌아온다`() {
        val kept = mutableListOf<TimetableCache>()
        val store = object : TimetableStore {
            override fun load(): TimetableCache = kept.lastOrNull() ?: TimetableCache()
            override fun save(cache: TimetableCache) { kept += cache }
        }

        val cache = TimetableCache("2026-08-12", mapOf("군자|7호선|청구|WEEKDAY" to listOf(674, 681)))
        store.save(cache)

        assertEquals(cache, store.load())
        assertFalse(store.load().isEmpty)
    }
}
