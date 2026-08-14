package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HotspotsTest {

    private val table = SpotTable(
        asOf = "2026-08",
        spots = listOf(
            Spot("역 앞 동네", "강남", "area", "강남", 37.49, 127.02, 0, "By the station"),
            Spot("가까운 시장", "강남", "market", "시장", 37.50, 127.02, 5, "Near Market"),
            Spot("먼 공원", "강남", "park", "공원", 37.52, 127.02, 20, "Far Park"),
            Spot("다른 역 명소", "노원", "landmark", "명소", 37.65, 127.06, 3),
        ),
    )

    @Test
    fun `역으로 고른다`() {
        assertEquals(3, table.near("강남").size)
        assertEquals(listOf("다른 역 명소"), table.near("노원").map { it.name })
        assertEquals(emptyList<Spot>(), table.near("없는역"))
    }

    /** 경로에는 `강남역`으로 저장된다. */
    @Test
    fun `역 붙임말을 넘어 찾는다`() {
        assertEquals(3, table.near("강남역").size)
    }

    /**
     * 30분 세워 둔 사람에게 편도 20분짜리를 권하면 그건 권한 것이 아니다.
     * 왕복에 둘러볼 시간까지 들어가야 갈 수 있는 곳이다.
     */
    @Test
    fun `머무는 시간에 못 다녀올 곳은 빼낸다`() {
        // 30분: 역 앞(0×2+10=10) OK, 시장(5×2+10=20) OK, 공원(20×2+10=50) 안 됨.
        assertEquals(listOf("역 앞 동네", "가까운 시장"), table.near("강남", 30).map { it.name })
        // 15분이면 역 앞만 남는다.
        assertEquals(listOf("역 앞 동네"), table.near("강남", 15).map { it.name })
        // 넉넉하면 다 나온다.
        assertEquals(3, table.near("강남", 120).size)
    }

    @Test
    fun `가까운 곳부터 내놓는다`() {
        assertEquals(listOf(0, 5, 20), table.near("강남").map { it.walkMinutes })
    }

    @Test
    fun `언어를 따라간다`() {
        val spot = table.near("강남").first { it.name == "가까운 시장" }
        assertEquals("Near Market", spot.localized(Locale.ENGLISH))
        assertEquals("가까운 시장", spot.localized(Locale.KOREAN))
        // 그 언어 표기가 없으면 한국어 그대로.
        assertEquals("다른 역 명소", table.near("노원").first().localized(Locale.ENGLISH))
    }

    @Test
    fun `갈래를 읽는다`() {
        assertEquals(SpotKind.MARKET, table.near("강남")[1].kind)
        assertEquals(SpotKind.UNKNOWN, Spot("x", "y", "없는갈래", "q").kind)
    }

    @Test
    fun `자료가 없으면 조용히 빈다`() {
        assertTrue(SpotTable().near("강남").isEmpty())
    }
}
