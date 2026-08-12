package com.actimedi.travle.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.Arrival
import com.actimedi.travle.data.ArrivalHeadsign
import com.actimedi.travle.data.ArrivalStatus
import com.actimedi.travle.data.ArrivalWhen
import com.actimedi.travle.data.ArrivalResult
import com.actimedi.travle.data.RealtimeArrivals
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.lineColorFor

/**
 * 한 역의 실시간 도착.
 *
 * 계획 화면의 시각은 건드리지 않는다 — 저장된 경로는 미래의 약속이고 이건
 * '지금'의 정보다. 실패하면 조용히 사라진다: 실시간이 없다고 해서 일정을 못
 * 보는 것은 아니기 때문이다.
 */
@Composable
fun ArrivalsPanel(
    stationName: String,
    network: SubwayNetwork,
    modifier: Modifier = Modifier,
    /** 여행 중일 때만 실시간을 부른다. 계획 중에는 부르지 않는다. */
    live: Boolean = true,
) {
    // 노선망 이름과 API 이름이 어긋나는 역이 51곳 있다 — 물어보기 전에 바꿔 둔다.
    val apiNames = remember(stationName, network) { network.realtimeNamesFor(stationName) }
    var state by remember(apiNames, live) { mutableStateOf<ArrivalResult?>(null) }

    LaunchedEffect(apiNames, live) {
        state = null
        if (live && apiNames.any { it.isNotBlank() }) {
            state = RealtimeArrivals.forStations(apiNames)
        }
    }

    if (!live) return

    // 아직 조회 중이면 자리를 잡지 않는다 — 카드가 깜빡이며 늘어나는 것보다 낫다.
    val result = state ?: return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.arrivals_title),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(8.dp))
        when (result) {
            // 잘라내지 않는다. 갈아탈 때 필요한 것은 '다음 열차' 하나가 아니라
            // 어느 방향이 언제 오는지 전부다.
            is ArrivalResult.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.arrivals.forEach { ArrivalRow(it, network) }
            }
            // 조용히 사라지면 고장으로 읽힌다 — 왜 비었는지 한 줄로 알린다.
            ArrivalResult.Empty -> Note(stringResource(R.string.arrivals_empty))
            is ArrivalResult.Failed -> Note(stringResource(R.string.arrivals_failed))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = RouteColor.StayLabel,
    )
}

/**
 * API가 준 한국어 문장을 그대로 쓰지 않고, 뜯어 둔 조각으로 다시 짓는다.
 * 역 이름은 이름표로 바꾸고 `~행`·`~분 후` 같은 말은 문자열 자원에서 온다.
 */
@Composable
private fun headsignText(headsign: ArrivalHeadsign, network: SubwayNetwork): String {
    if (headsign.raw.isNotBlank()) return headsign.raw
    val bound = stringResource(R.string.arrival_bound, stationLabel(headsign.destination, network))
    if (headsign.towards.isBlank() || headsign.towards == headsign.destination) return bound
    return bound + " · " + stringResource(
        R.string.arrival_towards,
        stationLabel(headsign.towards, network),
    )
}

@Composable
private fun timingText(timing: ArrivalWhen, network: SubwayNetwork): String {
    val what = when (val status = timing.status) {
        is ArrivalStatus.Minutes -> stringResource(R.string.arrival_minutes, status.minutes)
        is ArrivalStatus.StopsAway -> stringResource(R.string.arrival_stops_away, status.stops)
        ArrivalStatus.Entering -> stringResource(R.string.arrival_entering)
        ArrivalStatus.Arrived -> stringResource(R.string.arrival_arrived)
        ArrivalStatus.Departed -> stringResource(R.string.arrival_departed)
        ArrivalStatus.PreviousEntering -> stringResource(R.string.arrival_prev_entering)
        ArrivalStatus.PreviousArrived -> stringResource(R.string.arrival_prev_arrived)
        ArrivalStatus.PreviousDeparted -> stringResource(R.string.arrival_prev_departed)
        // 알아보지 못한 문구는 원문 그대로. 지우는 것보다 낫다.
        is ArrivalStatus.Unknown -> return status.text
    }
    if (timing.at.isBlank()) return what
    return stringResource(R.string.arrival_at, what, stationLabel(timing.at, network))
}

@Composable
private fun ArrivalRow(arrival: Arrival, network: SubwayNetwork) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (arrival.line.isNotBlank()) {
            Text(
                text = lineLabel(arrival.line),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                lineHeight = 10.5.sp,
                color = AmColor.White,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(lineColorFor(arrival.line))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(
            text = headsignText(arrival.headsign, network),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = AmColor.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = timingText(arrival.timing, network),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = AmColor.Blue,
            maxLines = 1,
            // 잘릴 때는 잘렸다고 보여야 한다. 그냥 끊기면 `(Yangjae` 처럼 읽혀
            // 이름이 그런 줄 안다.
            overflow = TextOverflow.Ellipsis,
        )
    }
}
