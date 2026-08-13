package com.actimedi.travle.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily

/**
 * 출처 표시.
 *
 * 번들 데이터의 라이선스가 출처 표시를 요구한다 — OSM은 ODbL, 서울교통공사 역간
 * 시간은 공공누리 1유형. 지도 화면에만 적어두면 시간 데이터의 출처가 어디에도
 * 드러나지 않아, 설정 탭이 그 자리를 맡는다.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            fontFamily = SuiteFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = AmColor.Navy,
        )
        Spacer(Modifier.height(20.dp))

        SourceCard(
            title = stringResource(R.string.about_network_title),
            body = stringResource(R.string.about_network_body),
        )
        Spacer(Modifier.height(10.dp))
        SourceCard(
            title = stringResource(R.string.about_schematic_title),
            body = stringResource(R.string.about_schematic_body),
        )
        Spacer(Modifier.height(10.dp))
        SourceCard(
            title = stringResource(R.string.about_times_title),
            body = stringResource(R.string.about_times_body),
        )
        Spacer(Modifier.height(10.dp))
        SourceCard(
            title = stringResource(R.string.about_fares_title),
            body = stringResource(R.string.about_fares_body),
        )
        Spacer(Modifier.height(10.dp))
        SourceCard(
            title = stringResource(R.string.about_fonts_title),
            body = stringResource(R.string.about_fonts_body),
        )
    }
}

@Composable
private fun SourceCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RouteColor.TabTrack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = AmColor.Navy,
        )
        Text(
            text = body,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = RouteColor.StayLabel,
        )
    }
}
