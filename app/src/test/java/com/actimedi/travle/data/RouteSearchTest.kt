package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 두 길이 있는 작은 노선망:
 *
 *   1호선  가 - 나 - 다 - 라 - 마 - 바        (직행이지만 6정거장)
 *   2호선  가 - 사 - 바                       (짧지만 갈아타야 함 — 사에서 3호선으로)
 *   3호선  사 - 바
 */
class RouteSearchTest {

    private val network = SubwayNetwork(
        source = "test",
        stations = listOf(
            SubwayStation("가", 37.50, 127.00, listOf("1호선", "2호선")),   // 0
            SubwayStation("나", 37.51, 127.00, listOf("1호선")),           // 1
            SubwayStation("다", 37.52, 127.00, listOf("1호선")),           // 2
            SubwayStation("라", 37.53, 127.00, listOf("1호선")),           // 3
            SubwayStation("마", 37.54, 127.00, listOf("1호선")),           // 4
            SubwayStation("바", 37.55, 127.00, listOf("1호선", "3호선")),   // 5
            SubwayStation("사", 37.52, 127.02, listOf("2호선", "3호선")),   // 6
        ),
        lines = listOf(
            SubwayLine("1호선", "#004A85", listOf(listOf(0, 1, 2, 3, 4, 5))),
            SubwayLine("2호선", "#00A23F", listOf(listOf(0, 6))),
            SubwayLine("3호선", "#ED6C00", listOf(listOf(6, 5))),
        ),
    )

    @Test
    fun `fastest takes the short hop even though it means changing`() {
        val result = RouteSearch.find(network, "가", "바", SearchGoal.FASTEST)!!
        // 2정거장 + 환승 대기 4분. 역당 시간은 실측이 없어 평균으로 메운다.
        val expected = (2 * TravelTimes.SECONDS_PER_HOP + TravelTimes.DEFAULT_TRANSFER_WAIT * 60) / 60
        assertEquals(expected, result.minutes)
        assertEquals(1, result.transfers)
        assertEquals(listOf("2호선", "3호선"), result.legs.map { it.line })
    }

    @Test
    fun `fewest transfers stays on one line`() {
        val result = RouteSearch.find(network, "가", "바", SearchGoal.FEWEST_TRANSFERS)!!
        assertEquals(0, result.transfers)
        assertEquals(listOf("1호선"), result.legs.map { it.line })
        // 5정거장을 내리 간다. 빠른 길보다 느리지만 갈아타지 않는다.
        assertEquals(Math.round(5.0 * TravelTimes.SECONDS_PER_HOP / 60).toInt(), result.minutes)
        assertTrue(result.minutes > RouteSearch.find(network, "가", "바", SearchGoal.FASTEST)!!.minutes)
    }

    @Test
    fun `legs carry the stations they pass through`() {
        val result = RouteSearch.find(network, "가", "바", SearchGoal.FEWEST_TRANSFERS)!!
        val leg = result.legs.single()
        assertEquals(listOf(0, 1, 2, 3, 4, 5), leg.stations)
        assertEquals(5, leg.hops)
    }

    @Test
    fun `a station name that is not on the network yields nothing`() {
        assertNull(RouteSearch.find(network, "없는역", "바", SearchGoal.FASTEST))
        assertNull(RouteSearch.find(network, "가", "없는역", SearchGoal.FASTEST))
    }

    @Test
    fun `the same station costs nothing`() {
        val result = RouteSearch.find(network, "가", "가", SearchGoal.FASTEST)!!
        assertEquals(0, result.minutes)
        assertTrue(result.legs.isEmpty())
    }

    @Test
    fun `the seoul network resolves a real journey`() {
        val seoul = SubwayNetwork(
            source = "test",
            stations = network.stations,
            lines = network.lines,
        )
        // 인접 목록이 양방향인지 — 되짚어 가는 길도 찾아야 한다.
        val back = RouteSearch.find(seoul, "바", "가", SearchGoal.FEWEST_TRANSFERS)!!
        assertEquals(0, back.transfers)
        assertEquals(listOf(5, 4, 3, 2, 1, 0), back.legs.single().stations)
    }
}
