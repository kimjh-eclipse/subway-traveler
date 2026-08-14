package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * 가서 둘러볼 최소한의 시간. 왕복만 하고 돌아오는 것은 다녀온 것이 아니다.
 *
 * [SpotTable] 안의 `private companion object`에 두었다가 기기에서 `IllegalAccessError`를
 * 만났다. `@Serializable`은 `Companion.serializer()`를 그 컴패니언에 붙이는데, private이면
 * 바깥에서 못 부른다. JVM 단위 테스트는 통과하고 ART에서만 터진다 — 그래서 최상위로 뺐다.
 */
private const val MINIMUM_LOOK_AROUND = 10

/** 갈래. 화면에서 칩 한 글자로 쓴다. */
enum class SpotKind {
    AREA, LANDMARK, MARKET, PARK, PALACE, SHOPPING, UNKNOWN;

    companion object {
        fun of(code: String): SpotKind = when (code) {
            "area" -> AREA
            "landmark" -> LANDMARK
            "market" -> MARKET
            "park" -> PARK
            "palace" -> PALACE
            "shopping" -> SHOPPING
            else -> UNKNOWN
        }
    }
}

/**
 * 역에서 내려 갈 만한 곳 하나.
 *
 * 가게가 아니라 동네와 명소다 — 가게는 문을 닫지만 광장시장은 안 없어진다.
 * 지금 뭐가 좋은지는 [query]를 쥐여 주어 지도 앱이 답한다.
 */
@Serializable
data class Spot(
    @SerialName("n") val name: String,
    /** 어느 역에서 가는가. 노선망의 한국어 이름이다. */
    @SerialName("st") val station: String,
    @SerialName("c") private val kindCode: String = "",
    /** 지도 앱에 넘길 검색어. */
    @SerialName("q") val query: String,
    @SerialName("y") val lat: Double = 0.0,
    @SerialName("x") val lon: Double = 0.0,
    /** 역에서 걸어서 몇 분. 0이면 역 앞 동네라 걸을 것이 없다는 뜻이다. */
    @SerialName("w") val walkMinutes: Int = 0,
    @SerialName("e") val english: String? = null,
    @SerialName("j") val japanese: String? = null,
    @SerialName("s") val simplified: String? = null,
    @SerialName("t") val traditional: String? = null,
) {
    val kind: SpotKind get() = SpotKind.of(kindCode)

    /** [locale]로 읽을 이름. 자료가 없으면 한국어 그대로 — 못 읽는 것보다 낫다. */
    fun localized(locale: Locale): String = when (locale.language) {
        "en" -> english
        "ja" -> japanese
        "zh" -> if (locale.isTraditionalChinese()) traditional ?: simplified else simplified
        else -> null
    } ?: name
}

/**
 * 역마다 '내려서 뭘 할까'.
 *
 * `tools/hotspots.py`가 만든다. [asOf]를 화면에 내보인다 — 이 자료는 늙고,
 * 언제 기준인지 모르면 낡은 것을 최신인 줄 안다.
 */
@Serializable
data class SpotTable(
    val asOf: String = "",
    val source: String = "",
    val spots: List<Spot> = emptyList(),
) {
    private val byStation: Map<String, List<Spot>> by lazy {
        spots.groupBy { it.station.replace(" ", "") }
    }

    /**
     * 이 역에서 갈 만한 곳. [stayMinutes]가 주어지면 **왕복하고도 남는 곳만** 남긴다 —
     * 30분 머무는 사람에게 편도 14분짜리를 권하면 그건 권한 것이 아니다.
     */
    fun near(station: String, stayMinutes: Int = 0): List<Spot> {
        val here = byStation[station.replace(" ", "")]
            ?: byStation[station.replace(" ", "").removeSuffix("역")]
            ?: return emptyList()
        if (stayMinutes <= 0) return here.sortedBy { it.walkMinutes }
        return here
            .filter { it.walkMinutes * 2 + MINIMUM_LOOK_AROUND <= stayMinutes }
            .sortedBy { it.walkMinutes }
    }
}

/** 자산을 한 번만 읽는다. */
object SpotsLoader {
    private const val ASSET = "hotspots.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): SpotTable = runCatching {
        context.assets.open(ASSET).bufferedReader().use {
            json.decodeFromString<SpotTable>(it.readText())
        }
    }.onFailure { Log.w("Hotspots", "가 볼 곳 자료를 읽지 못했습니다", it) }
        .getOrDefault(SpotTable())
}
