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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.ClockTime
import com.actimedi.travle.data.DayType
import com.actimedi.travle.data.SeoulTimetable
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.lineColorFor

/** 시간표에서 몇 편이나 보여줄지. 다음 열차만으로는 놓쳤을 때 대안이 안 보인다. */
private const val Shown = 5

/**
 * 계획 중인 역에서 실제로 떠나는 열차 시각.
 *
 * 여행 중이면 [ArrivalsPanel]이 '지금'을 알려주지만, 계획 중에는 그럴 '지금'이
 * 없다. 대신 그 요일의 시간표에서 계획한 시각 언저리를 보여준다 — 계획이 실제
 * 열차와 맞는지, 놓치면 다음이 언제인지 그 자리에서 알 수 있다.
 */
@Composable
fun SchedulePanel(
    station: String,
    line: String,
    towards: String,
    dayOfWeek: String,
    /** 계획상 이 역을 떠나는 시각. 이 시각 이후부터 보여준다. */
    around: ClockTime,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var times by remember(station, line, towards, dayOfWeek) {
        mutableStateOf<List<ClockTime>?>(null)
    }

    LaunchedEffect(station, line, towards, dayOfWeek, enabled) {
        if (!enabled || station.isBlank() || line.isBlank() || towards.isBlank()) return@LaunchedEffect
        times = SeoulTimetable.shared.departures(station, line, towards, DayType.of(dayOfWeek))
    }

    if (!enabled) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.schedule_title, towards),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(8.dp))

        when {
            // 아직 받는 중. 자리를 잡지 않는다 — 카드가 늘었다 줄었다 하는 것보다 낫다.
            times == null -> Note(stringResource(R.string.schedule_loading))

            times!!.isEmpty() -> Note(stringResource(R.string.schedule_empty))

            else -> {
                val next = times!!.filter { it >= around }.take(Shown)
                if (next.isEmpty()) {
                    Note(stringResource(R.string.schedule_after_last))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        next.forEachIndexed { index, at -> TimePill(at, line, first = index == 0) }
                    }
                }
            }
        }
    }
}

/** 첫 편만 노선 색으로 채운다 — 눈이 먼저 갈 곳은 '다음 열차' 하나다. */
@Composable
private fun TimePill(at: ClockTime, line: String, first: Boolean) {
    Text(
        text = at.format(),
        fontFamily = SuitFamily,
        fontWeight = if (first) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 11.5.sp,
        color = if (first) AmColor.White else AmColor.Black,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (first) lineColorFor(line) else RouteColor.StayBadgeFill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
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
