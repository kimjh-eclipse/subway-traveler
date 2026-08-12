package com.actimedi.travle.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.TransferPointLoader
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily

/**
 * 어느 칸에서 내려 어디로 갈아탈지.
 *
 * 낯선 역에서 캐리어를 끌고 반대편 끝까지 걷는 일이 이 한 줄로 사라진다. 서울교통공사가
 * 환승역마다 최단 환승 경로를 공개해 둔 것을 자산으로 싣고 있어 망이 없어도 나온다.
 *
 * **방향이 맞아야 값이 있다.** 같은 서울역이라도 시청 방면에서 왔으면 1-1, 남영
 * 방면에서 왔으면 1-2에서 내린다. 방향을 알아내지 못하면 [TransferTable]이 조용히
 * 물러나므로 여기서는 아무것도 그리지 않는다 — 틀린 칸에 세워 두느니 낫다.
 */
@Composable
fun TransferGuide(
    station: String,
    fromLine: String,
    /** 타고 온 정거장. 여기서 방향을 알아낸다. */
    fromStation: String,
    toLine: String,
    /** 갈아타고 갈 정거장. */
    toStation: String,
    network: SubwayNetwork,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val table = remember(context) { TransferPointLoader.load(context) }

    val point = remember(table, station, fromLine, fromStation, toLine, toStation, network) {
        val at = network.findStation(station) ?: return@remember null
        val from = network.findStation(fromStation) ?: return@remember null
        val to = network.findStation(toStation) ?: return@remember null

        // 타고 온 방향 그대로 한 정거장 더 간 곳이 자료가 말하는 '방면'이다.
        val arrivingTowards = network.stationBeyond(fromLine, from, at)
        // 갈아탄 뒤 첫 정거장이 곧 그쪽 방면이다.
        val leavingTowards = network.stationsBetween(toLine, at, to)?.getOrNull(1)

        table.find(
            station = network.stations[at].name,
            fromLine = fromLine,
            fromTowards = arrivingTowards?.let { network.stations[it].name }.orEmpty(),
            toLine = toLine,
            toTowards = leavingTowards?.let { network.stations[it].name }.orEmpty(),
        )
    } ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.transfer_cars, point.off, point.on),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = AmColor.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(AmColor.Navy)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        // 걸리는 시간은 아는 역만 말한다 — 중랑·상봉·망우는 원본에 값이 없다.
        point.seconds?.let { seconds ->
            Text(
                text = stringResource(R.string.transfer_walk, durationText(seconds / 60)),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = RouteColor.StayLabel,
            )
        }
    }
}
