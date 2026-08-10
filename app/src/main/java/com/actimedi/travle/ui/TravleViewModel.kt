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
        val updated = listOf(route) + routes
        routes = updated
        selectedRouteId = route.id
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(updated) }
        }
    }
}
