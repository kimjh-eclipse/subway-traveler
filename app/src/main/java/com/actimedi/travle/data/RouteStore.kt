package com.actimedi.travle.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Routes on disk, as a single JSON file in internal storage.
 *
 * Small enough that a whole-file rewrite is cheaper than a database; writes go
 * through a temp file so a crash mid-save cannot leave a truncated list behind.
 */
class RouteStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): List<Route> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<Route>>(file.readText()) }
            .onFailure { Log.w(TAG, "저장된 경로를 읽지 못했습니다", it) }
            .getOrDefault(emptyList())
            // A route with no segments would crash startTime/endTime.
            .filter { it.segments.isNotEmpty() }
    }

    fun save(routes: List<Route>) {
        runCatching {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(json.encodeToString(routes))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "경로를 저장하지 못했습니다", it) }
    }

    private companion object {
        const val FILE_NAME = "routes.json"
        const val TAG = "RouteStore"
    }
}
