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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.actimedi.travle.ui.common.ArrivalsPanel
import com.actimedi.travle.ui.common.stationLabel
import com.actimedi.travle.ui.common.lineLabel
import com.actimedi.travle.ui.common.SchedulePanel
import com.actimedi.travle.ui.common.SpotsPanel
import com.actimedi.travle.ui.common.TransferGuide
import com.actimedi.travle.ui.common.durationText
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.TimelineEntry
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
    live: Boolean = false,
    /** 계획 중일 때 어느 요일의 시간표를 볼지 정한다. */
    dayOfWeek: String = "",
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
                is RouteSegment.Move -> MoveRow(segment, network)
                is RouteSegment.Stay -> StayCard(
                    segment = segment,
                    entry = entry,
                    expanded = expanded,
                    onToggle = onToggle,
                    network = network,
                    live = live,
                )
            }
            // 기다림이 0분이어도 갈아타는 자리는 내놓는다 — 거기서 다음 열차를 봐야 한다.
            if (entry.transferStation != null || entry.transferWaitMinutes > 0) {
                Spacer(Modifier.height(6.dp))
                TransferWait(
                    minutes = entry.transferWaitMinutes,
                    station = entry.transferStation,
                    entry = entry,
                    network = network,
                    live = live,
                    dayOfWeek = dayOfWeek,
                )
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
private fun MoveRow(segment: RouteSegment.Move, network: SubwayNetwork) {
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
            text = lineLabel(segment.line),
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
            text = stationLabel(segment.destination, network),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = AmColor.Black,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = durationText(segment.minutes),
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
    live: Boolean,
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
                    text = stationLabel(segment.place, network),
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
                text = durationText(segment.minutes),
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
                        value = durationText(entry.cumulativeMinutes),
                        valueColor = AmColor.Blue,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (live && network.findStation(segment.place) != null) {
                    Spacer(Modifier.height(12.dp))
                    ArrivalsPanel(stationName = segment.place, network = network, live = true)
                }

                // 머무는 시간 안에 다녀올 수 있는 곳만. 없으면 스스로 사라진다.
                Spacer(Modifier.height(12.dp))
                SpotsPanel(station = segment.place, stayMinutes = segment.minutes)

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

/**
 * 환승 지점.
 *
 * 두 모드가 다른 것을 보여준다. 여행 중이면 실시간 도착이고, 계획 중이면 그 요일의
 * 시간표다 — 계획하는 시점에는 '지금'이라는 것이 없기 때문이다.
 *
 * 어느 쪽이든 **누르면 열린다**. 예전에는 여행 중일 때만 눌렸는데, 체류 카드는
 * 계획 중에도 열리다 보니 환승 칩만 고장 난 것처럼 보였다.
 *
 * 여행 중에는 처음부터 펼쳐 둔다 — 갈아타는 순간 알아야 하는 정보라 한 번 더
 * 누르게 할 이유가 없다. 계획 중에는 접어 둔다: 시간표 조회도 호출이라 화면을
 * 열 때마다 스무 번씩 부를 이유는 없다.
 */
@Composable
private fun TransferWait(
    minutes: Int,
    station: String?,
    entry: TimelineEntry,
    network: SubwayNetwork,
    live: Boolean,
    dayOfWeek: String,
) {
    val known = station != null && network.findStation(station) != null
    var expanded by rememberSaveable(station, live) { mutableStateOf(live) }

    Column {
        WaitChip(
            minutes = minutes,
            station = station.takeIf { known }?.let { stationLabel(it, network) },
            expanded = expanded,
            onClick = { expanded = !expanded }.takeIf { known },
        )
        if (!known || !expanded) return@Column

        // 어느 칸에서 내려 어디로 갈아탈지. 망이 없어도 나온다 — 자산에 실려 있다.
        val ride = entry.segment as? RouteSegment.Move
        if (ride != null && entry.arrivedOnLine != null && entry.arrivedFrom != null) {
            Spacer(Modifier.height(8.dp))
            TransferGuide(
                station = station!!,
                fromLine = entry.arrivedOnLine,
                fromStation = entry.arrivedFrom,
                toLine = ride.line,
                toStation = ride.destination,
                network = network,
            )
        }

        Spacer(Modifier.height(8.dp))
        when {
            live -> ArrivalsPanel(stationName = station!!, network = network, live = true)
            // 갈아타고 어디로 가는지 알아야 시간표에서 방향을 고를 수 있다.
            ride != null -> SchedulePanel(
                station = station!!,
                line = ride.line,
                towards = ride.destination,
                towardsLabel = stationLabel(ride.destination, network),
                dayOfWeek = dayOfWeek,
                around = ride.start,
            )
        }
    }
}

@Composable
private fun WaitChip(
    minutes: Int,
    station: String?,
    expanded: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(RouteColor.WaitFill)
            .border(1.dp, RouteColor.WaitLine, CircleShape)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
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
            text = when {
                // 시간표에 딱 맞아 기다림이 없는 환승도 있다. "대기 0분"은 말이 안 된다.
                station == null && minutes <= 0 -> stringResource(R.string.transfer_chip)
                station == null -> stringResource(R.string.wait_chip, minutes)
                minutes <= 0 -> stringResource(R.string.transfer_chip_at, station)
                else -> stringResource(R.string.wait_chip_at, station, minutes)
            },
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
            color = RouteColor.WaitText,
        )
        if (onClick != null) {
            Text(
                text = if (expanded) "▴" else "▾",
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 11.5.sp,
                color = RouteColor.WaitText,
            )
        }
    }
}
