package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실측 역간 시간이 추정보다 우선하는지, 없는 구간은 평균으로 메우는지 본다.
 * 실제 에셋이 아니라 손으로 만든 작은 노선망을 쓴다 — 값이 바뀌어도 규칙은 그대로다.
 */
class MeasuredTimesTest {

    private val stations = listOf(
        SubwayStation("가", 37.50, 127.00, listOf("1호선")),
        SubwayStation("나", 37.51, 127.00, listOf("1호선")),
        SubwayStation("다", 37.52, 127.00, listOf("1호선")),
    )
    private val lines = listOf(SubwayLine("1호선", "#004A85", listOf(listOf(0, 1, 2))))

    /** 가→나 60초, 나→다 180초. */
    private val measured = SubwayNetwork(
        stations = stations,
        lines = lines,
        times = listOf(listOf(0, 1, 60), listOf(1, 2, 180)),
    )
    private val bare = SubwayNetwork(stations = stations, lines = lines)

    @Test
    fun `measured seconds are used and rounded to minutes`() {
        val estimate = TravelTimes.estimate(measured, "1호선", "가", "다")
        assertEquals(4, estimate.minutes)              // 60 + 180 = 240초
        assertEquals(EstimateBasis.MEASURED, estimate.basis)
    }

    @Test
    fun `direction does not matter`() {
        assertEquals(
            TravelTimes.estimate(measured, "1호선", "가", "다").minutes,
            TravelTimes.estimate(measured, "1호선", "다", "가").minutes,
        )
        assertEquals(60, measured.secondsBetweenAdjacent(1, 0))
    }

    @Test
    fun `a leg with a gap falls back to the average and says so`() {
        val partial = SubwayNetwork(
            stations = stations,
            lines = lines,
            times = listOf(listOf(0, 1, 60)),
        )
        val estimate = TravelTimes.estimate(partial, "1호선", "가", "다")
        // 60초 실측 + 평균 92초 = 152초 → 3분
        assertEquals(3, estimate.minutes)
        assertEquals(EstimateBasis.STATIONS, estimate.basis)
    }

    @Test
    fun `without any measurements every hop uses the average`() {
        val estimate = TravelTimes.estimate(bare, "1호선", "가", "다")
        assertEquals(3, estimate.minutes)              // 92 × 2 = 184초
        assertEquals(EstimateBasis.STATIONS, estimate.basis)
        assertNull(bare.secondsBetweenAdjacent(0, 1))
    }

    @Test
    fun `search costs follow the measured times`() {
        val slow = SubwayNetwork(
            stations = stations,
            lines = lines,
            times = listOf(listOf(0, 1, 600), listOf(1, 2, 600)),
        )
        val quick = RouteSearch.find(measured, "가", "다", SearchGoal.FASTEST)!!
        val crawl = RouteSearch.find(slow, "가", "다", SearchGoal.FASTEST)!!
        assertTrue(crawl.minutes > quick.minutes)
        assertEquals(20, crawl.minutes)
    }

    @Test
    fun `the average hop is close to what the published data shows`() {
        // 서울교통공사 1~8호선 264개 구간의 평균 1분 32초.
        assertTrue(TravelTimes.SECONDS_PER_HOP in 80..110)
    }
}
