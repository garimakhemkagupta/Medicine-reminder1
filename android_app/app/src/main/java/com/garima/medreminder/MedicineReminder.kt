package com.garima.medreminder

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * One dose event: "take NEPAROW EYE DROP at 2026-09-17 07:00".
 */
data class Dose(
    val date: String,      // yyyy-MM-dd
    val time: String,      // HH:mm
    val medicine: String,
    val dosage: String,
    val type: String,      // "eye_drop" or "oral"
    val note: String
) {
    /** Unique, stable ID used both as the AlarmManager requestCode and the
     * notification ID, so we can always find & cancel the right alarm. */
    fun requestCode(): Int = "$date|$time|$medicine".hashCode()

    fun triggerMillis(): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse("$date $time")!!
        return cal.timeInMillis
    }
}

object ScheduleRepository {

    /** Reads app/src/main/assets/medicine_schedule.json (produced by
     * schedule_generator.py) and returns every dose as a Dose object. */
    fun loadAll(context: Context): List<Dose> {
        val json = context.assets.open("medicine_schedule.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val doses = root.getJSONArray("doses")
        val result = mutableListOf<Dose>()
        for (i in 0 until doses.length()) {
            val o = doses.getJSONObject(i)
            result.add(
                Dose(
                    date = o.getString("date"),
                    time = o.getString("time"),
                    medicine = o.getString("medicine"),
                    dosage = o.getString("dosage"),
                    type = o.getString("type"),
                    note = o.optString("note", "")
                )
            )
        }
        return result
    }

    /** Only doses that are still in the future - no point scheduling the past. */
    fun loadUpcoming(context: Context): List<Dose> {
        val now = System.currentTimeMillis()
        return loadAll(context).filter { it.triggerMillis() > now }
    }
}
