package com.actimedi.travle.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.actimedi.travle.data.TravelTimes
import com.actimedi.travle.ui.common.durationText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.data.StopKind
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.R
import com.actimedi.travle.ui.common.ArrivalsPanel
import com.actimedi.travle.ui.common.lineLabel
import com.actimedi.travle.ui.common.stationLabel
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.ui.theme.lineColorFor
import com.actimedi.travle.data.SchematicMapLoader
import com.actimedi.travle.data.MapStyle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext

/**
 * How much tighter than a whole-network fit the picker opens.
 *
 * 24였다가 8로 내렸다. 서울교통공사 도식으로 갈아탄 뒤에는 24배가 역 셋만
 * 보일 만큼 깊었다 — 이름이 편히 읽히면서도 이웃 역이 한 화면에 들어오는
 * 배율이 이쯤이다. 라벨은 절대 배율 1.4부터 나오므로 넉넉히 위다.
 */
private const val PickerZoomFactor = 8f

/**
 * 실마리가 없을 때 — 첫 경로의 첫 정거장 — 여는 배율.
 *
 * 강남을 기본값으로 두면 늘 강남부터 보게 되고, 전체를 보여 주면 이름이 안 보여
 * 한참 확대해야 한다. 중간이 맞다: 노선도 한가운데(도심)를 이름이 읽히는 배율로.
 */
private const val FirstOpenZoomFactor = 4f

/**
 * 지도에서 담을 때 고르는 자리.
 *
 * [StopKind]와 따로 둔다. `종착`은 정거장의 성질이 아니라 **거기서 끝난다**는 뜻이라
 * 담은 뒤 지도를 닫아야 하고, `출발`은 새 칸을 붙이는 대신 이미 있는 첫 칸을 채운다.
 * 편집기가 이 넷을 각각 다르게 받는다.
 */
