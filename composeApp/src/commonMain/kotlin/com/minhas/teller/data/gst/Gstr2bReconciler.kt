package com.minhas.teller.data.gst

import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.rupeesToPaise
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class Gstr2bInvoice(
    val gstin: String,
    val tradeName: String?,
    val invoiceNumber: String,
    val invoiceDate: Date?,
    val invoiceType: String = "R", // Regular, Debit Note, etc.
    val taxableValue: Double,
    val igst: Double = 0.0,
    val cgst: Double = 0.0,
    val sgst: Double = 0.0,
    val cess: Double = 0.0,
    val totalTax: Double = igst + cgst + sgst + cess,
    val itcAvailable: Boolean = true
)

enum class Gstr2bMatchStatus {
    EXACT_MATCH,
    VALUE_MISMATCH,
    MISSING_IN_BOOKS, // In 2B portal, not recorded in Teller
    MISSING_IN_2B     // In Teller, supplier hasn't filed GSTR-1 (ITC at risk!)
}

data class Gstr2bMatchResult(
    val twoBInvoice: Gstr2bInvoice?,
    val purchaseVoucher: VoucherWithAllEntries?,
    val status: Gstr2bMatchStatus,
    val difference: Double = 0.0,
    val notes: String? = null
)

data class Gstr2bReconciliationSummary(
    val total2bInvoices: Int,
    val total2bItc: Double,
    val matchedInvoices: Int,
    val matchedItc: Double,
    val atRiskInvoices: Int,
    val atRiskItc: Double,
    val missingInBooksInvoices: Int,
    val missingInBooksItc: Double,
    val results: List<Gstr2bMatchResult>
)

object Gstr2bReconciler {

