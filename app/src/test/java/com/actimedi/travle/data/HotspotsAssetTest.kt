package com.actimedi.travle.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 실제 자산을 읽어 본다. 화면에서 목록이 안 나올 때 자료가 문제인지 화면이 문제인지
 * 가리는 자리다 — 도구가 만든 자산과 앱이 읽는 모양이 어긋나면 여기서 걸린다.
 */
class HotspotsAssetTest {

    private val table: SpotTable = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/hotspots.json").readText())

    @Test
    fun `자산이 읽힌다`() {
        assertTrue("항목이 없다", table.spots.isNotEmpty())
        assertEquals("2026-08", table.asOf)
    }

    @Test
    fun `성수에서 30분이면 카페거리가 나온다`() {
        val spots = table.near("성수", 30)
        assertTrue("성수에 아무것도 없다", spots.isNotEmpty())
        assertEquals("성수동 카페거리", spots.first().name)
    }

    @Test
    fun `갈래가 다 읽힌다`() {
        val unknown = table.spots.filter { it.kind == SpotKind.UNKNOWN }
        assertTrue("모르는 갈래: ${unknown.map { it.name }}", unknown.isEmpty())
    }

    /**
     * 도구가 만든 다국어 이름이 화면에 그대로 나갈 만한지 본다. 자동으로 긁어 온
     * 이름에서 실제로 걸렸던 것들이다 — 역 이름이 새거나(`奉恩寺站`), 자리를 잡아 준
     * 안내소 이름이 오거나(`…韓屋村の案内所`), 읽는 법이 괄호로 붙어 온다.
     */
    @Test
    fun `다국어 이름이 화면에 낼 만하다`() {
        table.spots.forEach { spot ->
            listOfNotNull(spot.japanese, spot.simplified, spot.traditional).forEach {
                assertTrue("${spot.name}: 괄호가 남았다 — $it", '(' !in it && '（' !in it)
                assertTrue("${spot.name}: 역 이름이 섞였다 — $it", !it.endsWith("站") && !it.endsWith("驛"))
            }
        }
        val bongeunsa = table.spots.first { it.name == "봉은사" }
        assertEquals("奉恩寺", bongeunsa.simplified)
        val bukchon = table.spots.first { it.name == "북촌한옥마을" }
        assertEquals("北村韓屋村", bukchon.traditional)
    }

    /** 중국어는 한 벌만 구해 간체·번체로 나눈다. 한쪽만 있으면 나누다 만 것이다. */
    @Test
    fun `중국어는 간체와 번체가 짝을 이룬다`() {
        table.spots.forEach {
            assertEquals("${it.name}: 간체·번체가 짝이 아니다", it.simplified == null, it.traditional == null)
        }
    }

    @Test
    fun `좌표가 있고 도보 시간이 말이 된다`() {
        table.spots.forEach {
            assertTrue("${it.name} 좌표 없음", it.lat > 33.0 && it.lon > 124.0)
            assertTrue("${it.name} 도보 ${it.walkMinutes}분", it.walkMinutes in 0..25)
        }
    }
}
