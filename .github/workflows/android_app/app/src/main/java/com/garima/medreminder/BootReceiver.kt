package com.garima.medreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android clears ALL AlarmManager alarms on reboot, so we must re-schedule
 * every remaining dose the moment the phone comes back on.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val upcoming = ScheduleRepository.loadUpcoming(context)
            AlarmScheduler.scheduleAll(context, upcoming)
        }
    }
}
