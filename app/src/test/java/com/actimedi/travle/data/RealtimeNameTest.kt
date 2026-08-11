package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 노선망(OSM)과 실시간 도착(서울시)이 같은 역을 다르게 부른다. 어긋나면 API가
 * 오류 대신 빈 결과를 주어 화면에는 "들어올 열차가 없습니다"로 보인다 — 고장인지
 * 막차 이후인지 구분이 안 되는 조용한 실패라 이 변환을 못박아 둔다.
 */
class RealtimeNameTest {

    private val network = SubwayNetwork(
        source = "test",
        stations = listOf(
            // API가 괄호를 붙여 부르는 쪽.
            SubwayStation("군자", 37.55, 127.07, listOf("5호선", "7호선"), realtimeNames = listOf("군자(능동)")),
            // 반대로 우리 쪽에만 괄호가 있는 쪽.
            SubwayStation("교대(법원·검찰청)", 37.49, 127.01, listOf("2호선"), realtimeNames = listOf("교대")),
            // 양쪽 이름이 같아 바꿀 것이 없는 쪽.
            SubwayStation("강남", 37.49, 127.02, listOf("2호선")),
            // API가 노선별로 쪼개 둔 쪽 — 하나만 물으면 5호선이 통째로 빠진다.
            SubwayStation(
                "올림픽공원", 37.51, 127.13, listOf("5호선", "9호선"),
                realtimeNames = listOf("올림픽공원", "올림픽공원(한국체대)"),
            ),
        ),
    )

    @Test
    fun `노선별로 쪼개진 역은 이름을 다 돌려준다`() {
        assertEquals(
            listOf("올림픽공원", "올림픽공원(한국체대)"),
            network.realtimeNamesFor("올림픽공원역"),
        )
    }

    @Test
    fun `API가 쓰는 이름으로 바꾼다`() {
        assertEquals(listOf("군자(능동)"), network.realtimeNamesFor("군자"))
        assertEquals(listOf("교대"), network.realtimeNamesFor("교대(법원·검찰청)"))
    }

    @Test
    fun `경로에 저장된 역 이름도 찾아낸다`() {
        // 경로는 '군자역'처럼 적어 두지만 노선망은 '군자'다.
        assertEquals(listOf("군자(능동)"), network.realtimeNamesFor("군자역"))
    }

    @Test
    fun `짝이 없으면 이름을 그대로 둔다`() {
        assertEquals(listOf("강남"), network.realtimeNamesFor("강남"))
        // 노선망에 없는 곳 — 물어보긴 해야 하니 받은 이름을 돌려준다.
        assertEquals(listOf("교동마을 LG자이"), network.realtimeNamesFor("교동마을 LG자이"))
    }
}
