package com.piyush27080.autoskipads

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Auto Skip Ads, version 1.0.
 *
 * Watches only the YouTube and YouTube Music apps (see accessibility_service_config.xml) and:
 *  1) mutes the music stream while an ad is on screen (optional),
 *  2) taps the Skip button the moment it appears,
 *  3) counts an ad as skipped when the Skip button disappears after our tap,
 *  4) can try to move the ad's progress bar to the end for ads with no Skip button (experimental).
 */
class SkipService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastScan = 0L
    private var lastTap = 0L
    private var lastFastForward = 0L
    private var lastDump = 0L
    private var lastAdSeenAt = 0L
    private var adStartedAt = 0L
    private var adActive = false
    private var pendingSince = 0L      // when we tapped Skip and are waiting for the button to go away
    private var pendingSaved = 0L      // seconds of the ad that were left when we tapped
    private var mutedByUs = false

    // exact texts only: "Skip", "Skip ad", "Skip ads" (not countdown texts like "Skip ad in 5")
    private val skipLabel = Regex("(?i)\\s*skip(\\s+ads?)?\\s*")
    private val unitRegex = Regex("(\\d+)\\s*(hour|minute|second)", RegexOption.IGNORE_CASE)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // runs twice a second: un-mutes after the ad is gone, even if no new screen event arrives
    private val watchdog = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now - lastAdSeenAt > 1500) {
                if (mutedByUs) unmute("ad finished")
                adActive = false
            }
            if (pendingSince != 0L && now - pendingSince > 6000) pendingSince = 0L
            handler.postDelayed(this, 500)
        }
    }

    private class Scan {
        var skipNode: AccessibilityNodeInfo? = null
        var seekBar: AccessibilityNodeInfo? = null
        var seekDesc: String? = null
        var adSeen = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.of(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        running = true
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 500)
        log("Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()
        if (now - lastScan < 150) return   // look at the screen at most ~6 times per second
        lastScan = now
        try {
            scan(now)
        } catch (e: Exception) {
            Log.w(TAG, "scan failed", e)
        }
    }

    // ---------- main logic ----------

    private fun scan(now: Long) {
        if (!prefs.getBoolean(Prefs.ENABLED, true)) {
            if (mutedByUs) unmute("paused")
            return
        }
        val root = rootInActiveWindow ?: return

        val s = Scan()
        inspect(root, 0, s)

        if (s.adSeen) {
            lastAdSeenAt = now
            if (!adActive) {
                adActive = true
                adStartedAt = now
                log("Ad detected")
            }
        }

        // 1) mute
        if (s.adSeen && prefs.getBoolean(Prefs.MUTE_ADS, true)) mute()

        // 2) tap Skip
        val skip = s.skipNode
        if (skip != null) {
            if (now - lastTap >= 800) {   // not more than once per 0.8 seconds
                lastTap = now
                val method = tap(skip)
                log("TAP via $method: id=${skip.viewIdResourceName} text=${skip.text}")
                if (method != "failed") {
                    pendingSince = now
                    pendingSaved = remainingSeconds(s.seekDesc)
                }
            }
        } else if (pendingSince != 0L) {
            // the Skip button is gone after our tap: the ad was skipped
            if (now - pendingSince <= 6000) addStats(pendingSaved)
            pendingSince = 0L
        }

        // 3) fast-forward (experimental): only for ads without a Skip button
        if (s.adSeen && skip == null && prefs.getBoolean(Prefs.FAST_FORWARD, false) &&
            now - adStartedAt > 1500 && now - lastFastForward > 1500
        ) {
            val bar = s.seekBar
            if (bar != null) {
                lastFastForward = now
                fastForward(bar)
            }
        }

        // 4) optional debug dump
        if (prefs.getBoolean(Prefs.DEBUG_LOG, false) && (s.adSeen || skip != null) && now - lastDump > 1000) {
            lastDump = now
            val lines = ArrayList<String>()
            dump(root, 0, lines)
            debug("---- screen dump (${lines.size} lines) ----")
            for (line in lines) debug(line)
        }
    }

    // Looks through the screen once: finds the Skip button, the progress bar and ad markers.
    private fun inspect(node: AccessibilityNodeInfo?, depth: Int, s: Scan) {
        if (node == null || depth > 40) return
        val id = node.viewIdResourceName ?: ""
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        // markers that only exist in the video player's ad layer (not in feed ads)
        if (id.contains("skip_ad", true) || id.contains("ad_progress", true) ||
            id.contains("player_learn_more", true) || text == "Visit advertiser"
        ) {
            s.adSeen = true
        }

        if (s.skipNode == null && node.isVisibleToUser &&
            (skipLabel.matches(text ?: "") || skipLabel.matches(desc ?: "") ||
                id.contains("skip_ad_button", true))
        ) {
            s.skipNode = node
            s.adSeen = true
        }

        if (s.seekBar == null && node.isVisibleToUser &&
            node.className?.toString() == "android.widget.SeekBar"
        ) {
            s.seekBar = node
            s.seekDesc = desc
        }

        for (i in 0 until node.childCount) inspect(node.getChild(i), depth + 1, s)
    }

    private fun tap(node: AccessibilityNodeInfo): String {
        // 1) click the button itself, or the first clickable parent
        var target: AccessibilityNodeInfo? = node
        var hops = 0
        while (target != null && !target.isClickable && hops < 6) {
            target = target.parent
            hops++
        }
        if (target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "click"
        }

        // 2) fallback: touch the middle of the button with a tap gesture
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) {
            val path = Path()
            path.moveTo(r.exactCenterX(), r.exactCenterY())
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            if (dispatchGesture(gesture, null, null)) return "gesture"
        }
        return "failed"
    }

    private fun fastForward(bar: AccessibilityNodeInfo) {
        val range = bar.rangeInfo
        if (range == null) {
            log("Fast-forward: the progress bar has no range, nothing to do")
            return
        }
        val args = Bundle()
        args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, range.max)
        val ok = bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
        log("Fast-forward attempt: " + (if (ok) "accepted" else "refused by the app"))
    }

    // ---------- mute ----------

    private fun mute() {
        if (mutedByUs) return
        try {
            if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return   // already silent: do not touch it
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            mutedByUs = true
            log("Muted (ad on screen)")
        } catch (e: Exception) {
            Log.w(TAG, "mute failed", e)
        }
    }

    private fun unmute(reason: String) {
        if (!mutedByUs) return
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            log("Unmuted ($reason)")
        } catch (e: Exception) {
            Log.w(TAG, "unmute failed", e)
        }
        mutedByUs = false
    }

    // ---------- stats ----------

    private fun addStats(savedSeconds: Long) {
        val skipped = prefs.getInt(Prefs.ADS_SKIPPED, 0) + 1
        val saved = prefs.getLong(Prefs.SECONDS_SAVED, 0L) + savedSeconds
        prefs.edit().putInt(Prefs.ADS_SKIPPED, skipped).putLong(Prefs.SECONDS_SAVED, saved).apply()
        log("Ad skipped (total $skipped, saved about ${savedSeconds}s)")
    }

    // Reads a progress description such as "0 minutes 13 seconds of 4 minutes 52 seconds"
    // and returns how many seconds of the ad were left. Returns 0 if it cannot be read.
    private fun remainingSeconds(desc: String?): Long {
        if (desc == null) return 0L
        val parts = desc.split(" of ")
        if (parts.size != 2) return 0L
        val elapsed = toSeconds(parts[0])
        val total = toSeconds(parts[1])
        if (total <= 0L) return 0L
        return (total - elapsed).coerceIn(0L, 300L)   // an estimate, capped at 5 minutes
    }

    private fun toSeconds(part: String): Long {
        var sum = 0L
        for (m in unitRegex.findAll(part)) {
            val n = m.groupValues[1].toLong()
            sum += when (m.groupValues[2].lowercase()) {
                "hour" -> n * 3600L
                "minute" -> n * 60L
                else -> n
            }
        }
        return sum
    }

    // ---------- log (saved inside the app) ----------

    private fun dump(node: AccessibilityNodeInfo?, depth: Int, lines: MutableList<String>) {
        if (node == null || depth > 40 || lines.size >= 400) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable) {
            lines.add(
                "${"  ".repeat(depth)}[${node.className}] text=$text | desc=$desc | " +
                    "id=${node.viewIdResourceName} | clickable=${node.isClickable}"
            )
        }
        for (i in 0 until node.childCount) dump(node.getChild(i), depth + 1, lines)
    }

    // short event lines (no screen text): always saved
    private fun log(message: String) {
        Log.i(TAG, message)
        appendLog(this, timeFormat.format(Date()) + " " + message)
    }

    // detailed lines (screen text): saved only when "Save debug log" is on
    private fun debug(message: String) {
        if (!prefs.getBoolean(Prefs.DEBUG_LOG, false)) return
        appendLog(this, timeFormat.format(Date()) + " " + message)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::audio.isInitialized) unmute("service stopped")
        running = false
        if (::prefs.isInitialized) log("Service stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoSkip"
        private const val MAX_BYTES = 200_000L

        @Volatile
        var running = false

        fun logFile(context: Context): File = File(context.filesDir, "autoskip-log.txt")

        fun appendLog(context: Context, line: String) {
            try {
                val file = logFile(context)
                if (file.length() > MAX_BYTES) file.writeText("(older log removed)\n")
                file.appendText(line + "\n")
            } catch (e: Exception) {
                Log.w(TAG, "Could not write the log file", e)
            }
        }
    }
}
