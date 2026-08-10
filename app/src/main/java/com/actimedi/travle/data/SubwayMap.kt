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
) {
    /** Station name → index. Built once; lookups happen per rendered route. */
    private val byName: Map<String, Int> by lazy {
        stations.withIndex().associate { (i, s) -> s.name to i }
    }

    private val choseong: List<String> by lazy { stations.map { choseongOf(it.name) } }

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
     * Autocomplete. Matches on the name itself or, when the query is nothing but
     * initial consonants (ㄱㄴ → 강남), on the station's 초성.
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
            stations.indices.mapNotNull { i ->
                val name = stations[i].name
                when {
                    name == q -> 0 to i
                    name.startsWith(q) -> 1 to i
                    name.contains(q) -> 2 to i
                    choseong[i].startsWith(choseongOf(q)) -> 3 to i
                    else -> null
                }
            }
        }

        return ranked
            .sortedWith(compareBy({ it.first }, { stations[it.second].name.length }))
            .take(limit)
            .map { stations[it.second] }
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
        context.assets.open(ASSET).bufferedReader().use { json.decodeFromString<SubwayNetwork>(it.readText()) }
    }.onFailure { Log.w("SubwayNetwork", "노선도 데이터를 읽지 못했습니다", it) }
        .getOrDefault(SubwayNetwork())
}
