package com.actimedi.travle.data

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 수도권 전철 운임 추정.
 *
 * 정확한 운임은 노선별 영업거리표로 계산해야 하는데 번들 데이터에는 그것이 없다.
 * 여기서는 역 좌표를 이어 붙인 거리에 [ROUTE_WIGGLE]을 곱해 영업거리를 근사한다.
 * 따라서 결과는 언제나 **예상치**이고, UI도 그렇게 표시한다.
 *
 * 요금표는 바뀌므로 상수로 모아 두었다 — 인상되면 여기만 고치면 된다.
 */
object Fares {

    /** 카드 기준 성인 기본운임 (10km 이내). 2023.10.07 인상분. */
    const val BASE_FARE = 1_400
    const val BASE_DISTANCE_KM = 10.0

    /** 10~50km 구간은 5km마다, 50km 초과는 8km마다 100원씩 붙는다. */
    const val STEP_FARE = 100
    const val MID_STEP_KM = 5.0
    const val MID_LIMIT_KM = 50.0
    const val LONG_STEP_KM = 8.0

    /**
     * 통합운임 위에 별도운임이 붙는 노선.
     *
     * 구간별로 차등이 있지만 여기서는 노선당 한 값으로 근사한다. GTX-A는 아예
     * 별도 요금체계라 값이 크다.
     */
    val SURCHARGES = mapOf(
        "신분당선" to 1_000,
        "GTX-A" to 3_200,
    )

    /** 직선거리를 영업거리로 보정하는 계수. 선로는 곧지 않다. */
    const val ROUTE_WIGGLE = 1.25

    private const val EARTH_RADIUS_KM = 6371.0

    /** 거리에 따른 통합운임. */
    fun fareForKm(km: Double): Int {
        if (km <= BASE_DISTANCE_KM) return BASE_FARE
        val midKm = minOf(km, MID_LIMIT_KM) - BASE_DISTANCE_KM
        var fare = BASE_FARE + ceil(midKm / MID_STEP_KM).toInt() * STEP_FARE
        if (km > MID_LIMIT_KM) {
            fare += ceil((km - MID_LIMIT_KM) / LONG_STEP_KM).toInt() * STEP_FARE
        }
        return fare
    }

    fun distanceKm(a: SubwayStation, b: SubwayStation): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}

/** 개찰구를 나가지 않고 이어 탄 한 묶음 — 요금이 한 번 부과되는 단위. */
data class FareJourney(
    val from: String,
    val to: String,
    val lines: List<String>,
    val km: Double,
    /** 거리에 따른 통합운임. */
    val baseFare: Int,
    /** 신분당선·GTX-A 같은 별도운임 합계. */
    val surcharge: Int,
) {
    val total: Int get() = baseFare + surcharge
}

data class FareEstimate(
    val journeys: List<FareJourney> = emptyList(),
    /** 좌표를 찾지 못해 요금에 넣지 못한 구간의 노선 이름. */
    val skippedLines: List<String> = emptyList(),
) {
    val total: Int get() = journeys.sumOf { it.total }
    val rideCount: Int get() = journeys.size
    val hasSurcharge: Boolean get() = journeys.any { it.surcharge > 0 }
    val isEmpty: Boolean get() = journeys.isEmpty()
}

/**
 * 하루 전체의 지하철 요금을 추정한다.
 *
 * **체류는 요금을 끊는다.** 어딘가에 머문다는 것은 개찰구를 나갔다는 뜻이므로 다시
 * 탈 때 기본운임이 새로 붙는다. 반대로 환승은 개찰구를 나가지 않으니 거리만 이어진다.
 * 버스 구간은 노선망에 없으므로 지하철 요금에서 빠진다.
 */
fun Route.estimateFare(network: SubwayNetwork): FareEstimate {
    if (network.stations.isEmpty()) return FareEstimate()

    val journeys = mutableListOf<FareJourney>()
    val skipped = mutableListOf<String>()

    var previousIndex: Int? = network.findStation(origin.ifBlank { "" })
    var startName: String? = previousIndex?.let { network.stations[it].name }
    var km = 0.0
    var lines = mutableListOf<String>()
    var lastName: String? = startName

    fun close() {
        val from = startName
        val to = lastName
        if (from != null && to != null && lines.isNotEmpty() && km > 0.0) {
            val distance = km * Fares.ROUTE_WIGGLE
            journeys += FareJourney(
                from = from,
                to = to,
                lines = lines.distinct(),
                km = distance,
                baseFare = Fares.fareForKm(distance),
                surcharge = lines.distinct().sumOf { Fares.SURCHARGES[normalizeLineName(it)] ?: 0 },
            )
        }
        km = 0.0
        lines = mutableListOf()
        startName = null
    }

    segments.forEach { segment ->
        when (segment) {
            is RouteSegment.Stay -> {
                // 개찰구 밖으로 나갔다 — 다음 승차는 새 요금이다.
                close()
                // 'DDP'처럼 역이 아닌 곳에 머물렀다면 내린 역에서 다시 탄다.
                val at = network.findStation(segment.place) ?: previousIndex
                previousIndex = at
                startName = at?.let { network.stations[it].name }
                lastName = startName
            }

            is RouteSegment.Move -> {
                val to = network.findStation(segment.destination)
                val from = previousIndex
                if (to == null || from == null) {
                    // 버스처럼 노선망 밖의 구간. 요금에서 빼고 여정을 끊는다.
                    close()
                    if (segment.line.isNotBlank()) skipped += segment.line
                    previousIndex = to
                    startName = to?.let { network.stations[it].name }
                    lastName = startName
                    return@forEach
                }
                if (startName == null) startName = network.stations[from].name
                km += legDistanceKm(network, segment.line, from, to)
                lines += segment.line
                previousIndex = to
                lastName = network.stations[to].name
            }
        }
    }
    close()

    return FareEstimate(journeys, skipped.distinct())
}

/** 노선 위 실제 역 순서를 따라 거리를 재고, 특정할 수 없으면 두 역 사이 직선거리를 쓴다. */
private fun legDistanceKm(
    network: SubwayNetwork,
    line: String,
    fromIndex: Int,
    toIndex: Int,
): Double {
    val run = network.stationsBetween(line, fromIndex, toIndex)
        ?: return Fares.distanceKm(network.stations[fromIndex], network.stations[toIndex])
    return run.zipWithNext { a, b -> Fares.distanceKm(network.stations[a], network.stations[b]) }.sum()
}
