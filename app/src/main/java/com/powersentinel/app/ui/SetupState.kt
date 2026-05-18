package com.powersentinel.app.ui

import android.content.Context

object SetupState {
    private const val PREFS_NAME = "power_sentinel_setup"
    private const val KEY_SETUP_ACCEPTED = "setup_accepted"

    fun isAccepted(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_ACCEPTED, false)
    }

    fun markAccepted(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_ACCEPTED, true)
            .commit()
    }
}
