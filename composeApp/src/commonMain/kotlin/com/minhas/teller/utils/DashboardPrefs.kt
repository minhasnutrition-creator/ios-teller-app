package com.minhas.teller.utils

import android.content.Context
import android.content.SharedPreferences
import com.minhas.teller.SessionManager

object DashboardPrefs {
    private const val PREF_NAME = "dashboard_prefs"
    private const val KEY_VISIBLE_METRICS = "visible_metrics"

    // Default Visible Metrics
    val ALL_METRICS = setOf(
        "Total Sales", "Total Purchase", "Total Receipt", "Total Payment",
        "Money to Collect", "Money to Pay", "Cash in Hand", "Bank Balance"
    )

    private val DEFAULT_METRICS = setOf(
        "Total Sales", "Total Purchase", "Money to Collect", "Money to Pay"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Per-company key helper. Business/invoice settings are namespaced by the
     * active company so that, for example, tax can be ON for one company and OFF
     * for another at the same time.
     *
     * When no company id is supplied we fall back to the currently active company
     * (SessionManager). When there is no active company at all we use the plain,
     * un-scoped legacy key — which also lets us honour values saved before this
     * per-company change was introduced (see the `contains()` fallbacks below).
     */
    private fun scopedKey(base: String, companyId: Int?): String {
        val id = companyId ?: SessionManager.currentCompanyId.value
        return if (id != null && id > 0) "${base}__c$id" else base
    }

    fun getVisibleMetrics(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_VISIBLE_METRICS, DEFAULT_METRICS) ?: DEFAULT_METRICS
    }

    fun setVisibleMetrics(context: Context, metrics: Set<String>) {
        prefs(context).edit().putStringSet(KEY_VISIBLE_METRICS, metrics).apply()
    }

    // ─── Tax system master switch (per company) ───────────────────────────────
    private const val KEY_TAX_SYSTEM_ENABLED = "tax_system_enabled"

    fun isTaxSystemEnabled(context: Context, companyId: Int? = null): Boolean {
        val p = prefs(context)
        val key = scopedKey(KEY_TAX_SYSTEM_ENABLED, companyId)
        // Honour a per-company choice once made; otherwise fall back to the legacy
        // global value so existing users don't silently lose their setting.
        // Tax is ON by default — the user turns it off explicitly if they don't want it.
        return if (p.contains(key)) p.getBoolean(key, true)
        else p.getBoolean(KEY_TAX_SYSTEM_ENABLED, true)
    }

    fun setTaxSystemEnabled(context: Context, enabled: Boolean, companyId: Int? = null) {
        prefs(context).edit().putBoolean(scopedKey(KEY_TAX_SYSTEM_ENABLED, companyId), enabled).apply()
    }

    // ─── Smart Tax Estimation (per company) ───────────────────────────────────
    // When ON, item HSN codes and GST rates are auto-detected. Only meaningful
    // while the tax system master switch is ON. Defaults to ON.
    private const val KEY_SMART_TAX_ESTIMATION = "smart_tax_estimation"

    fun isSmartTaxEstimationEnabled(context: Context, companyId: Int? = null): Boolean {
        return prefs(context).getBoolean(scopedKey(KEY_SMART_TAX_ESTIMATION, companyId), true)
    }

    fun setSmartTaxEstimationEnabled(context: Context, enabled: Boolean, companyId: Int? = null) {
        prefs(context).edit().putBoolean(scopedKey(KEY_SMART_TAX_ESTIMATION, companyId), enabled).apply()
    }

    // ─── Overview card display mode ────────────────────────────────────────────
    enum class OverviewMode(val displayName: String) {
        SALES_VS_PURCHASE("Sales & Purchases"),
        MONTH_OVER_MONTH("Month-over-Month"),
        YEAR_OVER_YEAR("Year-over-Year"),
        CASH_IN_VS_OUT("Cash In vs Out"),
        MONEY_FLOW("Money Flow")
    }

    private const val KEY_OVERVIEW_MODE = "overview_mode"

