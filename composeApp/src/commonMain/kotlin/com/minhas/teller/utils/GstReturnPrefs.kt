package com.minhas.teller.utils

import android.content.Context

/**
 * Per-company GST filing state: which return period was last marked "filed", tracked separately for
 * GSTR-1 and GSTR-3B (they have different due dates). The reminder worker reads these to stop
 * nagging for a period the user has already filed.
 */
object GstReturnPrefs {

    private const val PREFS = "gst_return_prefs"

    fun lastFiledGstr1(context: Context, companyId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(companyId, "gstr1"), null)

    fun lastFiledGstr3b(context: Context, companyId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(companyId, "gstr3b"), null)

    fun setLastFiledGstr1(context: Context, companyId: Int, period: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(companyId, "gstr1"), period).apply()
    }

    fun setLastFiledGstr3b(context: Context, companyId: Int, period: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(companyId, "gstr3b"), period).apply()
    }

    private fun key(companyId: Int, returnType: String) = "last_filed_${returnType}_$companyId"
}
