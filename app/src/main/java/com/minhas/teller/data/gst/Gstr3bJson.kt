package com.minhas.teller.data.gst

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes a [GstReturn] into the official GSTR-3B JSON (outward summary, ITC, inward exempt).
 * Pure JSON assembly — classification lives in [GstReturnBuilder].
 *
 * Key sections: `sup_details` (osup_det / osup_zero / osup_nil_exmp / osup_nongst / isup_rev),
 * `inter_sup` (unreg_details), `itc_elg` (itc_avl / itc_rev / itc_net / itc_inelg), `inward_sup`.
 */
object Gstr3bJson {

    fun toJson(r: GstReturn): JSONObject = JSONObject().apply {
        put("gstin", r.gstin)
        put("fp", r.fp)
        put("sup_details", supDetails(r))
        put("inter_sup", interSup(r))
        put("itc_elg", itcElg(r))
        put("inward_sup", inwardSup(r))
    }

    private fun supDetails(r: GstReturn): JSONObject = JSONObject().apply {
        val wpayExports = r.outward.filter { it.supplyType == SupplyType.EXPORT && it.igstPaise > 0L }
        // Sales returns (credit notes) reduce each outward bucket they came from.
        val cn = r.creditNotes
        val taxable = r.outward.filter { it.supplyType == SupplyType.B2B || it.supplyType == SupplyType.B2CS || it.supplyType == SupplyType.B2CL } + wpayExports
        val taxableCn = cn.filter { it.supplyType == SupplyType.B2B || it.supplyType == SupplyType.B2CS || it.supplyType == SupplyType.B2CL || (it.supplyType == SupplyType.EXPORT && it.igstPaise > 0L) }
        put("osup_det", taxObj(
            sumTx(taxable) - sumTx(taxableCn), sumIgst(taxable) - sumIgst(taxableCn), sumCgst(taxable) - sumCgst(taxableCn),
            sumSgst(taxable) - sumSgst(taxableCn), sumCess(taxable) - sumCess(taxableCn)
        ))

        val zero = r.outward.filter { it.supplyType == SupplyType.EXPORT && it.igstPaise <= 0L }
        val zeroCn = cn.filter { it.supplyType == SupplyType.EXPORT && it.igstPaise <= 0L }
        put("osup_zero", taxObj(
            sumTx(zero) - sumTx(zeroCn), sumIgst(zero) - sumIgst(zeroCn), sumCgst(zero) - sumCgst(zeroCn),
            sumSgst(zero) - sumSgst(zeroCn), sumCess(zero) - sumCess(zeroCn)
        ))

        val nilExempt = r.outward.filter { it.supplyType == SupplyType.NIL || it.supplyType == SupplyType.EXEMPT }
        val nilExemptCn = cn.filter { it.supplyType == SupplyType.NIL || it.supplyType == SupplyType.EXEMPT }
        put("osup_nil_exmp", JSONObject().put("txval", (sumTx(nilExempt) - sumTx(nilExemptCn)).round2()))

        val nongst = r.outward.filter { it.supplyType == SupplyType.NONGST }
        val nongstCn = cn.filter { it.supplyType == SupplyType.NONGST }
        put("osup_nongst", JSONObject().put("txval", (sumTx(nongst) - sumTx(nongstCn)).round2()))

        val rev = r.inward.filter { it.reverseCharge }
        put("isup_rev", taxObj(sumTx(rev), sumIgst(rev), sumCgst(rev), sumSgst(rev), sumCess(rev)))
    }

    private fun interSup(r: GstReturn): JSONObject = JSONObject().apply {
        val unreg = JSONArray()
        r.outward.filter { it.supplyType == SupplyType.B2CL }
            .groupBy { it.placeOfSupply }
            .forEach { (pos, invoices) ->
                val returned = r.creditNotes.filter { it.supplyType == SupplyType.B2CL && it.placeOfSupply == pos }
                unreg.put(JSONObject().apply {
                    put("pos", pos)
                    put("txval", (sumTx(invoices) - sumTx(returned)).round2())
                    put("iamt", (sumIgst(invoices) - sumIgst(returned)).round2())
                })
            }
        put("unreg_details", unreg)
        put("comp_details", JSONArray())
        put("uin_details", JSONArray())
    }

