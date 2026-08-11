package com.actimedi.travle.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시간표 맞추기의 셈을 못박는다. 네트워크는 [FakeTimetable]로 대신하므로
 * 공공데이터포털이 죽어 있어도 이 규칙은 계속 검증된다.
 */
class TimetableTest {

    private val network = SubwayNetwork(
        source = "test",
        stations = listOf(
            SubwayStation("가역", 37.50, 127.00, listOf("1호선")),
            SubwayStation("나역", 37.51, 127.01, listOf("1호선", "2호선")),
            SubwayStation("라역", 37.53, 127.05, listOf("2호선")),
        ),
        lines = listOf(
            SubwayLine("1호선", "#004A85", listOf(listOf(0, 1))),
            SubwayLine("2호선", "#00A23F", listOf(listOf(1, 2))),
        ),
    )

    /** 정해진 시각표를 그대로 돌려준다. 등록하지 않은 역은 자료 없음. */
    private class FakeTimetable(
        private val table: Map<String, List<String>>,
    ) : TimetableSource {
        override suspend fun departures(
            station: String,
            line: String,
            towards: String,
            dayType: DayType,
        ): List<ClockTime> = table["$station|$line"].orEmpty().map { ClockTime.parse(it) }
    }

    private fun draft(vararg stops: RouteStop) = RouteDraft(
        title = "테스트",
        dayOfWeek = "월요일",
        startTime = ClockTime.parse("09:00"),
        stops = stops.toList(),
    )

    @Test
    fun `기다린 시간은 앞 정거장의 머무는 시간이 된다`() = runBlocking {
        val plan = draft(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", kind = StopKind.STAY, pauseMinutes = 30),
        )
        // 09:00에 나설 준비가 끝나지만 열차는 09:07에 온다.
        val source = FakeTimetable(mapOf("가역|1호선" to listOf("08:55", "09:07", "09:19")))

        val result = plan.alignToTimetable(network, source)

        assertEquals(7, result.draft.stops[0].pauseMinutes)
        assertEquals(1, result.aligned.size)
        assertEquals(ClockTime.parse("09:07"), result.aligned[0].boardTime)
        assertEquals(7, result.aligned[0].addedWaitMinutes)
    }

    @Test
    fun `열차가 딱 맞으면 계획을 그대로 둔다`() = runBlocking {
        val plan = draft(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", pauseMinutes = 30),
        )
        val source = FakeTimetable(mapOf("가역|1호선" to listOf("09:00", "09:10")))

        val result = plan.alignToTimetable(network, source)

        assertEquals(0, result.draft.stops[0].pauseMinutes)
        assertEquals(0, result.changedCount)
    }

    @Test
    fun `사용자가 정한 체류 시간은 줄지 않는다`() = runBlocking {
        val plan = draft(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", kind = StopKind.STAY, pauseMinutes = 60),
            RouteStop(name = "라역", line = "2호선", pauseMinutes = 0),
        )
        // 나역을 09:20에 떠나는 열차가 있어도 60분 체류가 먼저다.
        val source = FakeTimetable(
            mapOf(
                "가역|1호선" to listOf("09:00"),
                "나역|2호선" to listOf("09:20", "10:30", "11:00"),
            ),
        )

        val result = plan.alignToTimetable(network, source)

        // 09:00 출발 + 이동 → 나역 도착 후 60분. 그 이후 첫 열차는 10:30이다.
        assertEquals(ClockTime.parse("10:30"), result.aligned.last().boardTime)
        assertTrue("체류가 줄었다", result.draft.stops[1].pauseMinutes >= 60)
    }

    @Test
    fun `시간표가 없는 구간은 계획대로 두고 이름을 남긴다`() = runBlocking {
        val plan = draft(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", pauseMinutes = 30),
        )

        val result = plan.alignToTimetable(network, EmptyTimetable)

        assertEquals(plan, result.draft)
        assertTrue(result.isEmpty)
        assertEquals(listOf("1호선"), result.skipped)
    }

    @Test
    fun `막차가 지났으면 그 구간은 손대지 않는다`() = runBlocking {
        val plan = draft(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", pauseMinutes = 30),
        )
        val source = FakeTimetable(mapOf("가역|1호선" to listOf("05:30", "06:00")))

        val result = plan.alignToTimetable(network, source)

        assertEquals(0, result.draft.stops[0].pauseMinutes)
        assertTrue(result.isEmpty)
        // 자료는 있었으니 '자료 없음'으로 오해하게 두지 않는다.
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `정거장이 하나뿐이면 아무것도 하지 않는다`() = runBlocking {
        val plan = draft(RouteStop(name = "가역"))
        val result = plan.alignToTimetable(network, EmptyTimetable)
        assertEquals(plan, result.draft)
        assertTrue(result.isEmpty)
    }

    /**
     * 시간표는 `강남역`을 모른다 — 오류 없이 0건을 돌려주므로 이 규칙이 틀리면
     * "시간표가 없는 노선"으로 조용히 넘어간다. 그래서 못박아 둔다.
     */
    @Test
    fun `역 이름에서 붙임말을 떼고 먼저 물어본다`() {
        val source = SeoulTimetable(apiKey = "")
        assertEquals("강남", source.variants("강남역").first())
        assertEquals("총신대입구", source.variants("총신대입구(이수)").first())
        assertEquals("서울", source.variants("서울역").first())
        // 원래 이름도 뒤에 남겨 둔다 — 뗀 쪽이 빈손일 때를 대비한다.
        assertTrue(source.variants("강남역").contains("강남역"))
        // 이미 붙임말이 없으면 한 가지뿐이다.
        assertEquals(listOf("강남"), source.variants("강남"))
        // 한 글자만 남는 이름은 떼지 않는다.
        assertEquals(listOf("역"), source.variants("역"))
    }

    @Test
    fun `요일 문구로 시간표 종류를 고른다`() {
        assertEquals(DayType.WEEKDAY, DayType.of("월요일"))
        assertEquals(DayType.SATURDAY, DayType.of("토요일"))
        assertEquals(DayType.HOLIDAY, DayType.of("일요일"))
        assertEquals(DayType.WEEKDAY, DayType.of(""))
    }
}
