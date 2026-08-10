package com.actimedi.travle.ui.theme

import com.actimedi.travle.data.RouteSegment
import com.actimedi.travle.data.SeoulOneDayRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Line colours are no longer stored, so the name lookup has to reproduce every
 * colour the original design assigned.
 */
class LineColorTest {

    @Test
    fun `the seed route keeps the colours the mockup gave it`() {
        val expected = mapOf(
            "26-2번" to LineColor.Bus,
            "26 / 26-2번" to LineColor.Bus,
            "신분당선" to LineColor.Sinbundang,
            "1호선" to LineColor.Line1,
            "2호선" to LineColor.Line2,
            "4호선" to LineColor.Line4,
            "5호선" to LineColor.Line5,
            "6호선" to LineColor.Line6,
            "7호선" to LineColor.Line7,
            "수인분당선" to LineColor.Suin,
            "GTX-A" to LineColor.Gtx,
            "공항철도" to LineColor.Arex,
            "인천1호선" to LineColor.Incheon1,
            "서해선" to LineColor.Seohae,
        )

        val usedLines = SeoulOneDayRoute.segments
            .filterIsInstance<RouteSegment.Move>()
            .map { it.line }
            .toSet()
        assertEquals(expected.keys, usedLines)

        expected.forEach { (line, color) ->
            assertEquals("$line 색상", color, lineColorFor(line))
        }
    }

    @Test
    fun `인천1호선 is not swallowed by the 1호선 rule`() {
        assertEquals(LineColor.Incheon1, lineColorFor("인천1호선"))
        assertEquals(LineColor.Line1, lineColorFor("1호선"))
    }

    @Test
    fun `an unrecognised line falls back instead of throwing`() {
        assertEquals(LineColor.Unknown, lineColorFor("Line2"))
        assertEquals(LineColor.Unknown, lineColorFor(""))
    }
}
