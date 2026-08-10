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
import com.actimedi.travle.data.DaysOfWeek
import com.actimedi.travle.data.RouteStop
import com.actimedi.travle.data.ScheduledStop
import com.actimedi.travle.data.TravelTimes
import com.actimedi.travle.data.StopKind
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.normalizeLineName
import com.actimedi.travle.data.schedule
import com.actimedi.travle.data.validate
import com.actimedi.travle.data.withAutoLines
import com.actimedi.travle.ui.map.LineChip
import com.actimedi.travle.ui.map.StationPickerScreen
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.ui.theme.lineColorFor

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
    val validation = draft.validate(network)
    val scheduled = remember(draft, network) { draft.schedule(network) }

    BackHandler(onBack = onCancel)

    fun updateStop(id: String, transform: (RouteStop) -> RouteStop) {
        draft = draft
            .copy(stops = draft.stops.map { if (it.id == id) transform(it) else it })
            .withAutoLines(network)
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
                updateStop(stopId) {
                    if (line == null) {
                        it.copy(name = station)
                    } else {
                        it.copy(name = station, line = line, lineIsManual = true)
                    }
                }
                pickerStopId = null
            },
        )
        return
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
            onCancel = onCancel,
            onSave = { onSave(draft) },
        )
        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)

        LazyColumn(
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
                )
            }

            item {
                AddStopButton(
                    onClick = {
                        draft = draft
                            .copy(stops = draft.stops + draft.nextStopDefault())
                            .withAutoLines(network)
                    },
                )
            }

            if (validation.messages.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        validation.messages.forEach { message ->
                            Text(
                                text = "· $message",
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
                .clickable(enabled = canSave, onClick = onSave)
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
    error: String?,
    onChange: (RouteStop) -> Unit,
    onDelete: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val isFirst = index == 0
    val shape = RoundedCornerShape(22.dp)
    // Lines that can carry you straight from the previous stop to this one.
    val lineCandidates = remember(previousName, stop.name, network) {
        val from = previousName?.let { network.findStation(it) }
        val to = network.findStation(stop.name)
        if (from == null || to == null) emptyList() else network.linesBetween(from, to)
    }

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
            onOpenMap = onOpenMap,
        )

        // The line only becomes answerable once the station is known, so it stays
        // hidden until then rather than offering an empty box to type into.
        if (!isFirst && stop.name.isNotBlank()) {
            LineField(
                stop = stop,
                candidates = lineCandidates,
                onChange = onChange,
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
                text = error,
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
    onOpenMap: () -> Unit,
) {
    val suggestions = remember(value, network) {
        if (network.findStation(value) != null) emptyList() else network.suggest(value)
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
                            .clickable { onValueChange(station.name) }
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
                        Text(
                            text = station.lines.joinToString(" · "),
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
    onChange: (RouteStop) -> Unit,
) {
    if (candidates.isEmpty()) {
        BrandTextField(
            value = stop.line,
            onValueChange = { onChange(stop.copy(line = it, lineIsManual = it.isNotBlank())) },
            label = stringResource(R.string.editor_line),
            placeholder = stringResource(R.string.editor_line_hint),
        )
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
    }
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
                text = "${minutes}분",
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
            DaysOfWeek.forEach { day ->
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
