package com.minhas.teller.utils

import android.content.Context
import android.util.Base64
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.VoucherWithAllEntries
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Offline e-invoice (IRN) tracker.
 *
 * The government's Invoice Registration Portal (IRP) is what actually issues an IRN, and that needs
 * the internet. This class keeps the shop owner on track around it, fully offline:
 *   - remembers whether e-invoicing applies to this business (and from which date),
 *   - works out which B2B / export invoices still have no IRN,
 *   - stores the IRN + Ack No + Ack Date + signed QR the portal sends back, per invoice,
 *   - reads that reply (the JSON file the portal gives, or a bare 64-character IRN).
 * Data lives in a small private preferences file (not in the Room database) so it needs no schema migration.
 */
object EInvoiceStore {

    data class Record(val irn: String, val ackNo: String, val ackDate: String, val signedQr: String)

    /** Annual turnover above which e-invoicing is mandatory (Notification 10/2023-CT), in rupees. */
    const val THRESHOLD_RUPEES = 5_00_00_000.0

    private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }

    private fun prefs(context: Context? = null) =
        (context?.applicationContext ?: appContext)?.getSharedPreferences("teller_einvoice", Context.MODE_PRIVATE)

    // ---- per-company switch -------------------------------------------------------------

    fun isEnabled(companyId: Int, context: Context? = null): Boolean =
        prefs(context)?.getBoolean("on_$companyId", false) ?: false

    fun setEnabled(companyId: Int, enabled: Boolean, context: Context? = null) {
        val p = prefs(context) ?: return
        val e = p.edit().putBoolean("on_$companyId", enabled)
        // Remember the day it was switched on: older invoices are not chased.
        if (enabled && !p.contains("since_$companyId")) e.putLong("since_$companyId", startOfToday())
        e.apply()
    }

    fun sinceMillis(companyId: Int, context: Context? = null): Long =
        prefs(context)?.getLong("since_$companyId", 0L) ?: 0L

    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    // ---- per-invoice record -------------------------------------------------------------

    fun get(companyId: Int, voucherId: Int, context: Context? = null): Record? {
        val s = prefs(context)?.getString("rec_${companyId}_$voucherId", null) ?: return null
        return try {
            val o = JSONObject(s)
            Record(o.optString("irn"), o.optString("ackNo"), o.optString("ackDt"), o.optString("qr"))
        } catch (e: Exception) { null }
    }

    fun save(companyId: Int, voucherId: Int, r: Record, context: Context? = null) {
        val o = JSONObject().put("irn", r.irn).put("ackNo", r.ackNo).put("ackDt", r.ackDate).put("qr", r.signedQr)
        prefs(context)?.edit()?.putString("rec_${companyId}_$voucherId", o.toString())?.apply()
    }

    fun clear(companyId: Int, voucherId: Int, context: Context? = null) {
        prefs(context)?.edit()?.remove("rec_${companyId}_$voucherId")?.apply()
    }

    // ---- remembered customer shipping details --------------------------------------------------
    // A bulk "send all pending" action can't stop and ask for each customer's address — it reuses
    // whatever address/PIN was typed in once for that ledger. Saved only after a successful send,
    // so a bad address never gets remembered.

    data class PartyDetails(val address: String, val place: String, val pincode: String)

    fun savePartyDetails(ledgerId: Int, address: String, place: String, pincode: String, context: Context? = null) {
        if (address.isBlank() || pincode.trim().length != 6) return
        val o = JSONObject().put("addr", address).put("place", place).put("pin", pincode)
        prefs(context)?.edit()?.putString("party_$ledgerId", o.toString())?.apply()
    }

    fun partyDetails(ledgerId: Int, context: Context? = null): PartyDetails? {
        val s = prefs(context)?.getString("party_$ledgerId", null) ?: return null
        return try {
            val o = JSONObject(s)
            PartyDetails(o.optString("addr"), o.optString("place"), o.optString("pin"))
        } catch (e: Exception) { null }
    }

    // ---- who needs an e-invoice ---------------------------------------------------------
    //
    // B2B or B2C? A bill is B2B when the customer account on it (a debit-side ledger of the sale —
    // the party picked under "On Credit", or any customer ledger) has a valid 15-character GSTIN
    // saved. Cash / Bank / Finance-Sale bills with only a typed customer name are B2C: the account on
    // them is Cash, Bank or the finance company, so there is no buyer GSTIN to put on an e-invoice.
    // A bill whose Supply Type is set to Export always needs one; B2CS / B2CL / Nil / Exempt never do.

    private val GSTIN_PATTERN = Regex("^[0-9]{2}[A-Z0-9]{13}$")

    /** True for a well-formed 15-character GSTIN (2-digit state code followed by 13 letters/digits). */
    fun isValidGstin(gstin: String?): Boolean {
        val g = gstin?.trim()?.uppercase() ?: return false
        return g.length == 15 && GSTIN_PATTERN.matches(g)
    }

    /**
     * The buyer on a sales bill: the debit-side ledger that carries a GSTIN, else the first debit ledger.
     * (A bill can have more than one debit line — e.g. part cash, part on credit — and the party is not
     * always the first one.)
     */
    fun buyerLedger(v: VoucherWithAllEntries, ledgers: List<Ledger>): Ledger? {
        val debitLedgers = v.voucherEntries.filter { it.isDebit }.mapNotNull { e -> ledgers.find { it.id == e.ledgerId } }
        return debitLedgers.firstOrNull { isValidGstin(it.gstin) } ?: debitLedgers.firstOrNull()
    }

    /** A sale needs an e-invoice when it is B2B (buyer has a GSTIN) or an export, and is not deleted. */
    fun needsEInvoice(v: VoucherWithAllEntries, ledgers: List<Ledger>): Boolean {
        val voucher = v.voucher
        if (voucher.isDeleted || !voucher.type.equals("Sales", true)) return false
        val st = voucher.supplyType?.uppercase()
        if (st == "B2CL" || st == "B2CS" || st == "NIL" || st == "EXEMPT" || st == "NONGST") return false
        if (st == "EXPORT") return true
        return isValidGstin(buyerLedger(v, ledgers)?.gstin)
    }

    /** Marked B2B on the bill, but the customer's account has no (valid) GSTIN — can't be e-invoiced yet. */
    fun isB2bMissingGstin(v: VoucherWithAllEntries, ledgers: List<Ledger>): Boolean =
        !v.voucher.isDeleted && v.voucher.type.equals("Sales", true) &&
            v.voucher.supplyType.equals("B2B", true) && !isValidGstin(buyerLedger(v, ledgers)?.gstin)

    // ---- does e-invoicing apply to this business, and from when? ------------------------

    /** Why e-invoicing does / doesn't apply, in a form the screens can explain in plain words. */
    enum class Reason {
        /** Switched on by the owner (or after the first bill was sent). */
        SWITCHED_ON,
        /** Yearly sales crossed Rs 5 crore in an earlier financial year: compulsory now. */
        TURNOVER_CROSSED,
        /** Crossed Rs 5 crore only in the current year: compulsory from next 1 April. */
        CROSSED_THIS_YEAR,
        /** Below the limit and not switched on. */
        BELOW_LIMIT
    }

    data class Status(
        val reason: Reason,
        /** Bills dated on/after this need an IRN. Null = e-invoicing doesn't apply yet. */
        val fromMillis: Long?,
        /** Highest yearly sales (rupees) and that financial year, e.g. "2026-27". */
        val biggestTurnover: Double,
        val biggestFy: String,
        /** For [Reason.CROSSED_THIS_YEAR]: the day it becomes compulsory (next 1 April). */
        val compulsoryFromMillis: Long? = null
    )

    /** Notification 10/2023-CT: Rs 5 crore limit applies to invoices from 1 August 2023. */
    private fun ruleStartMillis(): Long = Calendar.getInstance().apply {
        set(2023, Calendar.AUGUST, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun fyOf(date: java.util.Date): Int {
        val c = Calendar.getInstance().apply { time = date }
        return if (c.get(Calendar.MONTH) >= Calendar.APRIL) c.get(Calendar.YEAR) else c.get(Calendar.YEAR) - 1
    }

    private fun fyStartMillis(fyStartYear: Int): Long = Calendar.getInstance().apply {
        set(fyStartYear, Calendar.APRIL, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun fyLabel(fyStartYear: Int) = "$fyStartYear-${"%02d".format((fyStartYear + 1) % 100)}"

    private fun isTaxLedger(l: Ledger): Boolean {
        val g = l.group.lowercase()
        val n = l.name.trim().lowercase()
        return g.contains("duties") || g.contains("tax") ||
            n == "cgst" || n == "sgst" || n == "igst" || n == "utgst" || n.contains("cess") || n.contains("gst")
    }

    /**
     * Sales value without GST of one bill, in paise: the items' taxable value, or — for a bill saved
     * without items — the non-tax credit lines (the sales account).
     */
    private fun billValuePaise(v: VoucherWithAllEntries, ledgerById: Map<Int, Ledger>): Long {
        if (v.inventoryEntries.isNotEmpty()) return v.inventoryEntries.sumOf { GstComplianceRules.taxablePaise(it) }
        return v.voucherEntries.filter { e -> !e.isDebit && ledgerById[e.ledgerId]?.let { isTaxLedger(it) } != true }.sumOf { it.amount }
    }

    /** Sales without GST per financial year (start year -> rupees), net of sales returns. */
    fun turnoverByYear(all: List<VoucherWithAllEntries>, ledgers: List<Ledger> = emptyList()): Map<Int, Double> {
        val ledgerById = ledgers.associateBy { it.id }
        val byYear = HashMap<Int, Long>()
        all.forEach { v ->
            if (v.voucher.isDeleted) return@forEach
            val t = v.voucher.type.trim().lowercase()
            val sign = when (t) {
                "sales" -> 1L
                "sales return", "credit note" -> -1L
                else -> return@forEach
            }
            val fy = fyOf(v.voucher.date)
            byYear[fy] = (byYear[fy] ?: 0L) + sign * billValuePaise(v, ledgerById)
        }
        return byYear.mapValues { it.value.paiseToRupees() }
    }

    /** Highest sales total (without GST) in any one financial year (April-March), in rupees. Approximates AATO. */
    fun biggestYearTurnover(all: List<VoucherWithAllEntries>, ledgers: List<Ledger> = emptyList()): Pair<Double, String> {
        val best = turnoverByYear(all, ledgers).maxByOrNull { it.value } ?: return 0.0 to ""
        return best.value to fyLabel(best.key)
    }

    /**
     * Works out whether e-invoicing applies and from which date. It switches itself on when the
     * books show yearly sales above Rs 5 crore in an earlier financial year — the owner no longer has
     * to find a hidden switch (there wasn't one: tracking only started after a first bill was sent,
     * so the pending list stayed empty for everyone who hadn't sent one yet).
     */
    fun status(companyId: Int, all: List<VoucherWithAllEntries>, ledgers: List<Ledger>, context: Context? = null): Status {
        val byYear = turnoverByYear(all, ledgers)
        val best = byYear.maxByOrNull { it.value }
        val bestAmt = best?.value ?: 0.0
        val bestFy = best?.key?.let { fyLabel(it) } ?: ""
        val currentFy = fyOf(java.util.Date())
        val firstPastCrossing = byYear.filter { it.key < currentFy && it.value >= THRESHOLD_RUPEES }.keys.minOrNull()
        // Compulsory from the April after the first year above the limit (never before 1 Aug 2023).
        // Bills of earlier financial years are not chased — they can no longer be reported anyway.
        val autoFrom = firstPastCrossing?.let {
            maxOf(ruleStartMillis(), fyStartMillis(it + 1), fyStartMillis(currentFy))
        }
        if (isEnabled(companyId, context)) {
            val since = sinceMillis(companyId, context)
            return Status(Reason.SWITCHED_ON, if (autoFrom != null) minOf(since, autoFrom) else since, bestAmt, bestFy)
        }
        if (autoFrom != null) return Status(Reason.TURNOVER_CROSSED, autoFrom, bestAmt, bestFy)
        val thisYear = byYear[currentFy] ?: 0.0
        if (thisYear >= THRESHOLD_RUPEES) {
            return Status(Reason.CROSSED_THIS_YEAR, null, thisYear, fyLabel(currentFy), compulsoryFromMillis = fyStartMillis(currentFy + 1))
        }
        return Status(Reason.BELOW_LIMIT, null, bestAmt, bestFy)
    }

    /**
     * Owner chose to start e-invoicing now (e.g. sales crossed Rs 5 crore this year, or a customer
     * asked for e-invoices). Bills from the start of the current financial year are listed.
     */
    fun startNow(companyId: Int, context: Context? = null) {
        val p = prefs(context) ?: return
        p.edit()
            .putBoolean("on_$companyId", true)
            .putLong("since_$companyId", fyStartMillis(fyOf(java.util.Date())))
            .apply()
    }

    /** Sales bills (on/after the applicable date) that still have no IRN. Empty when e-invoicing doesn't apply. */
    fun pending(companyId: Int, all: List<VoucherWithAllEntries>, ledgers: List<Ledger>, context: Context? = null): List<VoucherWithAllEntries> {
        val since = status(companyId, all, ledgers, context).fromMillis ?: return emptyList()
        return all.filter {
            it.voucher.date.time >= since && needsEInvoice(it, ledgers) && get(companyId, it.voucher.id, context) == null
        }
    }

    /** Counts that explain an empty or short pending list in plain words. */
    data class Breakdown(
        val pending: Int,
        val alreadySent: Int,
        /** Sales to customers without a GSTIN (cash / finance / walk-in) — no e-invoice needed. */
        val b2c: Int,
        /** Marked B2B but the customer's account has no valid GSTIN saved. */
        val b2bMissingGstin: Int
    )

    fun breakdown(companyId: Int, all: List<VoucherWithAllEntries>, ledgers: List<Ledger>, fromMillis: Long, context: Context? = null): Breakdown {
        var pending = 0; var sent = 0; var b2c = 0; var missing = 0
        all.forEach { v ->
            if (v.voucher.isDeleted || !v.voucher.type.equals("Sales", true) || v.voucher.date.time < fromMillis) return@forEach
            when {
                needsEInvoice(v, ledgers) -> if (get(companyId, v.voucher.id, context) != null) sent++ else pending++
                isB2bMissingGstin(v, ledgers) -> missing++
                else -> b2c++
            }
        }
        return Breakdown(pending, sent, b2c, missing)
    }

    fun daysOld(date: java.util.Date): Int = ((System.currentTimeMillis() - date.time) / 86_400_000L).toInt()

    // ---- reading the portal's reply -----------------------------------------------------

    /**
     * Understands the JSON the IRP gives back (single invoice or a bulk list, plain or wrapped in "Data"),
     * a bare 64-character IRN, or the signed JWT from scanning the e-invoice's own QR code (that QR's
     * payload carries Irn/AckNo/AckDt directly, so it needs no reply file at all). For a list it picks
     * the entry whose invoice number matches [wantedDocNo]. Returns null when nothing usable is found —
     * use [findError] in that case to see whether the portal actually rejected this invoice.
     */
    fun parseReply(text: String, wantedDocNo: String?): Record? {
        val t = text.trim().removePrefix("﻿")
        if (Regex("^[0-9a-fA-F]{64}$").matches(t)) return Record(t.lowercase(), "", "", "")
        val isJwt = isJwt(t)
        val root: Any = try {
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> JSONObject(t)
                isJwt -> decodeJwtPayload(t) ?: return null
                else -> return null
            }
        } catch (e: Exception) { return null }
        val found = mutableListOf<JSONObject>()
        collect(root, found)
        if (found.isEmpty()) return null

        val want = wantedDocNo?.trim()
        val pick = found.firstOrNull { want != null && docNoOf(it)?.trim().equals(want, true) }
            ?: found.singleOrNull()?.takeIf { docNoOf(it).let { d -> d == null || want == null || d.trim().equals(want, true) } }
            ?: return null
        val irn = key(pick, "Irn") ?: return null
        // When the input itself was a scanned QR's JWT, that whole string IS the signed QR to keep
        // (so it can be reprinted later) — a bulk/single reply JSON carries it as a separate field instead.
        val signedQr = key(pick, "SignedQRCode", "SignedQrCode") ?: (if (isJwt) t else "")
        return Record(irn, key(pick, "AckNo") ?: "", key(pick, "AckDt") ?: "", signedQr)
    }

    /**
     * If [text] is a bulk (or single) reply with a rejected entry for [wantedDocNo], returns the
     * government portal's own error text for it — so the user sees *why* it was rejected instead of
     * a generic "no IRN found", without this app guessing at NIC's error-code list.
     */
    fun findError(text: String, wantedDocNo: String?): String? {
        val t = text.trim().removePrefix("﻿")
        val root: Any = try {
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> JSONObject(t)
                else -> return null
            }
        } catch (e: Exception) { return null }
        val found = mutableListOf<JSONObject>()
        collectErrors(root, found)
        if (found.isEmpty()) return null
        val want = wantedDocNo?.trim()
        val pick = found.firstOrNull { want != null && docNoOf(it)?.trim().equals(want, true) } ?: found.singleOrNull() ?: return null
        val messages = when (val details = pick.opt("ErrorDetails")) {
            is JSONArray -> (0 until details.length()).mapNotNull { i -> (details.opt(i) as? JSONObject)?.let { key(it, "ErrorMessage") } }
            is JSONObject -> listOfNotNull(key(details, "ErrorMessage"))
            is String -> listOfNotNull(
                try { key(JSONObject(details), "ErrorMessage") } catch (e: Exception) { details.takeIf { it.isNotBlank() } }
            )
            else -> emptyList()
        }
        return messages.filter { it.isNotBlank() }.joinToString("; ").ifBlank { key(pick, "InfoDtls") }
    }

    private fun isJwt(s: String): Boolean = s.count { it == '.' } == 2 && Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$").matches(s)

    private fun decodeJwtPayload(jwt: String): JSONObject? = try {
        val part = jwt.split(".").getOrNull(1) ?: return null
        JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
    } catch (e: Exception) { null }

    private fun docNoOf(o: JSONObject): String? {
        val direct = key(o, "DocNo", "InvoiceNo", "DocNum")
        if (!direct.isNullOrBlank()) return direct
        val jwt = key(o, "SignedInvoice") ?: return null
        val payload = decodeJwtPayload(jwt) ?: return null
        val data = payload.opt("data")
        val inv = (data as? JSONObject) ?: (try { JSONObject(data.toString()) } catch (e: Exception) { return null })
        return inv.optJSONObject("DocDtls")?.optString("No")
    }

    private fun collect(n: Any?, out: MutableList<JSONObject>) {
        when (n) {
            is JSONObject -> {
                if (key(n, "Irn") != null) { out.add(n); return }
                n.keys().forEach { collect(n.opt(it), out) }
            }
            is JSONArray -> for (i in 0 until n.length()) collect(n.opt(i), out)
        }
    }

    /** Finds entries that look like a rejected invoice: carry ErrorDetails, or an explicit failure Status. */
    private fun collectErrors(n: Any?, out: MutableList<JSONObject>) {
        when (n) {
            is JSONObject -> {
                val status = key(n, "Status")?.lowercase()
                if (n.has("ErrorDetails") || status == "0" || status == "2" || status == "false") { out.add(n); return }
                n.keys().forEach { collectErrors(n.opt(it), out) }
            }
            is JSONArray -> for (i in 0 until n.length()) collectErrors(n.opt(i), out)
        }
    }

    private fun key(o: JSONObject, vararg names: String): String? {
        for (name in names) {
            val k = o.keys().asSequence().firstOrNull { it.equals(name, true) } ?: continue
            val v = o.opt(k)
            if (v != null && v != JSONObject.NULL && v.toString().isNotBlank()) return v.toString()
        }
        return null
    }
}
