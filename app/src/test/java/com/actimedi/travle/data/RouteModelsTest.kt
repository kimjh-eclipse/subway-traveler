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
