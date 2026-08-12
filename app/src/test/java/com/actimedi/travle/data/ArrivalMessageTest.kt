package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 실제로 API가 준 문구만 시험한다 — 전부 화면에서 눈으로 본 것들이다.
 * 지어낸 꼴을 맞추려다 진짜 자료를 놓치는 편이 더 나쁘다.
 */
class ArrivalMessageTest {

    @Test
    fun `행선지와 방면을 가른다`() {
        val h = parseHeadsign("성수행 - 삼성방면")
        assertEquals("성수", h.destination)
        assertEquals("삼성", h.towards)
        assertEquals("", h.raw)
    }

    @Test
    fun `방면이 없는 것도 있다`() {
        val h = parseHeadsign("인천공항2터미널행")
        assertEquals("인천공항2터미널", h.destination)
        assertEquals("", h.towards)
    }

    /** 순환선은 종착과 방면이 같고 괄호까지 달고 온다. */
    @Test
    fun `순환선 표기`() {
        val h = parseHeadsign("응암순환(상선)행 - 응암순환(상선)방면")
        assertEquals("응암순환(상선)", h.destination)
        assertEquals("응암순환(상선)", h.towards)
    }

    @Test
    fun `모르는 꼴은 원문을 들고 간다`() {
        val h = parseHeadsign("어딘가로 갑니다")
        assertEquals("어딘가로 갑니다", h.raw)
        assertEquals("", h.destination)
        assertEquals(ArrivalHeadsign(), parseHeadsign("   "))
    }

    @Test
    fun `몇 분 후인지 읽는다`() {
        val a = parseArrivalMessage("3분 후 (역삼)")
        assertEquals(ArrivalStatus.Minutes(3), a.status)
        assertEquals("역삼", a.at)

        assertEquals(ArrivalStatus.Minutes(1), parseArrivalMessage("1분 후").status)
    }

    @Test
    fun `몇 정거장 전인지 읽는다`() {
        val a = parseArrivalMessage("[2]번째 전역 (수지구청)")
        assertEquals(ArrivalStatus.StopsAway(2), a.status)
        assertEquals("수지구청", a.at)
    }

    /** `전역 도착`은 `도착`을 품고 있어 순서를 틀리면 통째로 잘못 읽는다. */
    @Test
    fun `앞 역에서의 상태를 먼저 본다`() {
        assertEquals(ArrivalStatus.PreviousArrived, parseArrivalMessage("전역 도착").status)
        assertEquals(ArrivalStatus.PreviousDeparted, parseArrivalMessage("전역출발").status)
        assertEquals(ArrivalStatus.PreviousEntering, parseArrivalMessage("전역 진입").status)
    }

    @Test
    fun `이 역에서의 상태`() {
        assertEquals(ArrivalStatus.Entering, parseArrivalMessage("서울역 진입").status)
        assertEquals(ArrivalStatus.Arrived, parseArrivalMessage("당역 도착").status)
        assertEquals(ArrivalStatus.Departed, parseArrivalMessage("계양 출발").status)
    }

    @Test
    fun `앞에 붙은 역 이름을 건진다`() {
        val a = parseArrivalMessage("강남구청 도착")
        assertEquals(ArrivalStatus.Arrived, a.status)
        assertEquals("강남구청", a.at)
        // `당역`·`전역`은 역 이름이 아니다.
        assertEquals("", parseArrivalMessage("당역 도착").at)
        assertEquals("", parseArrivalMessage("전역 도착").at)
    }

    @Test
    fun `못 알아본 문구는 그대로 남긴다`() {
        val a = parseArrivalMessage("운행중", position = "선릉")
        assertEquals(ArrivalStatus.Unknown("운행중"), a.status)
        assertEquals("선릉", a.at)
    }

    @Test
    fun `괄호가 없으면 따로 받은 위치를 쓴다`() {
        assertEquals("역삼", parseArrivalMessage("3분 후", position = "역삼").at)
    }
}

/**
 * 2026-08-12 군자(능동)에서 실제로 받은 응답. 역 이름이 괄호를 달고 오는 바람에
 * 괄호를 뜯어 위치를 잡던 첫 판이 무너졌다 — `7분 후 (천호(풍납토성))`이
 * `천호(풍납토성`으로 잘렸다.
 */
class ArrivalMessageRealDataTest {

    @Test
    fun `괄호 달린 역 이름`() {
        val a = parseArrivalMessage("7분 후 (천호(풍납토성))", position = "천호(풍납토성)")
        assertEquals(ArrivalStatus.Minutes(7), a.status)
        assertEquals("천호(풍납토성)", a.at)
    }

    @Test
    fun `괄호 달린 역 이름이 앞에 오는 경우`() {
        val a = parseArrivalMessage("군자(능동) 진입", position = "군자(능동)")
        assertEquals(ArrivalStatus.Entering, a.status)
        assertEquals("군자(능동)", a.at)
    }

    @Test
    fun `전역 도착은 위치를 그대로 받는다`() {
        val a = parseArrivalMessage("전역 도착", position = "어린이대공원(세종대)")
        assertEquals(ArrivalStatus.PreviousArrived, a.status)
        assertEquals("어린이대공원(세종대)", a.at)
    }

    @Test
    fun `실제 행선지 표기`() {
        val h = parseHeadsign("방화행 - 장한평방면")
        assertEquals("방화", h.destination)
        assertEquals("장한평", h.towards)
    }
}
