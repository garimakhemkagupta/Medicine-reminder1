package com.garima.medreminder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen "take your medicine" screen shown over the lock screen.
 * The ONLY way to stop the 5-minute snooze loop is to tap STOP here (or on
 * the notification action) - closing/ignoring the screen does NOT cancel it.
 */
class AlarmRingingActivity : AppCompatActivity() {

    private var requestCode: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_ringing)

        val medicine = intent.getStringExtra(EXTRA_MEDICINE) ?: "Medicine"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)

        findViewById<TextView>(R.id.textMedicineName).text = medicine
        findViewById<TextView>(R.id.textDosage).text = dosage
        findViewById<TextView>(R.id.textNote).text = note

        findViewById<Button>(R.id.buttonStop).setOnClickListener {
            val stopIntent = Intent(this, AlarmRingingService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_REQUEST_CODE, requestCode)
            }
            startService(stopIntent)
            finish()
        }
    }
}
