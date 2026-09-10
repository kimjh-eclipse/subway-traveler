package com.actimedi.travle.data

import android.content.Context

/**
 * 처음 켠 사람에게 안내를 한 번 보여 주었는가.
 *
 * 경로 자료와 달리 이것은 한 비트다. 파일을 하나 더 두는 대신 설정에 적는다.
 */
object Onboarding {
    private const val PREFS = "travle"
    private const val SEEN = "onboarding_seen"

    fun hasSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(SEEN, true).apply()
    }
}
