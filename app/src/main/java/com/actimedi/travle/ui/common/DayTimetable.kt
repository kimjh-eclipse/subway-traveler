package com.actimedi.travle.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** 한 줄에 놓을 분. 좁은 화면에서도 넘치지 않는 수다. */
private const val PerRow = 8

/**
 * 한 역의 하루치 시간표.
 *
 * [SchedulePanel]은 '다음 열차 몇 편'만 보인다 — 경로를 따라가는 중에는 그것이
 * 알고 싶은 전부이기 때문이다. 여기는 다르다. 노선도에서 역 하나를 짚어 보는
 * 사람은 **하루가 어떻게 생겼는지**를 본다. 첫차가 몇 시인지, 저녁에는 얼마나
 * 자주 오는지.
 *
 * 그래서 역 승강장에 붙은 시간표처럼 시(時)로 묶어 늘어놓는다.
 */
@Composable
fun DayTimetable(
    station: String,
    line: String,
    /** 방향. 이 역 바로 옆 역이며, 그 역을 지나는 열차만 이 방향이다. */
    towards: String,
    towardsLabel: String,
    dayType: DayType,
    dayLabel: String,
    /** 지금. 이 시각 다음 열차 하나에만 색을 준다. */
    now: ClockTime,
    modifier: Modifier = Modifier,
) {
    var times by remember(station, line, towards, dayType) {
        mutableStateOf<List<ClockTime>?>(null)
    }

    LaunchedEffect(station, line, towards, dayType) {
        if (station.isBlank() || line.isBlank() || towards.isBlank()) return@LaunchedEffect
        times = SeoulTimetable.shared.departures(station, line, towards, dayType)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.schedule_title, towardsLabel),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                color = RouteColor.DetailLabel,
                modifier = Modifier.weight(1f),
            )
            // 시간표는 요일로 갈린다. 어느 날 것을 보고 있는지 모르면 못 믿는다.
            Text(
                text = stringResource(R.string.timetable_day, dayLabel),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = RouteColor.StayLabel,
            )
        }
        Spacer(Modifier.height(8.dp))

        val found = times
        when {
            found == null -> Note(stringResource(R.string.schedule_loading))
            found.isEmpty() -> Note(stringResource(R.string.schedule_empty))
            else -> {
                val next = found.firstOrNull { it >= now }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    found.groupBy { it.minuteOfDay / 60 }
                        .toSortedMap()
                        .forEach { (hour, at) -> HourRow(hour, at, line, next) }
                }
            }
        }
    }
}

/** 한 시(時)와 그 시각들. 승강장 시간표와 같은 모양이다. */
@Composable
private fun HourRow(hour: Int, times: List<ClockTime>, line: String, next: ClockTime?) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "%02d".format(hour),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = RouteColor.StayLabel,
            modifier = Modifier.width(26.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            times.sortedBy { it.minuteOfDay }.chunked(PerRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { at -> MinutePill(at, line, isNext = at == next) }
                }
            }
        }
    }
}

/** 다음 열차 하나만 노선 색으로 채운다 — 눈이 먼저 갈 곳은 거기다. */
@Composable
private fun MinutePill(at: ClockTime, line: String, isNext: Boolean) {
    Text(
        text = "%02d".format(at.minuteOfDay % 60),
        fontFamily = SuitFamily,
        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 11.5.sp,
        color = if (isNext) AmColor.White else AmColor.Black,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isNext) lineColorFor(line) else RouteColor.StayBadgeFill)
            .padding(horizontal = 7.dp, vertical = 4.dp),
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
