package com.actimedi.travle.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily

/** The full Seoul network with this route drawn on top of it. */
@Composable
fun RouteMapScreen(
    route: Route,
    network: SubwayNetwork,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val mapped = remember(route, network) { route.mapOnto(network) }
    val projected = remember(network) { projectStations(network) }
    val camera = rememberMapCameraState()

    val routeBounds = remember(mapped, projected) {
        boundsOf(mapped.stops.map { projected[it.stationIndex] })
    }

    // Open framed on the route; the rest of the network stays visible around it.
    LaunchedEffect(camera.viewport, routeBounds) { camera.frame(routeBounds) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding(),
    ) {
        MapTopBar(route = route, mapped = mapped, onClose = onClose)
        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SubwayMapView(
                network = network,
                projected = projected,
                camera = camera,
                mapped = mapped,
            )

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapChip("이 경로") { camera.frame(routeBounds) }
                MapChip("전체 노선도") { camera.frame(boundsOf(projected)) }
            }

            Attribution(
                text = network.source,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )

        }
    }
}

@Composable
private fun MapTopBar(route: Route, mapped: MappedRoute, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = route.title.replace('\n', ' '),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AmColor.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append("역 ${mapped.stops.size}곳")
                    val approx = mapped.legs.count { it.isStraightHop }
                    if (approx > 0) append(" · 직선 표시 ${approx}구간")
                    if (mapped.unmatched.isNotEmpty()) append(" · 지도 밖 ${mapped.unmatched.size}곳")
                },
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = RouteColor.StayLabel,
            )
        }
        Text(
            text = "닫기",
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = AmColor.Blue,
            modifier = Modifier
                .clip(CircleShape)
                .background(RouteColor.StayBadgeFill)
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun MapChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = AmColor.Blue,
        modifier = Modifier
            .clip(CircleShape)
            .background(AmColor.White.copy(alpha = 0.94f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** ODbL requires the source to stay visible wherever the data is shown. */
@Composable
fun Attribution(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Text(
        text = text,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = RouteColor.DetailLabel,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AmColor.White.copy(alpha = 0.85f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
