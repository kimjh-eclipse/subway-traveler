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
import com.actimedi.travle.data.SeoulOneDayRoute
import com.actimedi.travle.data.SubwayNetwork
import com.actimedi.travle.data.SubwayNetworkLoader
import com.actimedi.travle.data.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { store.load() }
            val initial = stored.ifEmpty {
                listOf(SeoulOneDayRoute).also { seed ->
                    withContext(Dispatchers.IO) { store.save(seed) }
                }
            }
            routes = initial.sortedByDescending { it.createdAt }
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
