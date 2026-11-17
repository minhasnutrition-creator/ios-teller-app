package com.minhas.teller.utils

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Central money handling.
 *
 * Money is stored as integer paise (Long) so amounts sum exactly — `0.1 + 0.2` is no
 * longer `0.30000000000000004`. Rates, quantities, and percentages remain Double: they
 * are measures, not currency, and are never summed as money.
 *
 * Rule of thumb: if a value is ever summed or compared as currency, store it in paise.
 * Convert to/from Double only at the input/output boundary.
 */

/** Number of paise in one rupee. */
const val PAISE_PER_RUPEE = 100L

/**
 * Parses a rupee amount string into paise, e.g. "12.34" -> 1234.
 * Returns null for blank or unparseable input, matching the old `toDoubleOrNull()`.
 */
fun String.parseToPaise(): Long? {
    val d = toDoubleOrNull() ?: return null
    if (d.isNaN() || d.isInfinite()) return null
    return (d * PAISE_PER_RUPEE).roundToLong()
}

/** Parses a rupee amount string into paise, defaulting to 0 — replaces `toDoubleOrNull() ?: 0.0`. */
fun String.parseToPaiseOrZero(): Long = parseToPaise() ?: 0L

/** Converts a rupee Double to paise, e.g. 12.34 -> 1234. Use only at the rate/qty math boundary. */
fun Double.rupeesToPaise(): Long = (this * PAISE_PER_RUPEE).roundToLong()

/** Converts paise to a rupee Double, e.g. 1234 -> 12.34. Use only for math; never for summing. */
fun Long.paiseToRupees(): Double = this.toDouble() / PAISE_PER_RUPEE

/**
 * [ratePercent]% of [basePaise], rounded half-up to the nearest paisa, e.g. 9% of 10005 -> 900.
 * Exact decimal maths (no Double drift): this is how a single GST component is computed.
 */
fun percentOfPaise(basePaise: Long, ratePercent: Double): Long =
    java.math.BigDecimal.valueOf(basePaise)
        .multiply(java.math.BigDecimal.valueOf(ratePercent))
        .divide(java.math.BigDecimal(100), 0, java.math.RoundingMode.HALF_UP)
        .toLong()

/**
 * Tax-exclusive base of a tax-inclusive [grossPaise] at [ratePercent]%, rounded half-up,
 * e.g. 11800 at 18% -> 10000. Used for legacy rows that only stored the gross amount.
 */
fun backOutTaxPaise(grossPaise: Long, ratePercent: Double): Long {
    if (ratePercent <= 0.0) return grossPaise
    return java.math.BigDecimal.valueOf(grossPaise)
        .multiply(java.math.BigDecimal(100))
        .divide(java.math.BigDecimal(100).add(java.math.BigDecimal.valueOf(ratePercent)), 0, java.math.RoundingMode.HALF_UP)
        .toLong()
}

/** Formats paise as a currency string, e.g. 1234 -> "₹12.34". */
fun Long.formatMoney(symbol: String = "₹"): String =
    String.format(Locale.US, "%s%.2f", symbol, paiseToRupees())

/** Formats paise as a bare number string, e.g. 1234 -> "12.34". */
fun Long.formatAmount(): String = String.format(Locale.US, "%.2f", paiseToRupees())
