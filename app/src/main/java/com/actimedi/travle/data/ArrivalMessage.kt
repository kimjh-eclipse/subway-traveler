package com.actimedi.travle.data

/**
 * 실시간 도착 API가 주는 한국어 문장을 뜯어낸 것.
 *
 * API는 `성수행 - 삼성방면`, `3분 후 (역삼)`처럼 이미 완성된 한국어를 준다. 앱을
 * 영어로 보고 있어도 그대로 나오므로, 다른 언어로 보여주려면 조각으로 되돌린 뒤
 * 문자열 자원으로 다시 지어야 한다.
 *
 * 역 이름은 여기서 옮기지 않는다 — 한국어 이름 그대로 담아 두고, 화면이 이름표로
 * 바꾼다. 자료를 옮기는 것과 보여주는 것을 섞지 않기 위해서다.
 */
data class ArrivalHeadsign(
    /** 종착역. `성수행` → `성수`. */
    val destination: String = "",
    /** 방면 역. `삼성방면` → `삼성`. 없을 수 있다. */
    val towards: String = "",
    /** 뜯지 못한 원문. 비어 있으면 위 둘로 지어도 된다는 뜻이다. */
    val raw: String = "",
)

/** 열차가 지금 어디쯤인지. */
sealed interface ArrivalStatus {
    /** `3분 후`. */
    data class Minutes(val minutes: Int) : ArrivalStatus

    /** `[2]번째 전역`. */
    data class StopsAway(val stops: Int) : ArrivalStatus

    /** 이 역에 들어오는 중 / 도착 / 떠남. */
    data object Entering : ArrivalStatus
    data object Arrived : ArrivalStatus
    data object Departed : ArrivalStatus

    /** 앞 역에서의 같은 세 가지. */
    data object PreviousEntering : ArrivalStatus
    data object PreviousArrived : ArrivalStatus
    data object PreviousDeparted : ArrivalStatus

    /** 알아보지 못한 문구. 원문을 그대로 보여준다 — 지우는 것보다 낫다. */
    data class Unknown(val text: String) : ArrivalStatus
}

/** [ArrivalStatus]와, 그 일이 벌어진 역. */
data class ArrivalWhen(
    val status: ArrivalStatus,
    /** `3분 후 (역삼)`의 `역삼`. 없을 수 있다. */
    val at: String = "",
)

private val MINUTES = Regex("""(\d+)\s*분\s*후""")
private val STOPS_AWAY = Regex("""\[?(\d+)]?\s*번째\s*전역""")
private val IN_PARENS = Regex("""[(（]([^)）]+)[)）]""")

/**
 * `성수행 - 삼성방면`을 뜯는다.
 *
 * 노선마다 꼬리가 다르다 — 방면이 아예 없기도 하고(`인천공항2터미널행`), 순환선은
 * 종착과 방면이 같기도 하다(`응암순환(상선)행 - 응암순환(상선)방면`). 뜯지 못하면
 * 원문을 그대로 들고 간다.
 */
fun parseHeadsign(text: String): ArrivalHeadsign {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ArrivalHeadsign()

    val parts = trimmed.split(" - ", "-", limit = 2).map { it.trim() }
    val destination = parts.getOrNull(0)?.removeSuffix("행")?.trim().orEmpty()
    val towards = parts.getOrNull(1)?.removeSuffix("방면")?.trim().orEmpty()

    // `~행`이 아니면 우리가 아는 꼴이 아니다. 손대지 않는다.
    if (destination.isEmpty() || destination == parts[0]) return ArrivalHeadsign(raw = trimmed)
    return ArrivalHeadsign(destination = destination, towards = towards)
}

/**
 * `3분 후 (역삼)`, `전역 도착`, `[2]번째 전역 (구룡)`을 뜯는다.
 *
 * 순서가 중요하다. `전역 도착`은 `도착`을 품고 있어 앞의 것부터 봐야 하고, 숫자가
 * 들어간 것이 가장 흔하므로 먼저 본다.
 */
fun parseArrivalMessage(text: String, position: String = ""): ArrivalWhen {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ArrivalWhen(ArrivalStatus.Unknown(""), position)

    // 위치는 `arvlMsg3`을 먼저 쓴다. 괄호를 뜯는 쪽은 역 이름 자체가 괄호를 달고
    // 오면 무너진다 — `7분 후 (천호(풍납토성))`은 `천호(풍납토성`으로 잘린다.
    val at = position.ifBlank { IN_PARENS.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty() }
    val squashed = trimmed.replace(" ", "")

    MINUTES.find(trimmed)?.let {
        return ArrivalWhen(ArrivalStatus.Minutes(it.groupValues[1].toInt()), at)
    }
    STOPS_AWAY.find(trimmed)?.let {
        return ArrivalWhen(ArrivalStatus.StopsAway(it.groupValues[1].toInt()), at)
    }

    val status = when {
        squashed.contains("전역진입") -> ArrivalStatus.PreviousEntering
        squashed.contains("전역도착") -> ArrivalStatus.PreviousArrived
        squashed.contains("전역출발") -> ArrivalStatus.PreviousDeparted
        squashed.endsWith("진입") -> ArrivalStatus.Entering
        squashed.endsWith("도착") -> ArrivalStatus.Arrived
        squashed.endsWith("출발") -> ArrivalStatus.Departed
        else -> ArrivalStatus.Unknown(trimmed)
    }

    // `강남구청 도착`의 앞부분은 역 이름이다. 괄호로 온 것이 없을 때만 쓴다.
    val where = at.ifBlank {
        trimmed.removeSuffix("진입").removeSuffix("도착").removeSuffix("출발")
            .trim()
            .takeIf { it.isNotEmpty() && it != "당역" && it != "전역" }
            .orEmpty()
    }
    return ArrivalWhen(status, if (status is ArrivalStatus.Unknown) position else where)
}
