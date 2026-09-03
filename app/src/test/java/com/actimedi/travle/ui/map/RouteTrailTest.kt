package com.actimedi.travle.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 경로를 그림의 선을 따라 그리는 규칙.
 *
 * 역끼리 직선으로 이으면 도식도가 역이 아닌 자리에서 꺾는 구간에서 굵은 경로선이
 * 그림을 벗어난다. 이을 길이 있으면 따라가고, 없으면 직선으로 둔다.
 */
class RouteTrailTest {

    //  0 ──┐
    //      └── 1 ── 2      (0↔1 은 꺾이고, 1↔2 는 곧다)
    private val projected = listOf(
        Offset(0f, 0f),      // 0
        Offset(10f, 10f),    // 1
        Offset(20f, 10f),    // 2
    )
    private val bend = listOf(Offset(0f, 0f), Offset(0f, 10f), Offset(10f, 10f))
    private val runs = mapOf(runKey(0, 1) to bend)

    private fun leg(vararg stations: Int) =
        MappedLeg("2호선", stations.toList(), Color.Red, isStraightHop = false)

    @Test
    fun `이을 길이 있으면 그 길을 따라간다`() {
        assertEquals(bend, leg(0, 1).trail(projected, runs))
    }

    @Test
    fun `길이 없는 구간은 직선으로 둔다`() {
        assertEquals(listOf(projected[1], projected[2]), leg(1, 2).trail(projected, runs))
    }

    /** 자산은 `u < v` 로만 담긴다. 반대로 지날 때 뒤집지 않으면 선이 되돌아간다. */
    @Test
    fun `거꾸로 지나면 길도 거꾸로 쓴다`() {
        assertEquals(bend.reversed(), leg(1, 0).trail(projected, runs))
    }

    /** 이은 자리에서 같은 점이 두 번 들어가면 획이 끊겨 보인다. */
    @Test
    fun `구간을 이을 때 겹치는 점은 한 번만 넣는다`() {
        assertEquals(bend + projected[2], leg(0, 1, 2).trail(projected, runs))
    }

    @Test
    fun `자리를 모르는 역은 건너뛴다`() {
        val holes = listOf(Offset(0f, 0f), null, Offset(20f, 10f))
        assertEquals(emptyList<Offset>(), leg(0, 1).trail(holes, emptyMap()))
        assertEquals(listOf(Offset(0f, 0f)), leg(0).trail(holes, emptyMap()))
    }

    /** 순번을 뒤집어도 같은 열쇠여야 양방향에서 찾을 수 있다. */
    @Test
    fun `열쇠는 순서를 가리지 않는다`() {
        assertEquals(runKey(3, 9), runKey(9, 3))
    }
}
