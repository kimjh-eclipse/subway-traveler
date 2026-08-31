package com.actimedi.travle.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clipToBounds
import com.actimedi.travle.data.SchematicMap
import com.actimedi.travle.data.MapStyle
import com.actimedi.travle.data.SchematicSegment

const val MinMapScale = 0.35f
const val MaxMapScale = 40f
private const val LabelScaleThreshold = 1.4f
private const val StationDotScaleThreshold = 2.2f

/** Pan/zoom camera over the projected network. */
class MapCameraState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport by mutableStateOf(Size.Zero)
        internal set

    fun frame(target: Rect?) {
        if (viewport.width <= 0f || viewport.height <= 0f || target == null) return
        val pad = 1.24f
        val w = max(target.width, 0.02f) * pad
        val h = max(target.height, 0.02f) * pad
        scale = min(viewport.width / w, viewport.height / h).coerceIn(MinMapScale, MaxMapScale)
        val c = target.center
        offset = Offset(viewport.width / 2 - c.x * scale, viewport.height / 2 - c.y * scale)
    }

    /** The scale [frame] would choose for this target, without applying it. */
    fun fitScaleFor(target: Rect?): Float {
        if (viewport.width <= 0f || viewport.height <= 0f || target == null) return 1f
        val w = max(target.width, 0.02f) * 1.24f
        val h = max(target.height, 0.02f) * 1.24f
        return min(viewport.width / w, viewport.height / h)
    }

    /** Puts a single point in the middle of the viewport at a given scale. */
    fun centerOn(point: Offset, scale: Float) {
        if (viewport.width <= 0f || viewport.height <= 0f) return
        this.scale = scale.coerceIn(MinMapScale, MaxMapScale)
        offset = Offset(
            viewport.width / 2 - point.x * this.scale,
            viewport.height / 2 - point.y * this.scale,
        )
    }

    fun transform(pan: Offset, zoom: Float, centroid: Offset) {
        val next = (scale * zoom).coerceIn(MinMapScale, MaxMapScale)
        val applied = next / scale
        offset = (offset - centroid) * applied + centroid + pan
        scale = next
    }

    fun toScreen(p: Offset) = Offset(p.x * scale + offset.x, p.y * scale + offset.y)
    fun toContent(p: Offset) = Offset((p.x - offset.x) / scale, (p.y - offset.y) / scale)
}

@Composable
fun rememberMapCameraState() = remember { MapCameraState() }

/**
 * The whole network, optionally with a route drawn on it and optionally tappable.
 *
 * Used both by the route map and by the editor's station picker, so the drawing
 * and the hit testing stay in one place.
 */
