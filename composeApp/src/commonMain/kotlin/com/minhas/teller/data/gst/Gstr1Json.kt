package com.minhas.teller.data.gst

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes a [GstReturn] into the official GSTR-1 Offline Utility JSON (tables 4A/4B, 5, 7, 6A,
 * 8, 12). Pure JSON assembly — no classification logic here (that lives in [GstReturnBuilder]).
 *
 * Field names follow the GSTN offline-utility schema: top level `gstin`, `fp`, `gt`, `cur_gt`, then
 * `b2b` / `b2cl` / `b2cs` / `exp` / `nil` / `hsn`.
 */
object Gstr1Json {

    fun toJson(r: GstReturn): JSONObject = JSONObject().apply {
        put("gstin", r.gstin)
        put("fp", r.fp)
        put("gt", r.grossTurnover.round2())
        put("cur_gt", r.grossTurnover.round2())

        put("b2b", buildB2b(r))
        put("b2cl", buildB2cl(r))
        put("b2cs", buildB2cs(r))
        put("exp", buildExp(r))
        put("nil", buildNil(r))
        put("hsn", buildHsn(r))
        // Table 9B: credit notes for sales returns. B2CS returns are netted into b2cs instead.
        put("cdnr", buildCdnr(r))
        put("cdnur", buildCdnur(r))
    }

    private val TAXABLE_TYPES = setOf(SupplyType.B2B, SupplyType.B2CL, SupplyType.B2CS, SupplyType.EXPORT)

    private fun buildCdnr(r: GstReturn): JSONArray {
        val arr = JSONArray()
        r.creditNotes
            .filter { it.supplyType == SupplyType.B2B && !it.recipientGstin.isNullOrBlank() }
            .groupBy { it.recipientGstin!! }
            .forEach { (ctin, notes) ->
                arr.put(JSONObject().apply {
                    put("ctin", ctin)
                    put("nt", notes.map { noteJson(it, includePos = true) }.toJSONArray())
                })
            }
        return arr
    }

    private fun buildCdnur(r: GstReturn): JSONArray {
        val arr = JSONArray()
        r.creditNotes.filter { it.supplyType == SupplyType.B2CL || it.supplyType == SupplyType.EXPORT }.forEach { cn ->
            val typ = when {
                cn.supplyType == SupplyType.B2CL -> "B2CL"
                cn.igstPaise > 0L -> "EXPWP"
                else -> "EXPWOP"
            }
            arr.put(noteJson(cn, includePos = typ == "B2CL").apply { put("typ", typ) })
        }
        return arr
    }

    private fun noteJson(cn: GstInvoice, includePos: Boolean): JSONObject = JSONObject().apply {
        put("ntty", "C")
        put("nt_num", cn.invoiceNumber)
        put("nt_dt", cn.dateText)
        if (includePos) put("pos", cn.placeOfSupply)
        put("rchrg", if (cn.reverseCharge) "Y" else "N")
        put("inv_typ", "R")
        put("val", cn.totalValue.round2())
        val itms = JSONArray()
        cn.lines.forEachIndexed { index, line ->
            itms.put(JSONObject().apply {
                put("num", index + 1)
                put("itm_det", JSONObject().apply {
                    put("txval", line.taxableValue.round2())
                    put("rt", line.rate)
                    put("iamt", line.igst.round2())
                    put("camt", line.cgst.round2())
                    put("samt", line.sgst.round2())
                    put("csamt", line.cess.round2())
                })
            })
        }
        put("itms", itms)
    }

    private fun buildB2b(r: GstReturn): JSONArray {
        val arr = JSONArray()
        val byCtin = r.outward
            .filter { it.supplyType == SupplyType.B2B && !it.recipientGstin.isNullOrBlank() }
            .groupBy { it.recipientGstin!! }
        for ((ctin, invoices) in byCtin) {
            arr.put(JSONObject().apply {
                put("ctin", ctin)
                put("inv", invoices.map { invoiceJson(it) }.toJSONArray())
            })
        }
        return arr
    }

