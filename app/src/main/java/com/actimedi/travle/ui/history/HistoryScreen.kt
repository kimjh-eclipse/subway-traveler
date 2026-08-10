package com.actimedi.travle.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.formatClockSpan
import com.actimedi.travle.data.summarize
import com.actimedi.travle.ui.route.NewRouteButton
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import java.text.SimpleDateFormat
import java.util.Date

/** Every route made so far, newest first. Tapping one opens it on the 노선 tab. */
@Composable
fun HistoryScreen(
    routes: List<Route>,
    selectedRouteId: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreateRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deleting is irreversible and the list has no undo, so it goes through a dialog.
    var pendingDeletion by remember { mutableStateOf<Route?>(null) }

    Box(modifier = modifier.fillMaxSize().background(AmColor.SurfacePage)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp)) {
                Text(
                    text = stringResource(R.string.nav_log),
                    fontFamily = SuiteFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = AmColor.Navy,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.history_subtitle, routes.size),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = RouteColor.StayLabel,
                )
            }

            if (routes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = RouteColor.StayLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(routes, key = { it.id }) { route ->
                        HistoryCard(
                            route = route,
                            isSelected = route.id == selectedRouteId,
                            onClick = { onSelect(route.id) },
                            onEdit = { onEdit(route.id) },
                            onDelete = { pendingDeletion = route },
                        )
                    }
                }
            }
        }

        NewRouteButton(
            onClick = onCreateRoute,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
        )
    }

    pendingDeletion?.let { route ->
        DeleteConfirmDialog(
            route = route,
            onDismiss = { pendingDeletion = null },
            onConfirm = {
                onDelete(route.id)
                pendingDeletion = null
            },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(route: Route, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmColor.White,
        title = {
            Text(
                text = stringResource(R.string.delete_title),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AmColor.Navy,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_body, route.title.replace('\n', ' ')),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = RouteColor.StayLabel,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_confirm),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    color = RouteColor.Destructive,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.editor_cancel),
                    fontFamily = SuitFamily,
                    color = RouteColor.StayLabel,
                )
            }
        },
    )
}

@Composable
private fun HistoryCard(
    route: Route,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = route.summarize()
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmColor.White)
            .border(1.dp, if (isSelected) AmColor.Blue else AmColor.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = route.title.replace('\n', ' '),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = AmColor.Navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Text(
                    text = stringResource(R.string.history_showing),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = AmColor.Blue,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RouteColor.StayBadgeFill)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = listOfNotNull(
                stringResource(
                    R.string.history_span,
                    route.startTime.format(),
                    route.endTime.format(),
                    formatClockSpan(summary.totalMinutes),
                ),
                route.dayOfWeek.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = RouteColor.TimeStrong,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.history_counts,
                summary.legCount,
                summary.stayCount,
                formattedCreatedAt(route.createdAt),
            ),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = RouteColor.StayLabel,
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardAction(stringResource(R.string.route_edit), AmColor.Blue, onEdit)
            CardAction(stringResource(R.string.route_delete), RouteColor.Destructive, onDelete)
        }
    }
}

@Composable
private fun CardAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = SuitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 기기 로케일의 날짜 표기를 따른다. */
@Composable
private fun formattedCreatedAt(createdAt: Long): String {
    if (createdAt <= 0L) return stringResource(R.string.history_builtin)
    val locale = LocalConfiguration.current.locales[0]
    return remember(createdAt, locale) {
        SimpleDateFormat("yyyy.MM.dd", locale).format(Date(createdAt))
    }
}
