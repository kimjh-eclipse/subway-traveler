package com.actimedi.travle.data

import java.util.UUID

/** Whether a stop is somewhere you linger or just change trains. */
enum class StopKind { STAY, TRANSFER }

/**
 * One stop while editing.
 *
 * Clock times are never typed. The route starts at [RouteDraft.startTime] and
 * every arrival after that is derived from the leg before it, so the only things
 * a stop carries are how long you pause there and — when the estimate is wrong —
 * how long the ride actually took.
 */
data class RouteStop(
    val id: String = UUID.randomUUID().toString(),
    /** Station or place name. */
    val name: String = "",
    /** The line taken to get here. Unused on the first stop; auto-filled. */
    val line: String = "",
    val kind: StopKind = StopKind.STAY,
    /**
     * 체류 → how long you stay. 환승 → how long you wait for the connection.
     * On the first stop it is how long you linger before setting off.
     *
     * Defaults to zero so that a bare [RouteStop] never invents time; the editor
     * sets a sensible stay when it appends one (see [RouteDraft.nextStopDefault]).
     */
    val pauseMinutes: Int = 0,
    /** Set only when the user corrects the estimated ride time. */
    val travelMinutesOverride: Int? = null,
    val memo: String = "",
    /** Set once the user picks a line themselves, so auto-fill stops touching it. */
    val lineIsManual: Boolean = false,
)

data class RouteDraft(
    val title: String = "",
    val dayOfWeek: String = "",
    /** When you leave the first stop. The one clock time in the whole editor. */
    val startTime: ClockTime = ClockTime.of(9, 0),
    // The origin has no pause control; it only carries one when a route made
    // elsewhere (the seed) lingers there, and round-tripping must not lose it.
    val stops: List<RouteStop> = listOf(RouteStop()),
) {
    fun nextStopDefault() = RouteStop(pauseMinutes = TravelTimes.DEFAULT_STAY_MINUTES)

    val origin: String get() = stops.firstOrNull()?.name.orEmpty()
}

val DaysOfWeek = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

/** A stop with its computed clock times. */
data class ScheduledStop(
    val stop: RouteStop,
    /** Minutes riding to get here. Zero for the first stop. */
    val travelMinutes: Int,
    val travelBasis: EstimateBasis?,
    val arrival: ClockTime,
    val departure: ClockTime,
) {
    val travelIsEstimated: Boolean get() = stop.travelMinutesOverride == null && travelBasis != null
}

/**
 * Walks the route forward from [RouteDraft.startTime], estimating each ride.
 *
 * The final stop gets no pause — you have arrived, so a trailing wait would
 * invent time that is not part of the day.
 */
fun RouteDraft.schedule(network: SubwayNetwork): List<ScheduledStop> {
    val out = mutableListOf<ScheduledStop>()
    var clock = startTime

    stops.forEachIndexed { index, stop ->
        if (index == 0) {
            val departure = ClockTime(startTime.minuteOfDay + stop.pauseMinutes.coerceAtLeast(0))
            out += ScheduledStop(stop, 0, null, startTime, departure)
            clock = departure
            return@forEachIndexed
        }

        val estimate = TravelTimes.estimate(network, stop.line, stops[index - 1].name, stop.name)
        val travel = stop.travelMinutesOverride ?: estimate.minutes
        val arrival = ClockTime(clock.minuteOfDay + travel)
        val pause = if (index == stops.lastIndex) 0 else stop.pauseMinutes.coerceAtLeast(0)
        val departure = ClockTime(arrival.minuteOfDay + pause)

        out += ScheduledStop(stop, travel, estimate.basis, arrival, departure)
        clock = departure
    }
    return out
}

/**
 * Expands the schedule into segments.
 *
 * A ride runs from the previous stop's departure to this stop's arrival, so a
 * TRANSFER stop leaves a gap between two consecutive rides — exactly what
 * [toTimeline] reads back as 환승 대기. A STAY stop fills that gap with a card.
 */
fun RouteDraft.toSegments(network: SubwayNetwork): List<RouteSegment> {
    val scheduled = schedule(network)
    val out = mutableListOf<RouteSegment>()

    scheduled.forEachIndexed { index, item ->
        if (index > 0) {
            out += RouteSegment.Move(
                line = item.stop.line.trim(),
                destination = item.stop.name.trim(),
                start = scheduled[index - 1].departure,
                end = item.arrival,
                minutes = item.travelMinutes,
            )
        }
        val pause = item.departure - item.arrival
        if (item.stop.kind == StopKind.STAY && pause > 0) {
            out += RouteSegment.Stay(
                place = item.stop.name.trim(),
                label = item.stop.memo.trim(),
                start = item.arrival,
                end = item.departure,
                minutes = pause,
            )
        }
    }
    return out
}

