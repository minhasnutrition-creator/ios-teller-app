package com.minhas.teller.utils

import android.content.Context

/**
 * Per-company, per-period guided-filing progress: which wizard steps the user has marked done for a
 * given filing period (fp = "MMYYYY"). Stored as a StringSet of step ids. Mirrors [GstReturnPrefs],
 * which separately tracks the "last filed" period for the reminder worker.
 */
object GstFilingPrefs {

    private const val PREFS = "gst_filing_prefs"

    fun completedSteps(context: Context, companyId: Int, fp: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(key(companyId, fp), emptySet())?.toSet() ?: emptySet()

    fun isStepDone(context: Context, companyId: Int, fp: String, stepId: String): Boolean =
        stepId in completedSteps(context, companyId, fp)

    fun markStepDone(context: Context, companyId: Int, fp: String, stepId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // getStringSet returns a live reference — copy before mutating, or the edit is lost.
        val current = (prefs.getStringSet(key(companyId, fp), emptySet()) ?: emptySet()).toMutableSet()
        current.add(stepId)
        prefs.edit().putStringSet(key(companyId, fp), current).apply()
    }

    fun unmarkStepDone(context: Context, companyId: Int, fp: String, stepId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(key(companyId, fp), emptySet()) ?: emptySet()).toMutableSet()
        current.remove(stepId)
        prefs.edit().putStringSet(key(companyId, fp), current).apply()
    }

    private fun key(companyId: Int, fp: String) = "gst_steps_${companyId}_$fp"
}
