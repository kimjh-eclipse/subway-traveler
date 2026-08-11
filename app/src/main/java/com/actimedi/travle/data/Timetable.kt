package com.actimedi.travle.data

/** 시간표는 요일 종류로 갈린다. */
enum class DayType {
    WEEKDAY,
    SATURDAY,
    HOLIDAY,
    ;

    companion object {
        /** 경로에 적힌 요일 문구로 판정한다. 비어 있으면 평일로 본다. */
        fun of(dayOfWeek: String): DayType = when {
            dayOfWeek.startsWith("토") -> SATURDAY
            dayOfWeek.startsWith("일") -> HOLIDAY
            else -> WEEKDAY
        }
    }
}

/**
 * 한 역에서 한 노선으로 떠나는 열차 시각.
 *
 * 인터페이스로 두는 이유는 두 가지다 — 재계산 로직을 네트워크 없이 시험할 수 있고,
 * 공공데이터포털 시간표 API가 열리기 전에도 나머지를 완성할 수 있다.
 */
interface TimetableSource {
    /**
     * [station]에서 [line]을 타고 [towards] 방향으로 떠나는 시각들. 오름차순.
     * 자료가 없으면 빈 목록 — 그 구간은 원래 계획을 그대로 둔다.
     */
    suspend fun departures(
        station: String,
        line: String,
        towards: String,
        dayType: DayType,
    ): List<ClockTime>
}

/** 자료가 전혀 없는 원본. 시간표를 붙이기 전 기본값이다. */
object EmptyTimetable : TimetableSource {
    override suspend fun departures(
        station: String,
        line: String,
        towards: String,
        dayType: DayType,
    ): List<ClockTime> = emptyList()
}

/** 한 구간을 실제 열차에 맞추며 생긴 변화. */
data class AlignedLeg(
    val stopId: String,
    /** 시간표에서 고른 승차 시각. */
    val boardTime: ClockTime,
    /** 원래 계획보다 더 기다리게 된 시간(분). 0이면 계획과 맞아떨어졌다. */
    val addedWaitMinutes: Int,
)

data class AlignmentResult(
    val draft: RouteDraft,
    val aligned: List<AlignedLeg> = emptyList(),
    /** 시간표를 찾지 못해 계획대로 둔 구간의 노선 이름. */
    val skipped: List<String> = emptyList(),
) {
    val changedCount: Int get() = aligned.count { it.addedWaitMinutes != 0 }
    val isEmpty: Boolean get() = aligned.isEmpty()
}

/**
 * 저장된 계획을 실제 열차 시각에 맞춘다.
 *
 * 기다림은 **앞 정거장의 머무는 시간**으로 흡수한다. 열차를 기다리는 동안 당신은
 * 앞 역에 있는 것이므로, 그 자리에 시간을 더하는 것이 사실에 가깝다. 그래서
 * 사용자가 정한 체류 시간은 **최소값**이 된다 — 30분 머물기로 했는데 그 시점에
 * 열차가 없으면 다음 열차까지 늘어난다. 줄이지는 않는다.
 *
 * 이동 시간 자체는 건드리지 않는다. 그건 [TravelTimes]가 실측으로 아는 값이다.
 */
suspend fun RouteDraft.alignToTimetable(
    network: SubwayNetwork,
    source: TimetableSource,
): AlignmentResult {
    if (stops.size < 2) return AlignmentResult(this)

    val dayType = DayType.of(dayOfWeek)
    val stops = stops.toMutableList()
    val aligned = mutableListOf<AlignedLeg>()
    val skipped = mutableListOf<String>()
    var clock = startTime

    for (index in 1 until stops.size) {
        val previous = stops[index - 1]
        val stop = stops[index]

        // 앞 정거장에서 머물기로 한 시간이 지나야 탈 수 있다.
        val readyAt = ClockTime(clock.minuteOfDay + previous.pauseMinutes.coerceAtLeast(0))
        val timetable = source.departures(previous.name, stop.line, stop.name, dayType)

        val board = timetable.firstOrNull { it >= readyAt }
        if (board == null) {
            if (timetable.isEmpty() && stop.line.isNotBlank()) skipped += stop.line
            clock = readyAt
        } else {
            val added = board - readyAt
            if (added > 0) {
                // 기다리는 시간을 앞 정거장에 얹는다 — 그 시간 동안 거기 있으니까.
                stops[index - 1] = previous.copy(
                    pauseMinutes = previous.pauseMinutes.coerceAtLeast(0) + added,
                )
            }
            aligned += AlignedLeg(stop.id, board, added)
            clock = board
        }

        val travel = stop.travelMinutesOverride
            ?: TravelTimes.estimate(network, stop.line, previous.name, stop.name).minutes
        clock = ClockTime(clock.minuteOfDay + travel)
    }

    return AlignmentResult(copy(stops = stops), aligned, skipped.distinct())
}
