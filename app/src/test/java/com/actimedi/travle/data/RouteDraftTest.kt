package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor works in durations, not clock times: one start time, then how long
 * each ride and each pause takes. These tests pin that arithmetic down.
 */
class RouteDraftTest {

    /**
     * A tiny hand-built network: 세 정거장이 한 노선 위에 순서대로 있고,
     * 갈아탈 두 번째 노선이 가운데 역에서 갈라진다.
     */
    private val network = SubwayNetwork(
        source = "test",
        stations = listOf(
            SubwayStation("가역", 37.50, 127.00, listOf("1호선")),
            SubwayStation("나역", 37.51, 127.01, listOf("1호선", "2호선")),
            SubwayStation("다역", 37.52, 127.02, listOf("1호선")),
            SubwayStation("라역", 37.53, 127.05, listOf("2호선")),
        ),
        lines = listOf(
            SubwayLine("1호선", "#004A85", listOf(listOf(0, 1, 2))),
            SubwayLine("2호선", "#00A23F", listOf(listOf(1, 3))),
        ),
    )

    private val draft = RouteDraft(
        title = "테스트 경로",
        dayOfWeek = "토요일",
        startTime = ClockTime.parse("09:00"),
        stops = listOf(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", kind = StopKind.TRANSFER, pauseMinutes = 4),
            RouteStop(name = "라역", line = "2호선", kind = StopKind.STAY, pauseMinutes = 60, memo = "점심"),
            RouteStop(name = "나역", line = "2호선", kind = StopKind.TRANSFER, pauseMinutes = 4),
        ),
    )

    @Test
    fun `every clock time is derived from the single start time`() {
        val schedule = draft.schedule(network)

        // 출발지는 시각 하나뿐이다.
        assertEquals("09:00", schedule[0].departure.format())

        // 가역 → 나역: 한 정거장 = 2분.
        assertEquals(2, schedule[1].travelMinutes)
        assertEquals("09:02", schedule[1].arrival.format())
        assertEquals("09:06", schedule[1].departure.format())   // 환승 대기 4분

        // 나역 → 라역: 한 정거장 = 2분, 이후 60분 체류.
        assertEquals("09:08", schedule[2].arrival.format())
        assertEquals("10:08", schedule[2].departure.format())
    }

    @Test
    fun `the last stop gets no trailing pause`() {
        val schedule = draft.schedule(network)
        val last = schedule.last()
        assertEquals(last.arrival, last.departure)
    }

    @Test
    fun `a transfer becomes a wait, a stay becomes a card`() {
        val route = draft.toRoute(network, now = 1L)
        val timeline = route.toTimeline()

        // 나역은 환승이라 카드가 없고, 그 대신 다음 이동 앞에 대기가 붙는다.
        assertEquals(0, route.segments.count { it is RouteSegment.Stay && it.place == "나역" })
        assertEquals(4, timeline.first { it.transferWaitMinutes > 0 }.transferWaitMinutes)

        val stay = route.segments.filterIsInstance<RouteSegment.Stay>().single()
        assertEquals("라역", stay.place)
        assertEquals(60, stay.minutes)
        assertEquals("점심", stay.label)
    }

    @Test
    fun `an override replaces the estimate`() {
        val corrected = draft.copy(
            stops = draft.stops.mapIndexed { i, s ->
                if (i == 1) s.copy(travelMinutesOverride = 11) else s
            },
        )
        val schedule = corrected.schedule(network)
        assertEquals(11, schedule[1].travelMinutes)
        assertFalse(schedule[1].travelIsEstimated)
        assertEquals("09:11", schedule[1].arrival.format())
        // 뒤따르는 시각이 전부 밀린다.
        assertEquals("09:17", schedule[2].arrival.format())
    }

    @Test
    fun `totals still reconcile`() {
        val summary = draft.toRoute(network, now = 1L).summarize()
        assertEquals(summary.totalMinutes, summary.movingMinutes + summary.stayMinutes)
    }

    @Test
    fun `validation catches the mistakes a user can actually make`() {
        assertTrue(draft.validate(network).isValid)
        assertFalse(draft.copy(title = "  ").validate(network).isValid)
        assertFalse(draft.copy(stops = draft.stops.take(1)).validate(network).isValid)

        val blankLine = draft.copy(
            stops = draft.stops.mapIndexed { i, s -> if (i == 1) s.copy(line = "") else s },
        )
        assertFalse(blankLine.validate(network).isValid)

        // 자정을 넘기면 막는다.
        val tooLong = draft.copy(
            stops = draft.stops.mapIndexed { i, s ->
                if (i == 2) s.copy(pauseMinutes = 20 * 60) else s
            },
        )
        assertFalse(tooLong.validate(network).isValid)
    }

    @Test
    fun `the line is derived from the stations either side of it`() {
        val blank = draft.copy(stops = draft.stops.map { it.copy(line = "", lineIsManual = false) })
        val filled = blank.withAutoLines(network)
        assertEquals("1호선", filled.stops[1].line)
        assertEquals("2호선", filled.stops[2].line)
    }

    @Test
    fun `a hand-picked line survives auto-fill`() {
        val pinned = draft.copy(
            stops = draft.stops.mapIndexed { i, s ->
                if (i == 1) s.copy(line = "직접입력", lineIsManual = true) else s
            },
        )
        assertEquals("직접입력", pinned.withAutoLines(network).stops[1].line)
    }
}
