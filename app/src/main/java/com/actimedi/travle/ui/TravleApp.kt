package com.actimedi.travle.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.actimedi.travle.R
import com.actimedi.travle.data.toDraft
import com.actimedi.travle.ui.common.PlaceholderScreen
import com.actimedi.travle.ui.editor.RouteEditorScreen
import com.actimedi.travle.ui.history.HistoryScreen
import com.actimedi.travle.ui.map.RouteMapScreen
import com.actimedi.travle.ui.route.NewRouteButton
import com.actimedi.travle.ui.route.RouteScreen
import com.actimedi.travle.ui.theme.AmColor
import com.actimedi.travle.ui.theme.RouteColor
import com.actimedi.travle.ui.theme.SuitFamily
import com.actimedi.travle.ui.theme.SuiteFamily

/** Shown when every route has been consumed — currently only reachable if the
 *  seed write failed, but it keeps the tab from rendering a blank screen. */
@Composable
private fun EmptyRouteScreen(onCreateRoute: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(AmColor.SurfacePage).statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.route_empty_title),
                fontFamily = SuiteFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AmColor.Navy,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.route_empty_body),
                fontFamily = SuitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = RouteColor.StayLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            NewRouteButton(onClick = onCreateRoute)
        }
    }
}

private enum class TravleTab(val labelRes: Int) {
    ROUTE(R.string.nav_route),
    LOG(R.string.nav_log),
    SETTINGS(R.string.nav_settings),
}

@Composable
fun TravleApp(viewModel: TravleViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(TravleTab.ROUTE) }
    var isEditorOpen by rememberSaveable { mutableStateOf(false) }
    /** Id of the route being edited; null means the editor is creating a new one. */
    var editingRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var isMapOpen by rememberSaveable { mutableStateOf(false) }

    if (isEditorOpen) {
        val editing = editingRouteId?.let { id -> viewModel.routes.firstOrNull { it.id == id } }
        RouteEditorScreen(
            network = viewModel.network,
            initialDraft = editing?.toDraft(),
            onCancel = {
                isEditorOpen = false
                editingRouteId = null
            },
            onSave = { draft ->
                val id = editingRouteId
                if (id == null) viewModel.addRoute(draft) else viewModel.updateRoute(id, draft)
                isEditorOpen = false
                editingRouteId = null
                tab = TravleTab.ROUTE
            },
        )
        return
    }

    val mapRoute = viewModel.selectedRoute
    if (isMapOpen && mapRoute != null) {
        RouteMapScreen(
            route = mapRoute,
            network = viewModel.network,
            onClose = { isMapOpen = false },
        )
        return
    }

    // Only the 노선 tab puts a dark gradient behind the status bar; the other two
    // are white surfaces and need dark status-bar icons to stay legible.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                tab != TravleTab.ROUTE
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AmColor.SurfacePage)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                TravleTab.ROUTE -> {
                    val route = viewModel.selectedRoute
                    when {
                        route != null -> RouteScreen(
                            route = route,
                            onCreateRoute = {
                                editingRouteId = null
                                isEditorOpen = true
                            },
                            network = viewModel.network,
                            onOpenMap = { isMapOpen = true }.takeIf {
                                viewModel.network.stations.isNotEmpty()
                            },
                        )
                        // Nothing to show yet, and nothing to say until the disk read lands.
                        viewModel.isLoading -> Box(Modifier.fillMaxSize())
                        else -> EmptyRouteScreen(onCreateRoute = {
                            editingRouteId = null
                            isEditorOpen = true
                        })
                    }
                }

                TravleTab.LOG -> HistoryScreen(
                    routes = viewModel.routes,
                    selectedRouteId = viewModel.selectedRoute?.id,
                    onSelect = { id ->
                        viewModel.selectRoute(id)
                        tab = TravleTab.ROUTE
                    },
                    onEdit = { id ->
                        editingRouteId = id
                        isEditorOpen = true
                    },
                    onDelete = viewModel::deleteRoute,
                    onCreateRoute = {
                        editingRouteId = null
                        isEditorOpen = true
                    },
                )

                TravleTab.SETTINGS -> PlaceholderScreen(bodyRes = R.string.placeholder_settings)
            }
        }
        BottomNav(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomNav(selected: TravleTab, onSelect: (TravleTab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(AmColor.SurfacePage)) {
        HorizontalDivider(color = AmColor.Line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        ) {
            NavItem(TravleTab.ROUTE, RoundedCornerShape(6.dp), selected, onSelect, Modifier.weight(1f))
            NavItem(TravleTab.LOG, CircleShape, selected, onSelect, Modifier.weight(1f))
            NavItem(TravleTab.SETTINGS, RoundedCornerShape(4.dp), selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    tab: TravleTab,
    iconShape: Shape,
    selected: TravleTab,
    onSelect: (TravleTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selected
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) AmColor.Blue else RouteColor.NavInactiveIcon,
        animationSpec = tween(140),
        label = "navIcon",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) AmColor.Blue else RouteColor.NavInactive,
        animationSpec = tween(140),
        label = "navLabel",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSelect(tab) },
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(iconShape)
                .border(2.dp, iconColor, iconShape),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(tab.labelRes),
            fontFamily = SuitFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}
