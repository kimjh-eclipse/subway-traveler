package com.actimedi.travle.data

/**
 * 한 정거장에서 다음 정거장으로 떠나는 일에 대해, 막차가 언제이고 계획을 끝까지
 * 마치려면 언제까지 떠나야 하는지.
 *
 * 두 값을 따로 두는 이유가 있다. [lastTrain]은 **이 구간만** 보는 막차라
 * "지금 여기서 못 간다"를 알려주고, [latestBoard]는 **뒤 일정까지** 감안한 마감이라
 * "여기까지는 괜찮다"를 알려준다. 하나로 합치면 실패 지점을 엉뚱한 곳으로 짚는다 —
 * 앞 역에는 탈 열차가 멀쩡히 있는데 거기가 문제라고 말하게 된다.
 */
data class Deadline(
    val stopId: String,
    /** 떠나는 곳. */
    val station: String,
    /** 갈아타고 향하는 곳. */
    val towards: String,
    /** 계획대로라면 여기를 떠나는 시각. */
    val plannedDeparture: ClockTime,
    /** 이 구간의 막차. 시간표를 못 찾았으면 null. */
    val lastTrain: ClockTime? = null,
    /** 뒤 일정까지 마치려면 늦어도 이 열차는 타야 한다. 뒤가 막혀 불가능하면 null. */
    val latestBoard: ClockTime? = null,
) {
    /** 계획보다 몇 분 늦게까지 버틸 수 있는가. 자료가 없으면 null. */
    val slackMinutes: Int? get() = latestBoard?.let { it - plannedDeparture }

    /** 계획한 시각에는 이미 막차가 지났다 — 여기서 발이 묶인다. */
    val missesLastTrain: Boolean get() = lastTrain != null && plannedDeparture > lastTrain
}

data class LastTrainCheck(
    val deadlines: List<Deadline> = emptyList(),
    /** 시간표를 찾지 못해 따져보지 못한 노선. */
    val skipped: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = deadlines.isEmpty()

    /**
     * 계획이 무너지는 첫 지점. 막차가 이미 지난 곳을 먼저 보고, 그런 곳이 없으면
     * 뒤와 시각이 맞지 않아 이을 수 없는 곳을 본다.
     */
    val broken: Deadline?
        get() = deadlines.firstOrNull { it.missesLastTrain }
            ?: deadlines.firstOrNull { it.lastTrain != null && it.latestBoard == null }

    /**
     * 가장 아슬아슬한 지점. 여기만 지키면 나머지는 따라온다 — 각 지점의 여유는
     * 뒤 일정을 이미 감안한 값이기 때문이다.
     */
    val tightest: Deadline?
        get() = deadlines.filter { it.slackMinutes != null }.minByOrNull { it.slackMinutes!! }
}

/**
 * 이 계획대로 다니면 막차를 놓치는지 따져본다.
 *
 * 길찾기 앱들은 "지금 A에서 B로"를 답한다. 하루치 계획을 통째로 들고 있는 것은
 * 이쪽뿐이라, "이 일정 전체가 막차 안에 들어오는가"는 여기서만 답할 수 있다.
 *
 * **뒤에서부터 거슬러 올라간다.** 마지막 정거장에는 그 뒤가 없어 제약이 없고,
 * 거기서 한 칸씩 앞으로 오면서 "여기서 몇 시 열차까지는 타야 뒤가 다 되는가"를
 * 구한다. 앞에서부터 세면 뒤가 막힌 것을 알 수 없다.
 *
 * 체류 시간은 지켜야 할 약속으로 본다 — 30분 머물기로 했으면 그 30분을 빼고
 * 셈한다. 그래야 "서두르면 된다"가 아니라 "여기까지는 괜찮다"가 나온다.
 */
suspend fun RouteDraft.checkLastTrain(
    network: SubwayNetwork,
    source: TimetableSource,
): LastTrainCheck {
    if (stops.size < 2) return LastTrainCheck()

    val dayType = DayType.of(dayOfWeek)
    val planned = schedule(network)
    val found = arrayOfNulls<Deadline>(stops.size)
    val skipped = mutableListOf<String>()

    // 마지막 정거장에는 뒤가 없다 — 언제 떠나든 상관없다.
    var limit: ClockTime? = null

    for (index in stops.lastIndex downTo 1) {
        val from = stops[index - 1]
        val to = stops[index]
        val travel = planned[index].travelMinutes
        val timetable = source.departures(from.name, to.line, to.name, dayType)

        if (timetable.isEmpty()) {
            if (to.line.isNotBlank()) skipped += to.line
            // 모르는 구간은 계획을 그대로 믿는다. 여기서 끊으면 앞쪽까지 못 따진다.
            limit = planned[index - 1].departure
            continue
        }

        // 다음 정거장에 늦어도 이때까지는 닿아야 하고, 그러려면 이 시각 전에 떠나야 한다.
        val ceiling = limit
            ?.let { ClockTime(it.minuteOfDay - to.pauseMinutes.coerceAtLeast(0)) }
            ?.let { ClockTime(it.minuteOfDay - travel) }
        val latest = if (ceiling == null) timetable.last() else timetable.lastOrNull { it <= ceiling }

        found[index - 1] = Deadline(
            stopId = from.id,
            station = from.name,
            towards = to.name,
            plannedDeparture = planned[index - 1].departure,
            lastTrain = timetable.last(),
            latestBoard = latest,
        )
        // 뒤가 막혔으면 앞쪽 마감은 셈해도 뜻이 없다. 계획대로 둔 채 계속 훑어
        // 어디까지 이어지는지는 보여준다.
        limit = latest ?: planned[index - 1].departure
    }

    return LastTrainCheck(found.filterNotNull(), skipped.distinct())
}
