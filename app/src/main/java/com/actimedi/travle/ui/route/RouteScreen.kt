package com.actimedi.travle.ui.route

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.RouteFilter
import com.actimedi.travle.data.RouteSummary
import com.actimedi.travle.data.SeoulOneDayRoute
import com.actimedi.travle.data.FareEstimate
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.estimateFare
import com.actimedi.travle.data.formatWon
import com.actimedi.travle.data.TimelineEntry
import com.actimedi.travle.data.filterBy
import com.actimedi.travle.data.formatClockSpan
import com.actimedi.travle.data.summarize
import com.actimedi.travle.data.toTimeline
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.ui.theme.TravleTheme
import kotlin.math.min

private val BrandEasing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RouteScreenPreview() {
    TravleTheme {
        RouteScreen(route = SeoulOneDayRoute, onCreateRoute = {})
    }
}

/** How much of the header survives a full collapse. */
private const val CollapsedRatio = 0.25f

@Composable
fun RouteScreen(
    route: Route,
    onCreateRoute: () -> Unit,
    modifier: Modifier = Modifier,
    network: SubwayNetwork = SubwayNetwork(),
    /** Null until the bundled network has loaded. */
    onOpenMap: (() -> Unit)? = null,
) {
    val summary = remember(route) { route.summarize() }
    val timeline = remember(route) { route.toTimeline() }
    val fare = remember(route, network) { route.estimateFare(network) }

    var filter by rememberSaveable(route.id) { mutableStateOf(RouteFilter.ALL) }
    var expandedIndex by rememberSaveable(route.id) { mutableStateOf(-1) }
    val entries = remember(timeline, filter) { timeline.filterBy(filter) }

    // Natural height of the header content below the status bar, measured once.
    val expandedContentPx = remember { mutableFloatStateOf(0f) }
    // How far the header is currently collapsed, in px, within [0, maxCollapse].
    val collapsePx = remember { mutableFloatStateOf(0f) }

    // exitUntilCollapsed: scrolling down eats the header before the list moves;
    // it only re-expands once the list is back at the top.
    val nestedScroll = remember {
        object : NestedScrollConnection {
            private fun maxCollapse() = expandedContentPx.floatValue * (1f - CollapsedRatio)

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val max = maxCollapse()
                if (max <= 0f || available.y >= 0f) return Offset.Zero
                val taken = min(-available.y, max - collapsePx.floatValue)
                if (taken <= 0f) return Offset.Zero
                collapsePx.floatValue += taken
                return Offset(0f, -taken)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val given = min(available.y, collapsePx.floatValue)
                if (given <= 0f) return Offset.Zero
                collapsePx.floatValue -= given
                return Offset(0f, given)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmColor.SurfacePage)
                .nestedScroll(nestedScroll),
        ) {
            RouteHeader(
                title = route.title,
                startTime = route.startTime.format(),
                dayOfWeek = route.dayOfWeek,
                summary = summary,
                expandedContentPx = expandedContentPx.floatValue,
                collapsePx = collapsePx.floatValue,
                onContentMeasured = { expandedContentPx.floatValue = it },
            )
            FilterTabs(selected = filter, onSelect = { filter = it })
            HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
            RouteTimeline(
                entries = entries,
                summary = summary,
                expandedIndex = expandedIndex,
                onToggle = { index -> expandedIndex = if (expandedIndex == index) -1 else index },
                network = network,
                fare = fare,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onOpenMap?.let { open ->
                Text(
                    text = stringResource(R.string.open_map),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    color = AmColor.Blue,
                    modifier = Modifier
                        .shadow(8.dp, CircleShape, spotColor = RouteColor.CardShadow)
                        .clip(CircleShape)
                        .background(AmColor.White)
                        .clickable(onClick = open)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                )
            }
            NewRouteButton(onClick = onCreateRoute)
        }
    }
}

/** Floating entry point to the editor. The mockup has no FAB slot, so it sits
 *  above the list rather than inside the header, which collapses away. */
@Composable
fun NewRouteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(10.dp, CircleShape, spotColor = RouteColor.CardShadow)
            .clip(CircleShape)
            .background(AmColor.Blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "+",
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            color = AmColor.White,
        )
        Text(
            text = stringResource(R.string.new_route),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            color = AmColor.White,
        )
    }
}

@Composable
private fun RouteHeader(
    title: String,
    startTime: String,
    dayOfWeek: String,
    summary: RouteSummary,
    expandedContentPx: Float,
    collapsePx: Float,
    onContentMeasured: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val maxCollapse = expandedContentPx * (1f - CollapsedRatio)
    val fraction = if (maxCollapse > 0f) (collapsePx / maxCollapse).coerceIn(0f, 1f) else 0f

    // The full header is gone well before the collapse finishes, so the compact
    // row has clear air to fade into.
    val expandedAlpha = (1f - fraction * 1.9f).coerceIn(0f, 1f)
    val compactAlpha = ((fraction - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to RouteColor.HeaderTop,
                    0.52f to RouteColor.HeaderMid,
                    1f to RouteColor.HeaderBottom,
                ),
            ),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandedContentPx > 0f) {
                        Modifier.height(with(density) { (expandedContentPx - collapsePx).toDp() })
                    } else {
                        Modifier
                    },
                )
                .clipToBounds(),
        ) {
            ExpandedHeaderContent(
                title = title,
                startTime = startTime,
                dayOfWeek = dayOfWeek,
                summary = summary,
                alpha = expandedAlpha,
                // Slight parallax so the content leaves faster than the box shrinks.
                offsetY = -collapsePx * 0.35f,
                onContentMeasured = onContentMeasured,
            )
            CompactHeaderContent(
                title = title,
                summary = summary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = compactAlpha },
            )
        }
    }
}