/** Problems that block saving: a banner list plus per-stop messages for inline display. */
data class DraftValidation(
    val messages: List<String> = emptyList(),
    val stopErrors: Map<String, String> = emptyMap(),
) {
    val isValid: Boolean get() = messages.isEmpty() && stopErrors.isEmpty()
}

fun RouteDraft.validate(network: SubwayNetwork = SubwayNetwork()): DraftValidation {
    val messages = mutableListOf<String>()
    val stopErrors = mutableMapOf<String, String>()

    if (title.isBlank()) messages += "경로 이름을 입력하세요."
    if (stops.size < 2) messages += "경유지를 1곳 이상 추가하세요."

    stops.forEachIndexed { index, stop ->
        val problem = when {
            stop.name.isBlank() -> "역·장소 이름을 입력하세요."
            index > 0 && stop.line.isBlank() -> "여기까지 타고 온 노선을 입력하세요."
            else -> null
        }
        if (problem != null) stopErrors[stop.id] = problem
    }

    if (stops.size >= 2 && stopErrors.isEmpty()) {
        val end = schedule(network).last().departure
        if (end.minuteOfDay >= 24 * 60) messages += "일정이 자정을 넘습니다. 시간을 줄여 보세요."
    }

    return DraftValidation(messages, stopErrors)
}

/** Builds a saveable route. Call only when [validate] passes. */
fun RouteDraft.toRoute(
    network: SubwayNetwork,
    now: Long,
    id: String = UUID.randomUUID().toString(),
): Route = Route(
    id = id,
    title = title.trim(),
    dayOfWeek = dayOfWeek.trim(),
    createdAt = now,
    segments = toSegments(network),
    origin = origin.trim(),
)

/**
 * Turns a saved route back into something the editor can work on.
 *
 * Ride times come back as overrides rather than estimates: the saved route is
 * what the user decided, so reopening it must not silently re-time the day. The
 * `예상값으로` button puts any leg back under the estimate.
 */
fun Route.toDraft(): RouteDraft {
    val stops = mutableListOf<RouteStop>()
    val leadingStay = segments.firstOrNull() as? RouteSegment.Stay

    stops += RouteStop(
        name = origin.ifBlank { leadingStay?.place.orEmpty() },
        pauseMinutes = leadingStay?.minutes ?: 0,
        memo = leadingStay?.label.orEmpty(),
    )

    segments.forEachIndexed { index, segment ->
        if (segment !is RouteSegment.Move) return@forEachIndexed
        val following = segments.getOrNull(index + 1)
        val stay = following as? RouteSegment.Stay
        val nextMove = segments.drop(index + 1).filterIsInstance<RouteSegment.Move>().firstOrNull()

        stops += RouteStop(
            name = segment.destination,
            line = segment.line,
            lineIsManual = true,
            travelMinutesOverride = segment.minutes,
            kind = if (stay != null) StopKind.STAY else StopKind.TRANSFER,
            pauseMinutes = stay?.minutes
                ?: nextMove?.let { (it.start - segment.end).coerceAtLeast(0) }
                ?: 0,
            memo = stay?.label.orEmpty(),
        )
    }

    return RouteDraft(
        title = title,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        stops = stops,
    )
}

/**
 * Fills in the line for each leg from the stations either side of it.
 *
 * Only blanks and previously auto-filled values are touched — once the user
 * picks a line, it stays picked.
 */
fun RouteDraft.withAutoLines(network: SubwayNetwork): RouteDraft {
    if (network.stations.isEmpty()) return this
    return copy(
        stops = stops.mapIndexed { index, stop ->
            if (index == 0 || stop.lineIsManual) return@mapIndexed stop
            val from = network.findStation(stops[index - 1].name)
            val to = network.findStation(stop.name)
            val candidate = if (from != null && to != null) {
                network.linesBetween(from, to).firstOrNull()
            } else {
                null
            }
            if (candidate == null) stop else stop.copy(line = candidate)
        },
    )
}
