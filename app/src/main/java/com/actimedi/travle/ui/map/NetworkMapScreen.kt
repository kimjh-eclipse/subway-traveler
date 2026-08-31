package com.actimedi.travle.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.MapStyle
import com.actimedi.travle.data.SchematicMapLoader
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.ui.common.lineLabel
import com.actimedi.travle.ui.common.stationLabel
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.lineColorFor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily

/** 처음 열었을 때의 배율. 전체를 담되 이름이 나올 만큼은 당겨 둔다. */
private const val FirstOpenScale = 2.6f

/**
 * 경로와 상관없이 노선도만 보는 화면.
 *
 * 지금까지 노선도는 경로에 딸려서만 열렸고, 역 이름은 편집기의 역 선택 화면에서만
 * 나왔다. 처음 온 사람이 '이 도시가 어떻게 생겼나'를 보려면 없는 경로부터 만들어야
 * 했다. 그래서 그냥 보는 자리를 따로 둔다.
 *
 * 역을 누르면 이름과 지나는 노선을 아래에 내놓는다. 고르는 화면이 아니므로 그
 * 이상은 하지 않는다 — 누른 것을 어디에 쓰라고 재촉하지 않는다.
 */
@Composable
fun NetworkMapScreen(
    network: SubwayNetwork,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val schematic = remember(context) { SchematicMapLoader.load(context) }
    var style by rememberSaveable { mutableStateOf(MapStyle.SCHEMATIC) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    val projected = remember(network, schematic, style) {
        projectStations(network, schematic, style)
    }
    val backdrop = remember(network, schematic, style) {
        if (style == MapStyle.SCHEMATIC) placeSegments(network, schematic) else emptyList()
    }
    val waters = remember(network, schematic, style) {
        if (style == MapStyle.SCHEMATIC) placeWaters(network, schematic) else emptyList()
    }
    val wholeBounds = remember(projected, backdrop) {
        boundsOf(projected + backdrop.flatMap { it.points })
    }
    val camera = rememberMapCameraState()

    // 서울 한가운데에서, 이름이 읽히는 배율로 연다. 전체를 담아 열면 글자가 하나도
    // 안 나와서 '이름 없는 노선도'를 다시 보게 된다.
    LaunchedEffect(camera.viewport, wholeBounds) {
        val whole = wholeBounds ?: return@LaunchedEffect
        camera.centerOn(whole.center, camera.fitScaleFor(whole) * FirstOpenScale)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.nav_map),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AmColor.Navy,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.map_browse_hint),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = RouteColor.StayLabel,
            )
        }
        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SubwayMapView(
                network = network,
                projected = projected,
                backdrop = backdrop,
                waters = waters,
                camera = camera,
                selectedStation = selected,
                labelAllStations = true,
                onStationTap = { selected = it },
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapChip(stringResource(R.string.map_whole)) { camera.frame(wholeBounds) }
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
                text = if (style == MapStyle.SCHEMATIC && !schematic.isEmpty) {
                    stringResource(R.string.map_credit_schematic)
                } else {
                    network.source
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.55f)
                    .padding(12.dp),
            )
        }

        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
        StationBar(network = network, selected = selected)
    }
}

/** 누른 역. 아무것도 안 눌렀으면 무엇을 하면 되는지만 적는다. */
@Composable
private fun StationBar(network: SubwayNetwork, selected: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmColor.White)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        if (selected == null) {
            Text(
                text = stringResource(R.string.map_browse_empty),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = RouteColor.StayLabel,
            )
            return@Column
        }

        val station = network.stations[selected]
        Text(
            text = stationLabel(station.name, network),
            fontFamily = SuiteFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = AmColor.Navy,
        )
        if (station.lines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                station.lines.forEach { line -> LineBadge(line) }
            }
        }
    }
}

/** 지나는 노선. 고르는 것이 아니라 알리는 것이라 누를 수 없다. */
@Composable
private fun LineBadge(line: String) {
    val color = lineColorFor(line)
    Text(
        text = lineLabel(line),
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
