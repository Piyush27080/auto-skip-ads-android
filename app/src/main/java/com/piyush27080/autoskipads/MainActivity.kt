package com.piyush27080.autoskipads

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { updateStatus() }
        findViewById<Button>(R.id.shareLogButton).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            SkipService.logFile(this).delete()
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun isSwitchOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(packageName)
    }

    private fun updateStatus() {
        val file = SkipService.logFile(this)
        val logKb = if (file.exists()) file.length() / 1024 else 0
        statusText.text =
            "Accessibility switch: " + (if (isSwitchOn()) "ON" else "OFF") + "\n" +
            "Service connected: " + (if (SkipService.running) "YES" else "NO") + "\n" +
            "Saved log size: " + logKb + " KB"
    }

    private fun shareLog() {
        val file = SkipService.logFile(this)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "The log is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val text = file.readText().takeLast(40_000)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share log"))
    }
}
