package com.actimedi.travle.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 막차 안전장치의 셈. 뒤에서부터 거슬러 올라가는 것이 요점이라, 앞 구간의 여유가
 * 뒤 구간까지 감안한 값인지를 못박는다.
 */
class LastTrainTest {

    private val network = SubwayNetwork(
        source = "test",
        stations = listOf(
            SubwayStation("가역", 37.50, 127.00, listOf("1호선")),
            SubwayStation("나역", 37.51, 127.01, listOf("1호선", "2호선")),
            SubwayStation("다역", 37.52, 127.05, listOf("2호선")),
        ),
        lines = listOf(
            SubwayLine("1호선", "#004A85", listOf(listOf(0, 1))),
            SubwayLine("2호선", "#00A23F", listOf(listOf(1, 2))),
        ),
        // 두 구간 모두 10분으로 못박아 셈을 눈으로 따라갈 수 있게 한다.
        times = listOf(listOf(0, 1, 600), listOf(1, 2, 600)),
    )

    private class FakeTimetable(private val table: Map<String, List<String>>) : TimetableSource {
        override suspend fun departures(
            station: String,
            line: String,
            towards: String,
            dayType: DayType,
        ): List<ClockTime> = table["$station|$line"].orEmpty().map { ClockTime.parse(it) }
    }

    /** 가역 →(1호선) 나역 →(2호선) 다역. 나역에서 [stay]분 머문다. */
    private fun plan(start: String, stay: Int) = RouteDraft(
        title = "막차 시험",
        dayOfWeek = "월요일",
        startTime = ClockTime.parse(start),
        stops = listOf(
            RouteStop(name = "가역"),
            RouteStop(name = "나역", line = "1호선", kind = StopKind.STAY, pauseMinutes = stay),
            RouteStop(name = "다역", line = "2호선"),
        ),
    )

    @Test
    fun `뒤 구간의 막차가 앞 구간의 마감을 당긴다`() = runBlocking {
        val source = FakeTimetable(
            mapOf(
                // 가역은 밤늦게까지 다니지만…
                "가역|1호선" to listOf("22:00", "22:30", "23:00", "23:30"),
                // …나역에서 갈아탈 2호선 막차가 23:00이다.
                "나역|2호선" to listOf("22:00", "22:30", "23:00"),
            ),
        )

        val result = plan("22:00", stay = 20).checkLastTrain(network, source)

        val atNa = result.deadlines.single { it.station == "나역" }
        assertEquals(ClockTime.parse("23:00"), atNa.latestBoard)

        // 나역을 23:00에 떠나려면 20분 머문 뒤여야 하니 22:40까지 닿아야 하고,
        // 이동이 10분이니 가역은 22:30 열차가 마지막이다. 23:00·23:30은 소용없다.
        val atGa = result.deadlines.single { it.station == "가역" }
        assertEquals(ClockTime.parse("22:30"), atGa.latestBoard)
        // 이 구간만 보면 23:30까지 있다 — 앞당겨야 하는 이유는 뒤 구간에 있다.
        assertEquals(ClockTime.parse("23:30"), atGa.lastTrain)
    }

    @Test
    fun `계획보다 늦게까지 여유가 있으면 양수로 나온다`() = runBlocking {
        val source = FakeTimetable(
            mapOf(
                "가역|1호선" to listOf("09:00", "09:30", "10:00"),
                "나역|2호선" to listOf("09:00", "10:00", "11:00"),
            ),
        )

        val result = plan("09:00", stay = 30).checkLastTrain(network, source)

        // 나역 막차 11:00, 계획은 09:40 출발 → 80분 여유.
        val atNa = result.deadlines.single { it.station == "나역" }
        assertEquals(80, atNa.slackMinutes)
        assertNull(result.broken)
    }

    @Test
    fun `가장 아슬아슬한 지점을 골라낸다`() = runBlocking {
        val source = FakeTimetable(
            mapOf(
                "가역|1호선" to listOf("09:00", "09:05"),
                "나역|2호선" to listOf("09:40", "12:00"),
            ),
        )

        val result = plan("09:00", stay = 30).checkLastTrain(network, source)

        // 가역: 계획 09:00, 막차 09:05 → 여유 5분. 나역: 여유 140분.
        assertEquals("가역", result.tightest?.station)
        assertEquals(5, result.tightest?.slackMinutes)
    }

    @Test
    fun `탈 열차가 없으면 끊긴 지점을 알려준다`() = runBlocking {
        val source = FakeTimetable(
            mapOf(
                "가역|1호선" to listOf("23:50"),
                // 다역으로 가는 막차가 이미 끊겼다.
                "나역|2호선" to listOf("22:00"),
            ),
        )

        val result = plan("23:50", stay = 10).checkLastTrain(network, source)

        // 가역에서는 계획대로 23:50 열차를 탈 수 있다. 발이 묶이는 곳은 나역이다.
        val broken = result.broken
        assertEquals("나역", broken?.station)
        assertEquals("다역", broken?.towards)
        assertEquals(ClockTime.parse("22:00"), broken?.lastTrain)
    }

    @Test
    fun `시간표가 없는 구간은 계획을 믿고 이름을 남긴다`() = runBlocking {
        val result = plan("09:00", stay = 30).checkLastTrain(network, EmptyTimetable)

        assertTrue(result.isEmpty)
        assertEquals(listOf("1호선", "2호선"), result.skipped.sorted())
    }

    @Test
    fun `정거장이 하나뿐이면 따질 것이 없다`() = runBlocking {
        val single = RouteDraft(stops = listOf(RouteStop(name = "가역")))
        assertTrue(single.checkLastTrain(network, EmptyTimetable).isEmpty)
    }
}
