package com.actimedi.travle.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Minutes since midnight. Kept as a plain Int so the whole route is trivially comparable. */
@Serializable
@JvmInline
value class ClockTime(val minuteOfDay: Int) : Comparable<ClockTime> {
    override fun compareTo(other: ClockTime): Int = minuteOfDay.compareTo(other.minuteOfDay)

    /** "07:08" */
    fun format(): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    operator fun minus(other: ClockTime): Int = minuteOfDay - other.minuteOfDay

    companion object {
        val Zero = ClockTime(0)

        /** Parses "HH:mm". */
        fun parse(text: String): ClockTime {
            val (h, m) = text.split(':')
            return ClockTime(h.toInt() * 60 + m.toInt())
        }

        fun of(hour: Int, minute: Int) = ClockTime(hour * 60 + minute)
    }
}

/**
 * One leg of the day: either riding something, or being somewhere.
 *
 * Line colours are not stored — they are derived from the line name at render
 * time by [com.actimedi.travle.ui.theme.lineColorFor], so a route saved by the
 * editor only ever has to carry text.
 */
@Serializable
sealed interface RouteSegment {
    val start: ClockTime
    val end: ClockTime

    /** Scheduled length in minutes, as planned (may differ from `end - start`). */
    val minutes: Int

    /** A ride on a numbered line. */
    @Serializable
    @SerialName("move")
    data class Move(
        val line: String,
        val destination: String,
        override val start: ClockTime,
        override val end: ClockTime,
        override val minutes: Int,
    ) : RouteSegment

    /** Time spent at a place. */
    @Serializable
    @SerialName("stay")
    data class Stay(
        val place: String,
        val label: String,
        override val start: ClockTime,
        override val end: ClockTime,
        override val minutes: Int,
    ) : RouteSegment
}

/** A whole day's plan. */
@Serializable
data class Route(
    val id: String,
    val title: String,
    val dayOfWeek: String,
    /** Epoch millis, used only to order the history list. */
    val createdAt: Long,
    val segments: List<RouteSegment>,
    /**
     * Where the day starts. The first stop produces no segment of its own when
     * you leave straight away, so without this the origin would be lost.
     */
    val origin: String = "",
) {
    val startTime: ClockTime get() = segments.first().start
    val endTime: ClockTime get() = segments.last().end
}

/** Which slice of the route the list is showing. */
enum class RouteFilter { ALL, MOVE, STAY }

/** Totals shown in the header chips and the closing card. */
data class RouteSummary(
    val totalMinutes: Int,
    val movingMinutes: Int,
    val stayMinutes: Int,
    val transferCount: Int,
    val legCount: Int,
    val stayCount: Int,
    val finishTime: ClockTime,
    val finishPlace: String,
)

/**
 * Computes the header/footer totals.
 *
 * `movingMinutes` deliberately includes transfer waiting — the header chip is
 * labelled 이동·환승, so idle minutes between two consecutive rides belong to it
 * rather than disappearing from the day.
 */
fun Route.summarize(): RouteSummary {
    val totalMinutes = endTime - startTime
    val stayMinutes = segments.filterIsInstance<RouteSegment.Stay>().sumOf { it.minutes }
    val rideMinutes = segments.filterIsInstance<RouteSegment.Move>().sumOf { it.minutes }
    val waitMinutes = (totalMinutes - stayMinutes - rideMinutes).coerceAtLeast(0)

    val transferCount = segments.filterIndexed { index, segment ->
        segment is RouteSegment.Move && segments.getOrNull(index - 1) is RouteSegment.Move
    }.size

    val last = segments.last()
    val finishPlace = when (last) {
        is RouteSegment.Move -> last.destination
        is RouteSegment.Stay -> last.place
    }

    return RouteSummary(
        totalMinutes = totalMinutes,
        movingMinutes = rideMinutes + waitMinutes,
        stayMinutes = stayMinutes,
        transferCount = transferCount,
        legCount = segments.count { it is RouteSegment.Move },
        stayCount = segments.count { it is RouteSegment.Stay },
        finishTime = endTime,
        finishPlace = finishPlace,
    )
}

/** A segment plus everything the row needs that depends on its neighbours. */
data class TimelineEntry(
    val index: Int,
    val segment: RouteSegment,
    /** Minutes idled before this ride because the previous segment was also a ride. */
    val transferWaitMinutes: Int,
    /**
     * 갈아타는 역. 앞 이동의 도착지다 — 이 구간의 목적지가 아니다.
     *
     * 기다리는 시간이 0분이어도 채운다. 갈아타는 자리라는 사실은 기다림과 무관하고,
     * 여행 중에는 그 자리에서 다음 열차를 봐야 하기 때문이다. 갈아타지 않으면 null.
     */
    val transferStation: String? = null,
    /**
     * 타고 온 노선과 그 열차를 탄 곳. 환승 안내가 방향별이라 둘 다 있어야 한다 —
     * 같은 역이라도 어느 쪽에서 왔느냐에 따라 내릴 칸이 갈린다.
     */
    val arrivedOnLine: String? = null,
    val arrivedFrom: String? = null,
    /** Elapsed time from the start of the day to the end of this segment. */
    val cumulativeMinutes: Int,
)

/** Expands a route into rows, resolving transfer waits and running totals. */
fun Route.toTimeline(): List<TimelineEntry> {
    // 각 구간을 떠난 자리를 따라간다. 갈아탈 때 '어디서 왔는가'가 방향이 된다.
    val leftFrom = mutableListOf<String>()
    var place = origin
    segments.forEach { segment ->
        leftFrom += place
        place = when (segment) {
            is RouteSegment.Move -> segment.destination
            is RouteSegment.Stay -> segment.place
        }
    }

    return segments.mapIndexed { index, segment ->
    val previous = segments.getOrNull(index - 1)
    val isTransfer = previous is RouteSegment.Move && segment is RouteSegment.Move
    val wait = if (isTransfer) {
        ((segment as RouteSegment.Move).start - (previous as RouteSegment.Move).end).coerceAtLeast(0)
    } else {
        0
    }
    TimelineEntry(
        index = index,
        segment = segment,
        transferWaitMinutes = wait,
        transferStation = if (isTransfer) (previous as RouteSegment.Move).destination else null,
        arrivedOnLine = (previous as? RouteSegment.Move)?.line,
        arrivedFrom = leftFrom.getOrNull(index - 1),
        cumulativeMinutes = segment.end - startTime,
    )
    }
}

/** 이 갈래로 걸러도 볼 것이 남는가. 빈 갈래는 탭에서 잠근다. */
fun RouteFilter.isNotEmpty(timeline: List<TimelineEntry>): Boolean =
    timeline.filterBy(this).isNotEmpty()

fun List<TimelineEntry>.filterBy(filter: RouteFilter): List<TimelineEntry> = when (filter) {
    RouteFilter.ALL -> this
    RouteFilter.MOVE -> filter { it.segment is RouteSegment.Move }
    RouteFilter.STAY -> filter { it.segment is RouteSegment.Stay }
}

/** "13:28" — used for the header chips, where a fixed width reads better. */
fun formatClockSpan(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
