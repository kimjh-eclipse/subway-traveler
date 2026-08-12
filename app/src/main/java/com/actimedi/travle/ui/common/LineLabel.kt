package com.actimedi.travle.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.actimedi.travle.R
import com.actimedi.travle.data.normalizeLineName

/** 숫자만 다른 노선은 문구 하나로 끝난다 — `Line %d`, `%d号線`. */
private val NUMBERED = Regex("^(\\d+)호선$")
private val INCHEON = Regex("^인천(\\d+)호선$")

/** 이름을 가진 노선. GTX-A는 어느 언어에서도 GTX-A라 자리를 두지 않는다. */
private val NAMED = mapOf(
    "신분당선" to R.string.line_sinbundang,
    "수인분당선" to R.string.line_suinbundang,
    "공항철도" to R.string.line_airport,
    "경의중앙선" to R.string.line_gyeonguijungang,
    "경춘선" to R.string.line_gyeongchun,
    "경강선" to R.string.line_gyeonggang,
    "서해선" to R.string.line_seohae,
    "우이신설선" to R.string.line_uisinseol,
    "신림선" to R.string.line_sillim,
    "김포 골드라인" to R.string.line_gimpo,
    "용인 경전철" to R.string.line_yongin,
    "의정부경전철" to R.string.line_uijeongbu,
)

/**
 * 화면에 쓸 노선 이름.
 *
 * 저장된 경로는 늘 한국어 이름을 들고 있다 — 노선망과 API가 아는 이름이고, 언어를
 * 바꿨다고 저장된 자료까지 바뀌어서는 안 된다. 바뀌는 것은 보여줄 때뿐이다.
 *
 * 모르는 값은 그대로 둔다. `26-2번`이나 `도보`처럼 사용자가 직접 적은 구간이
 * 여기로 오는데, 그것까지 번역할 수는 없고 지워서도 안 된다.
 */
@Composable
fun lineLabel(rawLine: String): String {
    val line = normalizeLineName(rawLine)
    NUMBERED.matchEntire(line)?.let {
        return stringResource(R.string.line_numbered, it.groupValues[1].toInt())
    }
    INCHEON.matchEntire(line)?.let {
        return stringResource(R.string.line_incheon, it.groupValues[1].toInt())
    }
    return NAMED[line]?.let { stringResource(it) } ?: rawLine
}
