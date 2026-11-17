package com.minhas.teller.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.gst.SupplyType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GstEwayBillExporter {

    /**
     * GST's official Unit Quantity Code list (UQC), keyed by the common names shop owners type into
     * the Unit master. Anything not recognised maps to "OTH" (the portal's own "Others" code) rather
     * than a fabricated unit — the IRP/e-Way Bill portal is strict about this field.
     */
    private val UQC_BY_NAME = mapOf(
        "pcs" to "PCS", "pc" to "PCS", "piece" to "PCS", "pieces" to "PCS", "nos" to "NOS", "no" to "NOS",
        "unit" to "UNT", "units" to "UNT", "box" to "BOX", "boxes" to "BOX", "carton" to "CTN", "ctn" to "CTN",
        "bag" to "BAGS", "bags" to "BAGS", "bale" to "BAL", "bundle" to "BDL", "bundles" to "BDL",
        "bunch" to "BUN", "bottle" to "BTL", "bottles" to "BTL", "can" to "CAN", "drum" to "DRM",
        "dozen" to "DOZ", "doz" to "DOZ", "gross" to "GRS", "pair" to "PRS", "pairs" to "PRS",
        "packet" to "PAC", "pack" to "PAC", "pkt" to "PAC", "roll" to "ROL", "set" to "SET", "sets" to "SET",
        "tube" to "TUB", "kg" to "KGS", "kgs" to "KGS", "kilogram" to "KGS", "kilograms" to "KGS",
        "gram" to "GMS", "grams" to "GMS", "gm" to "GMS", "gms" to "GMS", "quintal" to "QTL",
        "ton" to "TON", "tonne" to "TON", "litre" to "LTR", "litres" to "LTR", "liter" to "LTR",
        "liters" to "LTR", "ltr" to "LTR", "ml" to "MLT", "millilitre" to "MLT", "meter" to "MTR",
        "metre" to "MTR", "meters" to "MTR", "metres" to "MTR", "mtr" to "MTR", "cm" to "CMS",
        "centimeter" to "CMS", "sqft" to "SQF", "sq.ft" to "SQF", "sqm" to "SQM", "sq.m" to "SQM",
        "sqyard" to "SQY", "yard" to "YDS", "yards" to "YDS"
    )

    /** Best-effort UQC for a stock item's unit; "OTH" (Others) when the name isn't recognised. */
    private fun uqcFor(unit: com.minhas.teller.data.entities.Unit?): String {
        val key = unit?.name?.trim()?.lowercase() ?: return "OTH"
        return UQC_BY_NAME[key] ?: "OTH"
    }

    /** Services use SAC codes, which are issued under GST chapter 99. */
    private fun isServiceHsn(hsn: String): Boolean = hsn.filter { it.isDigit() }.startsWith("99")

    /**
     * Checks whether enough is known about this invoice to produce JSON the government portal will
     * actually accept, instead of generating a file that only fails once uploaded. Returns the list
     * of problems to show the user; empty means it is safe to generate.
     */
    fun validateForGovernmentPortal(
        voucherWithEntries: VoucherWithAllEntries,
        partyLedger: Ledger,
        stockItemsMap: Map<Int, StockItem>,
        partyAddress: String?,
        partyPincode: String?,
        forEInvoice: Boolean
    ): List<String> {
        val problems = mutableListOf<String>()
        val voucher = voucherWithEntries.voucher
        val isExport = voucher.supplyType?.uppercase() == SupplyType.EXPORT
        if (forEInvoice && isExport) {
            problems += "Export e-invoicing needs the buyer's country and the foreign currency, which Teller doesn't collect yet — file this invoice through your CA or GSP tool instead."
        } else if (forEInvoice && !isExport && (partyLedger.gstin?.trim()?.length ?: 0) != 15) {
            problems += "'${partyLedger.name}' has no valid 15-character GSTIN — add one on this ledger first."
        }
        if (partyAddress.isNullOrBlank()) {
            problems += "Enter the customer's address — the portal requires it."
        }
        val pin = partyPincode?.trim()?.toIntOrNull()
        if (pin == null || partyPincode.trim().length != 6 || pin <= 0) {
            problems += "Enter the customer's 6-digit PIN code — the portal rejects invoices without it."
        }
        if (voucherWithEntries.inventoryEntries.isEmpty()) {
            problems += "This invoice has no item lines to report."
        } else {
            val missingHsn = voucherWithEntries.inventoryEntries.any { entry ->
                val code = (stockItemsMap[entry.stockItemId]?.hsnCode ?: entry.hsnCode)?.filter { it.isDigit() }
                code.isNullOrBlank()
            }
            if (missingHsn) problems += "One or more items have no HSN/SAC code — add it on the Stock Item first."
        }
        return problems
    }

    /**
     * Generates standard NIC e-Way Bill bulk upload JSON.
     * Compliant with the National e-Way Bill Portal (ewaybillgst.gov.in).
     */
    fun generateEwayBillJson(
        company: Company,
        voucherWithEntries: VoucherWithAllEntries,
        partyLedger: Ledger,
        stockItemsMap: Map<Int, StockItem>,
        partyAddress: String? = null,
        partyPlace: String? = null,
        partyPincode: String? = null,
        transportDistanceKm: Int? = null,
        unitsMap: Map<Int, com.minhas.teller.data.entities.Unit> = emptyMap()
    ): JSONObject = JSONObject().apply {
        put("version", "1.0.1118")
        put("billLists", JSONArray().put(buildEwayBillRoot(company, voucherWithEntries, partyLedger, stockItemsMap, partyAddress, partyPlace, partyPincode, transportDistanceKm, unitsMap)))
    }

    /** One customer/ledger's worth of e-Way Bill data for a [generateBulkEwayBillJson] run. */
    data class BulkEwayBillItem(
        val voucherWithEntries: VoucherWithAllEntries,
        val partyLedger: Ledger,
        val partyAddress: String,
        val partyPlace: String,
        val partyPincode: String,
        val transportDistanceKm: Int? = null
    )

    /** What a bulk send produced: the combined upload file (null if nothing was ready), how many
     *  invoices made it in, and which ones were skipped with the reason — so "Send all" can tell the
     *  user exactly what still needs fixing instead of silently dropping them. */
    data class BulkResult(val readyJson: JSONObject?, val sentCount: Int, val skipped: List<Pair<String, List<String>>>)

    /**
     * Bundles every ready e-Way Bill into one NIC bulk-upload file (the same "billLists" array the
     * portal's own bulk tool produces), skipping anything that fails [validateForGovernmentPortal].
     */
    fun generateBulkEwayBillJson(
        company: Company,
        items: List<BulkEwayBillItem>,
        stockItemsMap: Map<Int, StockItem>,
        unitsMap: Map<Int, com.minhas.teller.data.entities.Unit> = emptyMap()
    ): BulkResult {
        val billLists = JSONArray()
        val skipped = mutableListOf<Pair<String, List<String>>>()
        items.forEach { item ->
            val problems = validateForGovernmentPortal(item.voucherWithEntries, item.partyLedger, stockItemsMap, item.partyAddress, item.partyPincode, forEInvoice = false)
            if (problems.isNotEmpty()) {
                skipped += item.voucherWithEntries.voucher.voucherNumber to problems
            } else {
                billLists.put(buildEwayBillRoot(company, item.voucherWithEntries, item.partyLedger, stockItemsMap, item.partyAddress, item.partyPlace, item.partyPincode, item.transportDistanceKm, unitsMap))
            }
        }
        val json = if (billLists.length() > 0) JSONObject().put("version", "1.0.1118").put("billLists", billLists) else null
        return BulkResult(json, billLists.length(), skipped)
    }

    private fun buildEwayBillRoot(
        company: Company,
        voucherWithEntries: VoucherWithAllEntries,
        partyLedger: Ledger,
        stockItemsMap: Map<Int, StockItem>,
        partyAddress: String? = null,
        partyPlace: String? = null,
        partyPincode: String? = null,
        transportDistanceKm: Int? = null,
        unitsMap: Map<Int, com.minhas.teller.data.entities.Unit> = emptyMap()
    ): JSONObject {
        val voucher = voucherWithEntries.voucher
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val docDate = sdf.format(voucher.date)

        val invEntries = voucherWithEntries.inventoryEntries
        val compState = company.gstin?.take(2) ?: "27"
        val partyState = partyLedger.stateCode ?: partyLedger.gstin?.take(2) ?: compState
        val isInterState = compState != partyState

        var totTaxable = 0.0
        var totCgst = 0.0
        var totSgst = 0.0
        var totIgst = 0.0

        val itemList = JSONArray()
        if (invEntries.isNotEmpty()) {
            invEntries.forEachIndexed { index, entry ->
                val stockItem = stockItemsMap[entry.stockItemId]
                // entry.amount is the tax-INCLUSIVE gross (qty × rate); the taxable base and tax are
                // stored on the entry (and already rounded to paise). Recomputing tax on top of the
                // gross would double-count it.
                val taxable = GstComplianceRules.taxableValue(entry)
                val gstRate = entry.gstRate
                val taxAmt = GstComplianceRules.lineTax(entry)

                val (cgst, sgst) = if (!isInterState) GstComplianceRules.splitTax(taxAmt) else 0.0 to 0.0
                val igst = if (isInterState) GstComplianceRules.round2(taxAmt) else 0.0

                totTaxable += taxable
                totCgst += cgst
                totSgst += sgst
                totIgst += igst

                val itemObj = JSONObject().apply {
                    put("itemNo", index + 1)
                    put("productName", stockItem?.name ?: "Goods")
                    put("productDesc", stockItem?.name ?: "Goods")
                    put("hsnCode", (stockItem?.hsnCode ?: entry.hsnCode ?: "9999").filter { it.isDigit() })
                    put("quantity", entry.quantity)
                    put("qtyUnit", uqcFor(unitsMap[stockItem?.unitId]))
                    put("taxableAmount", taxable)
                    put("cgstRate", if (!isInterState) gstRate / 2.0 else 0.0)
                    put("sgstRate", if (!isInterState) gstRate / 2.0 else 0.0)
                    put("igstRate", if (isInterState) gstRate else 0.0)
                    put("cessRate", 0.0)
                }
                itemList.put(itemObj)
            }
        } else {
            val total = voucherWithEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
            totTaxable = total
            val itemObj = JSONObject().apply {
                put("itemNo", 1)
                put("productName", "Services / General Goods")
                put("productDesc", voucher.narration ?: "Goods")
                put("hsnCode", "9999")
                put("quantity", 1.0)
                put("qtyUnit", "NOS")
                put("taxableAmount", total)
                put("cgstRate", 0.0)
                put("sgstRate", 0.0)
                put("igstRate", 0.0)
                put("cessRate", 0.0)
            }
            itemList.put(itemObj)
        }

        val totalInvoiceVal = totTaxable + totCgst + totSgst + totIgst

        val root = JSONObject().apply {
            put("supplyType", "O") // Outward
            put("subSupplyType", "1") // Supply
            put("docType", "INV") // Tax Invoice
            put("docNo", voucher.voucherNumber)
            put("docDate", docDate)
            put("fromGstin", company.gstin ?: "URP")
            put("fromTrdName", company.name)
            put("fromAddr1", company.address ?: "Premises")
            put("fromPlace", company.state ?: "")
            put("fromPincode", company.pincode?.toIntOrNull() ?: 0)
            put("fromStateCode", compState.toIntOrNull() ?: 27)
            put("actualFromStateCode", compState.toIntOrNull() ?: 27)

            put("toGstin", partyLedger.gstin ?: "URP")
            put("toTrdName", partyLedger.name)
            put("toAddr1", partyAddress?.takeIf { it.isNotBlank() } ?: partyLedger.alias ?: "Customer Address")
            put("toPlace", partyPlace?.takeIf { it.isNotBlank() } ?: "Customer City")
            put("toPincode", partyPincode?.toIntOrNull() ?: 0)
            put("toStateCode", partyState.toIntOrNull() ?: 27)
            put("actualToStateCode", partyState.toIntOrNull() ?: 27)

            put("totalValue", totTaxable)
            put("cgstValue", totCgst)
            put("sgstValue", totSgst)
            put("igstValue", totIgst)
            put("cessValue", 0.0)
            put("totInvValue", totalInvoiceVal)

            put("transMode", "1") // Road
            put("transDistance", transportDistanceKm ?: 50)
            put("transporterName", "")
            put("transporterId", "")
            put("transDocNo", "")
            put("transDocDate", "")
            put("vehicleNo", "")
            put("vehicleType", "R") // Regular
            put("itemList", itemList)
        }

        return root
    }

    /**
     * Generates National e-Invoice Schema v1.03 JSON for B2B invoices.
     * Compliant with einvoice1.gst.gov.in.
     */
    fun generateEInvoiceJson(
        company: Company,
        voucherWithEntries: VoucherWithAllEntries,
        partyLedger: Ledger,
        stockItemsMap: Map<Int, StockItem>,
        partyAddress: String? = null,
        partyPlace: String? = null,
        partyPincode: String? = null,
        transportDistanceKm: Int? = null,
        unitsMap: Map<Int, com.minhas.teller.data.entities.Unit> = emptyMap()
    ): JSONObject {
        val voucher = voucherWithEntries.voucher
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val docDate = sdf.format(voucher.date)

        val invEntries = voucherWithEntries.inventoryEntries
        val compState = company.gstin?.take(2) ?: "27"
        val partyState = partyLedger.stateCode ?: partyLedger.gstin?.take(2) ?: compState
        val isInterState = compState != partyState

        var totTaxable = 0.0
        var totCgst = 0.0
        var totSgst = 0.0
        var totIgst = 0.0

        val itemList = JSONArray()
        invEntries.forEachIndexed { idx, entry ->
            val stockItem = stockItemsMap[entry.stockItemId]
            // Same convention as the e-Way Bill generator: entry.amount is gross, so read the
            // stored base/tax instead of recomputing tax on the gross (which would double-count).
            val taxable = GstComplianceRules.taxableValue(entry)
            val gstRate = entry.gstRate
            val taxAmt = GstComplianceRules.lineTax(entry)
            val (cgst, sgst) = if (!isInterState) GstComplianceRules.splitTax(taxAmt) else 0.0 to 0.0
            val igst = if (isInterState) GstComplianceRules.round2(taxAmt) else 0.0
            val hsnCode = (stockItem?.hsnCode ?: entry.hsnCode ?: "9999").filter { it.isDigit() }
            // Derived from the taxable value itself (not entry.rate) so Qty × UnitPrice always
            // reconciles with AssAmt/TotAmt below — the IRP schema checks that arithmetic.
            val unitPrice = if (entry.quantity > 0.0) GstComplianceRules.round2(taxable / entry.quantity) else taxable

            totTaxable += taxable
            totCgst += cgst
            totSgst += sgst
            totIgst += igst

            itemList.put(JSONObject().apply {
                put("SlNo", (idx + 1).toString())
                put("PrdDesc", stockItem?.name ?: "Product")
                put("IsServc", if (isServiceHsn(hsnCode)) "Y" else "N")
                put("HsnCd", hsnCode)
                put("Qty", entry.quantity)
                put("Unit", uqcFor(unitsMap[stockItem?.unitId]))
                put("UnitPrice", unitPrice)
                put("TotAmt", taxable)
                put("AssAmt", taxable)
                put("GstRt", gstRate)
                put("IgstAmt", igst)
                put("CgstAmt", cgst)
                put("SgstAmt", sgst)
                put("TotItemVal", GstComplianceRules.round2(taxable + taxAmt))
            })
        }

        // Reconciles any half-paisa drift from per-line rounding so TotInvVal matches the sum of
        // item values exactly, as the IRP schema's arithmetic check requires.
        val rawTotal = totTaxable + totCgst + totSgst + totIgst
        val totalInvoiceVal = GstComplianceRules.round2(rawTotal)
        val roundOff = GstComplianceRules.round2(totalInvoiceVal - rawTotal)

        val root = JSONObject().apply {
            put("Version", "1.1")
            put("TranDtls", JSONObject().apply {
                put("TaxSch", "GST")
                // "B2C" is not a valid SupTyp value on the IRP schema — e-invoicing never applies to
                // B2C sales, so anything that isn't an export is reported as B2B. Exports are split
                // by whether IGST was actually charged on the line items (paid vs. under LUT/bond).
                put("SupTyp", if (voucher.supplyType?.uppercase() == SupplyType.EXPORT) {
                    if (totIgst > 0.0) "EXPWP" else "EXPWOP"
                } else "B2B")
                put("RegRev", if (voucher.reverseCharge) "Y" else "N")
            })
            put("DocDtls", JSONObject().apply {
                put("Typ", "INV")
                put("No", voucher.voucherNumber)
                put("Dt", docDate)
            })
            put("SellerDtls", JSONObject().apply {
                put("Gstin", company.gstin ?: "URP")
                put("LglNm", company.name)
                put("Addr1", company.address ?: "Premises")
                put("Loc", company.state ?: "")
                put("Pin", company.pincode?.toIntOrNull() ?: 0)
                put("Stcd", compState)
            })
            put("BuyerDtls", JSONObject().apply {
                put("Gstin", partyLedger.gstin ?: "URP")
                put("LglNm", partyLedger.name)
                put("Pos", partyState)
                put("Addr1", partyAddress?.takeIf { it.isNotBlank() } ?: partyLedger.alias ?: "Customer Address")
                put("Loc", partyPlace?.takeIf { it.isNotBlank() } ?: "Customer City")
                put("Pin", partyPincode?.toIntOrNull() ?: 0)
                put("Stcd", partyState)
            })
            put("ItemList", itemList)
            put("ValDtls", JSONObject().apply {
                put("AssVal", totTaxable)
                put("CgstVal", totCgst)
                put("SgstVal", totSgst)
                put("IgstVal", totIgst)
                put("RndOffAmt", roundOff)
                put("TotInvVal", totalInvoiceVal)
            })
        }

        return root
    }

    /** One customer/ledger's worth of e-Invoice data for a [generateBulkEInvoiceFiles] run. */
    data class BulkEInvoiceItem(
        val voucherWithEntries: VoucherWithAllEntries,
        val partyLedger: Ledger,
        val partyAddress: String,
        val partyPlace: String,
        val partyPincode: String
    )

    /** What a bulk e-invoice send produced: one ready-to-share (fileName, json) pair per invoice that
     *  passed validation, plus which ones were skipped and why. */
    data class BulkEInvoiceResult(val readyFiles: List<Pair<String, JSONObject>>, val skipped: List<Pair<String, List<String>>>)

    /**
     * Builds one e-Invoice JSON per ready invoice (NOT a single combined file — NIC's bulk
     * e-invoice wrapper format isn't the same "billLists" shape the e-Way Bill portal uses, and
     * guessing at it would risk shipping a file that looks bulk-ready but silently fails on upload).
     * The caller shares every returned file together in one tap via [shareJsonFiles] instead.
     */
    fun generateBulkEInvoiceFiles(
        company: Company,
        items: List<BulkEInvoiceItem>,
        stockItemsMap: Map<Int, StockItem>,
        unitsMap: Map<Int, com.minhas.teller.data.entities.Unit> = emptyMap()
    ): BulkEInvoiceResult {
        val ready = mutableListOf<Pair<String, JSONObject>>()
        val skipped = mutableListOf<Pair<String, List<String>>>()
        items.forEach { item ->
            val problems = validateForGovernmentPortal(item.voucherWithEntries, item.partyLedger, stockItemsMap, item.partyAddress, item.partyPincode, forEInvoice = true)
            if (problems.isNotEmpty()) {
                skipped += item.voucherWithEntries.voucher.voucherNumber to problems
            } else {
                val json = generateEInvoiceJson(
                    company, item.voucherWithEntries, item.partyLedger, stockItemsMap,
                    partyAddress = item.partyAddress, partyPlace = item.partyPlace, partyPincode = item.partyPincode,
                    unitsMap = unitsMap
                )
                ready += "eInvoice_${item.voucherWithEntries.voucher.voucherNumber.replace("/", "_")}.json" to json
            }
        }
        return BulkEInvoiceResult(ready, skipped)
    }

    /** Shares several JSON files (e.g. a bulk-send batch) in a single Android Share Sheet action. */
    fun shareJsonFiles(context: Context, files: List<Pair<String, JSONObject>>) {
        if (files.isEmpty()) return
        try {
            val uris = ArrayList<Uri>()
            files.forEach { (fileName, json) ->
                val file = File(context.cacheDir, fileName)
                FileWriter(file).use { it.write(json.toString(2)) }
                uris += FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/json"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${files.size} file(s)"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Failed to share files: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves JSON to cache and shares via Android Share Sheet.
     */
    fun shareJsonFile(context: Context, json: JSONObject, fileName: String) {
        try {
            val file = File(context.cacheDir, fileName)
            val writer = FileWriter(file)
            writer.write(json.toString(2))
            writer.flush()
            writer.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share e-Way Bill / e-Invoice JSON"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Failed to share JSON: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
