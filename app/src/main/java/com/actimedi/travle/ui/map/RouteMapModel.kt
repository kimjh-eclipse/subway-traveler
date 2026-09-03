package com.actimedi.travle.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.RouteSegment
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.ui.theme.lineColorFor

/** One ride drawn on the map. */
data class MappedLeg(
    val line: String,
    val stations: List<Int>,
    val color: Color,
    /** True when we could not place the ride on a known line and drew a straight hop. */
    val isStraightHop: Boolean,
)

/**
 * 화면에 그릴 자취.
 *
 * 역과 역을 직선으로 이으면, 도식도가 역이 아닌 자리에서 꺾는 구간에서 굵은
 * 경로선이 그림을 벗어나 엉뚱한 데를 가로지른다. [runs]에 그 구간의 길이 있으면
 * 그것을 따라간다. 없으면 예전처럼 직선이다 — 못 그리는 것보다 나쁜 것은 잘못
 * 그리는 것이라, 안전하게 이을 수 있는 구간만 자산에 들어 있다.
 */
fun MappedLeg.trail(projected: List<Offset?>, runs: Map<Long, List<Offset>>): List<Offset> {
    val out = mutableListOf<Offset>()
    fun add(point: Offset) {
        if (out.lastOrNull() != point) out += point
    }
    if (stations.size == 1) return listOfNotNull(projected.getOrNull(stations[0]))
    stations.zipWithNext().forEach { (from, to) ->
        val a = projected.getOrNull(from)
        val b = projected.getOrNull(to)
        if (a == null || b == null) return@forEach
        val run = runs[runKey(from, to)]
        if (run == null || run.size < 2) {
            add(a)
            add(b)
            return@forEach
        }
        // 자산은 `u < v` 로만 담겨 있다. 반대로 지날 때는 뒤집는다.
        val forward = if (from <= to) run else run.reversed()
        forward.forEach(::add)
    }
    return out
}

/** A station the route actually stops at, in visiting order. */
data class MappedStop(
    val stationIndex: Int,
    val order: Int,
    val label: String,
    val isStay: Boolean,
)

data class MappedRoute(
    val legs: List<MappedLeg> = emptyList(),
    val stops: List<MappedStop> = emptyList(),
    /** Named places on the route that are not rail stations (bus stops, 'DDP', …). */
    val unmatched: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = stops.isEmpty()
}

/**
 * Places a route on the network.
 *
 * Rides follow the real station-by-station run when the line name resolves and
 * both ends sit on the same branch; otherwise the leg is drawn as a straight hop
 * and flagged, so the map never implies a path we did not verify.
 */
fun Route.mapOnto(network: SubwayNetwork): MappedRoute {
    if (network.stations.isEmpty()) return MappedRoute()

    val legs = mutableListOf<MappedLeg>()
    val stops = mutableListOf<MappedStop>()
    val unmatched = mutableListOf<String>()
    var previous: Int? = null

    fun visit(stationIndex: Int, label: String, isStay: Boolean) {
        if (stops.lastOrNull()?.stationIndex != stationIndex) {
            stops += MappedStop(stationIndex, stops.size + 1, label, isStay)
        }
        previous = stationIndex
    }

    segments.forEach { segment ->
        when (segment) {
            is RouteSegment.Stay -> {
                val index = network.findStation(segment.place)
                if (index == null) unmatched += segment.place else visit(index, segment.place, true)
            }

            is RouteSegment.Move -> {
                val index = network.findStation(segment.destination)
                if (index == null) {
                    unmatched += segment.destination
                    return@forEach
                }
                val from = previous
                if (from != null && from != index) {
                    val run = network.stationsBetween(segment.line, from, index)
                    legs += MappedLeg(
                        line = segment.line,
                        stations = run ?: listOf(from, index),
                        color = lineColorFor(segment.line),
                        isStraightHop = run == null,
                    )
                }
                visit(index, segment.destination, false)
            }
        }
    }

    return MappedRoute(legs, stops, unmatched.distinct())
}
