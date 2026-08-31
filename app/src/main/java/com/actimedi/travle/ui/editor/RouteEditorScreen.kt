package com.actimedi.travle.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.actimedi.travle.R
import com.actimedi.travle.data.ClockTime
import com.actimedi.travle.data.RouteDraft
import com.actimedi.travle.data.DayType
import com.actimedi.travle.data.DraftProblem
import com.actimedi.travle.data.RouteStop
import com.actimedi.travle.data.ScheduledStop
import com.actimedi.travle.data.SearchGoal
import com.actimedi.travle.data.SearchResult
import com.actimedi.travle.data.RouteSearch
import com.actimedi.travle.data.TravelTimes
import com.actimedi.travle.data.StopKind
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.normalizeLineName
import com.actimedi.travle.data.schedule
import com.actimedi.travle.data.validate
import com.actimedi.travle.data.withAutoLines
import androidx.compose.ui.res.stringArrayResource
import com.actimedi.travle.ui.common.durationText
import com.actimedi.travle.ui.common.problemText
import com.actimedi.travle.ui.map.LineChip
import com.actimedi.travle.ui.map.PickKind
import com.actimedi.travle.ui.map.StationPickerScreen
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.ui.theme.lineColorFor
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import com.actimedi.travle.data.LastTrainCheck
import com.actimedi.travle.data.SeoulTimetable
import com.actimedi.travle.data.checkLastTrain
import com.actimedi.travle.ui.common.lineLabel
import com.actimedi.travle.ui.common.stationLabel

