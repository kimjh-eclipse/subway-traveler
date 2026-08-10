package com.actimedi.travle.ui.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.actimedi.travle.R
import com.actimedi.travle.data.RouteSegment
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.TimelineEntry
import com.actimedi.travle.data.formatDuration
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.lineColorFor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily

private val TimeColumnWidth = 46.dp
private val RailColumnWidth = 34.dp

/** Mirrors the mockup: `max(30, min(96, 26 + minutes * 0.85))`. */
private fun railHeightFor(minutes: Int): Dp =
    (26f + minutes * 0.85f).coerceIn(30f, 96f).dp

@Composable
fun TimelineRow(
    entry: TimelineEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    network: SubwayNetwork = SubwayNetwork(),
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimeColumn(
            top = entry.segment.start.format(),
            bottom = entry.segment.end.format(),
        )
        Spacer(Modifier.width(8.dp))
        RailColumn(entry)
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp),
        ) {
            when (val segment = entry.segment) {
                is RouteSegment.Move -> MoveRow(segment)
                is RouteSegment.Stay -> StayCard(
                    segment = segment,
                    entry = entry,
                    expanded = expanded,
                    onToggle = onToggle,
                    network = network,
                )
            }
            if (entry.transferWaitMinutes > 0) {
                Spacer(Modifier.height(6.dp))
                WaitChip(entry.transferWaitMinutes)
            }
        }
    }
}

@Composable
private fun TimeColumn(top: String, bottom: String) {
    Column(
        modifier = Modifier.width(TimeColumnWidth).padding(top = 1.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = top,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            color = RouteColor.TimeStrong,
            textAlign = TextAlign.End,
        )
        Text(
            text = bottom,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.5.sp,
            color = RouteColor.TimeWeak,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RailColumn(entry: TimelineEntry) {
    Column(
        modifier = Modifier.width(RailColumnWidth).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val segment = entry.segment) {
            is RouteSegment.Move -> {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .heightIn(min = railHeightFor(segment.minutes))
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(lineColorFor(segment.line))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                )
            }

            is RouteSegment.Stay -> {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                        .shadow(3.dp, CircleShape, spotColor = RouteColor.CardShadow)
                        .clip(CircleShape)
                        .background(AmColor.White)
                        .border(4.dp, AmColor.Navy, CircleShape),
                )
                DashedRail(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(4.dp)
                        .weight(1f),
                )
            }
        }
    }
}

/** The `repeating-linear-gradient` dashed connector, drawn as a dashed stroke. */
@Composable
private fun DashedRail(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.heightIn(min = 8.dp)) {
        val dash = 5.dp.toPx()
        drawLine(
            color = RouteColor.DashRail,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
    }
}

@Composable
private fun MoveRow(segment: RouteSegment.Move) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(RouteColor.MoveRowFill)
            .padding(start = 9.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = segment.line,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
            color = AmColor.White,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .background(lineColorFor(segment.line))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        Text(
            text = segment.destination,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = AmColor.Black,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(segment.minutes),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            color = RouteColor.MoveDuration,
            maxLines = 1,
        )
    }
}

@Composable
private fun StayCard(
    segment: RouteSegment.Stay,
    entry: TimelineEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    network: SubwayNetwork,
) {
    val shape = RoundedCornerShape(22.dp)
    val borderColor by animateColorAsState(
        targetValue = if (expanded) AmColor.Blue else AmColor.Line,
        animationSpec = tween(160),
        label = "stayCardBorder",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (expanded) 12.dp else 4.dp,
                shape = shape,
                spotColor = RouteColor.CardShadow,
                ambientColor = RouteColor.CardShadow,
            )
            .clip(shape)
            .background(AmColor.White)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = segment.place,
                    fontFamily = SuiteFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 20.4.sp,
                    color = AmColor.Navy,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = segment.label,
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 18.85.sp,
                    color = RouteColor.StayLabel,
                )
            }
            Text(
                text = formatDuration(segment.minutes),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = AmColor.Blue,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RouteColor.StayBadgeFill)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(160)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailStat(
                        label = stringResource(R.string.detail_arrive),
                        value = segment.start.format(),
                        valueColor = AmColor.Black,
                    )
                    DetailStat(
                        label = stringResource(R.string.detail_depart),
                        value = segment.end.format(),
                        valueColor = AmColor.Black,
                    )
                    DetailStat(
                        label = stringResource(R.string.detail_cumulative),
                        value = formatDuration(entry.cumulativeMinutes),
                        valueColor = AmColor.Blue,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))
                NearbyFoodLinks(place = segment.place, network = network)
            }
        }
    }
}

/** Hands the place to a map app to look for somewhere to eat nearby. */
@Composable
private fun NearbyFoodLinks(place: String, network: SubwayNetwork) {
    val context = LocalContext.current
    val station = remember(place, network) {
        network.findStation(place)?.let { network.stations[it] }
    }

    Column {
        Text(
            text = stringResource(R.string.nearby_food),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapAppChip(stringResource(R.string.open_google_maps)) {
                MapAppLinks.openGoogleMaps(context, place, station?.lat, station?.lon)
            }
            MapAppChip(stringResource(R.string.open_naver_map)) {
                MapAppLinks.openNaverMap(context, place, station?.lat, station?.lon)
            }
        }
    }
}

@Composable
private fun MapAppChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        color = AmColor.Blue,
        modifier = Modifier
            .clip(CircleShape)
            .background(RouteColor.StayBadgeFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun DetailStat(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            color = valueColor,
        )
    }
}

@Composable
private fun WaitChip(minutes: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(RouteColor.WaitFill)
            .border(1.dp, RouteColor.WaitLine, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(RouteColor.WaitDot),
        )
        Text(
            text = "환승 대기 ${minutes}분",
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
            color = RouteColor.WaitText,
        )
    }
}
