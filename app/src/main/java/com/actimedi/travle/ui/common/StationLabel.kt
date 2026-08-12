package com.actimedi.travle.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.actimedi.travle.data.SubwayNetwork

/**
 * 화면에 쓸 역 이름.
 *
 * 저장된 경로는 늘 한국어 이름을 들고 있다 — 그것이 노선망과 API가 아는 이름이고,
 * 언어를 바꿨다고 저장된 자료까지 바뀌어서는 안 된다. 바뀌는 것은 보여줄 때뿐이다.
 */
@Composable
fun stationLabel(name: String, network: SubwayNetwork): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(name, network, locale) { network.displayName(name, locale) }
}