@Composable
fun RouteEditorScreen(
    network: SubwayNetwork,
    onCancel: () -> Unit,
    onSave: (RouteDraft) -> Unit,
    modifier: Modifier = Modifier,
    /** Non-null when reopening a saved route rather than starting a new one. */
    initialDraft: RouteDraft? = null,
) {
    var draft by remember(initialDraft) { mutableStateOf(initialDraft ?: RouteDraft()) }
    var isPickingStartTime by remember { mutableStateOf(false) }
    var pickerStopId by remember { mutableStateOf<String?>(null) }
    var searchStopId by remember { mutableStateOf<String?>(null) }
    /** 지도를 보며 정거장을 이어 담는 중인가. */
    var isBuildingOnMap by remember { mutableStateOf(false) }
    // 저장을 눌렀는데 막혔을 때 무엇이 막고 있는지. 고쳐지면 저절로 사라진다.
    var blocked by remember { mutableStateOf<DraftProblem?>(null) }
    // 저장을 누른 뒤 막차를 따져보는 동안, 그리고 그 결과 발이 묶이는 계획일 때.
    var isCheckingLastTrain by remember { mutableStateOf(false) }
    var lastTrainWarning by remember { mutableStateOf<LastTrainCheck?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val validation = draft.validate(network)
    val scheduled = remember(draft, network) { draft.schedule(network) }

    BackHandler(onBack = onCancel)

    fun updateStop(id: String, transform: (RouteStop) -> RouteStop) {
        draft = draft
            .copy(stops = draft.stops.map { if (it.id == id) transform(it) else it })
            .withAutoLines(network)
    }

    // 길찾기 결과는 환승역들을 정거장으로 펼쳐 넣는다 — 갈아타는 역도 들르는 곳이다.
    fun applySearch(stopId: String, result: SearchResult) {
        val index = draft.stops.indexOfFirst { it.id == stopId }
        if (index < 0 || result.legs.isEmpty()) return
        val target = draft.stops[index]
        val expanded = result.legs.mapIndexed { legIndex, leg ->
            val endName = network.stations[leg.stations.last()].name
            val ride = leg.hops * TravelTimes.MINUTES_PER_HOP
            if (legIndex == result.legs.lastIndex) {
                target.copy(
                    name = endName,
                    line = leg.line,
                    lineIsManual = true,
                    travelMinutesOverride = ride,
                )
            } else {
                RouteStop(
                    name = endName,
                    line = leg.line,
                    lineIsManual = true,
                    kind = StopKind.TRANSFER,
                    pauseMinutes = TravelTimes.DEFAULT_TRANSFER_WAIT,
                    travelMinutesOverride = ride,
                )
            }
        }
        draft = draft.copy(
            stops = draft.stops.toMutableList().apply {
                removeAt(index)
                addAll(index, expanded)
            },
        )
    }

    /**
     * 역을 **고른** 순간 — 자동완성이나 노선도에서 — 앞 정거장에서 바로 갈 수 있는지
     * 보고, 갈 수 없으면 그 자리에서 길을 정한다.
     *
     * 예전에는 회색 글씨 한 줄로 "직결 노선이 없습니다"라고만 일러 줬다. 눈에 띄지
     * 않으니 그냥 지나치게 되고, 노선이 비어 있어 저장은 막히는데 왜 막히는지는 알 수
     * 없었다. 이제 고른 자리에서 바로 물어본다.
     *
     * 최단 시간과 최소 환승이 같은 길이면 고를 것이 없으므로 묻지 않고 채운다.
     *
     * 글자를 칠 때마다 하지 않고 고를 때만 하는 이유는, `강남구청`을 치는 도중에도
     * `강남`이 잠깐 완성되기 때문이다.
     */
    fun chooseStation(stopId: String, name: String, line: String? = null) {
        updateStop(stopId) {
            if (line == null) {
                it.copy(name = name)
            } else {
                it.copy(name = name, line = line, lineIsManual = true)
            }
        }

        // 노선도가 열리는 중이면 그쪽이 먼저다 — 두 창이 겹치지 않게 한다.
        val index = draft.stops.indexOfFirst { it.id == stopId }
        if (pickerStopId != null || index <= 0 || draft.stops[index].line.isNotBlank()) return
        val previous = draft.stops[index - 1].name
        val from = network.findStation(previous) ?: return
        val to = network.findStation(name) ?: return
        // 직결 노선이 여럿이면 칩으로 고르면 된다. 아예 없을 때만 끼어든다.
        if (network.linesBetween(from, to).isNotEmpty()) return

        val fastest = RouteSearch.find(network, previous, name, SearchGoal.FASTEST)
        val fewest = RouteSearch.find(network, previous, name, SearchGoal.FEWEST_TRANSFERS)
        val onlyChoice = when {
            fastest != null && (fewest == null || fewest.legs == fastest.legs) -> fastest
            fastest == null -> fewest
            else -> null
        }
        if (onlyChoice != null) applySearch(stopId, onlyChoice) else searchStopId = stopId
    }

    /**
     * 지도를 닫고 나온 자리에서 하는 일.
     *
     * 담는 동안에는 길찾기를 띄우지 않았다 — 두 창이 겹치면 지도가 가린다. 여기서
     * 직결 노선이 없어 비어 있는 첫 구간만 묻는다.
     */
    fun finishBuilding() {
        isBuildingOnMap = false
        searchStopId = draft.stops
            .drop(1)
            .firstOrNull { it.line.isBlank() && it.name.isNotBlank() }
            ?.id
    }

    if (isBuildingOnMap) {
        // 출발지가 비어 있으면 첫 역은 출발이다. 그 자리는 이미 만들어져 있으므로
        // 새로 붙이지 않고 채운다 — 붙이면 이름 없는 첫 칸이 남는다.
        val needsOrigin = draft.stops.firstOrNull()?.name.isNullOrBlank()
        StationPickerScreen(
            network = network,
            initialStation = null,
            focusStation = draft.stops.lastOrNull { it.name.isNotBlank() }?.name,
            onCancel = { finishBuilding() },
            onPick = { _, _ -> },
            onAdd = { station, line, kind, stayMinutes, memo ->
                draft = if (kind == PickKind.START) {
                    // 출발지 칸은 이미 있다. 새로 붙이면 이름 없는 첫 칸이 남는다.
                    val first = draft.stops.first()
                    draft.copy(stops = draft.stops.toMutableList().apply {
                        this[0] = first.copy(name = station, kind = StopKind.STAY)
                    })
                } else {
                    draft.copy(
                        stops = draft.stops + RouteStop(
                            name = station,
                            // 종착은 정거장의 성질이 아니라 거기서 끝난다는 뜻이다.
                            // 마지막 칸의 머무는 시간은 어차피 0으로 계산된다.
                            kind = if (kind == PickKind.TRANSFER) StopKind.TRANSFER else StopKind.STAY,
                            line = line.orEmpty(),
                            lineIsManual = line != null,
                            memo = memo,
                            pauseMinutes = when (kind) {
                                PickKind.STAY -> stayMinutes
                                PickKind.TRANSFER -> TravelTimes.DEFAULT_TRANSFER_WAIT
                                else -> 0
                            },
                        ),
                    )
                }.withAutoLines(network)
                if (kind == PickKind.FINAL) finishBuilding()
            },
            addedStations = draft.stops.map { it.name }.filter { it.isNotBlank() },
            needsOrigin = needsOrigin,
        )
        return
    }

    pickerStopId?.let { stopId ->
        val stop = draft.stops.first { it.id == stopId }
        // Fall back to the nearest station already chosen earlier on the route, so
        // adding a stop opens next to where you left off rather than country-wide.
        val previousStation = draft.stops
            .takeWhile { it.id != stopId }
            .lastOrNull { it.name.isNotBlank() }
            ?.name
        StationPickerScreen(
            network = network,
            initialStation = stop.name.takeIf { it.isNotBlank() },
            focusStation = previousStation,
            onCancel = { pickerStopId = null },
            onPick = { station, line ->
                pickerStopId = null
                chooseStation(stopId, station, line)
            },
        )
        return
    }

    searchStopId?.let { stopId ->
        val index = draft.stops.indexOfFirst { it.id == stopId }
        val from = draft.stops.getOrNull(index - 1)?.name.orEmpty()
        val to = draft.stops.getOrNull(index)?.name.orEmpty()
        RouteSearchDialog(
            network = network,
            from = from,
            to = to,
            onDismiss = { searchStopId = null },
            onPick = { result ->
                applySearch(stopId, result)
                searchStopId = null
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding()
            .imePadding(),
    ) {
        EditorTopBar(
            titleRes = if (initialDraft == null) {
                R.string.editor_title
            } else {
                R.string.editor_title_edit
            },
            canSave = validation.isValid,
            isBusy = isCheckingLastTrain,
            onCancel = onCancel,
            onSave = {
                val problem = validation.firstProblem(draft.stops)
                if (problem == null) {
                    // 낮에 끝나는 일정은 막차와 무관하다 — 저장할 때마다 망을 탈 이유가 없다.
                    val endsLate = (scheduled.lastOrNull()?.arrival?.minuteOfDay ?: 0) >= LateEnough
                    if (!endsLate) {
                        onSave(draft)
                    } else {
                        isCheckingLastTrain = true
                        scope.launch {
                            val source = SeoulTimetable.shared
                            source.prefetch(draft, DayType.of(draft.dayOfWeek))
                            val check = draft.checkLastTrain(network, source)
                            isCheckingLastTrain = false
                            // 자료를 못 받았으면 붙잡지 않는다. 확인 못 한 것과 문제인 것은 다르다.
                            if (check.broken != null) lastTrainWarning = check else onSave(draft)
                        }
                    }
                } else {
                    // 회색 버튼이 아무 반응도 없으면 고장으로 읽힌다. 무엇이 막는지
                    // 말해 주고, 고칠 자리로 데려다 놓는다.
                    val (what, stopIndex) = problem
                    blocked = what
                    scope.launch {
                        listState.animateScrollToItem(stopIndex?.let { it + StopItemOffset } ?: 0)
                    }
                }
            },
        )
        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
        blocked?.takeIf { !validation.isValid }?.let { BlockedBanner(problemText(it)) }
        if (isCheckingLastTrain) BlockedBanner(stringResource(R.string.lastrain_checking))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BrandTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = stringResource(R.string.editor_name),
                    placeholder = stringResource(R.string.editor_name_hint),
                )
            }
            item {
                DayOfWeekPicker(
                    selected = draft.dayOfWeek,
                    onSelect = { draft = draft.copy(dayOfWeek = it) },
                )
            }
            item {
                TimeField(
                    label = stringResource(R.string.editor_start_time),
                    time = draft.startTime,
                    onClick = { isPickingStartTime = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.editor_stops),
                        fontFamily = SuiteFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AmColor.Navy,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.editor_stops_hint),
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = RouteColor.StayLabel,
                    )
                }
            }

            items(draft.stops, key = { it.id }) { stop ->
                val index = draft.stops.indexOfFirst { it.id == stop.id }
                StopCard(
                    stop = stop,
                    index = index,
                    previousName = draft.stops.getOrNull(index - 1)?.name,
                    isLast = index == draft.stops.lastIndex,
                    scheduled = scheduled.getOrNull(index),
                    network = network,
                    canDelete = draft.stops.size > 1,
                    error = validation.stopErrors[stop.id],
                    onChange = { updated -> updateStop(stop.id) { updated } },
                    onDelete = {
                        draft = draft
                            .copy(stops = draft.stops.filterNot { it.id == stop.id })
                            .withAutoLines(network)
                    },
                    onOpenMap = { pickerStopId = stop.id },
                    onSearchRoute = { searchStopId = stop.id },
                    onPickStation = { picked -> chooseStation(stop.id, picked) },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddStopButton(
                        onClick = {
                            draft = draft
                                .copy(stops = draft.stops + draft.nextStopDefault())
                                .withAutoLines(network)
                        },
                    )
                    // 한 칸씩 이름을 쳐 넣는 것 말고, 지도를 보며 이어 담는 길.
                    AddFromMapButton(onClick = { isBuildingOnMap = true })
                }
            }

            if (validation.messages.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        validation.messages.forEach { message ->
                            Text(
                                text = "· " + problemText(message),
                                fontFamily = SuitFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = RouteColor.WaitText,
                            )
                        }
                    }
                }
            }
        }
    }

    lastTrainWarning?.broken?.let { broken ->
        AlertDialog(
            onDismissRequest = { lastTrainWarning = null },
            title = { Text(stringResource(R.string.lastrain_title), fontFamily = SuitFamily) },
            text = {
                Text(
                    text = stringResource(
                        R.string.lastrain_broken,
                        broken.station,
                        broken.towards,
                        broken.lastTrain?.format().orEmpty(),
                        broken.plannedDeparture.format(),
                    ),
                    fontFamily = SuitFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            // 막차를 놓치는 계획도 계획이다. 알려주되 붙잡지는 않는다.
            confirmButton = {
                TextButton(onClick = { lastTrainWarning = null; onSave(draft) }) {
                    Text(stringResource(R.string.lastrain_save_anyway), fontFamily = SuitFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { lastTrainWarning = null }) {
                    Text(stringResource(R.string.lastrain_fix), fontFamily = SuitFamily)
                }
            },
        )
    }

    if (isPickingStartTime) {
        TimePickerDialog(
            initial = draft.startTime,
            onDismiss = { isPickingStartTime = false },
            onConfirm = { picked ->
                draft = draft.copy(startTime = picked)
                isPickingStartTime = false
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    titleRes: Int,
    canSave: Boolean,
    /** 막차를 따져보는 동안에는 다시 누르지 못하게 한다. */
    isBusy: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.editor_cancel),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = RouteColor.StayLabel,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(titleRes),
            fontFamily = SuiteFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = AmColor.Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.editor_save),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (canSave) AmColor.White else RouteColor.TabInactiveText,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (canSave) AmColor.Blue else RouteColor.TabTrack)
                // 막혀 있어도 누를 수는 있다 — 눌러야 왜 막혔는지 알려줄 수 있다.
                .clickable(enabled = !isBusy, onClick = onSave)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StopCard(
    stop: RouteStop,
    index: Int,
    previousName: String?,
    isLast: Boolean,
    scheduled: ScheduledStop?,
    network: SubwayNetwork,
    canDelete: Boolean,
    error: DraftProblem?,
    onChange: (RouteStop) -> Unit,
    onDelete: () -> Unit,
    onOpenMap: () -> Unit,
    onSearchRoute: () -> Unit,
    onPickStation: (String) -> Unit,
) {
    val isFirst = index == 0
    val shape = RoundedCornerShape(22.dp)
    // Lines that can carry you straight from the previous stop to this one.
    val from = remember(previousName, network) { previousName?.let { network.findStation(it) } }
    val to = remember(stop.name, network) { network.findStation(stop.name) }
    val lineCandidates = remember(from, to, network) {
        if (from == null || to == null) emptyList() else network.linesBetween(from, to)
    }
    // 양쪽 다 역이면 노선은 도출 가능한 문제다 — 직결이 없다면 답은 환승이지 타이핑이 아니다.
    val bothOnRail = from != null && to != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmColor.White)
            .border(1.dp, if (error != null) RouteColor.WaitLine else AmColor.Line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (isFirst) 40.dp else 28.dp, 28.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) AmColor.Navy else lineColorFor(stop.line)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isFirst) {
                        stringResource(R.string.editor_badge_start)
                    } else {
                        stringResource(R.string.editor_badge_index, index)
                    },
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = AmColor.White,
                )
            }
            Spacer(Modifier.weight(1f))
            if (canDelete) {
                Text(
                    text = stringResource(R.string.editor_delete_stop),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = RouteColor.MoveDuration,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        StationNameField(
            value = stop.name,
            network = network,
            onValueChange = { onChange(stop.copy(name = it)) },
            onPick = onPickStation,
            onOpenMap = onOpenMap,
        )

        // The line only becomes answerable once the station is known, so it stays
        // hidden until then rather than offering an empty box to type into.
        if (!isFirst && stop.name.isNotBlank()) {
            LineField(
                stop = stop,
                candidates = lineCandidates,
                bothOnRail = bothOnRail,
                canSearch = !previousName.isNullOrBlank(),
                onChange = onChange,
                onSearchRoute = onSearchRoute,
            )
        }

        if (!isFirst) {
            // Ride time is estimated from the line; the user only steps in when it is wrong.
            MinuteStepper(
                label = stringResource(R.string.editor_travel),
                minutes = scheduled?.travelMinutes ?: 0,
                isEstimated = scheduled?.travelIsEstimated == true,
                onChange = { onChange(stop.copy(travelMinutesOverride = it)) },
                onReset = { onChange(stop.copy(travelMinutesOverride = null)) },
            )

            ComputedTimes(scheduled = scheduled, isLast = isLast, kind = stop.kind)

            // 출발지에는 이전 이동이 없어 환승이 성립하지 않는다 — 항상 체류로 다룬다.
            KindToggle(
                selected = stop.kind,
                onSelect = {
                    onChange(
                        stop.copy(
                            kind = it,
                            pauseMinutes = if (it == StopKind.STAY) {
                                TravelTimes.DEFAULT_STAY_MINUTES
                            } else {
                                TravelTimes.DEFAULT_TRANSFER_WAIT
                            },
                        ),
                    )
                },
            )

            if (!isLast) {
                MinuteStepper(
                    label = stringResource(
                        if (stop.kind == StopKind.STAY) R.string.editor_stay else R.string.editor_wait,
                    ),
                    minutes = stop.pauseMinutes,
                    isEstimated = stop.kind == StopKind.TRANSFER,
                    onChange = { onChange(stop.copy(pauseMinutes = it)) },
                    onReset = null,
                )
            }
        }

        if (!isFirst && stop.kind == StopKind.STAY) {
            BrandTextField(
                value = stop.memo,
                onValueChange = { onChange(stop.copy(memo = it)) },
                label = stringResource(R.string.editor_memo),
                placeholder = stringResource(R.string.editor_memo_hint),
            )
        }

        if (error != null) {
            Text(
                text = problemText(error),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = RouteColor.WaitText,
            )
        }
    }
}

