package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 받아 둔 시간표를 다음 실행까지 남긴다.
 *
 * 여행자는 로밍이 끊기거나 eSIM이 말썽인 채로 지하철에 있는 경우가 많다. 노선도는
 * 앱에 들어 있고 경로도 디스크에 있는데 시간표만 망을 타면, 정작 막차가 궁금한
 * 순간에 답이 없다. 한 번 받아 본 구간은 망 없이도 답할 수 있어야 한다.
 */
interface TimetableStore {
    fun load(): TimetableCache
    fun save(cache: TimetableCache)
}

/**
 * `역|노선|목적지|요일` → 출발 시각(자정 기준 분).
 *
 * 열차번호까지는 남기지 않는다. 방향을 가리는 데만 쓰이고 그 판정은 이미 끝났으니,
 * 남길 값은 답 그 자체면 된다.
 */
@Serializable
data class TimetableCache(
    /** 이 자료를 받은 날. `2026-08-12` 꼴로, 낡았는지 가리는 데 쓴다. */
    val savedOn: String = "",
    val entries: Map<String, List<Int>> = emptyMap(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * 받은 지 [FRESH_DAYS]일이 지났는가. 자릿수가 고정된 날짜라 문자열 비교로 족하다.
     *
     * 낡았다고 버리지는 않는다 — 망이 되면 새로 받고, 안 되면 낡은 것이라도 쓴다.
     * 시간표 개정은 몇 달에 한 번이고, 몇 주 지난 값도 아예 없는 것보다 낫다.
     */
    fun isStale(today: String): Boolean = savedOn.isBlank() || daysBetween(savedOn, today) >= FRESH_DAYS

    companion object {
        const val FRESH_DAYS = 14

        /** `yyyy-MM-dd` 두 날짜의 간격. 형식이 어긋나면 낡은 것으로 본다. */
        internal fun daysBetween(from: String, to: String): Int {
            val a = epochDay(from) ?: return Int.MAX_VALUE
            val b = epochDay(to) ?: return Int.MAX_VALUE
            return b - a
        }

        private fun epochDay(text: String): Int? {
            val parts = text.split('-')
            if (parts.size != 3) return null
            val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
            // 그레고리력 통산일. 정확한 날짜 셈이 필요한 것이 아니라 간격만 보면 된다.
            val months = (m + 9) % 12
            val years = y - months / 10
            return 365 * years + years / 4 - years / 100 + years / 400 +
                (months * 306 + 5) / 10 + (d - 1)
        }
    }
}

/** 내부 저장소에 JSON 한 덩이로. [RouteStore]와 같은 방식이다. */
class FileTimetableStore(context: Context) : TimetableStore {

    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): TimetableCache {
        if (!file.exists()) return TimetableCache()
        return runCatching { json.decodeFromString<TimetableCache>(file.readText()) }
            .onFailure { Log.w(TAG, "저장된 시간표를 읽지 못했습니다", it) }
            .getOrDefault(TimetableCache())
    }

    override fun save(cache: TimetableCache) {
        runCatching {
            // 중간에 죽어도 반쪽짜리 파일이 남지 않게 임시 파일을 거친다.
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(json.encodeToString(cache))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "시간표를 저장하지 못했습니다", it) }
    }

    private companion object {
        const val FILE_NAME = "timetable.json"
        const val TAG = "TimetableStore"
    }
}