    private fun itcElg(r: GstReturn): JSONObject = JSONObject().apply {
        val avl = JSONArray()
        // Only purchases from GSTIN-registered suppliers are eligible for input tax credit.
        val eligibleInward = r.inward.filter { !it.recipientGstin.isNullOrBlank() }
        // Goods sent back to suppliers (purchase returns) reduce the credit claimed.
        val eligibleReturns = r.purchaseReturns.filter { !it.recipientGstin.isNullOrBlank() }
        val otherIgst = (eligibleInward.sumOf { it.igstPaise } - eligibleReturns.sumOf { it.igstPaise }) / 100.0
        val otherCgst = (eligibleInward.sumOf { it.cgstPaise } - eligibleReturns.sumOf { it.cgstPaise }) / 100.0
        val otherSgst = (eligibleInward.sumOf { it.sgstPaise } - eligibleReturns.sumOf { it.sgstPaise }) / 100.0
        avl.put(itcEntry("IMPG", 0.0, 0.0, 0.0, 0.0))
        avl.put(itcEntry("IMPS", 0.0, 0.0, 0.0, 0.0))
        avl.put(itcEntry("ISRC", 0.0, 0.0, 0.0, 0.0))
        avl.put(itcEntry("ISD", 0.0, 0.0, 0.0, 0.0))
        avl.put(itcEntry("OTH", otherIgst, otherCgst, otherSgst, 0.0))
        put("itc_avl", avl)

        put("itc_rev", JSONArray().apply {
            put(itcEntry("RUL", 0.0, 0.0, 0.0, 0.0))
            put(itcEntry("OTH", 0.0, 0.0, 0.0, 0.0))
        })

        put("itc_net", JSONObject().apply {
            put("iamt", otherIgst.round2())
            put("camt", otherCgst.round2())
            put("samt", otherSgst.round2())
            put("csamt", 0.0)
        })

        put("itc_inelg", JSONArray().apply {
            put(itcEntry("RUL", 0.0, 0.0, 0.0, 0.0))
            put(itcEntry("OTH", 0.0, 0.0, 0.0, 0.0))
        })
    }

    private fun inwardSup(r: GstReturn): JSONObject = JSONObject().apply {
        // Rate-0 inward supplies: those with a supplier GSTIN are nil/exempt GST supplies;
        // those without are genuinely non-GST (unregistered / out-of-scope).
        val zeroRated = r.inward.filter { inv -> inv.lines.all { it.rate <= 0.0 } }
        val nilExempt = zeroRated.filter { !it.recipientGstin.isNullOrBlank() }
        val nonGst = zeroRated.filter { it.recipientGstin.isNullOrBlank() }
        put("isup_details", JSONArray().apply {
            put(JSONObject().apply {
                put("ty", "GST")
                put("inter", nilExempt.filter { it.isInterState }.sumOf { it.taxablePaise }.div(100.0).round2())
                put("intra", nilExempt.filter { !it.isInterState }.sumOf { it.taxablePaise }.div(100.0).round2())
            })
            put(JSONObject().apply {
                put("ty", "NONGST")
                put("inter", nonGst.filter { it.isInterState }.sumOf { it.taxablePaise }.div(100.0).round2())
                put("intra", nonGst.filter { !it.isInterState }.sumOf { it.taxablePaise }.div(100.0).round2())
            })
        })
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun taxObj(tx: Double, igst: Double, cgst: Double, sgst: Double, cess: Double) =
        JSONObject().apply {
            put("txval", tx.round2())
            put("iamt", igst.round2())
            put("camt", cgst.round2())
            put("samt", sgst.round2())
            put("csamt", cess.round2())
        }

    private fun itcEntry(ty: String, igst: Double, cgst: Double, sgst: Double, cess: Double) =
        JSONObject().apply {
            put("ty", ty)
            put("iamt", igst.round2())
            put("camt", cgst.round2())
            put("samt", sgst.round2())
            put("csamt", cess.round2())
        }

    // Sum in exact paise, convert to rupees once at the end.
    private fun sumTx(list: List<GstInvoice>) = list.sumOf { it.taxablePaise } / 100.0
    private fun sumIgst(list: List<GstInvoice>) = list.sumOf { it.igstPaise } / 100.0
    private fun sumCgst(list: List<GstInvoice>) = list.sumOf { it.cgstPaise } / 100.0
    private fun sumSgst(list: List<GstInvoice>) = list.sumOf { it.sgstPaise } / 100.0
    private fun sumCess(list: List<GstInvoice>) = list.sumOf { it.cessPaise } / 100.0
}