/**
 * 역·장소 with suggestions. Typing 초성 only (ㄱㄴ) matches on initials, so
 * "ㄱㄴㄱㅊ" finds 강남구청 without switching the keyboard around.
 */
@Composable
private fun StationNameField(
    value: String,
    network: SubwayNetwork,
    onValueChange: (String) -> Unit,
    /** 추천 목록에서 **고른** 것. 글자를 치는 것과 달리 확정된 선택이다. */
    onPick: (String) -> Unit,
    onOpenMap: () -> Unit,
) {
    val suggestions = remember(value, network) {
        if (network.findStation(value) != null) emptyList() else network.suggest(value)
    }

    // 이름을 끝까지 직접 치면 추천 목록이 사라져 누를 것이 없다 — 그래서 손을 뗀
    // 것을 보고 알린다. `LaunchedEffect(value)`가 글자마다 취소되므로, 타이핑이
    // 멎어야 아래가 끝까지 간다. `강남구청`을 치다 잠깐 멈춘 `강남`에도 걸릴 수 있지만,
    // 그때 바로 갈 수 있는 역이면 아무 일도 일어나지 않는다.
    LaunchedEffect(value, network) {
        if (network.findStation(value) == null) return@LaunchedEffect
        delay(TypingSettleMs)
        onPick(value)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandTextField(
                value = value,
                onValueChange = onValueChange,
                label = stringResource(R.string.editor_place),
                placeholder = stringResource(R.string.editor_place_hint),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.editor_pick_on_map),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = AmColor.Blue,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(RouteColor.StayBadgeFill)
                    .clickable(onClick = onOpenMap)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }

        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RouteColor.TabTrack),
            ) {
                suggestions.forEach { station ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(station.name) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = station.name,
                            fontFamily = SuitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = AmColor.Black,
                        )
                        // 읽을 수 있는 이름을 나란히 둔다. 넣는 값은 한국어 이름이지만
                        // 목록에서 역을 알아보려면 자기 언어로 읽혀야 한다.
                        val reading = stationLabel(station.name, network)
                        val lines = station.lines.map { lineLabel(it) }.joinToString(" · ")
                        if (reading != station.name) {
                            Text(
                                text = reading,
                                fontFamily = SuitFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = AmColor.Blue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = lines,
                            fontFamily = SuitFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = RouteColor.StayLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How the leg was travelled.
 *
 * With both stations known the line is derivable, so this is a choice rather than
 * a typing job: one candidate needs no interaction at all, several become chips.
 * The free-text box is only for legs the rail network cannot explain — a bus, a
 * walk, a place that is not a station.
 */
@Composable
private fun LineField(
    stop: RouteStop,
    candidates: List<String>,
    bothOnRail: Boolean,
    canSearch: Boolean,
    onChange: (RouteStop) -> Unit,
    onSearchRoute: () -> Unit,
) {
    if (candidates.isEmpty()) {
        if (bothOnRail) {
            // 두 역 다 노선망에 있는데 직결이 없다 — 갈아타야 한다는 뜻이므로
            // 손으로 적게 하지 않고 길찾기로 보낸다.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.line_needs_transfer),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = RouteColor.StayLabel,
                )
                SearchButton(onSearchRoute)
            }
        } else {
            // 버스·도보처럼 노선망 밖의 구간에서만 직접 적는다.
            BrandTextField(
                value = stop.line,
                onValueChange = { onChange(stop.copy(line = it, lineIsManual = it.isNotBlank())) },
                label = stringResource(R.string.editor_line),
                placeholder = stringResource(R.string.editor_line_hint),
            )
        }
        return
    }

    Column {
        Text(
            text = stringResource(
                if (candidates.size == 1) R.string.editor_line else R.string.editor_line_choose,
            ),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            candidates.forEach { line ->
                LineChip(
                    line = line,
                    isSelected = normalizeLineName(stop.line) == line,
                    onClick = { onChange(stop.copy(line = line, lineIsManual = true)) },
                )
            }
        }
        if (canSearch) {
            Spacer(Modifier.height(8.dp))
            SearchButton(onSearchRoute)
        }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.search_open),
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        color = AmColor.Blue,
        modifier = Modifier
            .clip(CircleShape)
            .background(RouteColor.StayBadgeFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Read-only arrival/departure, so the computed schedule is visible while editing. */
@Composable
private fun ComputedTimes(scheduled: ScheduledStop?, isLast: Boolean, kind: StopKind) {
    if (scheduled == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RouteColor.TabTrack)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ComputedTime(stringResource(R.string.editor_arrive), scheduled.arrival.format())
        if (!isLast) {
            ComputedTime(stringResource(R.string.editor_depart), scheduled.departure.format())
        }
    }
}

@Composable
private fun ComputedTime(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            color = AmColor.Black,
        )
    }
}

