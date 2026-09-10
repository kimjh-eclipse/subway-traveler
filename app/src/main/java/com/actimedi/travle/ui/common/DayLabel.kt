package com.actimedi.travle.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.actimedi.travle.R
import com.actimedi.travle.data.DayOfWeekToken

/**
 * 화면에 쓸 요일 이름.
 *
 * 저장되는 것은 [DayOfWeekToken]의 언어 없는 값이고, 보이는 것만 로케일을 따른다.
 * 옛 경로가 `토요일`·`Saturday` 같은 문구를 들고 있어도 [DayOfWeekToken.canonical]이
 * 받아 준다 — 못 알아보는 값이면 저장된 그대로 내놓는다. 지워서는 안 되는 자료다.
 */
@Composable
fun dayLabel(stored: String): String {
    if (stored.isBlank()) return ""
    val token = DayOfWeekToken.canonical(stored)
    val index = DayOfWeekToken.ALL.indexOf(token)
    if (index < 0) return stored
    return stringArrayResource(R.array.days_of_week).getOrElse(index) { stored }
}
