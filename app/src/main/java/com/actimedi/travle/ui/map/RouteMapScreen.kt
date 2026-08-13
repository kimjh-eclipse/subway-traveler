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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.R
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.data.SchematicMapLoader
import com.actimedi.travle.data.MapStyle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

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
    val context = LocalContext.current
    val schematic = remember(context) { SchematicMapLoader.load(context) }
    // 도식이 기본이다 — 어디서 어디로 가는지 읽는 데는 지리적 정확함보다 선이
    // 곧은 것이 낫다. 실제 위치가 궁금할 때만 지리로 바꾼다.
    var style by rememberSaveable { mutableStateOf(MapStyle.SCHEMATIC) }
    val projected = remember(network, schematic, style) {
        projectStations(network, schematic, style)
    }
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
                MapChip(stringResource(R.string.map_this_route)) { camera.frame(routeBounds) }
                MapChip(stringResource(R.string.map_whole)) { camera.frame(boundsOf(projected)) }
                MapChip(
                    if (style == MapStyle.SCHEMATIC) {
                        stringResource(R.string.map_geographic)
                    } else {
                        stringResource(R.string.map_schematic)
                    },
                ) {
                    style = if (style == MapStyle.SCHEMATIC) {
                        MapStyle.GEOGRAPHIC
                    } else {
                        MapStyle.SCHEMATIC
                    }
                }
            }

            Attribution(
                // 도식일 때는 자리가 다른 자료에서 온다. 어느 쪽 것을 보고 있는지에
                // 따라 출처도 바뀌어야 한다 — 둘 다 출처표시가 조건인 라이선스다.
                text = if (style == MapStyle.SCHEMATIC && !schematic.isEmpty) {
                    "${schematic.source} · ${network.source}"
                } else {
                    network.source
                },
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
                text = listOfNotNull(
                    stringResource(R.string.map_stations, mapped.stops.size),
                    mapped.legs.count { it.isStraightHop }
                        .takeIf { it > 0 }
                        ?.let { stringResource(R.string.map_straight, it) },
                    mapped.unmatched.size
                        .takeIf { it > 0 }
                        ?.let { stringResource(R.string.map_offmap, it) },
                ).joinToString(" · "),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = RouteColor.StayLabel,
            )
        }
        Text(
            text = stringResource(R.string.map_close),
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
