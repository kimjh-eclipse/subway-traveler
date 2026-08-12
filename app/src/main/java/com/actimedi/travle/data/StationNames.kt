package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * 한 역의 다른 나라 표기.
 *
 * 자리를 아끼려고 한 글자짜리 이름을 쓴다 — 673개 역이 들어가는 자산이다.
 * 번체는 간체와 다를 때만 담는다. `江南`처럼 두 서체가 같은 역이 많다.
 */
@Serializable
data class StationName(
    @SerialName("e") val english: String? = null,
    @SerialName("j") val japanese: String? = null,
    @SerialName("s") val simplified: String? = null,
    @SerialName("t") val traditional: String? = null,
)

/**
 * 역 이름표.
 *
 * 노선망은 이름을 한국어로만 들고 있다. 앱을 영어로 봐도 `강남`, `군자`가 그대로
 * 나오면 다국어를 지원한들 정작 여행에 필요한 이름을 읽을 수 없다.
 *
 * `tools/station_names.py`가 OpenStreetMap과 Wikidata에서 만든다.
 */
@Serializable
data class StationNameTable(
    val source: String = "",
    val names: Map<String, StationName> = emptyMap(),
) {
    val isEmpty: Boolean get() = names.isEmpty()

    /**
     * [locale]로 읽을 이름. 자료가 없으면 받은 이름을 그대로 돌려준다 — 못 읽는
     * 것보다 낫고, 역 이름을 통째로 비워 두면 화면이 무너진다.
     *
     * 경로에는 `강남역`으로 저장되는데 이름표의 열쇠는 `강남`이다. [SubwayNetwork.findStation]과
     * 같은 규칙으로 '역'과 괄호를 떼어 가며 찾는다.
     */
    fun localized(korean: String, locale: Locale): String {
        val entry = lookup(korean) ?: return korean
        return when (locale.language) {
            "en" -> entry.english
            "ja" -> entry.japanese
            "zh" -> if (locale.isTraditionalChinese()) {
                entry.traditional ?: entry.simplified
            } else {
                entry.simplified
            }
            else -> null
        } ?: korean
    }

    private fun lookup(raw: String): StationName? {
        val name = raw.trim()
        if (name.isEmpty()) return null
        names[name]?.let { return it }
        if (name.endsWith("역") && name.length > 1) names[name.dropLast(1)]?.let { return it }
        names["${name}역"]?.let { return it }
        // `총신대입구 (이수)` ↔ `총신대입구(이수)` — 띄어쓰기가 자료마다 다르다.
        val squashed = name.replace(" ", "")
        return names.entries.firstOrNull { it.key.replace(" ", "") == squashed }?.value
    }
}

/**
 * 번체를 쓰는 자리인지. 안드로이드가 문자 체계를 채워 주면 그것을 믿고, 비어
 * 있으면 지역으로 가른다 — 대만·홍콩·마카오가 번체다.
 */
fun Locale.isTraditionalChinese(): Boolean = when {
    script == "Hant" -> true
    script == "Hans" -> false
    else -> country in setOf("TW", "HK", "MO")
}

/** 이름표를 한 번만 읽는다. 한국어로 볼 때는 아예 읽지 않는다. */
object StationNamesLoader {
    private const val ASSET = "station_names.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): StationNameTable = runCatching {
        context.assets.open(ASSET).bufferedReader().use {
            json.decodeFromString<StationNameTable>(it.readText())
        }
    }.onFailure { Log.w("StationNames", "역 이름표를 읽지 못했습니다", it) }
        .getOrDefault(StationNameTable())
}
