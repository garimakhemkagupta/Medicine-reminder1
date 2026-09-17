package com.garima.medreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

const val EXTRA_MEDICINE = "extra_medicine"
const val EXTRA_DOSAGE = "extra_dosage"
const val EXTRA_NOTE = "extra_note"
const val EXTRA_REQUEST_CODE = "extra_request_code"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"

/** How often the alarm re-fires ("snoozes") on its own until the user stops it. */
const val SNOOZE_MINUTES = 5L

object AlarmScheduler {

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntentFor(context: Context, dose: Dose, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MEDICINE, dose.medicine)
            putExtra(EXTRA_DOSAGE, dose.dosage)
            putExtra(EXTRA_NOTE, dose.note)
            putExtra(EXTRA_REQUEST_CODE, dose.requestCode())
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            dose.requestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedules the ORIGINAL alarm for a dose at its prescribed time. */
    fun scheduleDose(context: Context, dose: Dose) {
        val pi = pendingIntentFor(context, dose, isSnooze = false)
        setExact(context, dose.triggerMillis(), pi)
    }

    /** Schedules the next SNOOZE re-ring, SNOOZE_MINUTES after "now". Re-uses
     * the same requestCode so it can be found & cancelled by cancelForDose(). */
    fun scheduleSnooze(context: Context, dose: Dose) {
        val pi = pendingIntentFor(context, dose, isSnooze = true)
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setExact(context, triggerAt, pi)
    }

    /** Called when the user presses STOP / "Taken" - cancels any pending
     * snooze re-ring for this dose so it stops nagging. */
    fun cancelForDose(context: Context, dose: Dose) {
        val pi = pendingIntentFor(context, dose, isSnooze = true)
        alarmManager(context).cancel(pi)
    }

    private fun setExact(context: Context, triggerAtMillis: Long, pi: PendingIntent) {
        val am = alarmManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Permission not yet granted - MainActivity should have asked for
            // it, but fall back to an inexact alarm rather than crashing.
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            return
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    /** Schedules every upcoming dose. Call once from MainActivity after the
     * user taps "Activate reminders", and again from BootReceiver after a
     * phone restart (all alarms are cleared on reboot). */
    fun scheduleAll(context: Context, doses: List<Dose>) {
        doses.forEach { scheduleDose(context, it) }
    }
}
