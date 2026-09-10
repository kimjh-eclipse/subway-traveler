package com.actimedi.travle.data

/**
 * 저장하는 요일. **언어와 무관한 값**이다.
 *
 * 화면에 보이는 이름(`토요일`·`Saturday`·`土曜日`)은 로케일마다 다르므로 저장하면
 * 안 된다 — 저장된 경로를 다른 언어로 열면 읽을 수 없고, 요일 판정도 깨진다.
 */
object DayOfWeekToken {
    const val MONDAY = "MON"
    const val SATURDAY = "SAT"
    const val SUNDAY = "SUN"

    /** 월요일부터. `R.array.days_of_week`와 같은 차례다. */
    val ALL = listOf(MONDAY, "TUE", "WED", "THU", "FRI", SATURDAY, SUNDAY)

    // 옛 경로가 들고 있는 문구들. 지우지 못한다 — 이미 저장된 자료다.
    //
    // 언어마다 요일이 어디에 붙는지가 다르다. 한국어·일본어는 **첫 글자**
    // (`토요일`, `土曜日`), 영어는 앞 세 글자(`Saturday`), 중국어는 **마지막 글자**
    // (`星期六`)다. 순서를 잘못 잡아 `토요일`의 끝 `일`을 먼저 보고 일요일로 읽은
    // 적이 있다 — 한국어 요일은 모두 `일`로 끝난다.
    private val FIRST = mapOf(
        "월" to MONDAY, "화" to "TUE", "수" to "WED", "목" to "THU",
        "금" to "FRI", "토" to SATURDAY, "일" to SUNDAY,
        "月" to MONDAY, "火" to "TUE", "水" to "WED", "木" to "THU",
        "金" to "FRI", "土" to SATURDAY, "日" to SUNDAY,
    )
    private val ENGLISH = mapOf(
        "mon" to MONDAY, "tue" to "TUE", "wed" to "WED", "thu" to "THU",
        "fri" to "FRI", "sat" to SATURDAY, "sun" to SUNDAY,
    )
    private val CHINESE = mapOf(
        "一" to MONDAY, "二" to "TUE", "三" to "WED", "四" to "THU",
        "五" to "FRI", "六" to SATURDAY, "日" to SUNDAY, "天" to SUNDAY,
    )

    /** 무엇이 들어오든 [ALL]의 값 하나로. 못 알아보면 빈 문자열. */
    fun canonical(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        if (value in ALL) return value
        FIRST[value.take(1)]?.let { return it }
        ENGLISH[value.take(3).lowercase()]?.let { return it }
        CHINESE[value.takeLast(1)]?.let { return it }
        return ""
    }
}

/** 시간표는 요일 종류로 갈린다. */
enum class DayType {
    WEEKDAY,
    SATURDAY,
    HOLIDAY,
    ;

    companion object {
        /**
         * 경로에 적힌 요일로 판정한다. 비어 있으면 평일로 본다.
         *
         * 예전에는 **화면에 보이던 문구를 그대로** 저장했다. 그래서 영어로 쓰는
         * 사람이 토요일을 고르면 `Saturday`가 저장되고, 여기서는 `토`로만 가려
         * **평일 시간표**를 썼다 — 막차 확인까지 함께 틀어졌다. 일본어·중국어도
         * 마찬가지였다. 이제는 [DayOfWeekToken]으로 저장하고, 옛 경로를 위해
         * 다섯 언어의 문구도 함께 알아본다.
         */
        fun of(dayOfWeek: String): DayType = when (DayOfWeekToken.canonical(dayOfWeek)) {
            DayOfWeekToken.SATURDAY -> SATURDAY
            DayOfWeekToken.SUNDAY -> HOLIDAY
            else -> WEEKDAY
        }

        /**
         * 오늘. 경로에 딸리지 않은 화면 — 노선도에서 역 하나를 들여다볼 때 —
         * 에서 쓴다.
         *
         * 공휴일은 가리지 못한다. 달력을 들고 있지 않아서다. 평일 공휴일에는
         * 실제와 다른 시간표를 보여 주게 된다.
         */
        fun today(): DayType = when (java.time.LocalDate.now().dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> SATURDAY
            java.time.DayOfWeek.SUNDAY -> HOLIDAY
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
