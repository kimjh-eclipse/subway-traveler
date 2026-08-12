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