/** Route title plus total duration — what survives a full collapse. */
@Composable
private fun CompactHeaderContent(
    title: String,
    summary: RouteSummary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title.replace('\n', ' '),
            fontFamily = SuiteFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.01).em,
            color = AmColor.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatClockSpan(summary.totalMinutes),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            color = AmColor.White,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .background(RouteColor.HeaderChipFill)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ExpandedHeaderContent(
    title: String,
    startTime: String,
    dayOfWeek: String,
    summary: RouteSummary,
    alpha: Float,
    offsetY: Float,
    onContentMeasured: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY
            }
            // Lets the content keep its natural height while the parent shrinks.
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .onSizeChanged { onContentMeasured(it.height.toFloat()) }
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$startTime 출발",
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.02.em,
                color = AmColor.White.copy(alpha = 0.85f),
            )
            Text(
                text = dayOfWeek,
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.02.em,
                color = AmColor.White.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.eyebrow),
            fontFamily = SuiteFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            letterSpacing = 0.11.em,
            color = RouteColor.HeaderEyebrow,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontFamily = SuiteFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 27.sp,
            lineHeight = 33.75.sp,
            letterSpacing = (-0.01).em,
            color = AmColor.White,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(
                label = stringResource(R.string.stat_total),
                value = formatClockSpan(summary.totalMinutes),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = stringResource(R.string.stat_move),
                value = formatClockSpan(summary.movingMinutes),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = stringResource(R.string.stat_stay),
                value = formatClockSpan(summary.stayMinutes),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(RouteColor.HeaderChipFill)
            .border(1.dp, RouteColor.HeaderChipLine, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.HeaderStatLabel,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 17.sp,
            color = AmColor.White,
        )
    }
}

@Composable
private fun FilterTabs(selected: RouteFilter, onSelect: (RouteFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmColor.SurfacePage)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(RouteColor.TabTrack)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterTab(R.string.filter_all, RouteFilter.ALL, selected, onSelect, Modifier.weight(1f))
            FilterTab(R.string.filter_move, RouteFilter.MOVE, selected, onSelect, Modifier.weight(1f))
            FilterTab(R.string.filter_stay, RouteFilter.STAY, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterTab(
    labelRes: Int,
    filter: RouteFilter,
    selected: RouteFilter,
    onSelect: (RouteFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = filter == selected
    val spec = tween<Color>(durationMillis = 140, easing = BrandEasing)
    val background by animateColorAsState(
        targetValue = if (isSelected) AmColor.Blue else Color.Transparent,
        animationSpec = spec,
        label = "tabBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) AmColor.White else RouteColor.TabInactiveText,
        animationSpec = spec,
        label = "tabText",
    )

    Text(
        text = stringResource(labelRes),
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 12.5.sp,
        color = textColor,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable { onSelect(filter) }
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun RouteTimeline(
    entries: List<TimelineEntry>,
    summary: RouteSummary,
    expandedIndex: Int,
    onToggle: (Int) -> Unit,
    network: SubwayNetwork,
    fare: FareEstimate,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(AmColor.SurfacePage),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
    ) {
        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.empty_filtered),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = RouteColor.StayLabel,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        items(items = entries, key = { it.index }) { entry ->
            TimelineRow(
                entry = entry,
                expanded = expandedIndex == entry.index,
                onToggle = { onToggle(entry.index) },
                network = network,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            FinishCard(summary, fare)
            // Clears the floating 새 경로 button.
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun FinishCard(summary: RouteSummary, fare: FareEstimate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RouteColor.TabTrack)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AmColor.Navy),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.finish_mark),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                color = AmColor.White,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${summary.finishTime.format()} · ${summary.finishPlace} 도착",
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                color = AmColor.Navy,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "환승 ${summary.transferCount}회 · 정거장 ${summary.legCount}구간 · " +
                    "체류 ${summary.stayCount}곳",
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                lineHeight = 17.5.sp,
                color = RouteColor.StayLabel,
            )

            if (!fare.isEmpty) {
                Spacer(Modifier.height(8.dp))
                FareLine(fare)
            }
        }
    }
}

/**
 * 하루 지하철 요금. 영업거리표가 없어 좌표로 근사하므로 '예상'을 붙인다.
 * 체류할 때마다 개찰구를 나가므로 승차 횟수가 곧 요금이 부과된 횟수다.
 */
@Composable
private fun FareLine(fare: FareEstimate) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = formatWon(fare.total),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            color = AmColor.Blue,
        )
        Text(
            text = stringResource(R.string.fare_estimated),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            color = RouteColor.WaitText,
            modifier = Modifier
                .clip(CircleShape)
                .background(RouteColor.WaitFill)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Text(
            text = buildString {
                append(stringResource(R.string.fare_rides, fare.rideCount))
                if (fare.skippedLines.isNotEmpty()) {
                    append(" · ")
                    append(stringResource(R.string.fare_excluded, fare.skippedLines.joinToString(", ")))
                }
            },
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = RouteColor.StayLabel,
        )
    }
}
