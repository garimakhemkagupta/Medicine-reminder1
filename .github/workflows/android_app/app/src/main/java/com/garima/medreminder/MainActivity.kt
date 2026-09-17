package com.garima.medreminder

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        val doses = ScheduleRepository.loadAll(this)
        val upcoming = doses.filter { it.triggerMillis() > System.currentTimeMillis() }

        findViewById<TextView>(R.id.textSummary).text =
            "Loaded ${doses.size} total reminders.\n${upcoming.size} are still upcoming."

        findViewById<Button>(R.id.buttonActivate).setOnClickListener {
            if (!hasExactAlarmPermission()) {
                requestExactAlarmPermission()
                return@setOnClickListener
            }
            AlarmScheduler.scheduleAll(this, upcoming)
            Toast.makeText(
                this,
                "Activated! ${upcoming.size} reminders scheduled.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Please allow \"Alarms & reminders\" for this app, then tap Activate again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
