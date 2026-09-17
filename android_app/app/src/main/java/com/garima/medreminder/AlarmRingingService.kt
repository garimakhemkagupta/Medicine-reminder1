package com.garima.medreminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

const val CHANNEL_ID = "medicine_reminders"
const val ACTION_STOP = "com.garima.medreminder.ACTION_STOP"

/**
 * Foreground service that actually rings + vibrates + shows the persistent
 * notification. Runs every time ReminderReceiver fires (original dose time
 * AND every 5-minute snooze) and stops itself once the sound has looped a
 * couple of times or the user taps STOP.
 */
class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
            AlarmSchedulerKeyed.cancel(this, requestCode)
            stopRinging()
            return START_NOT_STICKY
        }

        val medicine = intent?.getStringExtra(EXTRA_MEDICINE) ?: "Medicine"
        val dosage = intent?.getStringExtra(EXTRA_DOSAGE) ?: ""
        val note = intent?.getStringExtra(EXTRA_NOTE) ?: ""
        val requestCode = intent?.getIntExtra(EXTRA_REQUEST_CODE, 0) ?: 0

        createChannel()
        startForeground(1001, buildNotification(medicine, dosage, requestCode))
        startRinging()

        // Also launch the full-screen "take your medicine" activity.
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_MEDICINE, medicine)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        startActivity(fullScreenIntent)

        return START_NOT_STICKY
    }

    private fun startRinging() {
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmRingingService, alarmUri)
            isLooping = true
            prepare()
            start()
        }
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarms reminding you to take your eye medicine"
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(medicine: String, dosage: String, requestCode: Int): Notification {
        val stopIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        val stopPI = PendingIntent.getService(
            this, requestCode, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_MEDICINE, medicine)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        val fullScreenPI = PendingIntent.getActivity(
            this, requestCode, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to take: $medicine")
            .setContentText(dosage.ifBlank { "Tap to open" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPI, true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP (Taken)", stopPI)
            .build()
    }
}
