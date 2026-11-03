package com.qingping.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.qingping.app.data.AppStat
import com.qingping.app.data.Prefs
import com.qingping.app.data.StatsRepository
import com.qingping.app.data.StatsSnapshot
import com.qingping.app.service.GuardService
import com.qingping.app.util.AccessibilityUtils
import com.qingping.app.util.PackageUtils
import com.qingping.app.util.SystemSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var btnAccessibility: MaterialButton

    private lateinit var tvTotal: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvAppCount: TextView
    private lateinit var statsContainer: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var btnClearStats: TextView

    private lateinit var switchSplash: SwitchMaterial
    private lateinit var switchJump: SwitchMaterial
    private lateinit var switchBoot: SwitchMaterial

    private lateinit var btnBattery: MaterialButton
    private lateinit var btnAutostart: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSwitches()
        setupClicks()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()

        if (Prefs.isBootEnabled(this)) {
            GuardService.start(this)
        }

        refreshStatus()
        refreshStats()
        updateBatteryState()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        btnAccessibility = findViewById(R.id.btnAccessibility)

        tvTotal = findViewById(R.id.tvTotal)
        tvToday = findViewById(R.id.tvToday)
        tvAppCount = findViewById(R.id.tvAppCount)
        statsContainer = findViewById(R.id.statsContainer)
        emptyState = findViewById(R.id.emptyState)
        btnClearStats = findViewById(R.id.btnClearStats)

        switchSplash = findViewById(R.id.switchSplash)
        switchJump = findViewById(R.id.switchJump)
        switchBoot = findViewById(R.id.switchBoot)

        btnBattery = findViewById(R.id.btnBattery)
        btnAutostart = findViewById(R.id.btnAutostart)
    }

    private fun setupSwitches() {
        switchSplash.isChecked = Prefs.isSplashEnabled(this)
        switchJump.isChecked = Prefs.isJumpGuardEnabled(this)
        switchBoot.isChecked = Prefs.isBootEnabled(this)

        switchSplash.setOnCheckedChangeListener { _, checked ->
            Prefs.setSplashEnabled(this, checked)
            if (checked) ensureAccessibility()
        }

        switchJump.setOnCheckedChangeListener { _, checked ->
            Prefs.setJumpGuardEnabled(this, checked)
            if (checked) ensureAccessibility()
        }

        switchBoot.setOnCheckedChangeListener { _, checked ->
            Prefs.setBootEnabled(this, checked)
            if (checked) {
                GuardService.start(this)
                requestNotificationPermission()
                Snackbar.make(
                    root,
                    getString(R.string.toast_guard_started),
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                GuardService.stop(this)
            }
        }
    }

    private fun setupClicks() {
        btnAccessibility.setOnClickListener {
            AccessibilityUtils.openSettings(this)
        }
        btnBattery.setOnClickListener {
            requestBatteryExemption()
        }
        btnAutostart.setOnClickListener {
            SystemSettings.openAutoStart(this)
        }
        btnClearStats.setOnClickListener {
            confirmClearStats()
        }
    }

    private fun ensureAccessibility() {
        if (!AccessibilityUtils.isEnabled(this)) {
            Snackbar.make(
                root,
                getString(R.string.toast_accessibility_needed),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.btn_open_accessibility)) {
                AccessibilityUtils.openSettings(this)
            }.show()
        }
    }

    private fun refreshStatus() {
        val enabled = AccessibilityUtils.isEnabled(this)

        statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.success else R.color.warning
            )
        )

        statusTitle.setText(
            if (enabled) R.string.status_on else R.string.status_off
        )
        statusDetail.setText(
            if (enabled) R.string.status_on_detail else R.string.status_off_detail
        )
        btnAccessibility.setText(
            if (enabled) {
                R.string.btn_accessibility_settings
            } else {
                R.string.btn_open_accessibility
            }
        )
    }

    private fun refreshStats() {
        StatsRepository.load(this) { snapshot ->
            if (!isFinishing && !isDestroyed) {
                renderSnapshot(snapshot)
            }
        }
    }

    private fun renderSnapshot(snapshot: StatsSnapshot) {
        tvTotal.text = snapshot.summary.total.toString()
        tvToday.text = snapshot.summary.today.toString()
        tvAppCount.text = snapshot.summary.appCount.toString()

        statsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (stat in snapshot.apps) {
            val row = inflater.inflate(R.layout.item_app_stat, statsContainer, false)
            bindStatRow(row, stat)
            statsContainer.addView(row)
        }

        emptyState.visibility =
            if (snapshot.apps.isEmpty()) View.VISIBLE else View.GONE
        btnClearStats.isEnabled = snapshot.apps.isNotEmpty()
        btnClearStats.alpha = if (snapshot.apps.isEmpty()) 0.4f else 1f
    }

    private fun bindStatRow(row: View, stat: AppStat) {
        val iconView = row.findViewById<ImageView>(R.id.ivAppIcon)
        val icon = PackageUtils.appIcon(this, stat.packageName)
        if (icon != null) {
            iconView.setImageDrawable(icon)
        } else {
            iconView.setImageResource(R.drawable.ic_launcher)
        }

        row.findViewById<TextView>(R.id.tvAppName).text = stat.label
        row.findViewById<TextView>(R.id.tvAppPackage).text = stat.packageName
        row.findViewById<TextView>(R.id.tvAppMeta).text = getString(
            R.string.stat_meta,
            stat.splash,
            stat.jump,
            formatRelative(stat.lastAt)
        )
        row.findViewById<TextView>(R.id.tvCount).text = stat.total.toString()
    }

    private fun formatRelative(timestamp: Long): String {
        if (timestamp <= 0L) return ""

        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000L -> getString(R.string.time_just_now)
            diff < 3_600_000L -> getString(
                R.string.time_minutes_ago,
                (diff / 60_000L).toInt()
            )
            diff < 86_400_000L -> getString(
                R.string.time_hours_ago,
                (diff / 3_600_000L).toInt()
            )
            else -> SimpleDateFormat(
                "MM-dd HH:mm",
                Locale.getDefault()
            ).format(Date(timestamp))
        }
    }

    private fun confirmClearStats() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_stats_title)
            .setMessage(R.string.clear_stats_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                StatsRepository.clear(this) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this,
                            R.string.toast_stats_cleared,
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshStats()
                    }
                }
            }
            .show()
    }

    private fun updateBatteryState() {
        val powerManager = getSystemService(PowerManager::class.java)
        val ignoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        btnBattery.setText(
            if (ignoring) R.string.btn_battery_done else R.string.btn_battery
        )
    }

    private fun requestBatteryExemption() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(
                this,
                R.string.toast_battery_done,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    R.string.toast_open_settings_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION = 1001
    }
}
