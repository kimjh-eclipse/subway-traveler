package com.actimedi.travle.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 요일은 **언어와 무관하게** 저장해야 한다.
 *
 * 예전에는 화면에 보이던 문구를 그대로 저장했다. 영어로 쓰는 사람이 토요일을
 * 고르면 `Saturday`가 저장되고, 판정은 한국어 `토`만 보고 있어서 **평일 시간표**를
 * 썼다 — 첫차·막차가 다른 날인데 다른 날 것을 보여 준 셈이다.
 */
class DayOfWeekTokenTest {

    @Test
    fun `저장한 값을 그대로 알아본다`() {
        assertEquals(DayOfWeekToken.SATURDAY, DayOfWeekToken.canonical("SAT"))
        assertEquals(DayOfWeekToken.SUNDAY, DayOfWeekToken.canonical("SUN"))
        assertEquals(DayOfWeekToken.MONDAY, DayOfWeekToken.canonical("MON"))
    }

    /** 이미 저장된 경로가 들고 있는 문구들. 지울 수 없는 자료다. */
    @Test
    fun `옛 경로의 다섯 언어 문구도 알아본다`() {
        listOf("토요일", "Saturday", "土曜日", "星期六", "週六").forEach {
            assertEquals("$it → 토요일이어야 한다", DayOfWeekToken.SATURDAY, DayOfWeekToken.canonical(it))
        }
        listOf("일요일", "Sunday", "日曜日", "星期日", "週日").forEach {
            assertEquals("$it → 일요일이어야 한다", DayOfWeekToken.SUNDAY, DayOfWeekToken.canonical(it))
        }
    }

    @Test
    fun `모르는 값과 빈 값은 빈 값이다`() {
        assertEquals("", DayOfWeekToken.canonical(""))
        assertEquals("", DayOfWeekToken.canonical("아무거나"))
    }

    /**
     * 시간표는 평일·토요일·휴일 셋으로 갈린다. 어느 언어로 고른 요일이든 같은
     * 갈래에 떨어져야 한다.
     */
    @Test
    fun `어느 언어로 골라도 같은 시간표 갈래로 간다`() {
        listOf("SAT", "토요일", "Saturday", "土曜日", "星期六").forEach {
            assertEquals("$it", DayType.SATURDAY, DayType.of(it))
        }
        listOf("SUN", "일요일", "Sunday", "日曜日", "星期日").forEach {
            assertEquals("$it", DayType.HOLIDAY, DayType.of(it))
        }
        listOf("WED", "수요일", "Wednesday", "水曜日", "星期三", "").forEach {
            assertEquals("$it", DayType.WEEKDAY, DayType.of(it))
        }
    }
}