    fun getOverviewMode(context: Context, companyId: Int? = null): OverviewMode {
        val p = prefs(context)
        val key = scopedKey(KEY_OVERVIEW_MODE, companyId)
        val name = p.getString(key, null) ?: return OverviewMode.SALES_VS_PURCHASE
        return try { OverviewMode.valueOf(name) } catch (_: Exception) { OverviewMode.SALES_VS_PURCHASE }
    }

    fun setOverviewMode(context: Context, mode: OverviewMode, companyId: Int? = null) {
        val p = prefs(context)
        val key = scopedKey(KEY_OVERVIEW_MODE, companyId)
        p.edit().putString(key, mode.name).apply()
    }

    /**
     * Invoice layouts, in the order they appear in the picker. Append new ones at the end — the
     * position is how users refer to them ("the 3rd one"), so reordering silently changes what a
     * saved preference means for anyone describing it.
     */
    enum class InvoiceTemplate(val displayName: String) {
        BLUE_YELLOW("Vibrant Blue-Yellow"),
        MAROON_SIDEBAR("Maroon Sidebar"),
        BLACK_ORANGE("Modern Black-Orange"),
        TEAL_CLEAN("Teal Clean Corporate"),
        GST_CLASSIC("GST Tax Invoice (Classic)"),
        MINIMAL_MONO("Minimal Mono"),
        ROYAL_INDIGO("Royal Indigo"),
        GREEN_RETAIL("Fresh Green Retail"),
        LETTERHEAD("Letterhead Friendly")
    }

    // ─── Invoice template (per company) ───────────────────────────────────────
    private const val KEY_INVOICE_TEMPLATE = "invoice_template"

    fun getInvoiceTemplate(context: Context, companyId: Int? = null): InvoiceTemplate {
        val p = prefs(context)
        val key = scopedKey(KEY_INVOICE_TEMPLATE, companyId)
        val name = if (p.contains(key)) p.getString(key, null)
        else p.getString(KEY_INVOICE_TEMPLATE, null)
        // Default is the Modern Black-Orange layout: clean, lightly styled, prints well in mono.
        return try { InvoiceTemplate.valueOf(name ?: InvoiceTemplate.BLACK_ORANGE.name) }
        catch (e: Exception) { InvoiceTemplate.BLACK_ORANGE }
    }

    fun setInvoiceTemplate(context: Context, template: InvoiceTemplate, companyId: Int? = null) {
        prefs(context).edit().putString(scopedKey(KEY_INVOICE_TEMPLATE, companyId), template.name).apply()
    }

    // ─── Tax country (per company) ────────────────────────────────────────────
    private const val KEY_TAX_COUNTRY = "tax_country"

    fun getTaxCountry(context: Context, companyId: Int? = null): String {
        val p = prefs(context)
        val key = scopedKey(KEY_TAX_COUNTRY, companyId)
        return if (p.contains(key)) (p.getString(key, "India") ?: "India")
        else (p.getString(KEY_TAX_COUNTRY, "India") ?: "India")
    }

    fun setTaxCountry(context: Context, country: String, companyId: Int? = null) {
        prefs(context).edit().putString(scopedKey(KEY_TAX_COUNTRY, companyId), country).apply()
    }

    // ─── Voucher Experience Mode (per company) ────────────────────────────────
    // Simple = auto-assign sale account (hides "Sale Type" picker)
    // Advanced (default) = full manual control, existing behaviour unchanged
    private const val KEY_SIMPLE_VOUCHER_MODE = "simple_voucher_mode"

    fun isSimpleVoucherMode(context: Context, companyId: Int? = null): Boolean {
        val p = prefs(context)
        val key = scopedKey(KEY_SIMPLE_VOUCHER_MODE, companyId)
        return if (p.contains(key)) p.getBoolean(key, false)
        else p.getBoolean(KEY_SIMPLE_VOUCHER_MODE, false) // default: Advanced mode
    }

    fun setSimpleVoucherMode(context: Context, enabled: Boolean, companyId: Int? = null) {
        prefs(context).edit().putBoolean(scopedKey(KEY_SIMPLE_VOUCHER_MODE, companyId), enabled).apply()
    }
}
