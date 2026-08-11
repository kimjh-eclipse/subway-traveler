package com.actimedi.travle.data

import android.util.Log
import android.util.Xml
import com.actimedi.travle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 한 역에 곧 들어올 열차 하나. */
data class Arrival(
    val line: String,
    /** "성수행 - 삼성방면" */
    val headsign: String,
    /** "전역 도착", "3분 후 (역삼)" 같은 안내 문구. */
    val message: String,
    /** 도착까지 남은 초. 0이면 이미 도착했거나 값이 없다. */
    val seconds: Int,
)

sealed interface ArrivalResult {
    data class Ready(val arrivals: List<Arrival>) : ArrivalResult

    /** 조회는 됐지만 들어올 열차가 없다 — 막차 이후이거나 지원하지 않는 역. */
    data object Empty : ArrivalResult

    data class Failed(val reason: String) : ArrivalResult
}

/**
 * 서울시 실시간 지하철 도착정보.
 *
 * 이 API는 HTTPS를 제공하지 않아 평문으로 부른다. 매니페스트의
 * `network_security_config`가 이 호스트만 예외로 열어 둔다 — 공개 교통정보라
 * 가로채여도 새는 정보가 없다.
 *
 * 인증키는 `.env`(git 제외)에서 빌드 시점에 주입된다. 값이 없으면 데모용
 * `sample` 키로 동작하며, 그 키는 서울시 정책상 언제든 막힐 수 있다.
 */
object RealtimeArrivals {

    private const val TAG = "RealtimeArrivals"
    private const val HOST = "http://swopenapi.seoul.go.kr/api/subway"
    private const val TIMEOUT_MS = 6_000

    /**
     * 한 역에서 받아올 최대 편수.
     *
     * 상·하행이 섞여 오므로 적게 받으면 한쪽만 남는다. 왕십리처럼 네 노선이 지나는
     * 역은 방향까지 치면 여덟 편으로는 절반도 못 본다. 호출 수는 그대로다.
     */
    private const val LIMIT = 20

    /**
     * 여러 이름을 물어 하나로 합친다.
     *
     * 올림픽공원처럼 API가 한 역을 노선별로 쪼개 둔 곳이 있다 — 이름 하나만 물으면
     * 5호선이 통째로 빠진다. 곧 들어올 순서로 섞어 놓아야 갈아탈 것을 고를 수 있다.
     */
    suspend fun forStations(names: List<String>): ArrivalResult {
        if (names.size == 1) return forStation(names.first())

        val results = names.map { forStation(it) }
        val arrivals = results.filterIsInstance<ArrivalResult.Ready>().flatMap { it.arrivals }
        if (arrivals.isNotEmpty()) {
            return ArrivalResult.Ready(arrivals.sortedBy { it.seconds })
        }
        // 하나라도 이유를 알면 그것을 전한다. 전부 조용히 비었으면 비었다고 한다.
        return results.filterIsInstance<ArrivalResult.Failed>().firstOrNull() ?: ArrivalResult.Empty
    }

    suspend fun forStation(stationName: String): ArrivalResult = withContext(Dispatchers.IO) {
        // 노선망 이름과 API 이름이 다를 수 있어 괄호를 떼고도 시도한다.
        val candidates = listOfNotNull(
            stationName,
            stationName.substringBefore('(').trim().takeIf { it != stationName },
            stationName.removeSuffix("역").takeIf { it != stationName && it.isNotBlank() },
        ).distinct()

        var lastFailure: ArrivalResult = ArrivalResult.Empty
        candidates.forEach { name ->
            when (val result = fetch(name)) {
                is ArrivalResult.Ready -> return@withContext result
                is ArrivalResult.Failed -> lastFailure = result
                ArrivalResult.Empty -> Unit
            }
        }
        lastFailure
    }

    private fun fetch(stationName: String): ArrivalResult {
        val encoded = URLEncoder.encode(stationName, "UTF-8")
        val url = "$HOST/${BuildConfig.SEOUL_API_KEY}/xml/realtimeStationArrival/0/$LIMIT/$encoded"

        val connection = runCatching { (URL(url).openConnection() as HttpURLConnection) }
            .getOrElse { Log.w(TAG, "연결 실패: $url -> $it"); return ArrivalResult.Failed("연결할 수 없습니다") }

        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${connection.responseCode} for $stationName")
                return ArrivalResult.Failed("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use {
                val body = it.reader().readText()
                val result = parse(body)
                if (result is ArrivalResult.Failed) {
                    // 키 값은 절대 남기지 않는다 — 응답의 앞부분만 본다.
                    Log.w(TAG, "실시간 조회 실패($stationName): ${body.take(200)}")
                }
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "실시간 도착 조회 실패: $stationName -> $e")
            ArrivalResult.Failed("불러오지 못했습니다")
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(xml: String): ArrivalResult {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val arrivals = mutableListOf<Arrival>()
        var row = mutableMapOf<String, String>()
        var field: String? = null
        var resultCode: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    field = parser.name
                    if (parser.name == "row") row = mutableMapOf()
                }

                XmlPullParser.TEXT -> field?.let { row[it] = parser.text.orEmpty().trim() }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "code") resultCode = row["code"]
                    if (parser.name == "row") arrivals += row.toArrival()
                    field = null
                }
            }
        }

        // INFO-000 정상, INFO-200 데이터 없음. 둘 다 오류가 아니다 —
        // ERROR-### 만 실패로 다룬다(키 권한 없음, 한도 초과 등).
        if (arrivals.isEmpty() && resultCode != null && resultCode.startsWith("ERROR")) {
            return ArrivalResult.Failed(resultCode)
        }
        return if (arrivals.isEmpty()) ArrivalResult.Empty else ArrivalResult.Ready(arrivals)
    }

    private fun Map<String, String>.toArrival(): Arrival {
        val seconds = this["barvlDt"]?.toIntOrNull() ?: 0
        // arvlMsg2가 "3분 후 (역삼)"처럼 이미 사람이 읽을 문구다. 비면 위치로 대신한다.
        val message = this["arvlMsg2"].orEmpty().ifBlank { this["arvlMsg3"].orEmpty() }
        return Arrival(
            line = subwayName(this["subwayId"].orEmpty()),
            headsign = this["trainLineNm"].orEmpty(),
            message = message,
            seconds = seconds,
        )
    }

    /** API의 노선 코드를 우리가 쓰는 이름으로. */
    private fun subwayName(id: String): String = when (id) {
        "1001" -> "1호선"; "1002" -> "2호선"; "1003" -> "3호선"; "1004" -> "4호선"
        "1005" -> "5호선"; "1006" -> "6호선"; "1007" -> "7호선"; "1008" -> "8호선"
        "1009" -> "9호선"; "1061" -> "중앙선"; "1063" -> "경의중앙선"; "1065" -> "공항철도"
        "1067" -> "경춘선"; "1075" -> "수인분당선"; "1077" -> "신분당선"; "1092" -> "우이신설선"
        "1093" -> "서해선"; "1081" -> "경강선"; "1032" -> "GTX-A"
        else -> ""
    }
}
