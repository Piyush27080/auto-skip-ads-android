package com.piyush27080.autoskipads

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SkipService : AccessibilityService() {

    private var lastDump = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastDump < 1500) return   // at most one dump every 1.5 seconds
        lastDump = now

        val root = rootInActiveWindow ?: return
        Log.d(TAG, "---- ${root.packageName} ----")
        dump(root, 0)
    }

    private fun dump(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable) {
            Log.d(
                TAG,
                "${"  ".repeat(depth)}[${node.className}] text=$text | desc=$desc | " +
                        "id=${node.viewIdResourceName} | clickable=${node.isClickable}"
            )
        }
        for (i in 0 until node.childCount) dump(node.getChild(i), depth + 1)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "AutoSkip"
    }
}