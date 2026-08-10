package com.actimedi.travle.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** How a travel time was arrived at — surfaced so the UI never passes a guess off as fact. */
enum class EstimateBasis {
    /** 서울교통공사 실측 역간 시간을 그대로 더했다. 가장 믿을 만하다. */
    MEASURED,

    /** 실측이 없는 구간이 섞여 평균 역당 시간으로 메웠다. */
    STATIONS,

    /** Straight-line distance at an average speed — used for buses and odd hops. */
    DISTANCE,

    /** Neither end could be placed; a flat placeholder. */
    FALLBACK,
}

data class TravelEstimate(val minutes: Int, val basis: EstimateBasis)

/**
 * Estimates how long a leg takes.
 *
 * There is no timetable in the bundled data — OpenStreetMap carries geometry, not
 * departure boards — so every number here is a model, not a measurement:
 *
 *  - ~[MINUTES_PER_HOP] minutes per station, which is the Seoul metro average
 *    including dwell time;
 *  - otherwise straight-line distance at [AVERAGE_SPEED_KMH], inflated by
 *    [ROUTE_WIGGLE] because track is never straight.
 *
 * The user can override any leg, and the UI marks estimated values.
 */
object TravelTimes {

    /**
     * 실측이 없는 구간에 쓰는 역당 시간(초).
     * 서울교통공사 1~8호선 264개 구간의 평균이 1분 32초였다.
     */
    const val SECONDS_PER_HOP = 92
    const val MINUTES_PER_HOP = 2
    const val DEFAULT_TRANSFER_WAIT = 4
    const val DEFAULT_STAY_MINUTES = 30
    const val FALLBACK_MINUTES = 15

    private const val AVERAGE_SPEED_KMH = 32.0
    private const val ROUTE_WIGGLE = 1.25
    private const val EARTH_RADIUS_KM = 6371.0

    fun estimate(
        network: SubwayNetwork,
        line: String,
        fromName: String,
        toName: String,
    ): TravelEstimate {
        val from = network.findStation(fromName)
        val to = network.findStation(toName)
        if (from == null || to == null) return TravelEstimate(FALLBACK_MINUTES, EstimateBasis.FALLBACK)
        if (from == to) return TravelEstimate(0, EstimateBasis.MEASURED)

        network.stationsBetween(line, from, to)?.let { run ->
            var seconds = 0
            var measured = 0
            val hops = (run.size - 1).coerceAtLeast(1)
            run.zipWithNext { a, b ->
                val known = network.secondsBetweenAdjacent(a, b)
                if (known != null) measured++
                seconds += known ?: SECONDS_PER_HOP
            }
            val minutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
            val basis = if (measured == hops) EstimateBasis.MEASURED else EstimateBasis.STATIONS
            return TravelEstimate(minutes, basis)
        }

        val a = network.stations[from]
        val b = network.stations[to]
        val km = distanceKm(a.lat, a.lon, b.lat, b.lon) * ROUTE_WIGGLE
        val minutes = (km / AVERAGE_SPEED_KMH * 60.0).roundToInt().coerceAtLeast(1)
        return TravelEstimate(minutes, EstimateBasis.DISTANCE)
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
