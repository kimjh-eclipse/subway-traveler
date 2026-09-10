package com.actimedi.travle.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actimedi.travle.R
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.LineColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily
import kotlinx.coroutines.launch

private data class Page(val title: Int, val body: Int)

private val PAGES = listOf(
    Page(R.string.intro_title_1, R.string.intro_body_1),
    Page(R.string.intro_title_2, R.string.intro_body_2),
    Page(R.string.intro_title_3, R.string.intro_body_3),
    Page(R.string.intro_title_4, R.string.intro_body_4),
)

/**
 * 처음 켠 사람에게 이 앱이 무엇인지 알려 준다.
 *
 * 예시 경로를 넣어 두던 것을 걷어내면서 첫 화면이 비었다. 빈 화면에 `새 경로`
 * 단추만 있으면, 처음 온 사람은 무엇을 만들라는 것인지 알 수 없다. 특히 역
 * 이름을 읽지 못하는 여행자에게는 **지도를 눌러 담을 수 있다는 것**부터 말해
 * 주어야 한다.
 *
 * 그림은 앱이 실제로 쓰는 것들로 그린다 — 노선 색, 역을 뜻하는 흰 점, 시각 알약.
 * 처음 보는 모양을 여기서 만들면 본 화면에서 다시 낯설어진다.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val state = rememberPagerState(pageCount = { PAGES.size })
    // Canvas 단위는 dp가 아니라 픽셀이다. 곱해 주지 않으면 3배 화면에서 1/3 크기로
    // 그려져, 선도 점도 실처럼 가늘어진다.
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val isLast = state.currentPage == PAGES.lastIndex
    BackHandler(onBack = onDone)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmColor.SurfacePage)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.intro_skip),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = RouteColor.StayLabel,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        HorizontalPager(state = state, modifier = Modifier.weight(1f)) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(190.dp)) { drawArt(page, density) }
                Spacer(Modifier.height(36.dp))
                Text(
                    text = stringResource(PAGES[page].title),
                    fontFamily = SuiteFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    color = AmColor.Navy,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(PAGES[page].body),
                    fontFamily = SuitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = RouteColor.StayLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            PAGES.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == state.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == state.currentPage) AmColor.Blue else RouteColor.TabTrack,
                        ),
                )
            }
        }

        Text(
            text = stringResource(if (isLast) R.string.intro_start else R.string.intro_next),
            fontFamily = SuitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = AmColor.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AmColor.Blue)
                .clickable {
                    if (isLast) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                }
                .padding(vertical = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** 역을 뜻하는 흰 점. 본 화면에서 쓰는 것과 같은 모양이다. */
private fun DrawScope.stop(at: Offset, d: Float, radius: Float = 7f) {
    drawCircle(AmColor.Navy, radius = radius * d, center = at)
    drawCircle(AmColor.White, radius = (radius - 2.5f) * d, center = at)
}

private fun DrawScope.drawArt(page: Int, d: Float) {
    val w = size.width
    val h = size.height
    val thick = 9f * d
    fun pill(x: Float, y: Float, width: Float, height: Float, colour: Color) = drawRoundRect(
        color = colour,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
    when (page) {
        // 하루 — 시각을 따라 내려가는 한 줄과, 가운데 머무는 자리
        0 -> {
            val x = w * 0.26f
            drawLine(LineColor.Line1, Offset(x, h * 0.12f), Offset(x, h * 0.4f), thick, StrokeCap.Round)
            drawLine(LineColor.Line2, Offset(x, h * 0.6f), Offset(x, h * 0.88f), thick, StrokeCap.Round)
            listOf(0.12f, 0.5f, 0.88f).forEachIndexed { i, t ->
                val stay = i == 1
                pill(
                    x + 22f * d, h * t - (if (stay) 15f else 11f) * d,
                    if (stay) w * 0.5f else w * 0.34f, (if (stay) 30f else 22f) * d,
                    if (stay) RouteColor.StayBadgeFill else RouteColor.TabTrack,
                )
                stop(Offset(x, h * t), d, if (stay) 10f else 7f)
            }
        }
        // 지도에서 담기 — 엇갈리는 노선 위에서 하나를 고른다
        1 -> {
            drawLine(LineColor.Line2, Offset(w * 0.08f, h * 0.72f), Offset(w * 0.92f, h * 0.72f), thick, StrokeCap.Round)
            drawLine(LineColor.Line3, Offset(w * 0.26f, h * 0.1f), Offset(w * 0.26f, h * 0.92f), thick, StrokeCap.Round)
            drawLine(LineColor.Line4, Offset(w * 0.74f, h * 0.12f), Offset(w * 0.74f, h * 0.72f), thick, StrokeCap.Round)
            drawLine(LineColor.Suin, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.72f), thick, StrokeCap.Round)
            stop(Offset(w * 0.26f, h * 0.72f), d)
            stop(Offset(w * 0.74f, h * 0.72f), d)
            stop(Offset(w * 0.5f, h * 0.72f), d)
            // 고른 자리 — 본 화면의 선택 표시와 같은 모양이다
            drawCircle(AmColor.Blue, radius = 16f * d, center = Offset(w * 0.5f, h * 0.3f))
            drawCircle(AmColor.White, radius = 7f * d, center = Offset(w * 0.5f, h * 0.3f))
        }
        // 시각 — 다음 열차 하나만 색이 찬다
        2 -> {
            val y = h * 0.42f
            val gap = w * 0.03f
            val each = (w * 0.84f - gap * 4) / 5
            repeat(5) { i ->
                pill(
                    w * 0.08f + (each + gap) * i, y - 15f * d, each, 30f * d,
                    if (i == 1) LineColor.Line4 else RouteColor.StayBadgeFill,
                )
            }
            // 그 아래로 하루가 이어진다
            repeat(2) { row ->
                repeat(5) { i ->
                    pill(
                        w * 0.08f + (each + gap) * i, y + (34f + row * 26f) * d, each, 18f * d,
                        RouteColor.TabTrack,
                    )
                }
            }
        }
        // 노선도 — 역마다 이름표가 붙는다
        else -> {
            val centre = Offset(w * 0.4f, h * 0.5f)
            val radius = h * 0.33f
            drawCircle(LineColor.Line2, radius = radius, center = centre, style = Stroke(thick))
            listOf(270f, 0f, 90f, 180f).forEach { deg ->
                val rad = Math.toRadians(deg.toDouble())
                val at = Offset(
                    centre.x + (radius * kotlin.math.cos(rad)).toFloat(),
                    centre.y + (radius * kotlin.math.sin(rad)).toFloat(),
                )
                pill(at.x + 13f * d, at.y - 10f * d, w * 0.22f, 20f * d, RouteColor.TabTrack)
                stop(at, d)
            }
        }
    }
}