/** A minutes value with −/+ steps. Marked 예상 until the user overrides it. */
@Composable
private fun MinuteStepper(
    label: String,
    minutes: Int,
    isEstimated: Boolean,
    onChange: (Int) -> Unit,
    onReset: (() -> Unit)?,
    step: Int = 5,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = RouteColor.DetailLabel,
                )
                if (isEstimated) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.editor_estimated),
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        color = RouteColor.WaitText,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RouteColor.WaitFill)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = durationText(minutes),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                color = AmColor.Black,
            )
        }
        if (onReset != null && !isEstimated) {
            Text(
                text = stringResource(R.string.editor_reset),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = RouteColor.MoveDuration,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        StepButton("−") { onChange((minutes - step).coerceAtLeast(0)) }
        Spacer(Modifier.size(6.dp))
        StepButton("+") { onChange(minutes + step) }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Text(
        text = symbol,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
        color = AmColor.Blue,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(RouteColor.StayBadgeFill)
            .clickable(onClick = onClick)
            .padding(top = 9.dp),
    )
}

/** 요일 is one of seven values, so it is picked rather than typed. */
@Composable
private fun DayOfWeekPicker(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.editor_day),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stringArrayResource(R.array.days_of_week).forEach { day ->
                val isSelected = selected == day
                Text(
                    text = day.first().toString(),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) AmColor.White else RouteColor.TabInactiveText,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AmColor.Blue else RouteColor.TabTrack)
                        .clickable { onSelect(if (isSelected) "" else day) }
                        .padding(top = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun KindToggle(selected: StopKind, onSelect: (StopKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(RouteColor.TabTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KindOption(R.string.editor_kind_stay, StopKind.STAY, selected, onSelect, Modifier.weight(1f))
        KindOption(
            R.string.editor_kind_transfer,
            StopKind.TRANSFER,
            selected,
            onSelect,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun KindOption(
    labelRes: Int,
    kind: StopKind,
    selected: StopKind,
    onSelect: (StopKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = kind == selected
    Text(
        text = stringResource(labelRes),
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 12.5.sp,
        color = if (isSelected) AmColor.White else RouteColor.TabInactiveText,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(if (isSelected) AmColor.Blue else AmColor.White.copy(alpha = 0f))
            .clickable { onSelect(kind) }
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun TimeField(
    label: String,
    time: ClockTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(RouteColor.TabTrack)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = RouteColor.DetailLabel,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = time.format(),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            color = AmColor.Black,
        )
    }
}

@Composable
private fun AddStopButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RouteColor.StayBadgeFill)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "+ " + stringResource(R.string.editor_add_stop),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = AmColor.Blue,
        )
    }
}

@Composable
private fun AddFromMapButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AmColor.White)
            .border(1.dp, AmColor.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.editor_add_from_map),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = AmColor.Navy,
        )
    }
}

