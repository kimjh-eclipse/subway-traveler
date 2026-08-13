package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteModelsTest {

    private val route = SeoulOneDayRoute
    private val summary = route.summarize()

    @Test
    fun `day spans 07-00 to 20-28`() {
        assertEquals("07:00", route.startTime.format())
        assertEquals("20:28", route.endTime.format())
        assertEquals(13 * 60 + 28, summary.totalMinutes)
        assertEquals("13:28", formatClockSpan(summary.totalMinutes))
    }

    @Test
    fun `moving and staying account for the whole day`() {
        assertEquals(summary.totalMinutes, summary.movingMinutes + summary.stayMinutes)
    }

    @Test
    fun `counts match the segment list`() {
        assertEquals(19, summary.legCount)
        assertEquals(8, summary.stayCount)
        assertEquals(11, summary.transferCount)
        assertEquals("교동마을 LG자이", summary.finishPlace)
    }

    @Test
    fun `transfer waits only appear between consecutive rides`() {
        val timeline = route.toTimeline()
        timeline.forEach { entry ->
            if (entry.transferWaitMinutes > 0) {
                assertTrue(entry.segment is RouteSegment.Move)
                assertTrue(route.segments[entry.index - 1] is RouteSegment.Move)
            }
        }
        // 26-2번 arrives 07:23, 신분당선 departs 07:33.
        assertEquals(10, timeline[2].transferWaitMinutes)
    }

    /**
     * 기다림이 0분이어도 갈아타는 자리는 남아야 한다. 예전에는 대기 시간이 있을 때만
     * 환승 역을 채워서, 시간표에 딱 맞는 환승은 화면에 아예 나오지 않았다 — 누를 수가
     * 없으니 그 역의 실시간 도착도 볼 수 없었다.
     */
    @Test
    fun `기다림이 없는 환승도 역을 남긴다`() {
        val back = Route(
            id = "t1",
            title = "연속 환승",
            dayOfWeek = "월요일",
            createdAt = 0L,
            origin = "청량리",
            segments = listOf(
                RouteSegment.Move("1호선", "신도림", ClockTime.parse("09:00"), ClockTime.parse("09:10"), 10),
                // 내리자마자 갈아탄다 — 기다림이 없다.
                RouteSegment.Move("2호선", "신촌", ClockTime.parse("09:10"), ClockTime.parse("09:25"), 15),
            ),
        )

        val timeline = back.toTimeline()

        assertEquals(0, timeline[1].transferWaitMinutes)
        assertEquals("신도림", timeline[1].transferStation)
        // 갈아타지 않는 첫 구간에는 붙지 않는다.
        assertEquals(null, timeline[0].transferStation)
    }

    /**
     * 체류가 없는 경로에서 `체류만`을 누르면 빈 화면이 됐다. 탭을 잠그려면 갈래마다
     * 볼 것이 있는지 먼저 알아야 한다.
     */
    @Test
    fun `갈래가 비었는지 알려준다`() {
        val timeline = route.toTimeline()
        assertTrue(RouteFilter.ALL.isNotEmpty(timeline))
        assertTrue(RouteFilter.MOVE.isNotEmpty(timeline))
        assertTrue(RouteFilter.STAY.isNotEmpty(timeline))

        val noStay = Route(
            id = "t2",
            title = "쭉 이동만",
            dayOfWeek = "",
            createdAt = 0L,
            origin = "강남",
            segments = listOf(
                RouteSegment.Move("2호선", "선릉", ClockTime.parse("09:00"), ClockTime.parse("09:10"), 10),
            ),
        ).toTimeline()
        assertTrue(RouteFilter.MOVE.isNotEmpty(noStay))
        assertFalse(RouteFilter.STAY.isNotEmpty(noStay))
    }

    @Test
    fun `filters partition the timeline`() {
        val timeline = route.toTimeline()
        val moves = timeline.filterBy(RouteFilter.MOVE)
        val stays = timeline.filterBy(RouteFilter.STAY)
        assertEquals(timeline.size, moves.size + stays.size)
        assertEquals(summary.legCount, moves.size)
        assertEquals(summary.stayCount, stays.size)
    }

    @Test
    fun `clock spans read as hours and minutes`() {
        // 사람이 읽는 시간 표기는 로케일 리소스가 맡는다. 여기서는 자릿수만 본다.
        assertEquals("13:28", formatClockSpan(808))
        assertEquals("0:08", formatClockSpan(8))
        assertEquals("1:00", formatClockSpan(60))
    }
}
