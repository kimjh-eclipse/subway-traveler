package com.actimedi.travle.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.DayType
import com.actimedi.travle.data.LastTrainCheck
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.SeoulTimetable
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.checkLastTrain
import com.actimedi.travle.data.toDraft
import com.actimedi.travle.ui.common.durationText
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import kotlinx.coroutines.launch

/**
 * 막차 안전장치.
 *
 * 길찾기 앱들은 "지금 A에서 B로"를 답한다. 하루치 계획을 통째로 들고 있는 것은
 * 이쪽뿐이라, "이 일정 전체가 막차 안에 들어오는가"는 여기서만 답할 수 있다.
 * 낯선 도시에서 막차를 놓치는 것은 실제로 무서운 일이라 결과를 눈에 띄게 남긴다.
 */
@Composable
fun LastTrainBar(
    route: Route,
    network: SubwayNetwork,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isRunning by remember(route.id) { mutableStateOf(false) }
    var result by remember(route.id) { mutableStateOf<LastTrainCheck?>(null) }
    var isDetailOpen by remember(route.id) { mutableStateOf(false) }

    val outcome = result
    val broken = outcome?.broken

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (broken != null) RouteColor.WaitFill else AmColor.White)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = summaryOf(isRunning, outcome),
            fontFamily = SuitFamily,
            fontWeight = if (broken != null) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = if (broken != null) RouteColor.WaitText else RouteColor.StayLabel,
            modifier = Modifier
                .weight(1f)
                // 결과가 나온 뒤에는 문구를 눌러 자세히 볼 수 있다.
                .then(
                    if (outcome == null) Modifier
                    else Modifier.clickable { isDetailOpen = true },
                ),
        )
        Text(
            text = stringResource(R.string.lastrain_action),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            color = if (isRunning) RouteColor.StayLabel else AmColor.Blue,
            modifier = Modifier
                .clip(CircleShape)
                .background(RouteColor.StayBadgeFill)
                .clickable(enabled = !isRunning) {
                    isRunning = true
                    scope.launch {
                        val draft = route.toDraft()
                        val source = SeoulTimetable.shared
                        // 조회를 먼저 몰아서 끝낸다 — 셈 자체는 순서를 지켜야 해서 느리다.
                        source.prefetch(draft, DayType.of(draft.dayOfWeek))
                        result = draft.checkLastTrain(network, source)
                        isRunning = false
                        isDetailOpen = true
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }

    if (isDetailOpen && outcome != null) {
        LastTrainDialog(outcome) { isDetailOpen = false }
    }
}

/** 바에 한 줄로 남는 요약. 누르기 전에는 무엇을 하는 기능인지 알려준다. */
@Composable
private fun summaryOf(isRunning: Boolean, outcome: LastTrainCheck?): String = when {
    isRunning -> stringResource(R.string.lastrain_loading)
    outcome == null -> stringResource(R.string.lastrain_note)
    outcome.broken != null -> stringResource(R.string.lastrain_chip_bad)
    outcome.tightest?.slackMinutes != null ->
        stringResource(R.string.lastrain_chip_ok, durationText(outcome.tightest!!.slackMinutes!!))
    else -> stringResource(R.string.lastrain_none)
}

@Composable
private fun LastTrainDialog(result: LastTrainCheck, onDismiss: () -> Unit) {
    val broken = result.broken
    val tightest = result.tightest

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lastrain_title), fontFamily = SuitFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        broken != null -> stringResource(
                            R.string.lastrain_broken,
                            broken.station,
                            broken.towards,
                            broken.lastTrain?.format().orEmpty(),
                            broken.plannedDeparture.format(),
                        )

                        tightest?.slackMinutes != null -> stringResource(
                            R.string.lastrain_safe,
                            tightest.station,
                            durationText(tightest.slackMinutes!!),
                        )

                        else -> stringResource(R.string.lastrain_none)
                    },
                    fontFamily = SuitFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )

                // 어디를 몇 시까지 떠나야 하는지가 실제로 행동을 바꾸는 한 줄이다.
                if (broken == null && tightest?.latestBoard != null) {
                    Text(
                        text = stringResource(
                            R.string.lastrain_tight,
                            tightest.station,
                            tightest.latestBoard!!.format(),
                            durationText(tightest.slackMinutes ?: 0),
                        ),
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = AmColor.Navy,
                    )
                }

                if (result.skipped.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.lastrain_skipped,
                            result.skipped.joinToString(", "),
                        ),
                        fontFamily = SuitFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = RouteColor.StayLabel,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_confirm), fontFamily = SuitFamily)
            }
        },
    )
}