@Composable
fun SubwayMapView(
    network: SubwayNetwork,
    /**
     * 순번대로 늘어놓은 역 자리. 도식도에 자리가 없는 역은 null이고, 그런 역과
     * 그 역에 닿는 선은 그리지 않는다 — 엉뚱한 데 찍느니 비우는 편이 낫다.
     */
    projected: List<Offset?>,
    /** 도식일 때 배경으로 깔 선. 비어 있으면 역끼리 이어 그린다. */
    backdrop: List<PlacedStroke> = emptyList(),
    /** 도식일 때 맨 밑에 깔 강. */
    waters: List<PlacedWater> = emptyList(),
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    mapped: MappedRoute? = null,
    selectedStation: Int? = null,
    /**
     * 이미 경로에 담은 역. 순서대로 번호를 붙여 찍는다.
     *
     * 지도에서 경로를 만들 때 지금까지 무엇을 담았는지 보이지 않으면 눈을 감고
     * 찍는 것과 같다.
     */
    marked: List<Int> = emptyList(),
    /**
     * 역마다 점과 이름을 찍을지. 충분히 확대했을 때만 나온다.
     *
     * 예전에는 `onStationTap != null`로 갈랐다. 누를 수 있는 화면에서만 이름이
     * 필요할 것이라 여겼기 때문인데, 그러다 보니 **노선도를 그냥 보는 화면에서
     * 역 이름이 하나도 안 나왔다**. 누를 수 있는 것과 읽을 수 있는 것은 다른
     * 이야기다.
     */
    labelAllStations: Boolean = false,
    onStationTap: ((Int) -> Unit)? = null,
    /**
     * 역이 아닌 빈 자리를 눌렀을 때. 고른 것을 놓는 자리다.
     *
     * 지금까지 빈 자리를 누르면 아무 일도 없었다. 역을 골라 시간표를 펴 놓고 나면
     * 접을 방법이 없어, 다른 역을 누르거나 탭을 옮겨야 했다.
     */
    onEmptyTap: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    // 그리기 람다는 @Composable 이 아니라 자원을 읽을 수 없다 — 이름을 미리 푼다.
    val locale = LocalConfiguration.current.locales[0]
    val nameOf = remember(network, locale) {
        { index: Int -> network.displayName(network.stations[index].name, locale) }
    }
    val lineColors = remember(network) { network.lines.associate { it.name to parseColor(it.colour) } }
    val tapSlop = 22f * density

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // 컴포즈는 그리기를 자리 밖으로 나가도 자르지 않는다. 지도 가장자리의
            // 역 이름이 위쪽 안내 문구를 덮고 있었다.
            .clipToBounds()
            .background(RouteColor.TabTrack)
            // Measured in layout, not during draw — writing state while drawing
            // does not reliably restart the framing effect.
            .onSizeChanged { camera.viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.transform(pan, zoom, centroid)
                }
            }
            .then(
                if (onStationTap == null && onEmptyTap == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(projected) {
                        detectTapGestures { tap ->
                            val at = nearestStation(projected, camera, tap, tapSlop)
                            if (at != null) onStationTap?.invoke(at) else onEmptyTap?.invoke()
                        }
                    }
                },
            ),
    ) {
        val baseStroke = 2f * density
        val routeStroke = 5f * density

        // 1 — the whole network, faded back to context.
        // 도식은 흐리게 깔면 형태가 무너진다. 색이 자료에 들어 있어 그대로 살린다.
        val alpha = if (mapped == null) {
            0.75f
        } else if (backdrop.isNotEmpty()) {
            0.5f
        } else {
            0.30f
        }
        if (backdrop.isNotEmpty()) {
            // 강이 맨 밑이다. 한강이 노선은 아니지만, 그려야 서울로 읽힌다.
            // 노선처럼 흐리게 하지 않는다 — 원래 옅은 색이라 더 빼면 사라진다.
            waters.forEach { water ->
                val path = Path()
                water.outline.forEachIndexed { i, point ->
                    val at = camera.toScreen(point)
                    if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                }
                path.close()
                drawPath(path, water.colour)
            }
            // 도식일 때는 그림에 들어 있던 선을 그대로 쓴다. 역끼리 직선으로 이으면
            // 역이 아닌 자리의 꺾임이 사라져 도식처럼 보이지 않는다.
            backdrop.forEach { stroke ->
                val path = Path()
                stroke.points.forEachIndexed { i, point ->
                    val at = camera.toScreen(point)
                    if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                }
                drawPath(
                    path = path,
                    color = stroke.colour.copy(alpha = alpha),
                    style = Stroke(
                        width = stroke.width * camera.scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        } else {
        network.lines.forEach { line ->
            val color = lineColors[line.name] ?: AmColor.Ink300
            line.paths.forEach { path ->
                // 자리를 모르는 역에서 선을 끊는다. 이어 버리면 없는 구간이 생긴다.
                path.split { projected[it] == null }.forEach { run ->
                    drawPolyline(
                        run.map { camera.toScreen(projected[it]!!) },
                        color.copy(alpha = alpha),
                        baseStroke,
                    )
                }
            }
        }
        }

        // 2 — every station, once they are far enough apart to aim at.
        if (labelAllStations && camera.scale >= StationDotScaleThreshold) {
            projected.filterNotNull().forEach { p ->
                val s = camera.toScreen(p)
                if (s.x in -40f..(size.width + 40f) && s.y in -40f..(size.height + 40f)) {
                    drawCircle(AmColor.White, radius = 3.4f * density, center = s)
                    drawCircle(AmColor.Ink400, radius = 3.4f * density, center = s, style = Stroke(1.2f * density))
                }
            }
        }

        // 3 — the route.
        mapped?.legs?.forEach { leg ->
            val points = leg.stations.mapNotNull { projected[it]?.let(camera::toScreen) }
            if (leg.isStraightHop) {
                drawDashedHop(points, leg.color, routeStroke, 7f * density)
            } else {
                drawPolyline(points, leg.color, routeStroke)
            }
        }

        val dot = 5.5f * density
        mapped?.stops?.forEach { stop ->
            val p = camera.toScreen(projected[stop.stationIndex] ?: return@forEach)
            drawCircle(AmColor.Navy, radius = dot + 2f * density, center = p)
            drawCircle(AmColor.White, radius = dot, center = p)
            if (stop.isStay) drawCircle(AmColor.Blue, radius = dot * 0.55f, center = p)
        }

        // 3b — 지도에서 담아 가는 중인 역. 번호가 곧 순서다.
        marked.forEachIndexed { order, index ->
            val p = camera.toScreen(projected[index] ?: return@forEachIndexed)
            drawCircle(AmColor.Navy, radius = 8.5f * density, center = p)
            drawCircle(AmColor.White, radius = 5f * density, center = p)
            val text = measurer.measure(
                "${order + 1}",
                TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AmColor.Navy),
            )
            drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
        }

        // 4 — the picker's current choice.
        selectedStation?.let { index ->
            val p = camera.toScreen(projected[index] ?: return@let)
            drawCircle(AmColor.Blue, radius = 9f * density, center = p)
            drawCircle(AmColor.White, radius = 4f * density, center = p)
        }

        // 5 — labels. Anything that would collide with an already-placed label is
        // dropped, so zooming out thins them out instead of turning to mush.
        val placed = mutableListOf<Rect>()
        selectedStation?.let { index ->
            drawLabelIfVisible(
                measurer, nameOf(index),
                camera.toScreen(projected[index] ?: return@let), density, size, placed, strong = true,
            )
        }
        if (camera.scale >= LabelScaleThreshold) {
            mapped?.stops?.forEach { stop ->
                drawLabelIfVisible(
                    measurer, "${stop.order}. ${nameOf(stop.stationIndex)}",
                    camera.toScreen(projected[stop.stationIndex] ?: return@forEach), density, size, placed,
                )
            }
        }
        if (labelAllStations && camera.scale >= StationDotScaleThreshold) {
            // Nearest the middle of the screen wins a contested slot — whatever the
            // user is looking at should keep its name.
            val centre = Offset(size.width / 2f, size.height / 2f)
            network.stations.indices
                .mapNotNull { i -> projected[i]?.let { i to camera.toScreen(it) } }
                .filter { (_, p) ->
                    p.x > -LabelMargin && p.y > -60f &&
                        p.x < size.width + LabelMargin && p.y < size.height + 60f
                }
                .sortedBy { (_, p) -> hypot(p.x - centre.x, p.y - centre.y) }
                .forEach { (index, p) ->
                    drawLabelIfVisible(
                        measurer, nameOf(index), p, density, size, placed,
                    )
                }
        }
    }
}

private fun nearestStation(
    /**
     * 순번대로 늘어놓은 역 자리. 도식도에 자리가 없는 역은 null이고, 그런 역과
     * 그 역에 닿는 선은 그리지 않는다 — 엉뚱한 데 찍느니 비우는 편이 낫다.
     */
    projected: List<Offset?>,
    camera: MapCameraState,
    tap: Offset,
    slop: Float,
): Int? {
    var best = -1
    var bestDistance = slop
    projected.forEachIndexed { index, point ->
        val p = point ?: return@forEachIndexed
        val s = camera.toScreen(p)
        val d = hypot(s.x - tap.x, s.y - tap.y)
        if (d < bestDistance) {
            bestDistance = d
            best = index
        }
    }
    return best.takeIf { it >= 0 }
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A ride we could not place on a known line — dashed, so it reads as approximate. */
private fun DrawScope.drawDashedHop(points: List<Offset>, color: Color, width: Float, dash: Float) {
    points.zipWithNext { a, b ->
        val d = b - a
        val len = hypot(d.x, d.y)
        if (len <= 0f) return@zipWithNext
        var t = 0f
        while (t < len) {
            drawLine(color, a + d * (t / len), a + d * (min(t + dash, len) / len), width, StrokeCap.Round)
            t += dash * 2
        }
    }
}

private fun DrawScope.drawLabelIfVisible(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    density: Float,
    canvas: Size,
    placed: MutableList<Rect>,
    strong: Boolean = false,
) {
    // 여유를 넉넉히 잡는다. `Gyeongin Nat'l Univ. of Education` 같은 이름은 한글
    // 역명의 서너 배라, 좁게 자르면 화면에 들어와야 할 라벨이 먼저 사라진다.
    if (at.x < -LabelMargin || at.y < -60f ||
        at.x > canvas.width + LabelMargin || at.y > canvas.height + 60f
    ) return
    val layout = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = if (strong) 12.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (strong) AmColor.White else AmColor.Navy,
        ),
    )
    val pad = 3f * density
    val gap = 10f * density
    // Flip to the left of the dot when the label would run off the right edge.
    val wantsFlip = at.x + gap + layout.size.width + pad > canvas.width
    val left = if (wantsFlip) at.x - gap - layout.size.width else at.x + gap
    val topLeft = Offset(left, at.y - layout.size.height / 2f)
    val box = Rect(
        topLeft.x - pad,
        topLeft.y - pad,
        topLeft.x + layout.size.width + pad,
        topLeft.y + layout.size.height + pad,
    )
    if (box.left < 0f || placed.any { it.overlaps(box) }) return
    placed += box

    drawRoundRect(
        color = if (strong) AmColor.Blue else AmColor.White.copy(alpha = 0.88f),
        topLeft = Offset(box.left, box.top),
        size = Size(box.width, box.height),
        cornerRadius = CornerRadius(4f * density),
    )
    drawText(layout, topLeft = topLeft)
}

