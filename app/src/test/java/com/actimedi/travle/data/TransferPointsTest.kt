package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 환승 안내는 **방향까지 맞아야** 제 값이다. 같은 역이라도 어느 쪽에서 왔느냐에
 * 따라 내릴 칸이 달라진다. 틀린 칸을 알려주면 낯선 역에서 반대편 끝까지 걷게 되므로,
 * 확신이 없으면 아무 말도 하지 않는 쪽을 택한다.
 */
class TransferPointsTest {

    /** 실제 자산에서 그대로 가져온 모양. 서울역은 방향에 따라 내릴 칸이 갈린다. */
    private val table = TransferTable(
        source = "test",
        rows = listOf(
            listOf("서울역", "1호선", "시청", "공항철도", "공덕", "1-1", "3-2", "600"),
            listOf("서울역", "1호선", "남영", "공항철도", "공덕", "10-4", "3-2", "600"),
            listOf("서울역", "1호선", "남영", "4호선", "숙대입구", "1-2", "3-3", "214"),
            // 소요시간이 비어 있는 역도 칸은 멀쩡하다.
            listOf("중랑", "경의중앙선", "상봉", "경춘선", "회기", "5-1", "5-1", null),
        ),
    )

    @Test
    fun `어느 쪽에서 왔는지에 따라 내릴 칸이 다르다`() {
        val fromCityHall = table.find("서울역", "1호선", "시청", "공항철도", "공덕")
        val fromNamyeong = table.find("서울역", "1호선", "남영", "공항철도", "공덕")

        assertEquals("1-1", fromCityHall?.off)
        assertEquals("10-4", fromNamyeong?.off)
        // 갈아타서 탈 자리는 같다 — 어디서 왔든 공덕 방면 열차는 한 곳에서 탄다.
        assertEquals("3-2", fromCityHall?.on)
        assertEquals("3-2", fromNamyeong?.on)
        assertEquals(600, fromCityHall?.seconds)
    }

    @Test
    fun `소요시간이 없어도 칸은 알려준다`() {
        val point = table.find("중랑", "경의중앙선", "상봉", "경춘선", "회기")
        assertEquals("5-1", point?.off)
        assertEquals("5-1", point?.on)
        assertNull(point?.seconds)
    }

    /**
     * 방향을 못 알아낸 경우. 노선만 맞는 줄이 하나뿐이면 그것을 쓴다 — 방향이
     * 갈릴 여지가 없기 때문이다.
     */
    @Test
    fun `노선만 맞아도 줄이 하나뿐이면 쓴다`() {
        assertEquals("1-2", table.find("서울역", "1호선", "모르는곳", "4호선", "모르는곳")?.off)
    }

    /** 여럿이면 고를 수 없다. 반대편 끝에 세워 놓느니 말하지 않는다. */
    @Test
    fun `노선만 맞는 줄이 여럿이면 아무것도 내놓지 않는다`() {
        assertNull(table.find("서울역", "1호선", "모르는곳", "공항철도", "모르는곳"))
    }

    @Test
    fun `모르는 역이나 노선이면 없다고 한다`() {
        assertNull(table.find("없는역", "1호선", "시청", "4호선", "숙대입구"))
        assertNull(table.find("서울역", "9호선", "시청", "4호선", "숙대입구"))
        assertNull(TransferTable().find("서울역", "1호선", "시청", "4호선", "숙대입구"))
    }
}
