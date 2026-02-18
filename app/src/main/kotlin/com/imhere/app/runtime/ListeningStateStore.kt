package com.imhere.app.runtime

import android.content.Context

object ListeningStateStore {
    private const val PREF_NAME = "imhere_runtime_state"
    private const val KEY_LISTENING_DESIRED = "listening_desired"

    fun setListeningDesired(context: Context, desired: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LISTENING_DESIRED, desired)
            .apply()
    }

    fun isListeningDesired(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LISTENING_DESIRED, false)
    }
}
