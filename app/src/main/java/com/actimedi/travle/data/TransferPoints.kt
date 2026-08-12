package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 환승역에서 어느 칸에 내려 어디로 갈아타면 되는지.
 *
 * 서울교통공사가 환승역마다 최단 환승 경로를 공개한다. 낯선 역에서 캐리어를 끌고
 * 반대편 끝까지 걷는 일이 이 한 줄로 사라진다.
 */
data class TransferPoint(
    /** 갈아타는 역. */
    val station: String,
    val fromLine: String,
    /** 타고 온 열차가 향하던 곳 — 방향을 가리는 값이다. */
    val fromTowards: String,
    val toLine: String,
    /** 갈아탄 열차가 향하는 곳. */
    val toTowards: String,
    /** 내릴 자리. `10-4`는 10호차 4번 문이다. */
    val off: String,
    /** 탈 자리. */
    val on: String,
    /** 걸리는 시간(초). 원본에 없는 역이 셋 있어 null이 된다. */
    val seconds: Int?,
)

/**
 * 자산으로 실린 환승 안내.
 *
 * 방향까지 맞아야 제 값이다. 같은 서울역이라도 시청 방면에서 왔으면 1-1,
 * 남영 방면에서 왔으면 1-2에서 내린다 — 방향을 무시하고 아무 줄이나 쓰면
 * 엉뚱한 칸에 서 있게 된다.
 */
@Serializable
data class TransferTable(
    val source: String = "",
    /** `[역, 시작노선, 하차방면, 종료노선, 승차방면, 하차위치, 승차위치, 초]`. */
    @SerialName("t") val rows: List<List<String?>> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    private val byStation: Map<String, List<TransferPoint>> by lazy {
        rows.mapNotNull { it.toPoint() }.groupBy { it.station }
    }

    private fun List<String?>.toPoint(): TransferPoint? {
        if (size < 8) return null
        val at = get(0) ?: return null
        return TransferPoint(
            station = at,
            fromLine = get(1) ?: return null,
            fromTowards = get(2) ?: return null,
            toLine = get(3) ?: return null,
            toTowards = get(4) ?: return null,
            off = get(5) ?: return null,
            on = get(6) ?: return null,
            seconds = get(7)?.toIntOrNull(),
        )
    }

    /**
     * 이 갈아타기에 맞는 안내.
     *
     * 방향까지 맞는 줄을 먼저 찾고, 없으면 노선만 맞는 줄이 **하나뿐일 때만** 쓴다.
     * 여럿이면 어느 것을 고를지 알 수 없으므로 아무것도 내놓지 않는다 — 틀린 칸을
     * 알려주느니 말하지 않는 편이 낫다.
     */
    fun find(
        station: String,
        fromLine: String,
        fromTowards: String,
        toLine: String,
        toTowards: String,
    ): TransferPoint? {
        val here = byStation[station] ?: return null
        val onLines = here.filter { it.fromLine == fromLine && it.toLine == toLine }
        if (onLines.isEmpty()) return null

        onLines.firstOrNull { it.fromTowards == fromTowards && it.toTowards == toTowards }
            ?.let { return it }
        return onLines.singleOrNull()
    }
}

/** `assets/transfer_points.json`을 한 번만 읽는다. */
object TransferPointLoader {

    @Volatile
    private var cached: TransferTable? = null

    fun load(context: Context): TransferTable = cached ?: synchronized(this) {
        cached ?: read(context).also { cached = it }
    }

    private fun read(context: Context): TransferTable = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<TransferTable>(text)
    }.onFailure { Log.w(TAG, "환승 안내를 읽지 못했습니다", it) }
        .getOrDefault(TransferTable())

    private const val ASSET = "transfer_points.json"
    private const val TAG = "TransferPoints"
}
