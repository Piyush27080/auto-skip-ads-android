package com.piyush27080.autoskipads

import android.content.Context
import android.content.SharedPreferences

/** Names of the saved settings and counters, shared by the app screen and the service. */
object Prefs {
    const val NAME = "autoskip"

    const val ENABLED = "enabled"            // master on/off switch
    const val MUTE_ADS = "mute_ads"
    const val FAST_FORWARD = "fast_forward"  // experimental
    const val DEBUG_LOG = "debug_log"        // off by default

    const val ADS_SKIPPED = "ads_skipped"
    const val SECONDS_SAVED = "seconds_saved"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
