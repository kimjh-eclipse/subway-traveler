package com.actimedi.travle.data

import android.util.Log
import com.actimedi.travle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 서울교통공사 열차시간표 (공공데이터포털).
 *
 * 필수 파라미터가 넷이다 — `tmprTmtblYn`, `upbdnbSe`, `wkndSe`, `lineNm`.
 * 하나라도 빠지면 조용히 CLIENT_ERROR(97)를 낸다.
 *
 * 방향 판정이 이 클래스에서 제일 까다롭다. 상행/하행이라는 이름만으로는 어느 쪽이
 * 내가 가려는 쪽인지 알 수 없고, 2호선은 아예 내선/외선을 쓴다. 종착역으로 짐작해
 * 볼 수도 없다 — 2호선은 내선도 외선도 종착이 성수다.
 *
 * 그래서 **열차번호로 맞춘다**: 같은 번호의 열차가 출발역과 도착역 양쪽 시간표에
 * 모두 있고 도착역 시각이 더 늦으면, 그 방향이 내가 탈 방향이다. 순환선도 분기도
 * 이 규칙 하나로 풀린다. 덤으로 중간에 회차하는 열차가 저절로 걸러진다 —
 * 구로행 1호선은 인천까지 데려다주지 않는다.
 *
 * 한 가지 함정이 더 있다. 이 API는 **지난 개정판까지 전부** 돌려준다. 1호선 서울역
 * 상행이 1,158행인데 실제 열차는 241편이고, 나머지는 2025년의 옛 시간표다. 그대로
 * 쓰면 같은 열차번호가 여섯 번 나와 방향 판정까지 틀린다. `vldBgngDt`~`vldEndDt`로
 * 오늘 유효한 판만 남긴다.
 */
