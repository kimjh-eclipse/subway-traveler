package com.actimedi.travle.ui.route

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.actimedi.travle.R

/**
 * Hands a place off to an installed map app to look for somewhere to eat.
 *
 * Each target is tried in order: the app's own scheme first, then a web URL that
 * the installed app usually claims and a browser can always handle. Launching is
 * guarded rather than probed with `resolveActivity`, which would need a
 * `<queries>` manifest entry on Android 11+.
 */
object MapAppLinks {

    private const val TAG = "MapAppLinks"

    fun openGoogleMaps(context: Context, place: String, lat: Double?, lon: Double?) {
        val query = Uri.encode("$place ${context.getString(R.string.nearby_food_query)}")
        val candidates = buildList {
            if (lat != null && lon != null) add("geo:$lat,$lon?q=$query")
            add("https://www.google.com/maps/search/?api=1&query=$query")
        }
        launchFirst(context, candidates)
    }

    fun openNaverMap(context: Context, place: String, lat: Double?, lon: Double?) {
        val query = Uri.encode("$place ${context.getString(R.string.nearby_food_query)}")
        val candidates = buildList {
            // Naver's scheme requires the caller's package name.
            add("nmap://search?query=$query&appname=${context.packageName}")
            if (lat != null && lon != null) {
                add("https://map.naver.com/p/search/$query?c=$lon,$lat,15,0,0,0,dh")
            }
            add("https://map.naver.com/p/search/$query")
        }
        launchFirst(context, candidates)
    }

    private fun launchFirst(context: Context, uris: List<String>) {
        uris.forEach { uri ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "처리할 앱이 없어 다음 후보로 넘어갑니다: $uri", e)
            }
        }
        Log.w(TAG, "지도 앱도 브라우저도 열 수 없습니다")
    }

    private fun String.toUri(): Uri = Uri.parse(this)
}
