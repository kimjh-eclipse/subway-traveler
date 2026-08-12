package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StationNamesTest {

    private val table = StationNameTable(
        names = mapOf(
            // 두 서체가 같은 역 — 번체는 담지 않는다.
            "강남" to StationName("Gangnam", "カンナム(江南)", "江南"),
            // 서체가 갈리는 역.
            "용산" to StationName("Yongsan", "龍山", "龙山", "龍山"),
            // 띄어쓰기가 자료마다 다른 역.
            "총신대입구 (이수)" to StationName("Chongshin Univ. (Isu)", "チョンシンデイック", "总神大学", "總神大學"),
        ),
    )

    @Test
    fun `언어마다 그 나라 표기를 준다`() {
        assertEquals("Gangnam", table.localized("강남", Locale.ENGLISH))
        assertEquals("カンナム(江南)", table.localized("강남", Locale.JAPANESE))
        assertEquals("강남", table.localized("강남", Locale.KOREAN))
    }

    @Test
    fun `중국어는 문자 체계로 가른다`() {
        assertEquals("龙山", table.localized("용산", Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals("龍山", table.localized("용산", Locale.forLanguageTag("zh-Hant-TW")))
        // 문자 체계가 비어 있으면 지역으로 판단한다.
        assertEquals("龍山", table.localized("용산", Locale.forLanguageTag("zh-HK")))
        assertEquals("龙山", table.localized("용산", Locale.forLanguageTag("zh-CN")))
    }

    @Test
    fun `번체가 없으면 간체로 대신한다`() {
        // 江南은 두 서체가 같아 번체를 담지 않았다.
        assertEquals("江南", table.localized("강남", Locale.forLanguageTag("zh-Hant-TW")))
    }

    /** 경로에는 `강남역`으로 저장되지만 이름표의 열쇠는 `강남`이다. */
    @Test
    fun `역 붙임말과 띄어쓰기를 넘어 찾는다`() {
        assertEquals("Gangnam", table.localized("강남역", Locale.ENGLISH))
        assertEquals("Chongshin Univ. (Isu)", table.localized("총신대입구(이수)", Locale.ENGLISH))
    }

    @Test
    fun `모르는 이름은 그대로 둔다`() {
        // 노선망에 없는 장소 — 사용자가 직접 적은 곳이다.
        assertEquals("교동마을 LG자이", table.localized("교동마을 LG자이", Locale.ENGLISH))
        assertEquals("강남", StationNameTable().localized("강남", Locale.ENGLISH))
    }

    @Test
    fun `문자 체계 판정`() {
        assertTrue(Locale.forLanguageTag("zh-Hant").isTraditionalChinese())
        assertTrue(Locale.forLanguageTag("zh-MO").isTraditionalChinese())
        assertFalse(Locale.forLanguageTag("zh-Hans").isTraditionalChinese())
        assertFalse(Locale.forLanguageTag("zh-SG").isTraditionalChinese())
    }
}

/**
 * 다른 나라 표기로 역을 찾는 것. 영어로 앱을 쓰는 사람에게는 `Gangnam`이 그 역의
 * 이름이지, `강남`을 칠 방법이 없다 — 자판부터 없다.
 */
class ForeignSuggestTest {

    private val network = SubwayNetwork(
        stations = listOf(
            SubwayStation("강남", 37.49, 127.02, listOf("2호선")),
            SubwayStation("강남구청", 37.51, 127.04, listOf("7호선")),
            SubwayStation("노원", 37.65, 127.06, listOf("4호선")),
        ),
        stationNames = StationNameTable(
            names = mapOf(
                "강남" to StationName("Gangnam", "カンナム(江南)", "江南"),
                "강남구청" to StationName("Gangnam-gu Office", "カンナムクチョン", "江南区厅"),
                "노원" to StationName("Nowon", "ノウォン(盧原)", "芦原", "蘆原"),
            ),
        ),
    )

    @Test
    fun `영문으로 찾는다`() {
        assertEquals(listOf("강남", "강남구청"), network.suggest("Gangnam").map { it.name })
        assertEquals(listOf("노원"), network.suggest("nowon").map { it.name })
    }

    @Test
    fun `대소문자를 가리지 않는다`() {
        assertEquals("강남", network.suggest("GANGNAM").first().name)
        assertEquals("강남", network.suggest("gangnam").first().name)
    }

    @Test
    fun `일본어와 중국어로도 찾는다`() {
        assertEquals("노원", network.suggest("ノウォン").first().name)
        assertEquals("노원", network.suggest("芦原").first().name)
        // 번체로 쳐도 같은 역이 나온다.
        assertEquals("노원", network.suggest("蘆原").first().name)
    }

    @Test
    fun `한국어로 정확히 맞은 역이 앞에 온다`() {
        // `강남`은 한국어 완전 일치라 영문 완전 일치인 자기 자신보다 앞선다는 뜻이
        // 아니라, 부분 일치한 강남구청보다 앞선다는 뜻이다.
        assertEquals("강남", network.suggest("강남").first().name)
    }

    @Test
    fun `이름표가 없으면 한국어 검색만 한다`() {
        val bare = SubwayNetwork(stations = network.stations)
        assertEquals(emptyList<String>(), bare.suggest("Gangnam").map { it.name })
        assertEquals(listOf("강남", "강남구청"), bare.suggest("강남").map { it.name })
    }
}
