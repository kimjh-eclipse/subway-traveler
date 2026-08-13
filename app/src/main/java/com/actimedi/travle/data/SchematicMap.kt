package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 도식 노선도의 역 자리.
 *
 * 지리 좌표로 그린 노선도는 사실이지만 읽기 어렵다 — 선이 제멋대로 꺾이고 도심에서는
 * 역이 뭉친다. 사람들이 아는 지하철 노선도는 옥토리니어 도식도이고, 그 배치는 지리
 * 좌표에서 나오지 않는다. 역마다 도식 전용 좌표가 따로 있어야 한다.
 *
 * 자리는 역 이름이 아니라 **노선망의 순번**으로 담는다. 같은 이름의 역이 20쌍 있어
 * (5호선 양평과 경의중앙선 양평은 서로 다른 역이다) 이름으로는 담을 수 없다.
 *
 * `tools/schematic_map.py`가 MIT 라이선스 SVG에서 캐낸다.
 */
/** 도식도의 선 한 토막. 자리는 원본 SVG 좌표 그대로이고, 화면 좌표로는 그릴 때 옮긴다. */
@Serializable
data class SchematicSegment(
    @SerialName("a") val from: List<Float> = emptyList(),
    @SerialName("b") val to: List<Float> = emptyList(),
    /** `#00a9dc` 꼴. 노선 색이 자료에 이미 들어 있어 우리가 고를 것이 없다. */
    @SerialName("c") val colour: String = "",
    @SerialName("w") val width: Float = 3f,
) {
    val isUsable: Boolean get() = from.size >= 2 && to.size >= 2 && colour.startsWith("#")
}

@Serializable
data class SchematicMap(
    val source: String = "",
    /** 원본 그림의 크기. 정규화할 때 쓴다. */
    @SerialName("w") val width: Float = 0f,
    @SerialName("h") val height: Float = 0f,
    /** 순번대로 늘어놓은 `[x, y]`. 자리를 못 찾은 역은 null. */
    @SerialName("p") val points: List<List<Float>?> = emptyList(),
    /**
     * 도식도의 선 그 자체 — `[x1, y1, x2, y2, 색, 굵기]`.
     *
     * 역끼리 직선으로 이어 그리면 도식처럼 보이지 않는다. 진짜 도식도는 역이 아닌
     * 자리에서도 꺾이고 그 꺾임이 그림의 성격을 만든다. 굵은 하늘색 한 줄기는
     * 한강이라 노선은 아니지만, 그려야 서울로 읽힌다.
     */
    @SerialName("s") val segments: List<SchematicSegment> = emptyList(),
) {
    val isEmpty: Boolean get() = points.none { it != null }

    /** 이 역의 도식 자리. 모르면 null. */
    fun at(index: Int): Pair<Float, Float>? =
        points.getOrNull(index)?.takeIf { it.size >= 2 }?.let { it[0] to it[1] }
}

/** `assets/schematic_map.json`을 한 번만 읽는다. */
object SchematicMapLoader {

    @Volatile
    private var cached: SchematicMap? = null

    fun load(context: Context): SchematicMap = cached ?: synchronized(this) {
        cached ?: read(context).also { cached = it }
    }

    private fun read(context: Context): SchematicMap = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<SchematicMap>(text)
    }.onFailure { Log.w(TAG, "도식 노선도를 읽지 못했습니다", it) }
        .getOrDefault(SchematicMap())

    private const val ASSET = "schematic_map.json"
    private const val TAG = "SchematicMap"
}

/**
 * 노선도를 어떤 모양으로 볼 것인가.
 *
 * 기본은 도식이다 — 어디서 어디로 가는지 읽는 것이 목적이고, 그 일에는 지리적
 * 정확함보다 선이 곧은 것이 낫다. 실제 위치가 궁금할 때만 지리로 바꾼다.
 */
enum class MapStyle { SCHEMATIC, GEOGRAPHIC }
