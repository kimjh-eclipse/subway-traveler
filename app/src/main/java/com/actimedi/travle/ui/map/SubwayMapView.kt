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
    projected: List<Offset>,
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    mapped: MappedRoute? = null,
    selectedStation: Int? = null,
    onStationTap: ((Int) -> Unit)? = null,
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
                if (onStationTap == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(projected) {
                        detectTapGestures { tap ->
                            nearestStation(projected, camera, tap, tapSlop)?.let(onStationTap)
                        }
                    }
                },
            ),
    ) {
        val baseStroke = 2f * density
        val routeStroke = 5f * density

        // 1 — the whole network, faded back to context.
        network.lines.forEach { line ->
            val color = lineColors[line.name] ?: AmColor.Ink300
            val alpha = if (mapped == null) 0.75f else 0.30f
            line.paths.forEach { path ->
                drawPolyline(path.map { camera.toScreen(projected[it]) }, color.copy(alpha = alpha), baseStroke)
            }
        }

        // 2 — every station, once they are far enough apart to aim at.
        if (onStationTap != null && camera.scale >= StationDotScaleThreshold) {
            projected.forEach { p ->
                val s = camera.toScreen(p)
                if (s.x in -40f..(size.width + 40f) && s.y in -40f..(size.height + 40f)) {
                    drawCircle(AmColor.White, radius = 3.4f * density, center = s)
                    drawCircle(AmColor.Ink400, radius = 3.4f * density, center = s, style = Stroke(1.2f * density))
                }
            }
        }

        // 3 — the route.
        mapped?.legs?.forEach { leg ->
            val points = leg.stations.map { camera.toScreen(projected[it]) }
            if (leg.isStraightHop) {
                drawDashedHop(points, leg.color, routeStroke, 7f * density)
            } else {
                drawPolyline(points, leg.color, routeStroke)
            }
        }

        val dot = 5.5f * density
        mapped?.stops?.forEach { stop ->
            val p = camera.toScreen(projected[stop.stationIndex])
            drawCircle(AmColor.Navy, radius = dot + 2f * density, center = p)
            drawCircle(AmColor.White, radius = dot, center = p)
            if (stop.isStay) drawCircle(AmColor.Blue, radius = dot * 0.55f, center = p)
        }

        // 4 — the picker's current choice.
        selectedStation?.let { index ->
            val p = camera.toScreen(projected[index])
            drawCircle(AmColor.Blue, radius = 9f * density, center = p)
            drawCircle(AmColor.White, radius = 4f * density, center = p)
        }

        // 5 — labels. Anything that would collide with an already-placed label is
        // dropped, so zooming out thins them out instead of turning to mush.
        val placed = mutableListOf<Rect>()
        selectedStation?.let { index ->
            drawLabelIfVisible(
                measurer, nameOf(index),
                camera.toScreen(projected[index]), density, size, placed, strong = true,
            )
        }
        if (camera.scale >= LabelScaleThreshold) {
            mapped?.stops?.forEach { stop ->
                drawLabelIfVisible(
                    measurer, "${stop.order}. ${nameOf(stop.stationIndex)}",
                    camera.toScreen(projected[stop.stationIndex]), density, size, placed,
                )
            }
        }
        if (onStationTap != null && camera.scale >= StationDotScaleThreshold) {
            // Nearest the middle of the screen wins a contested slot — whatever the
            // user is looking at should keep its name.
            val centre = Offset(size.width / 2f, size.height / 2f)
            network.stations.indices
                .map { it to camera.toScreen(projected[it]) }
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
    projected: List<Offset>,
    camera: MapCameraState,
    tap: Offset,
    slop: Float,
): Int? {
    var best = -1
    var bestDistance = slop
    projected.forEachIndexed { index, p ->
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
fun projectStations(network: SubwayNetwork): List<Offset> {
    if (network.stations.isEmpty()) return emptyList()
    val lat0 = network.stations.sumOf { it.lat } / network.stations.size
    val k = cos(Math.toRadians(lat0)).toFloat()
    val raw = network.stations.map { Offset(it.lon.toFloat() * k, -it.lat.toFloat()) }
    val bounds = boundsOf(raw) ?: return raw
    val span = max(bounds.width, bounds.height).takeIf { it > 0f } ?: return raw
    val f = MapContentSpan / span
    return raw.map { Offset((it.x - bounds.left) * f, (it.y - bounds.top) * f) }
}

fun boundsOf(points: List<Offset>): Rect? {
    if (points.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    points.forEach {
        minX = min(minX, it.x); maxX = max(maxX, it.x)
        minY = min(minY, it.y); maxY = max(maxY, it.y)
    }
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
