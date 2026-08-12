package com.actimedi.travle.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.RouteSearch
import com.actimedi.travle.data.SearchGoal
import com.actimedi.travle.data.SearchResult
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.ui.common.durationText
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import com.actimedi.travle.ui.theme.lineColorFor
import com.actimedi.travle.ui.common.lineLabel

/**
 * 두 역 사이의 길을 제안한다.
 *
 * 고르면 갈아타는 역들이 환승 정거장으로 펼쳐져 들어간다. 시각표가 없으므로
 * 소요 시간은 추정치이고, 화면에도 그렇게 적는다.
 */
@Composable
fun RouteSearchDialog(
    network: SubwayNetwork,
    from: String,
    to: String,
    onDismiss: () -> Unit,
    onPick: (SearchResult) -> Unit,
) {
    val options = remember(network, from, to) {
        listOf(SearchGoal.FASTEST, SearchGoal.FEWEST_TRANSFERS)
            .mapNotNull { RouteSearch.find(network, from, to, it) }
            .filter { it.legs.isNotEmpty() }
            .distinctBy { result -> result.legs.map { it.line to it.stations.last() } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmColor.White,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.search_title),
                    fontFamily = SuiteFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AmColor.Navy,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$from → $to",
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = RouteColor.StayLabel,
                )
            }
        },
        text = {
            if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_none),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = RouteColor.StayLabel,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { option ->
                        SearchOptionCard(option) { onPick(option) }
                    }
                    Text(
                        text = stringResource(R.string.search_note),
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = RouteColor.DetailLabel,
                    )
                }
            }
        },
        confirmButton = {
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
private fun SearchOptionCard(result: SearchResult, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RouteColor.TabTrack)
            .border(1.dp, AmColor.Line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    when (result.goal) {
                        SearchGoal.FASTEST -> R.string.search_fastest
                        SearchGoal.FEWEST_TRANSFERS -> R.string.search_fewest_transfers
                    },
                ),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AmColor.Navy,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = durationText(result.minutes),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AmColor.Blue,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.search_summary,
                result.transfers,
                result.legs.sumOf { it.hops },
            ),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            color = RouteColor.StayLabel,
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            result.legs.forEachIndexed { index, leg ->
                if (index > 0) {
                    Text(
                        text = "›",
                        fontFamily = SuitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RouteColor.DetailLabel,
                    )
                }
                Text(
                    text = lineLabel(leg.line),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    lineHeight = 11.5.sp,
                    color = AmColor.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(lineColorFor(leg.line))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
    }
}