/** The projected network spans this many units on its longer side. */
const val MapContentSpan = 1000f

/**
 * Equirectangular projection — accurate enough over a 100 km box, and cheap to
 * invert for hit testing. Longitude is scaled by cos(latitude) so the city keeps
 * its real proportions.
 *
 * The result is normalised to a [MapContentSpan]-unit box. Leaving it in degrees
 * would make the camera's scale limits meaningless: the network is only ~0.5°
 * wide, so fitting it to a phone needs a factor of ~2000.
 */
/**
 * 역 자리를 화면 좌표로.
 *
 * 도식이든 지리든 결과는 같은 모양 — 순번대로 늘어놓은, [MapContentSpan] 상자에
 * 맞춘 점들이다. 그래서 그리는 쪽은 어느 모양인지 몰라도 된다.
 *
 * 도식에 자리가 없는 역은 지리 좌표를 도식 상자에 욱여넣지 않는다. 엉뚱한 데
 * 찍히느니 안 그리는 편이 낫다 — 그런 역은 null로 남는다.
 */
fun projectStations(
    network: SubwayNetwork,
    schematic: SchematicMap = SchematicMap(),
    style: MapStyle = MapStyle.GEOGRAPHIC,
): List<Offset?> {
    if (style == MapStyle.SCHEMATIC && !schematic.isEmpty) {
        val raw = network.stations.indices.map { i ->
            schematic.at(i)?.let { (x, y) -> Offset(x, y) }
        }
        val known = raw.filterNotNull()
        val bounds = boundsOf(known) ?: return raw
        val span = max(bounds.width, bounds.height).takeIf { it > 0f } ?: return raw
        val f = MapContentSpan / span
        return raw.map { it?.let { p -> Offset((p.x - bounds.left) * f, (p.y - bounds.top) * f) } }
    }
    return projectGeographically(network)
}

