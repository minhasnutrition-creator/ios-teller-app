package com.minhas.teller.utils

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.minhas.teller.ui.screens.DailyTasksArchive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Employee / showroom codes for the Teller Team app.
 *
 * The code is the only secret protecting an employee's stock, transactions and daily reports in
 * Firebase. The old codes were 6 digits (1,000,000 possibilities), which a script can try in
 * minutes. New codes are 10 characters from a 32-symbol alphabet (~1.1 x 10^15 possibilities) that
 * avoids look-alike characters (no 0/O, 1/I), so they can be read out or typed without mistakes.
 *
 * [migrateLegacyCodes] moves every legacy 6-digit employee to a new code: it copies all five
 * Firebase nodes, then leaves a {moved:true} marker at the old task_config so the employee's app
 * knows to ask for the new code. database.rules.json still accepts 6-digit codes during the
 * changeover; remove that once every manager has opened the updated Teller.
 */
object TeamCodes {

    private const val TAG = "TeamCodes"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 10

    /** Firebase nodes keyed by employee code. */
    private val NODES = listOf("task_config", "task_reports", "stock", "transactions", "pending_transactions")

    private val random = SecureRandom()
    private val migrationLock = Mutex()

    fun generate(existing: Collection<String> = emptyList()): String {
        val used = existing.toSet()
        var code: String
        do {
            code = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        } while (code in used)
        return code
    }

    /** "K7PX92MDQA" -> "K7PX-92MD-QA" for display. Legacy codes are returned unchanged. */
    fun format(code: String): String =
        if (code.length == LENGTH) "${code.substring(0, 4)}-${code.substring(4, 8)}-${code.substring(8)}" else code

    /** A well-formed new-style code (what database.rules.json will accept once legacy codes are retired). */
    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    fun isLegacy(code: String): Boolean = code.length == 6 && code.all { it.isDigit() }

    data class MovedCode(val name: String, val oldCode: String, val newCode: String)

    /**
     * Upgrade every legacy 6-digit code stored on this device. Safe to call repeatedly and from
     * several screens: it is serialized and does nothing once all codes are upgraded. Returns the
     * codes that were moved in this call so the UI can show them to the manager to share.
     */
    suspend fun migrateLegacyCodes(context: Context): List<MovedCode> = migrationLock.withLock {
        val app = context.applicationContext
        val taskPrefs = app.getSharedPreferences("DailyTasksPrefs", Context.MODE_PRIVATE)
        val stockPrefs = app.getSharedPreferences("LiveStockPrefs", Context.MODE_PRIVATE)

        val employees: JSONArray = try {
            JSONArray(taskPrefs.getString("employees", null) ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val allCodes = (0 until employees.length()).map { employees.getJSONObject(it).optString("code") }.toMutableSet()
        val moved = mutableListOf<MovedCode>()

        for (i in 0 until employees.length()) {
            val emp: JSONObject = employees.getJSONObject(i)
            val old = emp.optString("code")
            if (!isLegacy(old)) continue
            val new = generate(allCodes)
            if (!moveNodes(old, new)) continue // network problem: retry next time, keep old code
            allCodes.add(new)
            emp.put("code", new)
            // Persist after each employee so a crash mid-way can't lose a completed move.
            taskPrefs.edit().putString("employees", employees.toString()).commit()
            DailyTasksArchive.rename(app, old, new)
            if (stockPrefs.getString("showroom_code", null) == old) {
                stockPrefs.edit().putString("showroom_code", new).commit()
            }
            moved.add(MovedCode(emp.optString("name"), old, new))
        }

        // Live Stock can also run on a standalone showroom code with no employee attached.
        val showroom = stockPrefs.getString("showroom_code", null)
        if (showroom != null && isLegacy(showroom)) {
            val new = generate(allCodes)
            if (moveNodes(showroom, new)) {
                stockPrefs.edit().putString("showroom_code", new).commit()
                moved.add(MovedCode("Showroom", showroom, new))
            }
        }
        moved
    }

    /** Copy all nodes old -> new, then clear the old ones. Returns false if anything failed. */
    private suspend fun moveNodes(old: String, new: String): Boolean {
        val db = FirebaseDatabase.getInstance()
        return try {
            val copies = mutableMapOf<String, Any?>()
            for (node in NODES) {
                val snap = db.getReference(node).child(old).get().await()
                if (snap?.exists() == true) copies["/$node/$new"] = snap?.value
            }
            if (copies.isNotEmpty()) db.reference.updateChildren(copies).await()

            val clear = mutableMapOf<String, Any?>()
            for (node in NODES) clear["/$node/$old"] = null
            // Marker (no new code in it - anyone guessing the old code must not learn the new one).
            clear["/task_config/$old"] = mapOf("moved" to true, "movedAt" to System.currentTimeMillis())
            db.reference.updateChildren(clear).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not move code $old", e)
            false
        }
    }
}
