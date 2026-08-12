package com.actimedi.travle.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.actimedi.travle.R
import com.actimedi.travle.data.DraftProblem
import java.text.NumberFormat

/**
 * 로케일에 따라 달라지는 표현은 여기서만 만든다.
 *
 * 데이터 계층은 숫자와 열거형만 다루고 문자열을 만들지 않는다 — 그래야 번역이
 * 리소스 하나로 끝난다.
 */

/** "8분" / "8 min" / "8分" */
@Composable
fun durationText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes < 60 -> stringResource(R.string.dur_minutes, minutes)
        rest == 0 -> stringResource(R.string.dur_hours, hours)
        else -> stringResource(R.string.dur_hours_minutes, hours, rest)
    }
}

/** "17,900원" / "₩17,900" / "17,900ウォン" — 자릿수 구분은 로케일 규칙을 따른다. */
@Composable
fun wonText(amount: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val grouped = NumberFormat.getIntegerInstance(locale).format(amount)
    return stringResource(R.string.fare_amount, grouped)
}

@Composable
fun problemText(problem: DraftProblem): String = stringResource(
    when (problem) {
        DraftProblem.BLANK_TITLE -> R.string.invalid_title
        DraftProblem.TOO_FEW_STOPS -> R.string.invalid_too_few_stops
        DraftProblem.BLANK_STOP_NAME -> R.string.invalid_blank_name
        DraftProblem.BLANK_LINE -> R.string.invalid_blank_line
    },
)
