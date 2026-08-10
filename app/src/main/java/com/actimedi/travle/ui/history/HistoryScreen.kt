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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.Locale

/** Every route made so far, newest first. Tapping one opens it on the 노선 tab. */
@Composable
fun HistoryScreen(
    routes: List<Route>,
    selectedRouteId: String?,
    onSelect: (String) -> Unit,
    onCreateRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
}

@Composable
private fun HistoryCard(route: Route, isSelected: Boolean, onClick: () -> Unit) {
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
            text = buildString {
                append(route.startTime.format())
                append(" → ")
                append(route.endTime.format())
                append(" · 총 ")
                append(formatClockSpan(summary.totalMinutes))
                if (route.dayOfWeek.isNotBlank()) {
                    append(" · ")
                    append(route.dayOfWeek)
                }
            },
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
    }
}

private val createdAtFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

private fun formattedCreatedAt(createdAt: Long): String =
    if (createdAt <= 0L) "기본 제공" else createdAtFormat.format(Date(createdAt))
