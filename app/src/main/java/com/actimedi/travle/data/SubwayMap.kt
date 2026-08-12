package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The whole Seoul metropolitan rail network, bundled as an asset.
 *
 * Field names are single letters because this ships as a 50 KB asset read on
 * every cold start; see `assets/subway_map.json`.
 */
@Serializable
data class SubwayStation(
    @SerialName("n") val name: String,
    @SerialName("y") val lat: Double,
    @SerialName("x") val lon: Double,
    /** Lines serving this station — drives transfer dots and the editor's line chips. */
    @SerialName("l") val lines: List<String> = emptyList(),
    /**
     * 실시간 도착 API가 부르는 이름들. [name]으로 그냥 통하면 비어 있다.
     *
     * 노선망은 OSM에서, 실시간 도착은 서울시에서 와 이름이 어긋난다 — 그것도 한
     * 방향이 아니다. OSM의 `교대(법원·검찰청)`을 API는 `교대`라 하고, OSM의 `군자`를
     * API는 `군자(능동)`이라 한다. 어긋나면 API는 오류 없이 빈 결과를 주므로
     * 화면에는 막차가 끊긴 것처럼 보인다.
     *
     * 하나로 안 되는 역이 있어 목록이다. 올림픽공원은 API가 노선별로 쪼개 두어
     * `올림픽공원`(9호선)과 `올림픽공원(한국체대)`(5호선)을 다 물어야 한다.
     *
     * `tools/realtime_names.py`가 채운다 — 전수 조사해서 실제로 자료가 오는
     * 이름만 남긴다.
     */
    @SerialName("r") val realtimeNames: List<String> = emptyList(),
)

@Serializable
data class SubwayLine(
    @SerialName("n") val name: String,
    @SerialName("c") val colour: String,
    /** Each path is an ordered run of station indices; branches get their own path. */
    @SerialName("p") val paths: List<List<Int>>,
)

@Serializable
data class SubwayNetwork(
    val source: String = "",
    val stations: List<SubwayStation> = emptyList(),
    val lines: List<SubwayLine> = emptyList(),
    /**
     * 실측 역간 소요시간. `[역A, 역B, 초]`이며 방향은 구분하지 않는다.
     * 서울교통공사 1~8호선만 있어 나머지 노선은 추정으로 메운다.
     */
    val times: List<List<Int>> = emptyList(),
    val timeSource: String = "",
    /**
     * 역 이름의 다른 나라 표기. 자산이 따로라 [SubwayNetworkLoader]가 채운다 —
     * 한국어로 볼 때는 읽을 필요가 없어 노선도 자산과 나눠 두었다.
     */
    @kotlinx.serialization.Transient
    val stationNames: StationNameTable = StationNameTable(),
) {
    /** 화면에 쓸 역 이름. 자료가 없으면 한국어 그대로다. */
    fun displayName(rawName: String, locale: java.util.Locale): String =
        stationNames.localized(rawName, locale)

    /** (작은 인덱스, 큰 인덱스) → 초. 조회가 잦아 한 번만 만든다. */
    private val edgeSeconds: Map<Long, Int> by lazy {
        times.associate { (a, b, seconds) -> edgeKey(a, b) to seconds }
    }

    /** 두 역이 이웃일 때의 실측 소요시간(초). 자료가 없으면 null. */
    fun secondsBetweenAdjacent(a: Int, b: Int): Int? = edgeSeconds[edgeKey(a, b)]

    private fun edgeKey(a: Int, b: Int): Long =
        minOf(a, b).toLong() * 100_000L + maxOf(a, b).toLong()

    /** Station name → index. Built once; lookups happen per rendered route. */
    private val byName: Map<String, Int> by lazy {
        stations.withIndex().associate { (i, s) -> s.name to i }
    }

    private val choseong: List<String> by lazy { stations.map { choseongOf(it.name) } }

    /**
     * 역마다 붙은 다른 나라 표기를, 견주기 좋게 소문자로.
     *
     * 이름표가 없으면 (한국어로 보는 흔한 경우) 통째로 비어 아무 값도 만들지 않는다.
     */
    private val foreignNames: List<List<String>> by lazy {
        if (stationNames.isEmpty) {
            emptyList()
        } else {
            stations.map { station ->
                stationNames.names[station.name]
                    ?.let { listOfNotNull(it.english, it.japanese, it.simplified, it.traditional) }
                    ?.map { it.lowercase() }
                    ?.distinct()
                    .orEmpty()
            }
        }
    }

    /**
     * Resolves a name typed by the user, or stored on a route, to a station.
     *
     * Route data says 미금역 / 강남역 while OSM says 미금 / 강남 — but Seoul Station
     * really is named 서울역, so both directions have to be tried.
     */
    fun findStation(rawName: String): Int? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        byName[name]?.let { return it }
        if (name.endsWith("역") && name.length > 1) byName[name.dropLast(1)]?.let { return it }
        byName["${name}역"]?.let { return it }
        return null
    }

    /**
     * 실시간 도착 API에 물을 때 쓸 이름들. 대개 하나지만 올림픽공원처럼 API가 노선을
     * 쪼개 둔 역은 둘이다. 짝을 못 찾으면 받은 이름을 그대로 돌려준다 — 틀린 이름으로
     * 묻는 것이 아예 묻지 않는 것보다 낫다.
     */
    fun realtimeNamesFor(rawName: String): List<String> {
        val index = findStation(rawName) ?: return listOf(rawName.trim())
        val station = stations[index]
        return station.realtimeNames.ifEmpty { listOf(station.name) }
    }

    /**
     * Autocomplete. Matches on the name itself or, when the query is nothing but
     * initial consonants (ㄱㄴ → 강남), on the station's 초성.
     *
     * 다른 나라 표기로도 찾는다. 영어로 앱을 쓰는 사람에게는 `Gangnam`이 그 역의
     * 이름이지, `강남`을 칠 방법이 없다 — 자판부터 없다.
     */
    fun suggest(query: String, limit: Int = 6): List<SubwayStation> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val ranked = if (isChoseongQuery(q)) {
            stations.indices.mapNotNull { i ->
                val cho = choseong[i]
                when {
                    cho.startsWith(q) -> 0 to i
                    cho.contains(q) -> 1 to i
                    else -> null
                }
            }
        } else {
            val lower = q.lowercase()
            stations.indices.mapNotNull { i ->
                val rank = minOf(rankKorean(i, q), rankForeign(i, lower))
                if (rank == NoMatch) null else rank to i
            }
        }

        return ranked
            .sortedWith(compareBy({ it.first }, { stations[it.second].name.length }))
            .take(limit)
            .map { stations[it.second] }
    }

    private fun rankKorean(index: Int, q: String): Int {
        val name = stations[index].name
        return when {
            name == q -> 0
            name.startsWith(q) -> 1
            name.contains(q) -> 2
            choseong[index].startsWith(choseongOf(q)) -> 3
            else -> NoMatch
        }
    }

    /** 한국어보다 한 칸씩 뒤에 둔다 — 한국어로 정확히 맞은 역이 먼저 보여야 한다. */
    private fun rankForeign(index: Int, lower: String): Int {
        val names = foreignNames.getOrNull(index) ?: return NoMatch
        return when {
            names.any { it == lower } -> 1
            names.any { it.startsWith(lower) } -> 2
            names.any { it.contains(lower) } -> 4
            else -> NoMatch
        }
    }

    /**
     * The real run of stations between two stops on a line, if the line is one we
     * know and both ends sit on the same branch. Null means "draw a straight
     * connector instead" — true for buses and for anything we cannot place.
     */
    fun stationsBetween(lineName: String, fromIndex: Int, toIndex: Int): List<Int>? {
        val line = lines.firstOrNull { it.name == normalizeLineName(lineName) } ?: return null
        for (path in line.paths) {
            val a = path.indexOf(fromIndex)
            val b = path.indexOf(toIndex)
            if (a < 0 || b < 0) continue
            return if (a <= b) path.subList(a, b + 1) else path.subList(b, a + 1).reversed()
        }
        return null
    }

    fun lineColour(lineName: String): String? =
        lines.firstOrNull { it.name == normalizeLineName(lineName) }?.colour

    /**
     * Lines that could carry you from one station to the other without changing —
     * i.e. both stations sit on the same branch. Shortest run first, so the most
     * direct option is the one the editor auto-fills.
     */
    fun linesBetween(fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex == toIndex) return emptyList()
        return lines.mapNotNull { line ->
            val best = line.paths.mapNotNull { path ->
                val a = path.indexOf(fromIndex)
                val b = path.indexOf(toIndex)
                if (a < 0 || b < 0) null else kotlin.math.abs(a - b)
            }.minOrNull()
            best?.let { line.name to it }
        }.sortedBy { it.second }.map { it.first }
    }
}

