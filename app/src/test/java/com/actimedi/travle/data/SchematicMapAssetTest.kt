package com.actimedi.travle.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * 도식 노선도 자산이 멀쩡한지 본다.
 *
 * 이 자산은 손으로 쓰지 않는다 — `tools/seoul_schematic.py`가 서울교통공사 공식
 * 노선도에서 캐낸다. 캐내는 일이 어긋나는 방식이 고약하다. 파일은 멀쩡하고 앱도
 * 뜨는데 **역이 남의 자리에 앉는다.** 왕십리가 옆 마장의 자리를 차지하면 그 뒤로
 * 회기→중랑→상봉이 한 칸씩 밀리는 식으로, 눈으로 그림을 뜯어보기 전에는 드러나지
 * 않는다.
 *
 * 그 어긋남을 잡는 잣대가 **역은 자기 노선 위에 있다**는 것이다. 자리가 밀리면 역이
 * 선에서 떨어진다. 그래서 여기서는 이름을 맞춰 보는 대신 기하를 본다.
 */
class SchematicMapAssetTest {

    /** 자산에 자리가 담긴 역이 이보다 적으면 캐내는 일이 어딘가 무너진 것이다. */
    private val leastPlaced = 650

    /**
     * 역이 선에서 이만큼까지는 떨어져 있어도 된다.
     *
     * 실제로는 절반이 0.1 안에 있다. 멀어지는 것은 나란히 달리는 두 노선 사이에
     * 놓인 환승역인데, 선 굵기가 29이므로 그 절반쯤 떨어지는 것이 오히려 제자리다.
     * 자리가 한 칸 밀리면 이 값을 훌쩍 넘는다.
     */
    private val furthestFromLine = 60.0

    private val assets: File = sequenceOf(
        File("src/main/assets"),
        File("app/src/main/assets"),
    ).first { it.isDirectory }

    private val json = Json { ignoreUnknownKeys = true }

    private val schematic: SchematicMap =
        json.decodeFromString(File(assets, "schematic_map.json").readText())

    private val network: SubwayNetwork =
        json.decodeFromString(File(assets, "subway_map.json").readText())

    @Test
    fun `역마다 자리 한 칸씩, 순번이 노선망과 맞는다`() {
        // 자리는 이름이 아니라 순번으로 담긴다. 길이가 어긋나면 통째로 밀린다 —
        // 같은 이름의 역이 여럿 있어 이름으로는 밀린 것을 알아챌 수 없다.
        assertEquals(network.stations.size, schematic.points.size)
        val placed = schematic.points.count { it != null }
        assertTrue("자리를 찾은 역이 $placed 개뿐이다", placed >= leastPlaced)
    }

    @Test
    fun `자리는 모두 그림 안에 있다`() {
        assertTrue(schematic.width > 0 && schematic.height > 0)
        schematic.points.forEachIndexed { index, point ->
            if (point == null) return@forEachIndexed
            assertTrue(
                "$index 번 역이 그림 밖에 있다: $point",
                point[0] in 0f..schematic.width && point[1] in 0f..schematic.height,
            )
        }
    }

    @Test
    fun `선은 색과 굵기를 갖추고 있다`() {
        assertTrue("선이 비어 있다", schematic.segments.size > 500)
        assertTrue(
            "쓸 수 없는 선이 섞여 있다",
            schematic.segments.all { it.isUsable && it.width > 0f },
        )
    }

    @Test
    fun `역은 노선 위에 앉아 있다`() {
        val lines = schematic.segments.filter { it.isUsable }
        val strays = schematic.points.withIndex().mapNotNull { (index, point) ->
            if (point == null) return@mapNotNull null
            val gap = lines.minOf { distanceTo(point[0], point[1], it) }
            if (gap > furthestFromLine) index to gap else null
        }
        assertTrue("선에서 떨어진 역: $strays", strays.isEmpty())
    }

    /** 점에서 선 토막까지의 거리. 토막을 벗어나면 가까운 끝점까지로 잰다. */
    private fun distanceTo(x: Float, y: Float, segment: SchematicSegment): Double {
        val (ax, ay) = segment.from
        val (bx, by) = segment.to
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        val along = if (length == 0f) 0f else {
            ((x - ax) * dx + (y - ay) * dy) / length
        }.coerceIn(0f, 1f)
        return hypot((x - ax - along * dx).toDouble(), (y - ay - along * dy).toDouble())
    }
}
