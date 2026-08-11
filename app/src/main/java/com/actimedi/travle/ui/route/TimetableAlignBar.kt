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
import com.actimedi.travle.data.AlignmentResult
import com.actimedi.travle.data.DayType
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.RouteDraft
import com.actimedi.travle.data.SeoulTimetable
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.alignToTimetable
import com.actimedi.travle.data.toDraft
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import kotlinx.coroutines.launch

/**
 * 계획한 시각을 실제 열차 시각에 맞춘다.
 *
 * 자동으로 돌리지 않는 이유가 둘 있다. 하나는 조회가 구간마다 망을 타는 일이라
 * 화면을 열 때마다 하기엔 무겁고, 다른 하나는 결과가 저장된 계획을 바꾸기 때문이다.
 * 그래서 누를 때만 계산하고, 무엇이 바뀌는지 보여준 뒤에 저장한다.
 */
@Composable
fun TimetableAlignBar(
    route: Route,
    network: SubwayNetwork,
    onApply: (RouteDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val source = remember(route.id) { SeoulTimetable() }
    var isRunning by remember(route.id) { mutableStateOf(false) }
    var result by remember(route.id) { mutableStateOf<AlignmentResult?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AmColor.White)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (isRunning) R.string.align_loading else R.string.align_note),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = RouteColor.StayLabel,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.align_action),
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
                        // 조회를 먼저 몰아서 끝낸다 — 계산은 순서를 지켜야 해서 느리다.
                        source.prefetch(draft, DayType.of(draft.dayOfWeek))
                        result = draft.alignToTimetable(network, source)
                        isRunning = false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }

    result?.let { outcome ->
        AlignmentDialog(
            result = outcome,
            onDismiss = { result = null },
            onApply = {
                onApply(outcome.draft)
                result = null
            },
        )
    }
}

@Composable
private fun AlignmentDialog(
    result: AlignmentResult,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val changed = result.changedCount
    // 바뀔 것이 없으면 '적용'은 아무 일도 하지 않는 버튼이 된다 — 닫기만 남긴다.
    val hasChange = changed > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.align_title), fontFamily = SuitFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        result.isEmpty -> stringResource(R.string.align_none)
                        hasChange -> stringResource(R.string.align_changed, changed)
                        else -> stringResource(R.string.align_unchanged)
                    },
                    fontFamily = SuitFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                if (result.skipped.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.align_skipped,
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
            if (hasChange) {
                TextButton(onClick = onApply) {
                    Text(stringResource(R.string.align_apply), fontFamily = SuitFamily)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.editor_confirm), fontFamily = SuitFamily)
                }
            }
        },
        dismissButton = if (hasChange) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.editor_cancel), fontFamily = SuitFamily)
                }
            }
        } else {
            null
        },
    )
}