    // Fresh formatter per use: SimpleDateFormat is not thread-safe and this object is a singleton.
    private val sdf: SimpleDateFormat get() = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    /**
     * Parses standard Government GST Portal GSTR-2B JSON return files.
     */
    fun parseGstr2bJson(jsonString: String): List<Gstr2bInvoice> {
        val invoices = mutableListOf<Gstr2bInvoice>()
        try {
            val root = JSONObject(jsonString)
            val data = root.optJSONObject("data") ?: root
            val docData = data.optJSONObject("docdata") ?: data

            // b2b array contains standard B2B supplies
            val b2bArray = docData.optJSONArray("b2b") ?: return emptyList()

            for (i in 0 until b2bArray.length()) {
                val supplierObj = b2bArray.getJSONObject(i)
                val ctin = supplierObj.optString("ctin")
                val trdNm = supplierObj.optString("trdnm")
                val invArray = supplierObj.optJSONArray("inv") ?: continue

                for (j in 0 until invArray.length()) {
                    val invObj = invArray.getJSONObject(j)
                    val inum = invObj.optString("inum")
                    val idtStr = invObj.optString("idt")
                    val invDate = try { sdf.parse(idtStr) } catch (e: Exception) { null }
                    val invTyp = invObj.optString("inv_typ", "R")

                    // Accumulate in exact paise; the portal's rupee amounts are 2-decimal values.
                    var taxableVal = 0L
                    var igst = 0L
                    var cgst = 0L
                    var sgst = 0L
                    var cess = 0L

                    val items = invObj.optJSONArray("items")
                    if (items != null) {
                        for (k in 0 until items.length()) {
                            val item = items.getJSONObject(k)
                            taxableVal += item.optDouble("txval", 0.0).rupeesToPaise()
                            igst += item.optDouble("iamt", 0.0).rupeesToPaise()
                            cgst += item.optDouble("camt", 0.0).rupeesToPaise()
                            sgst += item.optDouble("samt", 0.0).rupeesToPaise()
                            cess += item.optDouble("csamt", 0.0).rupeesToPaise()
                        }
                    }

                    invoices.add(
                        Gstr2bInvoice(
                            gstin = ctin,
                            tradeName = trdNm,
                            invoiceNumber = inum,
                            invoiceDate = invDate,
                            invoiceType = invTyp,
                            taxableValue = taxableVal.paiseToRupees(),
                            igst = igst.paiseToRupees(),
                            cgst = cgst.paiseToRupees(),
                            sgst = sgst.paiseToRupees(),
                            cess = cess.paiseToRupees()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Gstr2bReconciler", "Error parsing GSTR-2B JSON: ${e.message}", e)
        }
        return invoices
    }

    /**
     * Performs automated 3-way matching between GSTR-2B invoices and Teller Purchase vouchers.
     */
    fun reconcile(
        twoBInvoices: List<Gstr2bInvoice>,
        purchaseVouchers: List<VoucherWithAllEntries>,
        supplierLedgers: List<Ledger>
    ): Gstr2bReconciliationSummary {
        val ledgerMap = supplierLedgers.associateBy { it.id }
        val unmatched2B = twoBInvoices.toMutableList()
        val unmatchedPurchases = purchaseVouchers.filter { it.voucher.type == "Purchase" }.toMutableList()
        val results = mutableListOf<Gstr2bMatchResult>()

        var matchedCount = 0
        var matchedItc = 0.0

        // 1. Match by Invoice Number & GSTIN
        val iter = unmatched2B.iterator()
        while (iter.hasNext()) {
            val twoB = iter.next()
            val match = unmatchedPurchases.firstOrNull { vw ->
                val vNumberMatches = vw.voucher.voucherNumber.trim().equals(twoB.invoiceNumber.trim(), ignoreCase = true)
                val supplierLedger = vw.voucherEntries.mapNotNull { ledgerMap[it.ledgerId] }.firstOrNull { it.gstin?.isNotBlank() == true }
                val gstinMatches = supplierLedger?.gstin?.trim()?.equals(twoB.gstin.trim(), ignoreCase = true) == true

                vNumberMatches || gstinMatches
            }

            if (match != null) {
                val bookTotal = match.inventoryEntries.sumOf { if (it.totalAmount > 0) it.totalAmount else it.amount }.paiseToRupees()
                val twoBTotal = twoB.taxableValue + twoB.totalTax
                val diff = abs(bookTotal - twoBTotal)

                if (diff < 1.0) {
                    results.add(
                        Gstr2bMatchResult(
                            twoBInvoice = twoB,
                            purchaseVoucher = match,
                            status = Gstr2bMatchStatus.EXACT_MATCH
                        )
                    )
                    matchedCount++
                    matchedItc += twoB.totalTax
                } else {
                    results.add(
                        Gstr2bMatchResult(
                            twoBInvoice = twoB,
                            purchaseVoucher = match,
                            status = Gstr2bMatchStatus.VALUE_MISMATCH,
                            difference = diff,
                            notes = "Book Total ₹%.2f vs 2B Portal ₹%.2f".format(bookTotal, twoBTotal)
                        )
                    )
                }
                iter.remove()
                unmatchedPurchases.remove(match)
            }
        }

        // 2. Remaining in 2B are MISSING_IN_BOOKS (Supplier uploaded, but user hasn't recorded)
        var missingInBooksItc = 0.0
        unmatched2B.forEach { twoB ->
            results.add(
                Gstr2bMatchResult(
                    twoBInvoice = twoB,
                    purchaseVoucher = null,
                    status = Gstr2bMatchStatus.MISSING_IN_BOOKS,
                    notes = "Available ITC of ₹%.2f unrecorded in books".format(twoB.totalTax)
                )
            )
            missingInBooksItc += twoB.totalTax
        }

        // 3. Remaining in Books are MISSING_IN_2B (Supplier hasn't filed GSTR-1, ITC at risk!)
        var atRiskItc = 0.0
        unmatchedPurchases.forEach { vw ->
            val totalTax = vw.inventoryEntries.sumOf { it.taxAmount }.paiseToRupees()
            results.add(
                Gstr2bMatchResult(
                    twoBInvoice = null,
                    purchaseVoucher = vw,
                    status = Gstr2bMatchStatus.MISSING_IN_2B,
                    notes = "ITC of ₹%.2f at risk: Supplier has not filed GSTR-1".format(totalTax)
                )
            )
            atRiskItc += totalTax
        }

        return Gstr2bReconciliationSummary(
            total2bInvoices = twoBInvoices.size,
            total2bItc = twoBInvoices.sumOf { it.totalTax },
            matchedInvoices = matchedCount,
            matchedItc = matchedItc,
            atRiskInvoices = unmatchedPurchases.size,
            atRiskItc = atRiskItc,
            missingInBooksInvoices = unmatched2B.size,
            missingInBooksItc = missingInBooksItc,
            results = results
        )
    }
}