    private fun buildB2cl(r: GstReturn): JSONArray {
        val arr = JSONArray()
        val byPos = r.outward.filter { it.supplyType == SupplyType.B2CL }.groupBy { it.placeOfSupply }
        for ((pos, invoices) in byPos) {
            arr.put(JSONObject().apply {
                put("pos", pos)
                put("inv", invoices.map { invoiceJson(it) }.toJSONArray())
            })
        }
        return arr
    }

    private fun buildB2cs(r: GstReturn): JSONArray {
        val arr = JSONArray()
        // Aggregate intra-state B2C by (place of supply, rate).
        val agg = LinkedHashMap<Pair<String, Double>, GstInvoiceLine>()
        r.outward.filter { it.supplyType == SupplyType.B2CS }.forEach { inv ->
            inv.lines.forEach { line ->
                val key = inv.placeOfSupply to line.rate
                agg[key] = (agg[key] ?: GstInvoiceLine(0L, line.rate, 0L, 0L, 0L)).plus(line)
            }
        }
        // Returns of B2C (small) sales are reported net in the same pos/rate bucket.
        r.creditNotes.filter { it.supplyType == SupplyType.B2CS }.forEach { cn ->
            cn.lines.forEach { line ->
                val key = cn.placeOfSupply to line.rate
                agg[key] = (agg[key] ?: GstInvoiceLine(0L, line.rate, 0L, 0L, 0L)).minus(line)
            }
        }
        agg.forEach { (key, line) ->
            arr.put(JSONObject().apply {
                put("pos", key.first)
                put("sply_ty", "INTRA")
                put("rt", line.rate)
                put("txval", line.taxableValue.round2())
                put("iamt", line.igst.round2())
                put("camt", line.cgst.round2())
                put("samt", line.sgst.round2())
                put("csamt", line.cess.round2())
            })
        }
        return arr
    }

    private fun buildExp(r: GstReturn): JSONArray {
        val arr = JSONArray()
        val exports = r.outward.filter { it.supplyType == SupplyType.EXPORT }
        // With-payment exports (IGST charged) must be filed separately from LUT/bond exports.
        val wpay = exports.filter { it.igstPaise > 0L }
        val wopay = exports.filter { it.igstPaise <= 0L }
        if (wopay.isNotEmpty()) {
            arr.put(JSONObject().apply {
                put("exp_typ", "WOPAY")
                put("inv", wopay.map { invoiceJson(it) }.toJSONArray())
            })
        }
        if (wpay.isNotEmpty()) {
            arr.put(JSONObject().apply {
                put("exp_typ", "WPAY")
                put("inv", wpay.map { invoiceJson(it) }.toJSONArray())
            })
        }
        return arr
    }

    private fun buildNil(r: GstReturn): JSONObject {
        val inv = JSONArray()
        r.outward.filter { it.supplyType == SupplyType.NIL || it.supplyType == SupplyType.EXEMPT || it.supplyType == SupplyType.NONGST }
            .forEach { invoice ->
                inv.put(JSONObject().apply {
                    put("sply_ty", splyTypeFor(invoice, r.businessStateCode))
                    put("nil_amt", if (invoice.supplyType == SupplyType.NIL) invoice.taxableValue.round2() else 0.0)
                    put("expt_amt", if (invoice.supplyType == SupplyType.EXEMPT) invoice.taxableValue.round2() else 0.0)
                    put("ngsup_amt", if (invoice.supplyType == SupplyType.NONGST) invoice.taxableValue.round2() else 0.0)
                })
            }
        return JSONObject().put("inv", inv)
    }

