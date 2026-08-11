package com.actimedi.travle.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.R
import com.actimedi.travle.ui.common.ArrivalsPanel
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.ui.theme.lineColorFor

/**
 * How much tighter than a whole-network fit the picker opens.
 * Roughly four and a half doublings — station names read comfortably.
 */
private const val PickerZoomFactor = 24f

/** Where the very first pick starts, before the route has any stations. */
private const val DefaultFocusStation = "강남"

/**
 * Pick a station by tapping the map.
 *
 * Returns both the station name and the line the user tapped in the chip row, so
 * the editor can fill 역·장소 and 타고 온 노선 in one go.
 */
@Composable
fun StationPickerScreen(
    network: SubwayNetwork,
    initialStation: String?,
    onCancel: () -> Unit,
    onPick: (station: String, line: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Where to open the camera — usually the previous stop on the route. */
    focusStation: String? = null,
) {
    BackHandler(onBack = onCancel)

    val projected = remember(network) { projectStations(network) }
    val camera = rememberMapCameraState()
    var selected by remember { mutableStateOf(initialStation?.let { network.findStation(it) }) }
    var chosenLine by remember { mutableStateOf<String?>(null) }

    // Open zoomed in near the station being worked on. Framing the whole network
    // leaves the stations too small to aim at. Deliberately not keyed on
    // `selected`, so tapping around does not yank the camera back.
    LaunchedEffect(camera.viewport, projected) {
        if (projected.isEmpty()) return@LaunchedEffect
        val whole = boundsOf(projected)
        val focus = initialStation?.let { network.findStation(it) }
            ?: focusStation?.let { network.findStation(it) }
            ?: network.findStation(DefaultFocusStation)
        if (focus != null) {
            camera.centerOn(projected[focus], camera.fitScaleFor(whole) * PickerZoomFactor)
        } else {
            camera.frame(whole)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.picker_title),
                    fontFamily = SuiteFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AmColor.Navy,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.picker_hint),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = RouteColor.StayLabel,
                )
            }
            Text(
                text = stringResource(R.string.editor_cancel),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = RouteColor.StayLabel,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SubwayMapView(
                network = network,
                projected = projected,
                camera = camera,
                selectedStation = selected,
                onStationTap = {
                    selected = it
                    chosenLine = null
                },
            )
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                MapChip(stringResource(R.string.map_whole)) { camera.frame(boundsOf(projected)) }
            }
            Attribution(
                text = network.source,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }

        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
        SelectionBar(
            network = network,
            selected = selected,
            chosenLine = chosenLine,
            onChooseLine = { chosenLine = it },
            onConfirm = { index -> onPick(network.stations[index].name, chosenLine) },
        )
    }
}

@Composable
private fun SelectionBar(
    network: SubwayNetwork,
    selected: Int?,
    chosenLine: String?,
    onChooseLine: (String?) -> Unit,
    onConfirm: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmColor.White)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (selected == null) {
            Text(
                text = stringResource(R.string.picker_empty),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = RouteColor.StayLabel,
            )
            return@Column
        }

        val station = network.stations[selected]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = station.name,
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = AmColor.Navy,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.picker_use),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = AmColor.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AmColor.Blue)
                    .clickable { onConfirm(selected) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        ArrivalsPanel(stationName = station.name, network = network, maxRows = 3)

        if (station.lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.picker_line_optional),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = RouteColor.DetailLabel,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                station.lines.forEach { line ->
                    LineChip(
                        line = line,
                        isSelected = chosenLine == line,
                        onClick = { onChooseLine(if (chosenLine == line) null else line) },
                    )
                }
            }
        }
    }
}

@Composable
fun LineChip(line: String, isSelected: Boolean, onClick: () -> Unit) {
    val color = lineColorFor(line)
    Text(
        text = line,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = if (isSelected) AmColor.White else color,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) color else color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
