package com.minhas.teller.ui.screens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Permanent on-device store for Daily Tasks reports.
 *
 * Firebase is used only as a live sync channel for *today's* report. As soon as a day is in the
 * past, the manager's Teller app copies that day's report into this local archive and deletes it
 * from Firebase — so the Realtime Database never accumulates history and the project stays on the
 * free tier. All past reports live here, one JSON file per employee code, and the manager can
 * delete them by date from inside the app.
 */
object DailyTasksArchive {

    private fun dir(context: Context): File =
        File(context.filesDir, "daily_tasks").apply { if (!exists()) mkdirs() }

    private fun file(context: Context, code: String): File = File(dir(context), "$code.json")

    /** Upsert the given reports (keyed by date) into this employee's local archive. */
    fun archive(context: Context, code: String, reports: List<DailyReport>) {
        if (reports.isEmpty()) return
        val byDate = loadRaw(context, code).associateBy { it.date }.toMutableMap()
        reports.forEach { byDate[it.date] = it }
        save(context, code, byDate.values.toList())
    }

    /** All archived reports for an employee, newest first. */
    fun load(context: Context, code: String): List<DailyReport> =
        loadRaw(context, code).sortedByDescending { it.date }

    /** Remove the given dates from the local archive. */
    fun delete(context: Context, code: String, dates: Set<String>) {
        if (dates.isEmpty()) return
        val kept = loadRaw(context, code).filter { it.date !in dates }
        save(context, code, kept)
    }

    /** Move an employee's local archive to a new code (used when a legacy code is upgraded). */
    fun rename(context: Context, oldCode: String, newCode: String) {
        try {
            val from = file(context, oldCode)
            if (from.exists()) from.renameTo(file(context, newCode))
        } catch (_: Exception) {
        }
    }

    /** Delete this employee's entire local archive (used when the employee is removed). */
    fun deleteAll(context: Context, code: String) {
        try {
            val f = file(context, code)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    private fun loadRaw(context: Context, code: String): List<DailyReport> {
        val f = file(context, code)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, code: String, reports: List<DailyReport>) {
        val arr = JSONArray()
        reports.forEach { arr.put(toJson(it)) }
        try {
            file(context, code).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun toJson(r: DailyReport): JSONObject {
        val o = JSONObject()
        o.put("date", r.date)
        o.put("status", r.status)
        o.put("arrival", r.arrival)
        o.put("departure", r.departure)
        o.put("managerComments", r.managerComments)
        o.put("submittedAt", r.submittedAt)
        val entries = JSONArray()
        r.entries.forEach { e ->
            val eo = JSONObject()
            eo.put("id", e.id)
            eo.put("label", e.label)
            eo.put("type", e.type)
            eo.put("done", e.done)
            eo.put("value", e.value)
            eo.put("remarks", e.remarks)
            entries.put(eo)
        }
        o.put("entries", entries)
        return o
    }

    private fun fromJson(o: JSONObject): DailyReport {
        val entriesArr = o.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until entriesArr.length()).map { i ->
            val eo = entriesArr.getJSONObject(i)
            ReportEntry(
                id = eo.optString("id"),
                label = eo.optString("label"),
                type = eo.optString("type", TASK_TYPE_CHECK),
                done = eo.optBoolean("done", false),
                value = eo.optString("value"),
                remarks = eo.optString("remarks")
            )
        }
        return DailyReport(
            date = o.optString("date"),
            status = o.optString("status", "PENDING"),
            arrival = o.optString("arrival"),
            departure = o.optString("departure"),
            managerComments = o.optString("managerComments"),
            submittedAt = o.optLong("submittedAt", 0L),
            entries = entries
        )
    }
}
