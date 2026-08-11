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
import com.actimedi.travle.data.ArrivalResult
import com.actimedi.travle.data.RealtimeArrivals
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
    modifier: Modifier = Modifier,
    maxRows: Int = 3,
    /** 여행 중일 때만 실시간을 부른다. 계획 중에는 부르지 않는다. */
    live: Boolean = true,
) {
    var state by remember(stationName, live) { mutableStateOf<ArrivalResult?>(null) }

    LaunchedEffect(stationName, live) {
        state = null
        if (live && stationName.isNotBlank()) state = RealtimeArrivals.forStation(stationName)
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
            is ArrivalResult.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.arrivals.take(maxRows).forEach { ArrivalRow(it) }
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

@Composable
private fun ArrivalRow(arrival: Arrival) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (arrival.line.isNotBlank()) {
            Text(
                text = arrival.line,
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
            text = arrival.headsign,
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
            text = arrival.message,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = AmColor.Blue,
            maxLines = 1,
        )
    }
}