class SeoulTimetable(
    private val apiKey: String = BuildConfig.SEOUL_TIMETABLE_KEY,
) : TimetableSource {

    /**
     * 디스크 저장소. 앱이 시작할 때 한 번 붙인다.
     *
     * 붙지 않아도 동작한다 — 그때는 이번 실행 동안만 기억한다. 시험에서 파일을
     * 만들지 않아도 되도록 이렇게 뒀다.
     */
    var store: TimetableStore? = null
        set(value) {
            field = value
            value?.load()?.let { saved ->
                answers.putAll(saved.entries)
                savedOn = saved.savedOn
            }
        }

    /**
     * 물어본 것의 **답**을 그대로 남긴다 — `역|노선|목적지|요일` → 출발 시각들.
     *
     * 열차번호 단위로 남기지 않는 이유는, 방향을 가리는 데만 쓰이고 그 판정은 이미
     * 끝났기 때문이다. 답만 남기면 되고, 그래야 망 없이도 같은 답을 낼 수 있다.
     */
    private val answers = ConcurrentHashMap<String, List<Int>>()
    private var savedOn: String = ""

    /**
     * 한 번 부른 (역·노선·방향·요일)은 다시 부르지 않는다. 한 경로에서 역이 겹친다.
     * [prefetch]가 여러 갈래로 채우므로 동시 접근을 견디는 지도를 쓴다. 같은 칸을
     * 두 갈래가 동시에 채워도 결과가 같아 잠금까지는 걸지 않는다.
     */
    private val cache = ConcurrentHashMap<String, List<Train>>()

    private data class Train(val no: String, val at: ClockTime)

    /**
     * 경로에 나오는 역들의 시간표를 미리, 동시에 받아 둔다.
     *
     * [alignToTimetable]은 앞 구간의 결과가 있어야 다음을 셀 수 있어 순서대로 돌 수밖에
     * 없다. 조회까지 그 순서를 따르면 열댓 구간에 1분 넘게 걸린다 — 버튼 하나 누르고
     * 기다리기엔 길다. 조회는 서로 독립이니 미리 병렬로 채워 두면 그만큼 줄어든다.
     */
    suspend fun prefetch(draft: RouteDraft, dayType: DayType): Unit = coroutineScope {
        // 지난번에 받아 둔 답이 아직 성하면 망을 아예 타지 않는다 — 비행기 모드에서도
        // 여기서 곧장 돌아선다.
        val today = NOW_FORMAT.format(Date()).take(10)
        if (!TimetableCache(savedOn).isStale(today) && draft.isAnswered(dayType)) return@coroutineScope

        val gate = Semaphore(MAX_IN_FLIGHT)
        draft.stops.zipWithNext()
            .flatMap { (from, to) ->
                val line = normalizeLineName(to.line)
                if (line.isBlank() || from.name.isBlank() || to.name.isBlank()) return@flatMap emptyList()
                val directions = if (line in LOOP_LINES) LOOP_DIRECTIONS else LINEAR_DIRECTIONS
                listOf(from.name, to.name).flatMap { station ->
                    directions.map { Triple(station, line, it) }
                }
            }
            .distinct()
            .map { (station, line, direction) ->
                async(Dispatchers.IO) {
                    gate.withPermit { fetch(station, line, direction, dayType) }
                }
            }
            .awaitAll()
    }

    /** 이 경로의 모든 구간에 답이 남아 있는가. */
    private fun RouteDraft.isAnswered(dayType: DayType): Boolean =
        stops.zipWithNext().all { (from, to) ->
            val line = normalizeLineName(to.line)
            line.isBlank() || answers.containsKey("${from.name}|$line|${to.name}|$dayType")
        }

    override suspend fun departures(
        station: String,
        line: String,
        towards: String,
        dayType: DayType,
    ): List<ClockTime> = withContext(Dispatchers.IO) {
        if (station.isBlank() || towards.isBlank()) return@withContext emptyList()
        val lineName = normalizeLineName(line).ifBlank { return@withContext emptyList() }
        val key = "$station|$lineName|$towards|$dayType"
        val today = NOW_FORMAT.format(Date()).take(10)
        val kept = answers[key]

        // 받아 둔 지 얼마 안 됐으면 그대로 쓴다. 망을 탈 이유가 없다.
        if (kept != null && !TimetableCache(savedOn).isStale(today)) {
            return@withContext kept.map { ClockTime(it) }
        }
        if (apiKey.isBlank()) return@withContext kept.orEmpty().map { ClockTime(it) }

        val fresh = lookUp(station, lineName, towards, dayType)
        if (fresh.isNotEmpty()) {
            answers[key] = fresh.map { it.minuteOfDay }
            savedOn = today
            // 얻은 자리에서 바로 남긴다. 어차피 망을 탄 참이라 파일 쓰기는 묻힌다.
            flush()
            return@withContext fresh
        }
        // 망이 안 되거나 자료가 비었다. 낡았어도 남아 있는 것이 아예 없는 것보다 낫다.
        kept.orEmpty().map { ClockTime(it) }
    }

    /** 저장소에 밀어 넣는다. 저장소가 붙지 않았으면 아무 일도 하지 않는다. */
    private fun flush() {
        store?.save(TimetableCache(savedOn, answers.toMap()))
    }

    private suspend fun lookUp(
        station: String,
        lineName: String,
        towards: String,
        dayType: DayType,
    ): List<ClockTime> = withContext(Dispatchers.IO) {
        for (direction in DIRECTIONS) {
            val boarding = fetch(station, lineName, direction, dayType)
            if (boarding.isEmpty()) continue

            val alighting = fetch(towards, lineName, direction, dayType).associateBy { it.no }
            val serving = boarding.filter { train ->
                alighting[train.no]?.let { it.at > train.at } == true
            }
            // 한 편이라도 맞으면 이 방향이 맞다. 아니면 다음 방향을 본다.
            if (serving.isNotEmpty()) {
                return@withContext serving.map { it.at }.distinct().sorted()
            }
        }
        emptyList()
    }

    /**
     * 시간표가 쓰는 역 이름으로 고친다.
     *
     * 우리 자료는 `강남역`처럼 '역'을 붙여 두는데 시간표는 `강남`으로만 찾는다 —
     * `강남역`으로 물으면 오류 없이 0건이 온다. 괄호 병기(`총신대입구(이수)`)도 뗀다.
     * `서울역`은 떼도 붙여도 같은 결과라 굳이 예외로 두지 않는다.
     *
     * 괄호 안의 이름도 후보로 내놓는다. 총신대입구(이수)는 한 역인데 4호선에서는
     * 총신대입구, 7호선에서는 이수다 — 7호선 시간표를 총신대입구로 물으면 오류
     * 없이 0건이 오므로, 앞 이름이 빈손이면 괄호 안 이름으로 다시 묻는다.
     * 교대(법원·검찰청)처럼 괄호가 부제인 곳은 그 이름이 0건이라 그냥 지나간다.
     */
    internal fun variants(name: String): List<String> {
        val bare = name.substringBefore('(').trim()
        val noSuffix = bare.removeSuffix("역").takeIf { it.length > 1 } ?: bare
        val inParens = name.substringAfter('(', "").substringBefore(')').trim()
        return listOf(noSuffix, bare, inParens, name).distinct().filter { it.isNotBlank() }
    }

    private fun fetch(
        station: String,
        line: String,
        direction: String,
        dayType: DayType,
    ): List<Train> = cache.getOrPut("$station|$line|$direction|$dayType") {
        variants(station).firstNotNullOfOrNull { name ->
            fetchExact(name, line, direction, dayType).takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun fetchExact(
        station: String,
        line: String,
        direction: String,
        dayType: DayType,
    ): List<Train> {
        val now = NOW_FORMAT.format(Date())
        val collected = mutableListOf<Train>()
        var seen = 0

        // 대개 한 장이면 끝난다. 옛 개정판이 쌓여 한 장을 넘길 때만 더 본다.
        for (pageNo in 1..MAX_PAGES) {
            val query = buildString {
                append("serviceKey=").append(apiKey)      // 인코딩 키를 그대로 보낸다
                append("&pageNo=").append(pageNo)
                append("&numOfRows=").append(PAGE_SIZE)
                append("&dataType=JSON&tmprTmtblYn=N")
                append("&upbdnbSe=").append(encode(direction))
                append("&wkndSe=").append(encode(dayType.toParam()))
                append("&lineNm=").append(encode(line))
                append("&stnNm=").append(encode(station))
            }
            val page = runCatching { request("$ENDPOINT?$query", now) }
                .onFailure { Log.w(TAG, "시간표 조회 실패($station $line $direction): $it") }
                .getOrNull() ?: break

            collected += page.trains
            seen += page.seen
            if (page.seen == 0 || seen >= page.total) break
        }
        return collected
    }

    /** 한 장의 응답. [seen]은 걸러내기 전 행 수라 다음 장이 있는지 알려준다. */
    private data class Page(val trains: List<Train>, val seen: Int, val total: Int)

    private fun request(url: String, now: String): Page {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${connection.responseCode}")
                return Page(emptyList(), 0, 0)
            }
            parse(connection.inputStream.reader().readText(), now)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String, now: String): Page {
        val empty = Page(emptyList(), 0, 0)
        val response = JSONObject(body).optJSONObject("response") ?: return empty
        val code = response.optJSONObject("header")?.optString("resultCode")
        if (code != "00") {
            // 인증키 값은 절대 남기지 않는다 — 코드만 본다.
            Log.w(TAG, "시간표 응답 코드 $code")
            return empty
        }
        val page = response.optJSONObject("body") ?: return empty
        val items = page.optJSONObject("items")?.optJSONArray("item") ?: return empty

        val trains = (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            if (!item.isCurrentOn(now)) return@mapNotNull null
            val no = item.optString("trainno").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // 종착역에는 출발 시각이 없고, 시발역에는 도착 시각이 없다. 있는 쪽을 쓴다.
            val at = parseTime(item.optString("trainDptreTm"))
                ?: parseTime(item.optString("trainArvlTm"))
                ?: return@mapNotNull null
            Train(no, at)
        }
        return Page(trains, items.length(), page.optInt("totalCount", items.length()))
    }

    /**
     * 오늘 시행 중인 개정판인지. 날짜가 `2026-02-28T04:00:00` 꼴로 자릿수가 고정이라
     * 문자열 비교만으로 앞뒤가 가려진다 — 시간대 해석이 끼어들 틈이 없다.
     */
    private fun JSONObject.isCurrentOn(now: String): Boolean {
        val from = optString("vldBgngDt").takeIf { it.isNotBlank() && it != "null" }
        val until = optString("vldEndDt").takeIf { it.isNotBlank() && it != "null" }
        if (from != null && from > now) return false
        if (until != null && until < now) return false
        return true
    }

    /** "06:48:00". 막차는 25시를 넘기도 하므로 시를 그대로 분으로 환산한다. */
    private fun parseTime(text: String): ClockTime? {
        val parts = text.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return ClockTime(hour * 60 + minute)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun DayType.toParam(): String = when (this) {
        DayType.WEEKDAY -> "평일"
        // 공휴일·상시로 물으면 0건이 온다. 자료가 있는 쪽으로 묶는다.
        DayType.SATURDAY, DayType.HOLIDAY -> "주말"
    }

    companion object {
        /**
         * 화면 곳곳이 같은 시간표를 다시 받지 않도록 한 벌을 나눠 쓴다. 시간표는
         * 하루 동안 변하지 않으니 오래 들고 있어도 상하지 않고, 무엇보다 호출을 아낀다.
         */
        val shared: SeoulTimetable by lazy { SeoulTimetable() }

        const val TAG = "SeoulTimetable"
        const val ENDPOINT = "https://apis.data.go.kr/B553766/schedule/getTrainSch"
        const val TIMEOUT_MS = 15_000

        /** 옛 개정판까지 오다 보니 1호선 한 역·한 방향이 1,158행이다. */
        const val PAGE_SIZE = 2_000

        /** 넉넉한 상한. 여기 걸릴 만큼 쌓였다면 그건 우리 쪽 가정이 틀린 것이다. */
        const val MAX_PAGES = 5

        /** `vldBgngDt`와 같은 꼴로 지금을 적어 문자열끼리 견준다. */
        val NOW_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        /** 미리 받을 때는 노선에 맞는 쪽만 부른다 — 헛걸음이 곧 일일 호출 한도다. */
        val LINEAR_DIRECTIONS = listOf("상행", "하행")
        val LOOP_DIRECTIONS = listOf("내선", "외선")
        val LOOP_LINES = setOf("2호선")

        /** 앞의 둘로 안 되면 순환선이다. 맞는 방향이 나올 때까지 차례로 본다. */
        val DIRECTIONS = LINEAR_DIRECTIONS + LOOP_DIRECTIONS

        /** 공공 API를 몰아치지 않으면서 기다림은 줄이는 선. */
        const val MAX_IN_FLIGHT = 6
    }
}
