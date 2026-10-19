package com.piyush27080.autoskipads

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var setupCard: View
    private lateinit var skippedCount: TextView
    private lateinit var timeSaved: TextView
    private lateinit var enabledSwitch: SwitchCompat
    private lateinit var muteSwitch: SwitchCompat
    private lateinit var fastForwardSwitch: SwitchCompat
    private lateinit var debugSwitch: SwitchCompat

    private val handler = Handler(Looper.getMainLooper())
    private var updating = false

    // refresh the screen whenever a setting or counter changes
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    // refresh once per second while the screen is open (so "Service connected" updates)
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = Prefs.of(this)
        statusText = findViewById(R.id.statusText)
        setupCard = findViewById(R.id.setupCard)
        skippedCount = findViewById(R.id.skippedCount)
        timeSaved = findViewById(R.id.timeSaved)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        muteSwitch = findViewById(R.id.muteSwitch)
        fastForwardSwitch = findViewById(R.id.fastForwardSwitch)
        debugSwitch = findViewById(R.id.debugSwitch)

        bindSwitch(enabledSwitch, Prefs.ENABLED)
        bindSwitch(muteSwitch, Prefs.MUTE_ADS)
        bindSwitch(fastForwardSwitch, Prefs.FAST_FORWARD)
        bindSwitch(debugSwitch, Prefs.DEBUG_LOG)

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.appSettingsButton).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            prefs.edit().putInt(Prefs.ADS_SKIPPED, 0).putLong(Prefs.SECONDS_SAVED, 0L).apply()
            Toast.makeText(this, "Stats reset", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.shareLogButton).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            SkipService.logFile(this).delete()
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        findViewById<TextView>(R.id.footerText).text =
            "Works with YouTube and YouTube Music" + (if (version != null) "  ·  v$version" else "") +
                "\nEducational project, not affiliated with YouTube or Google"
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacks(ticker)
    }

    private fun bindSwitch(switch: SwitchCompat, key: String) {
        switch.setOnCheckedChangeListener { _, checked ->
            if (!updating) prefs.edit().putBoolean(key, checked).apply()
        }
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(packageName)
    }

    private fun formatTime(total: Long): String {
        if (total < 60) return "${total}s"
        val minutes = total / 60
        if (minutes < 60) return "${minutes}m ${total % 60}s"
        return "${minutes / 60}h ${minutes % 60}m"
    }

    private fun render() {
        updating = true
        enabledSwitch.isChecked = prefs.getBoolean(Prefs.ENABLED, true)
        muteSwitch.isChecked = prefs.getBoolean(Prefs.MUTE_ADS, true)
        fastForwardSwitch.isChecked = prefs.getBoolean(Prefs.FAST_FORWARD, false)
        debugSwitch.isChecked = prefs.getBoolean(Prefs.DEBUG_LOG, false)

        skippedCount.text = prefs.getInt(Prefs.ADS_SKIPPED, 0).toString()
        timeSaved.text = formatTime(prefs.getLong(Prefs.SECONDS_SAVED, 0L))

        val accessibilityOn = isAccessibilityOn()
        setupCard.visibility = if (accessibilityOn) View.GONE else View.VISIBLE

        when {
            !accessibilityOn -> setStatus("Service is off", R.color.asa_red)
            !prefs.getBoolean(Prefs.ENABLED, true) -> setStatus("Paused", R.color.asa_red)
            !SkipService.running -> setStatus("Starting... (see \"Service keeps stopping?\" if this stays)", R.color.asa_amber)
            else -> setStatus("Active", R.color.asa_green)
        }
        updating = false
    }

    private fun setStatus(text: String, colorRes: Int) {
        statusText.text = text
        statusText.setTextColor(resources.getColor(colorRes, theme))
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
