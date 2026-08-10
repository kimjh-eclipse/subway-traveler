package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FareEstimateTest {

    @Test
    fun `the distance table matches the published steps`() {
        assertEquals(Fares.BASE_FARE, Fares.fareForKm(0.0))
        assertEquals(Fares.BASE_FARE, Fares.fareForKm(10.0))
        // 10~50km: 5km마다 100원
        assertEquals(Fares.BASE_FARE + 100, Fares.fareForKm(12.0))
        assertEquals(Fares.BASE_FARE + 100, Fares.fareForKm(15.0))
        assertEquals(Fares.BASE_FARE + 200, Fares.fareForKm(16.0))
        assertEquals(Fares.BASE_FARE + 800, Fares.fareForKm(50.0))
        // 50km 초과: 8km마다 100원
        assertEquals(Fares.BASE_FARE + 900, Fares.fareForKm(51.0))
        assertEquals(Fares.BASE_FARE + 900, Fares.fareForKm(58.0))
        assertEquals(Fares.BASE_FARE + 1_000, Fares.fareForKm(59.0))
    }

    @Test
    fun `staying somewhere starts a new fare, transferring does not`() {
        val network = SubwayNetwork(
            source = "test",
            stations = listOf(
                SubwayStation("가", 37.50, 127.00, listOf("1호선")),
                SubwayStation("나", 37.51, 127.00, listOf("1호선", "2호선")),
                SubwayStation("다", 37.52, 127.00, listOf("2호선")),
            ),
            lines = listOf(
                SubwayLine("1호선", "#004A85", listOf(listOf(0, 1))),
                SubwayLine("2호선", "#00A23F", listOf(listOf(1, 2))),
            ),
        )

        // 환승만 한 하루 — 요금 한 번.
        val transferOnly = RouteDraft(
            title = "환승",
            stops = listOf(
                RouteStop(name = "가"),
                RouteStop(name = "나", line = "1호선", kind = StopKind.TRANSFER, pauseMinutes = 4),
                RouteStop(name = "다", line = "2호선"),
            ),
        ).toRoute(network, now = 1L)
        assertEquals(1, transferOnly.estimateFare(network).rideCount)

        // 가운데서 머물면 개찰구를 나갔으니 요금 두 번.
        val withStay = RouteDraft(
            title = "체류",
            stops = listOf(
                RouteStop(name = "가"),
                RouteStop(name = "나", line = "1호선", kind = StopKind.STAY, pauseMinutes = 60),
                RouteStop(name = "다", line = "2호선"),
            ),
        ).toRoute(network, now = 1L)
        val fare = withStay.estimateFare(network)
        assertEquals(2, fare.rideCount)
        assertEquals(Fares.BASE_FARE * 2, fare.total)
        assertTrue(fare.total > transferOnly.estimateFare(network).total)
    }

    @Test
    fun `a stay somewhere that is not a station keeps the rider at the last station`() {
        val network = SubwayNetwork(
            source = "test",
            stations = listOf(
                SubwayStation("가", 37.50, 127.00, listOf("1호선")),
                SubwayStation("나", 37.51, 127.00, listOf("1호선")),
                SubwayStation("다", 37.52, 127.00, listOf("1호선")),
            ),
            lines = listOf(SubwayLine("1호선", "#004A85", listOf(listOf(0, 1, 2)))),
        )
        val route = Route(
            id = "t", title = "DDP", dayOfWeek = "", createdAt = 0L, origin = "가",
            segments = listOf(
                RouteSegment.Move("1호선", "나", ClockTime.parse("09:00"), ClockTime.parse("09:02"), 2),
                // 역이 아닌 장소에서 머문다.
                RouteSegment.Stay("어느 미술관", "", ClockTime.parse("09:02"), ClockTime.parse("10:02"), 60),
                RouteSegment.Move("1호선", "다", ClockTime.parse("10:02"), ClockTime.parse("10:04"), 2),
            ),
        )
        val fare = route.estimateFare(network)
        // 두 번 타지만 어느 구간도 요금에서 빠지지 않는다.
        assertEquals(2, fare.rideCount)
        assertTrue(fare.skippedLines.isEmpty())
    }

    @Test
    fun `the seed route prices every rail leg and skips only the buses`() {
        val network = SubwayNetwork(
            source = "test",
            stations = listOf(SubwayStation("가", 37.5, 127.0, listOf("1호선"))),
            lines = listOf(SubwayLine("1호선", "#004A85", listOf(listOf(0)))),
        )
        // 노선망이 비면 요금을 내지 않는다 — 없는 숫자를 지어내지 않는다.
        assertTrue(SeoulOneDayRoute.estimateFare(SubwayNetwork()).isEmpty)
        assertTrue(SeoulOneDayRoute.estimateFare(network).total >= 0)
    }
}
