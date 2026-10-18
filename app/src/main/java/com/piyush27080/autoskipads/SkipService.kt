package com.piyush27080.autoskipads

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version 0.2 (diagnostic): watches the YouTube apps and records what it sees.
 * It does not tap anything yet. The log is saved inside the app so it can be shared
 * from the app screen, no computer needed.
 */
class SkipService : AccessibilityService() {

    private var lastScan = 0L
    private var lastHeartbeat = 0L
    private val marker = Regex("(?i)\\bskip\\b|\\bad\\b|sponsored|advertis")
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        writeLog("Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastScan < 1000) return   // look at the screen at most once per second
        lastScan = now

        val root = rootInActiveWindow
        if (root == null) {
            heartbeat(now, "no active window")
            return
        }

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