/** Route line names are typed by hand; nudge the common variants onto asset names. */
fun normalizeLineName(raw: String): String {
    val n = raw.trim().replace(" ", "")
    return when {
        n.startsWith("GTX", ignoreCase = true) -> "GTX-A"
        n.contains("수인분당") || n.contains("수인·분당") -> "수인분당선"
        n.contains("신분당") -> "신분당선"
        n.contains("공항철도") -> "공항철도"
        n.contains("경의중앙") || n.contains("경의·중앙") -> "경의중앙선"
        n.contains("인천1") -> "인천1호선"
        n.contains("인천2") -> "인천2호선"
        n.contains("서해") -> "서해선"
        n.contains("경춘") -> "경춘선"
        n.contains("경강") -> "경강선"
        n.contains("우이신설") -> "우이신설선"
        n.contains("신림") -> "신림선"
        Regex("^\\d호선$").matches(n) -> n
        else -> n
    }
}

private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
private const val HANGUL_BASE = 0xAC00
private const val HANGUL_LAST = 0xD7A3
private const val JAMO_PER_CHOSEONG = 588

/** "강남구청" → "ㄱㄴㄱㅊ". Non-Hangul characters pass through unchanged. */
fun choseongOf(text: String): String = buildString {
    text.forEach { ch ->
        val code = ch.code
        if (code in HANGUL_BASE..HANGUL_LAST) {
            append(CHOSEONG[(code - HANGUL_BASE) / JAMO_PER_CHOSEONG])
        } else {
            append(ch)
        }
    }
}

/** True when the query is only standalone consonants, e.g. "ㄱㄴ". */
fun isChoseongQuery(query: String): Boolean =
    query.isNotEmpty() && query.all { it in CHOSEONG }

/** Reads the bundled network once. */
object SubwayNetworkLoader {
    private const val ASSET = "subway_map.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): SubwayNetwork = runCatching {
        context.assets.open(ASSET).bufferedReader()
            .use { json.decodeFromString<SubwayNetwork>(it.readText()) }
            .copy(stationNames = StationNamesLoader.load(context))
    }.onFailure { Log.w("SubwayNetwork", "노선도 데이터를 읽지 못했습니다", it) }
        .getOrDefault(SubwayNetwork())
}

/** 자동완성에서 '맞지 않음'. 작은 값이 앞이라 어떤 순위보다도 뒤에 있어야 한다. */
private const val NoMatch = Int.MAX_VALUE