@Composable
private fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    // The field owns a TextFieldValue locally. Handing OutlinedTextField a plain
    // String while the state lives up in `draft` makes every keystroke recompose
    // from above, which wrecks the Hangul composing region mid-syllable.
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }

    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        label = {
            Text(label, fontFamily = SuitFamily, fontSize = 12.sp)
        },
        placeholder = {
            Text(
                placeholder,
                fontFamily = SuitFamily,
                fontSize = 13.sp,
                color = RouteColor.DetailLabel,
            )
        },
        textStyle = TextStyle(
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = AmColor.Black,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmColor.Blue,
            unfocusedBorderColor = AmColor.Line,
            focusedLabelColor = AmColor.Blue,
            unfocusedLabelColor = RouteColor.DetailLabel,
            cursorColor = AmColor.Blue,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: ClockTime,
    onDismiss: () -> Unit,
    onConfirm: (ClockTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.minuteOfDay / 60,
        initialMinute = initial.minuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(ClockTime.of(state.hour, state.minute)) }) {
                Text(
                    stringResource(R.string.editor_confirm),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.editor_cancel),
                    fontFamily = SuitFamily,
                    color = RouteColor.StayLabel,
                )
            }
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        containerColor = AmColor.White,
        modifier = Modifier.navigationBarsPadding(),
    )
}

/** 정거장 카드가 목록에서 시작하는 자리. 앞에 이름·요일·출발 시각·소제목이 있다. */
private const val StopItemOffset = 4

/** 저장이 막힌 이유. 저장을 누른 뒤에만 나오고, 고치면 사라진다. */
@Composable
private fun BlockedBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RouteColor.WaitFill)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(RouteColor.WaitDot),
        )
        Text(
            text = text,
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = RouteColor.WaitText,
        )
    }
}

/** 타이핑이 멎었다고 보는 시간. 짧으면 치는 중에 끼어들고, 길면 답답하다. */
private const val TypingSettleMs = 900L

/**
 * 이 시각 이후에 끝나는 일정만 막차를 따져본다.
 *
 * 낮에 끝나는 계획은 어떤 노선도 막차가 지나지 않았다. 저장할 때마다 망을 타는 값이
 * 아깝고, 무엇보다 기다릴 이유가 없다. 21시로 잡은 것은 수도권에서 막차가 가장 이른
 * 축인 경의중앙선 용문·지평 방면과 공항철도 인천공항 방면이 22시대이기 때문이다.
 */
private const val LateEnough = 21 * 60
