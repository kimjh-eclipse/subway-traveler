package com.actimedi.travle.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.ui.route.MapAppLinks
import com.actimedi.travle.data.Spot
import com.actimedi.travle.data.SpotKind
import com.actimedi.travle.data.SpotsLoader
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily

/** 한 정거장에 몇 곳까지. 더 늘어놓으면 고르는 일이 되어 버린다. */
private const val Shown = 4

/**
 * 이 역에서 내려 갈 만한 곳.
 *
 * 담긴 것은 가게가 아니라 동네와 명소다 — 가게는 문을 닫고, 앱에 구워 넣은 목록은
 * 관광객을 없어진 문 앞으로 보낸다. **어디로 갈지는 여기가 알려주고, 지금 뭐가
 * 좋은지는 눌렀을 때 열리는 지도 앱이 답한다.**
 *
 * 머무는 시간에 못 다녀올 곳은 아예 내놓지 않는다. 30분 세워 둔 사람에게 편도
 * 14분짜리를 권하는 것은 권한 것이 아니다.
 */
@Composable
fun SpotsPanel(
    station: String,
    stayMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val table = remember(context) { SpotsLoader.load(context) }
    val spots = remember(table, station, stayMinutes) {
        table.near(station, stayMinutes).take(Shown)
    }
    if (spots.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.spots_title),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                color = RouteColor.DetailLabel,
                modifier = Modifier.weight(1f),
            )
            // 이 자료는 늙는다. 언제 기준인지 모르면 낡은 것을 최신인 줄 안다.
            if (table.asOf.isNotBlank()) {
                Text(
                    text = stringResource(R.string.spots_as_of, table.asOf),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    color = RouteColor.StayLabel,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            spots.forEach { spot ->
                SpotRow(spot, spot.localized(locale)) {
                    MapAppLinks.openPlace(context, spot.query, spot.lat, spot.lon)
                }
            }
        }
    }
}

@Composable
private fun SpotRow(spot: Spot, label: String, onClick: () -> Unit) {
    // 이름을 위, 갈래·도보를 아래. 한 줄에 셋을 나란히 놓아 보았더니 카드가 좁아
    // 이름만 잘렸다 — 영어 `Gangnam Underground Mall`이 `Gangnam Undergro…`가 되었다.
    // 이름은 못 자른다. 잘린 이름으로는 그곳을 찾아갈 수 없다.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = AmColor.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(kindLabel(spot.kind)),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = AmColor.Blue,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RouteColor.StayBadgeFill)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Text(
                // 걸을 것이 없는 곳은 분을 적지 않는다. `도보 0분`은 말이 안 된다.
                text = if (spot.walkMinutes > 0) {
                    stringResource(R.string.spots_walk, spot.walkMinutes)
                } else {
                    stringResource(R.string.spots_at_station)
                },
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = RouteColor.StayLabel,
                maxLines = 1,
            )
        }
    }
}

private fun kindLabel(kind: SpotKind): Int = when (kind) {
    SpotKind.LANDMARK -> R.string.spot_kind_landmark
    SpotKind.MARKET -> R.string.spot_kind_market
    SpotKind.PARK -> R.string.spot_kind_park
    SpotKind.PALACE -> R.string.spot_kind_palace
    SpotKind.SHOPPING -> R.string.spot_kind_shopping
    SpotKind.AREA, SpotKind.UNKNOWN -> R.string.spot_kind_area
}
