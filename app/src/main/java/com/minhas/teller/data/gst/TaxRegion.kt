package com.minhas.teller.data.gst

/**
 * Region gate for tax features. The GST (GSTR-1 / GSTR-3B) filing flow is India-specific, so the
 * returns screen and the reminder worker only activate for Indian companies. Other countries are
 * added later as separate providers behind the same seam.
 */
object TaxRegion {

    /** True when the company should use India GST filing. Empty/unset counts as India — the app is
     *  India-first and existing companies predate the country field. */
    fun isIndia(country: String?): Boolean {
        if (country.isNullOrBlank()) return true
        val c = country.trim().lowercase()
        return c == "india" || c == "in" || c == "भारत" || c == "bharat"
    }
}
