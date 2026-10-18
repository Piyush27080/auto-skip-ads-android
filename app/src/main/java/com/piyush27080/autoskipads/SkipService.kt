package com.piyush27080.autoskipads

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version 0.3: taps the Skip button as soon as it appears in the YouTube / YouTube Music apps.
 * It also keeps writing a small log inside the app, so problems can be found without a computer.
 */
class SkipService : AccessibilityService() {

    private var lastTapScan = 0L
    private var lastTap = 0L
    private var lastScan = 0L
    private var lastHeartbeat = 0L

    // exact texts only: "Skip", "Skip ad", "Skip ads" (not countdown texts like "Skip ad in 5")
    private val skipLabel = Regex("(?i)\\s*skip(\\s+ads?)?\\s*")
    private val marker = Regex("(?i)\\bskip\\b|\\bad\\b|sponsored|advertis")
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        writeLog("Service connected (v0.3)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        val root = rootInActiveWindow
        if (root == null) {
            heartbeat(now, "no active window")
            return
        }

        // 1) Tap Skip (looks at the screen up to 4 times per second)
        if (now - lastTapScan >= 250) {
            lastTapScan = now
            trySkipTap(root, now)
        }

        // 2) Diagnostic log (at most once per second)
        if (now - lastScan < 1000) return
        lastScan = now
        val pkg = root.packageName?.toString() ?: "?"
        val lines = ArrayList<String>()
        val interesting = collect(root, 0, lines)
        if (interesting) {
            writeLog("---- $pkg: ad/skip text found (${lines.size} lines) ----")
            for (line in lines) writeLog(line)
        } else {
            heartbeat(now, "$pkg: ${lines.size} lines, no ad/skip text")
        }
    }

    // ---------- tapping ----------

    private fun matchesSkip(value: CharSequence?): Boolean {
        val s = value?.toString() ?: return false
        return skipLabel.matches(s)
    }

    private fun findSkipNode(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        val id = node.viewIdResourceName ?: ""
        val looksLikeSkip =
            matchesSkip(node.text) || matchesSkip(node.contentDescription) ||
                id.contains("skip_ad", ignoreCase = true) || id.contains("skip_button", ignoreCase = true)
        if (looksLikeSkip && node.isVisibleToUser) return node

        for (i in 0 until node.childCount) {
            val found = findSkipNode(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
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

    private fun trySkipTap(root: AccessibilityNodeInfo, now: Long) {
        if (now - lastTap < 800) return   // do not tap more than once per 0.8 seconds
        val node = findSkipNode(root, 0) ?: return
        lastTap = now

        val method = tap(node)
        writeLog(
            "TAP via $method: [${node.className}] text=${node.text} | " +
                "desc=${node.contentDescription} | id=${node.viewIdResourceName}"
        )
        if (method != "failed") addTap()
    }

    private fun addTap() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TAPS, prefs.getInt(KEY_TAPS, 0) + 1).apply()
    }

    // ---------- diagnostic log ----------

    // Collects the texts on screen. Returns true if any text looks like an ad or a Skip button.
    private fun collect(node: AccessibilityNodeInfo?, depth: Int, lines: MutableList<String>): Boolean {
        if (node == null || depth > 40) return false
        var interesting = false

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable) {
            if (lines.size < 400) {
                lines.add(
                    "${"  ".repeat(depth)}[${node.className}] text=$text | desc=$desc | " +
                        "id=${node.viewIdResourceName} | clickable=${node.isClickable}"
                )
            }
            if (marker.containsMatchIn(text ?: "") || marker.containsMatchIn(desc ?: "")) {
                interesting = true
            }
        }

        for (i in 0 until node.childCount) {
            if (collect(node.getChild(i), depth + 1, lines)) interesting = true
        }
        return interesting
    }

    private fun heartbeat(now: Long, message: String) {
        if (now - lastHeartbeat < 5000) return   // one short status line every 5 seconds
        lastHeartbeat = now
        writeLog(message)
    }

    private fun writeLog(message: String) {
        Log.i(TAG, message)
        appendLog(this, timeFormat.format(Date()) + " " + message)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        writeLog("Service stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoSkip"
        private const val MAX_BYTES = 400_000L
        const val PREFS = "autoskip"
        const val KEY_TAPS = "taps"

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