    private fun buildHsn(r: GstReturn): JSONObject {
        val data = JSONArray()
        val agg = LinkedHashMap<Pair<String, Double>, GstInvoiceLine>()
        r.outward.filter { it.supplyType != SupplyType.NIL && it.supplyType != SupplyType.EXEMPT && it.supplyType != SupplyType.NONGST }
            .forEach { inv ->
            inv.lines.forEach { line ->
                val hsn = line.hsnCode?.takeIf { it.isNotBlank() } ?: "9999"
                val key = hsn to line.rate
                agg[key] = (agg[key] ?: GstInvoiceLine(0L, line.rate, 0L, 0L, 0L)).plus(line).copy(hsnCode = hsn)
            }
        }
        // HSN summary is net of returned goods.
        r.creditNotes.filter { it.supplyType in TAXABLE_TYPES }.forEach { cn ->
            cn.lines.forEach { line ->
                val hsn = line.hsnCode?.takeIf { it.isNotBlank() } ?: "9999"
                val key = hsn to line.rate
                agg[key] = (agg[key] ?: GstInvoiceLine(0L, line.rate, 0L, 0L, 0L)).minus(line).copy(hsnCode = hsn)
            }
        }
        var num = 1
        agg.forEach { (_, line) ->
            data.put(JSONObject().apply {
                put("num", num++)
                put("hsn_sc", line.hsnCode ?: "9999")
                put("uqc", "OTH")
                put("qty", line.quantity)
                put("txval", line.taxableValue.round2())
                put("rt", line.rate)
                put("iamt", line.igst.round2())
                put("camt", line.cgst.round2())
                put("samt", line.sgst.round2())
                put("csamt", line.cess.round2())
            })
        }
        return JSONObject().put("data", data)
    }

    private fun invoiceJson(inv: GstInvoice): JSONObject = JSONObject().apply {
        put("inum", inv.invoiceNumber)
        put("idt", inv.dateText)
        put("val", inv.totalValue.round2())
        put("pos", inv.placeOfSupply)
        put("rchrg", if (inv.reverseCharge) "Y" else "N")
        put("inv_typ", "R")
        val itms = JSONArray()
        inv.lines.forEachIndexed { index, line ->
            itms.put(JSONObject().apply {
                put("num", index + 1)
                put("itm_det", JSONObject().apply {
                    put("txval", line.taxableValue.round2())
                    put("rt", line.rate)
                    put("iamt", line.igst.round2())
                    put("camt", line.cgst.round2())
                    put("samt", line.sgst.round2())
                    put("csamt", line.cess.round2())
                })
            })
        }
        put("itms", itms)
    }

    private fun splyTypeFor(inv: GstInvoice, businessStateCode: String?): String {
        val b2b = !inv.recipientGstin.isNullOrBlank()
        val inter = businessStateCode != null && inv.placeOfSupply != businessStateCode
        return when {
            b2b && inter -> "INTERB2B"
            b2b && !inter -> "INTRB2B"
            !b2b && inter -> "INTERB2C"
            else -> "INTRB2C"
        }
    }

    // small helper: mutable-ish line addition for aggregation
    private fun GstInvoiceLine.plus(o: GstInvoiceLine) = copy(
        taxablePaise = taxablePaise + o.taxablePaise,
        cgstPaise = cgstPaise + o.cgstPaise,
        sgstPaise = sgstPaise + o.sgstPaise,
        igstPaise = igstPaise + o.igstPaise,
        cessPaise = cessPaise + o.cessPaise,
        quantity = quantity + o.quantity
    )

    private fun GstInvoiceLine.minus(o: GstInvoiceLine) = copy(
        taxablePaise = taxablePaise - o.taxablePaise,
        cgstPaise = cgstPaise - o.cgstPaise,
        sgstPaise = sgstPaise - o.sgstPaise,
        igstPaise = igstPaise - o.igstPaise,
        cessPaise = cessPaise - o.cessPaise,
        quantity = quantity - o.quantity
    )

    private fun List<JSONObject>.toJSONArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it) }
        return a
    }
}
