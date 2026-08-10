package com.actimedi.travle.ui.map

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