private fun projectGeographically(network: SubwayNetwork): List<Offset> {
    if (network.stations.isEmpty()) return emptyList()
    val lat0 = network.stations.sumOf { it.lat } / network.stations.size
    val k = cos(Math.toRadians(lat0)).toFloat()
    val raw = network.stations.map { Offset(it.lon.toFloat() * k, -it.lat.toFloat()) }
    val bounds = boundsOf(raw) ?: return raw
    val span = max(bounds.width, bounds.height).takeIf { it > 0f } ?: return raw
    val f = MapContentSpan / span
    return raw.map { Offset((it.x - bounds.left) * f, (it.y - bounds.top) * f) }
}

fun boundsOf(points: List<Offset?>): Rect? {
    if (points.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var any = false
    points.filterNotNull().forEach {
        any = true
        minX = min(minX, it.x); maxX = max(maxX, it.x)
        minY = min(minY, it.y); maxY = max(maxY, it.y)
    }
    if (!any) return null
    return Rect(minX, minY, maxX, maxY)
}

fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(AmColor.Ink300)

/**
 * 화면 밖 라벨을 자르는 여유(px).
 *
 * 점이 화면 밖에 있어도 라벨은 안쪽으로 뻗어 들어올 수 있다. 한글 역명은 서너
 * 글자라 220이면 넉넉했지만 라틴 표기는 그보다 훨씬 길어, 좁게 두면 보여야 할
 * 이름이 먼저 사라진다.
 */
private const val LabelMargin = 520f

/**
 * [drop]인 자리에서 끊어 이어진 토막들로. 자리를 모르는 역을 건너뛰고 선을 그으면
 * 없는 구간이 생기므로, 아예 끊어 놓는다.
 */
private fun List<Int>.split(drop: (Int) -> Boolean): List<List<Int>> {
    val runs = mutableListOf<List<Int>>()
    var run = mutableListOf<Int>()
    forEach { item ->
        if (drop(item)) {
            if (run.size > 1) runs += run
            run = mutableListOf()
        } else {
            run += item
        }
    }
    if (run.size > 1) runs += run
    return runs
}


/**
 * 화면에 그릴 준비가 끝난 도식 선 한 획 — 이어지는 꺾은선.
 *
 * 토막마다 따로 그으면 안 된다. 반투명으로 그릴 때 둥근 끝단이 겹치는 자리마다
 * 두 번 칠해져 진해지고, 선이 비늘처럼 얼룩덜룩해진다. 이어지는 토막을 한 획으로
 * 묶어 한 번에 그리면 겹침이 한 획 안의 일이 되어 얼룩이 사라진다.
 */
data class PlacedStroke(val points: List<Offset>, val colour: Color, val width: Float)

/** 화면에 그릴 준비가 끝난 물 한 덩이. */
data class PlacedWater(val outline: List<Offset>, val colour: Color)

/** 도식도의 강을 역 자리와 같은 자로 옮긴다. [placeSegments]와 같은 이유, 같은 셈. */
fun placeWaters(network: SubwayNetwork, schematic: SchematicMap): List<PlacedWater> {
    if (schematic.isEmpty || schematic.waters.isEmpty()) return emptyList()
    val raw = network.stations.indices.mapNotNull { schematic.at(it)?.let { (x, y) -> Offset(x, y) } }
    val bounds = boundsOf(raw) ?: return emptyList()
    val span = max(bounds.width, bounds.height).takeIf { it > 0f } ?: return emptyList()
    val f = MapContentSpan / span

    return schematic.waters.filter { it.isUsable }.map { water ->
        PlacedWater(
            outline = water.points.chunked(2).map {
                Offset((it[0] - bounds.left) * f, (it[1] - bounds.top) * f)
            },
            colour = parseColor(water.colour),
        )
    }
}

/**
 * 도식도의 선을 역 자리와 **같은 자로** 옮긴다.
 *
 * 둘이 어긋나면 역이 선 위에 얹히지 않는다. 그래서 자리를 옮기는 셈을 한 군데서만
 * 한다 — 역 자리로 잡은 틀을 선에도 그대로 쓴다.
 */
fun placeSegments(network: SubwayNetwork, schematic: SchematicMap): List<PlacedStroke> {
    if (schematic.isEmpty || schematic.segments.isEmpty()) return emptyList()
    val raw = network.stations.indices.mapNotNull { schematic.at(it)?.let { (x, y) -> Offset(x, y) } }
    val bounds = boundsOf(raw) ?: return emptyList()
    val span = max(bounds.width, bounds.height).takeIf { it > 0f } ?: return emptyList()
    val f = MapContentSpan / span

    fun place(v: List<Float>) = Offset((v[0] - bounds.left) * f, (v[1] - bounds.top) * f)

    // 자료는 토막의 나열이고, 이어지는 토막은 끝점을 공유한다. 끝이 맞닿고 색과
    // 굵기가 같은 동안 한 획으로 잇는다.
    val strokes = mutableListOf<PlacedStroke>()
    var run = mutableListOf<Offset>()
    var colour = ""
    var width = 0f
    schematic.segments.filter { it.isUsable }.forEach { segment ->
        val from = place(segment.from)
        val to = place(segment.to)
        val chains = run.isNotEmpty() && run.last() == from &&
            colour == segment.colour && width == segment.width
        if (!chains) {
            if (run.size > 1) strokes += PlacedStroke(run, parseColor(colour), width * f)
            run = mutableListOf(from)
            colour = segment.colour
            width = segment.width
        }
        run += to
    }
    if (run.size > 1) strokes += PlacedStroke(run, parseColor(colour), width * f)
    return strokes
}
