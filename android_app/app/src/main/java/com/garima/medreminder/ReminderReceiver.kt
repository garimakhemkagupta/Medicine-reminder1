package com.garima.medreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires both for the ORIGINAL dose time and for every 5-minute snooze
 * re-ring. Either way: start the alarm UI + sound, and immediately queue up
 * the *next* snooze so the alarm keeps nagging until the user hits STOP.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicine = intent.getStringExtra(EXTRA_MEDICINE) ?: return
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)

        // Re-use the ORIGINAL requestCode so the snooze chain is cancellable.
        AlarmSchedulerKeyed.scheduleSnoozeWithCode(context, requestCode, medicine, dosage, note)

        // Start the foreground service that plays the alarm sound/vibration
        // and shows the full-screen "take your medicine" screen + notification.
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(EXTRA_MEDICINE, medicine)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        context.startForegroundService(serviceIntent)
    }
}

/** Small helper so ReminderReceiver can re-arm the snooze without needing a
 * fully-dated Dose object (we only know the original requestCode by then). */
object AlarmSchedulerKeyed {
    fun scheduleSnoozeWithCode(context: Context, requestCode: Int, medicine: String, dosage: String, note: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MEDICINE, medicine)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val pi = android.app.PendingIntent.getBroadcast(
            context, requestCode, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** Cancels the pending snooze re-ring for a given requestCode - call this
     * when the user presses STOP / "Taken". */
    fun cancel(context: Context, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = android.app.PendingIntent.getBroadcast(
            context, requestCode, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