enum class PickKind { START, TRANSFER, STAY, FINAL }

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
    /**
     * 담기 모드. 주면 아래 칸이 `이 역으로` 대신 **경유·머무르기·종착**으로 바뀌고,
     * 하나를 고를 때마다 경로에 붙인 뒤 화면을 닫지 않는다 — 지도를 보며 여러 역을
     * 이어 담는 것이 이 모드의 전부다. 종착만은 담고 닫는다.
     */
    onAdd: ((
        station: String,
        line: String?,
        kind: PickKind,
        stayMinutes: Int,
        memo: String,
    ) -> Unit)? = null,
    /** 지금까지 담은 역. 지도에 번호로 찍는다. */
    addedStations: List<String> = emptyList(),
    /** 출발지가 아직 비었는가. 비었으면 첫 역은 출발로만 담을 수 있다. */
    needsOrigin: Boolean = false,
) {
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    val schematic = remember(context) { SchematicMapLoader.load(context) }
    // 노선도 화면과 같이 도식이 기본이다. 역을 겨냥하기에도 도식이 낫다 —
    // 지리로 그리면 도심에서 역이 뭉쳐 손가락으로 집기 어렵다.
    var style by rememberSaveable { mutableStateOf(MapStyle.SCHEMATIC) }
    val projected = remember(network, schematic, style) {
        projectStations(network, schematic, style)
    }
    val backdrop = remember(network, schematic, style) {
        if (style == MapStyle.SCHEMATIC) placeSegments(network, schematic) else emptyList()
    }
    val waters = remember(network, schematic, style) {
        if (style == MapStyle.SCHEMATIC) placeWaters(network, schematic) else emptyList()
    }
    // 도식은 역보다 선이 더 멀리 뻗는다 — 선까지 담아야 전체 보기에서 잘리지 않는다.
    val wholeBounds = remember(projected, backdrop) {
        boundsOf(projected + backdrop.flatMap { it.points })
    }
    val camera = rememberMapCameraState()
    var selected by remember { mutableStateOf(initialStation?.let { network.findStation(it) }) }
    var chosenLine by remember { mutableStateOf<String?>(null) }
    val marks = remember(addedStations, network) {
        addedStations.mapNotNull { network.findStation(it) }
    }

    // Open zoomed in near the station being worked on — the stop's own name first,
    // then the previous stop on the route. Deliberately not keyed on `selected`,
    // so tapping around does not yank the camera back.
    //
    // 아무 실마리가 없으면(첫 경로의 첫 정거장) 도심을 적당히 확대해 연다.
    // 특정 역을 기본값으로 두면 늘 그 역부터 보게 되고(강남이 그랬다), 전체를
    // 보여 주면 이름이 안 보여 한참 확대해야 한다.
    // 처음 한 번, 그리고 도식↔지리를 바꿀 때만 자리를 잡는다. 담을 때마다 다시
    // 잡으면 배율이 제멋대로 튄다 — 역을 하나 담을 때마다 지도가 확 당겨졌다.
    var framedFor by remember { mutableStateOf<MapStyle?>(null) }
    LaunchedEffect(camera.viewport, projected, style) {
        if (projected.isEmpty() || camera.viewport.width <= 0f) return@LaunchedEffect
        if (framedFor == style) return@LaunchedEffect
        framedFor = style
        val whole = wholeBounds
        val focus = initialStation?.let { network.findStation(it) }
            ?: focusStation?.let { network.findStation(it) }
        val at = focus?.let { projected[it] }
        if (at != null) {
            camera.centerOn(at, camera.fitScaleFor(whole) * PickerZoomFactor)
        } else if (whole != null) {
            camera.centerOn(whole.center, camera.fitScaleFor(whole) * FirstOpenZoomFactor)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding()
            // 머무는 이유를 적을 때 키보드가 그 칸을 덮지 않게 한다.
            .imePadding(),
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
                backdrop = backdrop,
                waters = waters,
                camera = camera,
                selectedStation = selected,
                marked = marks,
                labelAllStations = true,
                onStationTap = {
                    selected = it
                    chosenLine = null
                },
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapChip(stringResource(R.string.map_whole)) { camera.frame(wholeBounds) }
                // 도식에 자리가 없는 역이 여섯 곳 있다. 그 역을 고르려면 지리로 넘어가야 한다.
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
                // 구석에는 짧게 — 온전한 출처는 설정의 '도식 노선도' 항목에 있다.
                text = if (style == MapStyle.SCHEMATIC && !schematic.isEmpty) {
                    stringResource(R.string.map_credit_schematic)
                } else {
                    network.source
                },
                // 출처가 길어 두 줄로 넘치면 왼쪽 칩을 덮는다. 반쪽만 쓰게 묶어 둔다.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.55f)
                    .padding(12.dp),
            )
        }

        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
        SelectionBar(
            network = network,
            selected = selected,
            chosenLine = chosenLine,
            onChooseLine = { chosenLine = it },
            onConfirm = { index -> onPick(network.stations[index].name, chosenLine) },
            onAdd = onAdd?.let { add ->
                { index, kind, minutes, memo ->
                    add(network.stations[index].name, chosenLine, kind, minutes, memo)
                    // 방금 담은 역을 한가운데로. 배율은 건드리지 않는다 — 이어서 담을
                    // 곳은 대개 그 근처다.
                    projected.getOrNull(index)?.let { camera.centerOn(it, camera.scale) }
                    // 담은 뒤에는 고른 것을 놓아 준다. 그대로 두면 다음 역을 고르지 않고
                    // 같은 역을 한 번 더 담기 쉽다.
                    selected = null
                    chosenLine = null
                }
            },
            needsOrigin = needsOrigin,
            addedCount = addedStations.size,
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
    onAdd: ((Int, PickKind, Int, String) -> Unit)? = null,
    needsOrigin: Boolean = false,
    addedCount: Int = 0,
) {
    // 머무르기를 고른 뒤 얼마나·왜를 정하는 중인가.
    var isPlanningStay by remember { mutableStateOf(false) }
    var stayMinutes by remember { mutableIntStateOf(TravelTimes.DEFAULT_STAY_MINUTES) }
    var stayMemo by remember { mutableStateOf("") }
    // 다른 역으로 옮기면 정하던 것은 없던 일이 된다.
    LaunchedEffect(selected) {
        isPlanningStay = false
        stayMemo = ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmColor.White)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (selected == null) {
            Text(
                text = when {
                    onAdd == null -> stringResource(R.string.picker_empty)
                    addedCount == 0 -> stringResource(R.string.picker_build_empty)
                    else -> stringResource(R.string.picker_build_count, addedCount)
                },
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
                // 이름표를 거쳐 보인다. 화면을 영어로 보는데 여기서만 `회현`이 나왔다.
                text = stationLabel(station.name, network),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = AmColor.Navy,
                modifier = Modifier.weight(1f),
            )
            if (onAdd == null) {
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
        }

        // 담기 모드 — 어떤 자리로 담을지 고르는 순간이 곧 담는 순간이다.
        // 다만 머무르기만은 한 번 더 묻는다: 얼마나, 그리고 왜.
        if (onAdd != null) {
            Spacer(Modifier.height(12.dp))
            if (isPlanningStay) {
                StayForm(
                    minutes = stayMinutes,
                    onMinutes = { stayMinutes = it },
                    memo = stayMemo,
                    onMemo = { stayMemo = it },
                    onCancel = { isPlanningStay = false },
                    onAdd = {
                        onAdd(selected, PickKind.STAY, stayMinutes, stayMemo.trim())
                        isPlanningStay = false
                        stayMinutes = TravelTimes.DEFAULT_STAY_MINUTES
                        stayMemo = ""
                    },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needsOrigin) {
                        // 출발지가 비어 있으면 첫 역은 출발일 수밖에 없다. 나머지를
                        // 함께 내놓으면 고를 수 있는 것처럼 보이고, 눌러도 아무 일이 없다.
                        KindAddButton(
                            label = stringResource(R.string.picker_add_start),
                            marker = { StartMarker() },
                            modifier = Modifier.weight(1f),
                        ) { onAdd(selected, PickKind.START, 0, "") }
                    } else {
                        KindAddButton(
                            label = stringResource(R.string.picker_add_transfer),
                            marker = { TransferMarker() },
                            modifier = Modifier.weight(1f),
                        ) { onAdd(selected, PickKind.TRANSFER, 0, "") }
                        KindAddButton(
                            label = stringResource(R.string.picker_add_stay),
                            marker = { StayMarker() },
                            modifier = Modifier.weight(1f),
                        ) { isPlanningStay = true }
                        KindAddButton(
                            label = stringResource(R.string.picker_add_final),
                            marker = { FinalMarker() },
                            modifier = Modifier.weight(1f),
                        ) { onAdd(selected, PickKind.FINAL, 0, "") }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ArrivalsPanel(stationName = station.name, network = network)

        // 출발지에는 타고 온 것이 없다. 노선을 고르라고 내밀 자리가 아니다.
        if (station.lines.isNotEmpty() && !needsOrigin) {
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
        text = lineLabel(line),
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

/**
 * 담을 자리 하나. 아이콘은 시간표 화면에서 쓰는 표시를 그대로 가져왔다 —
 * 지도에서 고른 것이 나중에 어떤 모양으로 나타날지 여기서 미리 보인다.
 */
@Composable
private fun KindAddButton(
    label: String,
    marker: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(RouteColor.StayBadgeFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        marker()
        Text(
            text = label,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = AmColor.Blue,
            textAlign = TextAlign.Center,
        )
    }
}

/** 출발 — 시간표의 첫 점. */
@Composable
private fun StartMarker() {
    Box(
        modifier = Modifier.size(14.dp).clip(CircleShape).background(AmColor.Navy),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(AmColor.White))
    }
}

/** 경유 — 환승 칩의 노란 점. */
@Composable
private fun TransferMarker() {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(RouteColor.WaitFill)
            .border(1.dp, RouteColor.WaitLine, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(RouteColor.WaitDot))
    }
}

/** 머무름 — 체류 카드의 파란 속점. */
@Composable
private fun StayMarker() {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(AmColor.White)
            .border(2.dp, AmColor.Navy, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(AmColor.Blue))
    }
}

/** 머물 만한 시간. 자주 쓰는 것만 내놓고, 어긋나는 값은 편집기에서 분 단위로 고친다. */
private val StayChoices = listOf(15, 30, 45, 60, 90, 120, 180)

/**
 * 얼마나, 그리고 왜 머무는가.
 *
 * 시간을 안 물으면 전부 30분으로 담기고, 지도에서 만든 하루는 실제와 어긋난 채
 * 저장된다. 이유는 선택이다 — `점심`이라고만 적어 두어도 나중에 그 카드가 무엇을
 * 하려던 자리였는지 알아볼 수 있다.
 */
@Composable
private fun StayForm(
    minutes: Int,
    onMinutes: (Int) -> Unit,
    memo: String,
    onMemo: (String) -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.picker_stay_how_long),
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
            StayChoices.forEach { choice ->
                val isSelected = choice == minutes
                Text(
                    text = durationText(choice),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isSelected) AmColor.White else AmColor.Blue,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) AmColor.Blue else RouteColor.StayBadgeFill)
                        .clickable { onMinutes(choice) }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = memo,
            onValueChange = onMemo,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = AmColor.Black,
            ),
            cursorBrush = SolidColor(AmColor.Blue),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AmColor.SurfacePage)
                // 테두리가 없으면 적는 칸인지 안내 문구인지 알 수 없다.
                .border(1.dp, AmColor.Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { field ->
                if (memo.isEmpty()) {
                    Text(
                        text = stringResource(R.string.picker_stay_why),
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = RouteColor.StayLabel,
                    )
                }
                field()
            },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.editor_cancel),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = RouteColor.StayLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                text = stringResource(R.string.picker_stay_add),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = AmColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(AmColor.Blue)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/** 종착 — 시간표의 마지막 점. 속을 채워 여기서 끝난다는 것을 보인다. */
@Composable
private fun FinalMarker() {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(AmColor.White)
            .border(2.dp, AmColor.Navy, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(AmColor.Navy))
    }
}
