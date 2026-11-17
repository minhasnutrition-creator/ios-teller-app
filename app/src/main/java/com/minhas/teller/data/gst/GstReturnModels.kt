package com.minhas.teller.data.gst

import java.util.Locale

/**
 * Intermediate GST-return model — the GSP-ready seam.
 *
 * `GstReturnBuilder` classifies the books into this shape; `Gstr1Json` / `Gstr3bJson` serialize it
 * to the official GSTN JSON today. A future GSP/ASP client can POST the same `GstReturn` to a
 * provider API instead of writing a file, without touching the classification logic.
 */

/** Outward-supply categories we can file. `null` on a voucher means "infer". */
object SupplyType {
    const val B2B = "B2B"
    const val B2CL = "B2CL"
    const val B2CS = "B2CS"
    const val EXPORT = "EXPORT"
    const val NIL = "NIL"
    const val EXEMPT = "EXEMPT"
    const val NONGST = "NONGST"

    val all: Set<String> = setOf(B2B, B2CL, B2CS, EXPORT, NIL, EXEMPT, NONGST)
}

/**
 * Money is held as integer paise so totals across thousands of lines sum exactly (the app-wide
 * convention, see utils/Money.kt). The Double rupee getters exist only for JSON / display.
 */
data class GstInvoiceLine(
    val taxablePaise: Long,
    val rate: Double,          // total GST % on this line
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long,
    val cessPaise: Long = 0L,
    val hsnCode: String? = null,
    val quantity: Double = 0.0,
    val unitCode: String? = null // GST UQC (e.g. "KGS", "PCS"); null → "OTH"
) {
    val taxableValue: Double get() = taxablePaise / 100.0
    val cgst: Double get() = cgstPaise / 100.0
    val sgst: Double get() = sgstPaise / 100.0
    val igst: Double get() = igstPaise / 100.0
    val cess: Double get() = cessPaise / 100.0
    val taxPaise: Long get() = cgstPaise + sgstPaise + igstPaise + cessPaise
}

data class GstInvoice(
    val voucherId: Int,
    val invoiceNumber: String,
    val dateText: String,      // dd-MM-yyyy
    val totalPaise: Long,      // taxable + tax (invoice total), in paise
    val placeOfSupply: String, // 2-digit state code
    val supplyType: String,    // one of SupplyType.*
    val reverseCharge: Boolean,
    val recipientGstin: String?, // B2B counterparty GSTIN (null for B2C)
    val recipientName: String?,  // party/customer name (for warnings + review)
    val lines: List<GstInvoiceLine>
) {
    val taxablePaise: Long get() = lines.sumOf { it.taxablePaise }
    val cgstPaise: Long get() = lines.sumOf { it.cgstPaise }
    val sgstPaise: Long get() = lines.sumOf { it.sgstPaise }
    val igstPaise: Long get() = lines.sumOf { it.igstPaise }
    val cessPaise: Long get() = lines.sumOf { it.cessPaise }
    val taxPaise: Long get() = cgstPaise + sgstPaise + igstPaise + cessPaise

    val totalValue: Double get() = totalPaise / 100.0
    val taxableValue: Double get() = taxablePaise / 100.0
    val cgst: Double get() = cgstPaise / 100.0
    val sgst: Double get() = sgstPaise / 100.0
    val igst: Double get() = igstPaise / 100.0
    val cess: Double get() = cessPaise / 100.0
    val totalTax: Double get() = taxPaise / 100.0

    val isInterState: Boolean get() = igstPaise > 0L && (cgstPaise + sgstPaise) == 0L
    val isNilRated: Boolean get() = supplyType == SupplyType.NIL
    val isExempt: Boolean get() = supplyType == SupplyType.EXEMPT
    val isExport: Boolean get() = supplyType == SupplyType.EXPORT
}

data class GstReturn(
    val gstin: String,
    val fp: String,                 // MMYYYY
    val businessStateCode: String?, // 2-digit code of the registered place of business
    val outward: List<GstInvoice>,  // Sales — drives GSTR-1 and GSTR-3B outward
    val inward: List<GstInvoice>,   // Purchases — drives GSTR-3B ITC / inward
    val warnings: List<String>,
    // Sales Returns (credit notes issued to customers) — GSTR-1 cdnr/cdnur, reduce GSTR-3B output tax.
    val creditNotes: List<GstInvoice> = emptyList(),
    // Purchase Returns (goods sent back to suppliers) — reduce GSTR-3B input tax credit.
    val purchaseReturns: List<GstInvoice> = emptyList()
) {
    val grossTurnover: Double get() = outward.sumOf { it.totalPaise } / 100.0
}

/** Round a money amount to 2 decimals (half-up) for the return JSON. */
fun Double.round2(): Double = java.math.BigDecimal.valueOf(this).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

/** Parse a "MMYYYY" period into (month 1..12, year); null if malformed. */
fun parseFp(fp: String): Pair<Int, Int>? {
    if (fp.length != 6) return null
    val m = fp.substring(0, 2).toIntOrNull() ?: return null
    val y = fp.substring(2, 6).toIntOrNull() ?: return null
    return if (m in 1..12) m to y else null
}
