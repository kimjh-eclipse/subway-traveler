package com.actimedi.travle.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.actimedi.travle.data.Route
import com.actimedi.travle.data.RouteDraft
import com.actimedi.travle.data.RouteStore
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.SubwayNetworkLoader
import com.actimedi.travle.data.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.actimedi.travle.data.FileTimetableStore
import com.actimedi.travle.data.SeoulTimetable

/** Owns the saved routes and which one the 노선 tab is showing. */
class TravleViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RouteStore(application)

    /** The bundled Seoul network. Empty until the asset finishes loading. */
    var network by mutableStateOf(SubwayNetwork())
        private set

    /** Newest first. */
    var routes by mutableStateOf<List<Route>>(emptyList())
        private set

    var selectedRouteId by mutableStateOf<String?>(null)
        private set

    /** True until the first disk read finishes, so the UI can hold off on an empty state. */
    var isLoading by mutableStateOf(true)
        private set

    /** Falls back to the most recently created route. */
    val selectedRoute: Route?
        get() = routes.firstOrNull { it.id == selectedRouteId } ?: routes.firstOrNull()

    init {
        viewModelScope.launch {
            network = withContext(Dispatchers.IO) { SubwayNetworkLoader.load(application) }
        }
        // 받아 둔 시간표를 붙인다. 로밍이 끊긴 채로 지하철에 있을 때 막차를 물으면
        // 지난번에 본 구간은 망 없이도 답한다.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SeoulTimetable.shared.store = FileTimetableStore(application)
            }
        }
        viewModelScope.launch {
            // 처음 켠 사람에게는 아무 경로도 넣지 않는다.
            //
            // 예전에는 `앱이 비어 보이지 않게` 예시 경로를 하나 저장해 두었다.
            // 그런데 그것은 시안에서 옮겨 온 **누군가의 하루**였다 — 교동마을에서
            // 출발해 `신당동 떡볶이`를 먹는 일정이, 그것도 하드코딩된 한국어로.
            // 처음 켠 외국인 여행자가 읽을 수 없는 남의 일정을 자기 기록에 담긴
            // 채로 보게 되고, 지우기 전에는 없앨 수도 없었다.
            //
            // 빈 화면에는 무엇을 하면 되는지 적혀 있다(`EmptyRouteScreen`).
            // 볼 것이 필요하면 노선도 탭이 654개 역을 들고 기다린다.
            val stored = withContext(Dispatchers.IO) { store.load() }
            routes = stored.sortedByDescending { it.createdAt }
            selectedRouteId = routes.firstOrNull()?.id
            isLoading = false
        }
    }

    fun selectRoute(id: String) {
        selectedRouteId = id
    }

    /** Saves a freshly drawn route and makes it the one on show. */
    fun addRoute(draft: RouteDraft) {
        val route = draft.toRoute(network = network, now = System.currentTimeMillis())
        persist(listOf(route) + routes, select = route.id)
    }

    /** Rewrites an existing route in place, keeping its id and its spot in history. */
    fun updateRoute(id: String, draft: RouteDraft) {
        val existing = routes.firstOrNull { it.id == id } ?: return
        val route = draft.toRoute(network = network, now = existing.createdAt, id = id)
        persist(routes.map { if (it.id == id) route else it }, select = id)
    }

    fun deleteRoute(id: String) {
        val remaining = routes.filterNot { it.id == id }
        val nextSelection = if (selectedRouteId == id) remaining.firstOrNull()?.id else selectedRouteId
        persist(remaining, select = nextSelection)
    }

    private fun persist(next: List<Route>, select: String?) {
        routes = next
        selectedRouteId = select
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(next) }
        }
    }
}
