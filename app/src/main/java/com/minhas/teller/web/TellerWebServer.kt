package com.minhas.teller.web

import android.content.Context
import android.util.Log
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.gst.GstReturnBuilder
import com.minhas.teller.data.gst.Gstr1Json
import com.minhas.teller.data.gst.Gstr3bJson
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.GstEwayBillExporter
import com.minhas.teller.utils.SmartTaxService
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.utils.paiseToRupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TellerWebServer(
    private val context: Context,
    private val port: Int = 8080
) {
    private val tag = "TellerWebServer"
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    // Bounded pool: an unbounded cached pool let any device on the Wi-Fi spawn threads without limit.
    // One thread runs the accept loop; the rest serve requests (excess connections wait in the queue).
    private val threadPool = Executors.newFixedThreadPool(MAX_THREADS)

    companion object {
        private const val MAX_THREADS = 8
        /** Largest request body accepted (all API bodies are small JSON documents). */
        private const val MAX_BODY_CHARS = 2 * 1024 * 1024
        private const val MAX_HEADERS = 100
    }
    private val db = AppDatabase.getDatabase(context)
    private val voucherRepo = VoucherRepository(
        db.voucherDao(),
        db.voucherEntryDao(),
        db.inventoryEntryDao(),
        db.billAllocationDao(),
        db.auditLogDao(),
        db.ledgerDao(),
        db
    )

    fun start(): Boolean {
        if (isRunning.get()) return true
        return try {
            CompanionAuth.getOrCreateToken(context)
            serverSocket = ServerSocket(port)
            isRunning.set(true)
            threadPool.execute {
                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        threadPool.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(tag, "Client accept error: ${e.message}")
                        }
                    }
                }
            }
            Log.i(tag, "Teller Desktop Web Server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start web server on port $port: ${e.message}", e)
            false
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase(Locale.US)
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val queryParams = parseQueryParams(fullPath)

            // Read Headers
            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            var headerCount = 0
            while (!line.isNullOrBlank()) {
                if (++headerCount > MAX_HEADERS) {
                    sendResponse(out, 431, "Request Header Fields Too Large", "text/plain", "Too many headers")
                    return
                }
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim().lowercase(Locale.US)
                    val value = line.substring(idx + 1).trim()
                    headers[key] = value
                }
                line = reader.readLine()
            }

            // Read Body if Content-Length specified
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            // Reject oversized/negative lengths up front: the old code allocated a buffer of whatever
            // size the client claimed, so one request could exhaust memory and crash the app.
            if (contentLength < 0 || contentLength > MAX_BODY_CHARS) {
                sendResponse(out, 413, "Payload Too Large", "application/json", """{"error":"Request too large"}""")
                return
            }
            val body = if (contentLength > 0) {
                val charBuf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(charBuf, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                String(charBuf, 0, readTotal)
            } else ""

            // Background heartbeats (trackpad polling, pairing checks) would flood the live log.
            if (!path.startsWith("/api/input/") && path != "/api/pair/status") {
                WebCompanionManager.recordRequest("$method $path")
            }

            // CORS Pre-flight
            if (method == "OPTIONS") {
                sendResponse(out, 204, "No Content", "text/plain", "")
                return
            }

            // Static assets (index.html, styles.css, app.js, …) carry no accounting data, so they
            // are served without the pairing token. Only the /api/* endpoints require authorization
            // (Authorization: Bearer header only - never a URL query param, which leaks into browser
            // history, logs and Referer headers), since they expose the full books.
            // /api/pair/* is open on purpose: a PC that has no token yet uses it to ask the phone owner
            // for approval. It reveals nothing until the owner taps Allow on the phone.
            val isApi = path.startsWith("/api/") && !path.startsWith("/api/pair/")
            if (isApi && !isAuthorized(queryParams, headers)) {
                sendResponse(out, 401, "Unauthorized", "application/json", """{"error":"Unauthorized"}""")
                return
            }

            // Route Requests
            when {
                path == "/" || path == "/index.html" -> serveAsset(out, "index.html")
                path == "/styles.css" -> serveAsset(out, "styles.css")
                path == "/app.js" -> serveAsset(out, "app.js")
                path == "/quickbill.js" -> serveAsset(out, "quickbill.js")
                path == "/api/status" -> handleStatus(out)
                path == "/api/pair/request" && method == "POST" -> handlePairRequest(out, socket, headers)
                path == "/api/pair/status" -> handlePairStatus(out, queryParams)
                path == "/api/next-number" -> handleNextNumber(out)
                path == "/api/quickbill/create" && method == "POST" -> handleQuickBillCreate(out, body)
                path == "/api/companies" -> handleGetCompanies(out)
                path == "/api/select-company" && method == "POST" -> handleSelectCompany(out, body)
                path == "/api/dashboard" -> handleDashboard(out)
                path == "/api/ledgers" -> handleGetLedgers(out)
                path == "/api/stock-items" -> handleGetStockItems(out)
                path == "/api/daybook" -> handleGetDaybook(out, queryParams)
                path == "/api/vouchers" -> handleGetVouchersByType(out, queryParams)
                path == "/api/ledger-statement" -> handleLedgerStatement(out, queryParams)
                path == "/api/outstanding" -> handleOutstanding(out)
                path == "/api/reports/stock-summary" -> handleStockSummary(out)
                path == "/api/reports/profit-loss" -> handleProfitLoss(out, queryParams)
                path == "/api/reports/balance-sheet" -> handleBalanceSheet(out, queryParams)
                path == "/api/vouchers/create" && method == "POST" -> handleCreateVoucher(out, body)
                path == "/api/vouchers/update" && method == "POST" -> handleVoucherUpdate(out, body)
                path == "/api/voucher" -> handleVoucherGet(out, queryParams)
                path == "/api/vouchers/delete" && method == "POST" -> handleVoucherDelete(out, body)
                path == "/api/reports/gst" -> handleGstReport(out)
                path == "/api/reports/gstr1" -> handleGstr1(out, queryParams)
                path == "/api/reports/gstr3b" -> handleGstr3b(out, queryParams)
                path == "/api/voucher/ewaybill" -> handleEwayBill(out, queryParams)
                path == "/api/voucher/einvoice" -> handleEInvoice(out, queryParams)
                path == "/api/input/events" -> handleInputEvents(out, queryParams)
                path == "/api/input/keyboard" && method == "POST" -> handleKeyboardStatus(out, body)
                path == "/api/input/keyboard" -> handleKeyboardGet(out)
                path == "/api/reports/trial-balance" -> handleTrialBalance(out)
                path == "/print-station" -> handlePrintStationHtml(out)
                path == "/api/print-queue" -> handlePrintQueue(out)
                path == "/api/print-job" && method == "POST" -> handleQueuePrintJob(out, body)
                path == "/api/voucher-print" -> handlePrintVoucher(out, queryParams)
                path == "/api/units" -> handleGetUnits(out)
                path == "/api/ledgers/create" && method == "POST" -> handleLedgerCreate(out, body)
                path == "/api/ledgers/update" && method == "POST" -> handleLedgerUpdate(out, body)
                path == "/api/ledgers/delete" && method == "POST" -> handleLedgerDelete(out, body)
                path == "/api/stock-items/tax-info" -> handleStockTaxInfo(out, queryParams)
                path == "/api/stock-items/create" && method == "POST" -> handleStockCreate(out, body)
                path == "/api/stock-items/update" && method == "POST" -> handleStockUpdate(out, body)
                path == "/api/stock-items/delete" && method == "POST" -> handleStockDelete(out, body)
                path == "/api/units/create" && method == "POST" -> handleUnitCreate(out, body)
                path == "/api/units/delete" && method == "POST" -> handleUnitDelete(out, body)
                else -> {
                    if (!isApi && path.contains('.')) {
                        serveAsset(out, path.removePrefix("/"))
                    } else {
                        sendResponse(out, 404, "Not Found", "application/json", """{"error":"Not Found"}""")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling client request: ${e.message}", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /** Serve a static asset from the bundled web/ directory. */
    private fun serveAsset(out: OutputStream, assetPath: String): Boolean {
        return try {
            val bytes = context.assets.open("web/$assetPath").use { it.readBytes() }
            val contentType = when (assetPath.substringAfterLast('.', "").lowercase(Locale.US)) {
                "html" -> "text/html; charset=UTF-8"
                "css" -> "text/css; charset=UTF-8"
                "js" -> "application/javascript; charset=UTF-8"
                "json" -> "application/json"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "svg" -> "image/svg+xml"
                "ico" -> "image/x-icon"
                else -> "application/octet-stream"
            }
            sendBytes(out, 200, "OK", contentType, bytes)
            true
        } catch (e: Exception) {
            Log.e(tag, "Asset not found: $assetPath (${e.message})")
            sendResponse(out, 404, "Not Found", "text/plain", "Not Found")
            false
        }
    }

    private fun handleStatus(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val company = db.companyDao().getCompanyByIdSync(companyId)
        val json = JSONObject().apply {
            put("status", "running")
            put("companyId", companyId)
            put("companyName", company?.name ?: "Unknown")
            put("port", port)
        }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleGetCompanies(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companies = db.companyDao().getAllCompanies().first()
        val arr = JSONArray()
        companies.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("address", c.address ?: "")
                put("gstin", c.gstin ?: "")
            })
        }
        val json = JSONObject().apply {
            put("companies", arr)
            put("activeCompanyId", getActiveCompanyId())
        }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleSelectCompany(out: OutputStream, body: String) {
        try {
            val json = JSONObject(body)
            val id = json.getInt("companyId")
            SessionManager.setCompanyId(id)
            sendResponse(out, 200, "OK", "application/json", """{"success":true,"companyId":$id}""")
        } catch (e: Exception) {
            sendResponse(out, 400, "Bad Request", "application/json", """{"error":"${e.message}"}""")
        }
    }

    private fun handleDashboard(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())
        val startOfToday = sdf.parse(todayStr) ?: Date()
        val endOfToday = Date(startOfToday.time + 24 * 60 * 60 * 1000 - 1)

        val todayVouchers = db.voucherDao().getAllVouchersWithAllEntriesBetweenDates(companyId, startOfToday, endOfToday)
        val allVouchers = db.voucherDao().getAllVouchersWithAllEntries(companyId)

        var todaySales = 0.0
        var todayPurchases = 0.0

        todayVouchers.forEach { vwe ->
            val v = vwe.voucher
            val total = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
            if (v.type.equals("Sales", true)) todaySales += total
            if (v.type.equals("Purchase", true)) todayPurchases += total
        }

        // Cash & Bank balances
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
        var cashInHand = 0.0
        var bankBalance = 0.0

        ledgers.forEach { ledger ->
            val group = ledger.group.lowercase(Locale.US)
            val bal = db.ledgerDao().getClosingBalance(ledger.id, companyId, Date()).firstOrNull() ?: ledger.openingBalance
            if (group.contains("cash")) cashInHand += bal
            if (group.contains("bank")) bankBalance += bal
        }

        // Recent 10 transactions
        val recentArr = JSONArray()
        val recentVouchers = allVouchers.take(10)
        recentVouchers.forEach { vwe ->
            val v = vwe.voucher
            val total = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
            val firstEntry = vwe.voucherEntries.firstOrNull()
            val particular = if (firstEntry != null) {
                db.ledgerDao().getLedgerByIdSync(firstEntry.ledgerId)?.name ?: v.narration ?: ""
            } else {
                v.narration ?: ""
            }

            recentArr.put(JSONObject().apply {
                put("id", v.id)
                put("voucherNumber", v.voucherNumber)
                put("type", v.type)
                put("date", SimpleDateFormat("dd MMM yyyy", Locale.US).format(v.date))
                put("particulars", particular)
                put("amount", total)
            })
        }

        val json = JSONObject().apply {
            put("todaySales", todaySales)
            put("todayPurchases", todayPurchases)
            put("cashInHand", cashInHand)
            put("bankBalance", bankBalance)
            put("totalVouchers", allVouchers.size)
            put("recentTransactions", recentArr)
        }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleGetLedgers(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
        val arr = JSONArray()

        ledgers.forEach { l ->
            val bal = db.ledgerDao().getClosingBalance(l.id, companyId, Date()).firstOrNull() ?: l.openingBalance
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("name", l.name)
                put("group", l.group)
                put("balance", bal)
                put("gstin", l.gstin ?: "")
                put("phoneNumber", l.phoneNumber ?: "")
                put("alias", l.alias ?: "")
                put("openingBalance", l.openingBalance)
            })
        }
        val json = JSONObject().apply { put("ledgers", arr) }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleGetStockItems(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val items = db.stockItemDao().getAllStockItems(companyId)
        val arr = JSONArray()

        items.forEach { item ->
            val unit = db.unitDao().getUnitById(item.unitId)
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("rate", item.rate)
                put("hsnCode", item.hsnCode ?: "")
                put("gstRate", item.gstRate)
                put("stock", item.openingStock)
                put("unit", unit?.name ?: "Nos")
                put("unitId", item.unitId)
                put("alias", item.alias ?: "")
            })
        }
        val json = JSONObject().apply { put("stockItems", arr) }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleGetDaybook(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val dateStr = params["date"]
        val vouchers = if (!dateStr.isNullOrBlank()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(dateStr) ?: Date()
            val start = Date(d.time)
            val end = Date(d.time + 24 * 60 * 60 * 1000 - 1)
            db.voucherDao().getAllVouchersWithAllEntriesBetweenDates(companyId, start, end)
        } else {
            db.voucherDao().getAllVouchersWithAllEntries(companyId).take(100)
        }

        val arr = JSONArray()
        vouchers.forEach { vwe ->
            val v = vwe.voucher
            val totalDr = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
            val totalCr = vwe.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }
            val firstEntry = vwe.voucherEntries.firstOrNull()
            val particular = if (firstEntry != null) {
                db.ledgerDao().getLedgerByIdSync(firstEntry.ledgerId)?.name ?: ""
            } else ""

            arr.put(JSONObject().apply {
                put("id", v.id)
                put("voucherNumber", v.voucherNumber)
                put("type", v.type)
                put("date", SimpleDateFormat("dd-MM-yyyy", Locale.US).format(v.date))
                put("narration", v.narration ?: "")
                put("particulars", particular)
                put("totalDebit", totalDr)
                put("totalCredit", totalCr)
            })
        }
        val json = JSONObject().apply { put("vouchers", arr) }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleCreateVoucher(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        try {
            val json = JSONObject(body)
            val type = json.getString("type")
            val dateStr = json.optString("date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            val voucherNumber = json.getString("voucherNumber")
            val debitLedgerId = json.getInt("debitLedgerId")
            val creditLedgerId = json.getInt("creditLedgerId")
            val amount = json.getDouble("amount")
            val narration = if (json.has("narration") && !json.isNull("narration")) json.getString("narration") else null
            // Customer name typed on the PC (Quick Bill); it prints on the invoice like the phone's own field.
            val customField = if (json.has("customField") && !json.isNull("customField")) json.getString("customField").takeIf { it.isNotBlank() } else null

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = try { sdf.parse(dateStr) ?: Date() } catch (_: Exception) { Date() }

            val voucher = Voucher(
                type = type,
                date = date,
                voucherNumber = voucherNumber,
                narration = narration,
                companyId = companyId,
                customField = customField
            )

            // Parse items. Rate is the tax-INCLUSIVE selling price (matching the mobile app), so the
            // taxable base is backed out and the tax is `gross - base`, all rounded to paise.
            val inventoryEntries = mutableListOf<InventoryEntry>()
            var taxableTotal = 0.0
            var taxTotal = 0.0
            if (json.has("items")) {
                val itemsArr = json.getJSONArray("items")
                for (i in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(i)
                    val stockItemId = itemObj.getInt("stockItemId")
                    val qty = itemObj.getDouble("quantity")
                    val rate = itemObj.getDouble("rate")
                    val gstRate = itemObj.optDouble("gstRate", 0.0)
                    val gross = GstComplianceRules.round2(qty * rate)
                    val taxable = if (gstRate > 0.0) GstComplianceRules.round2(gross / (1.0 + gstRate / 100.0)) else gross
                    val tax = GstComplianceRules.round2(gross - taxable)
                    taxableTotal += taxable
                    taxTotal += tax
                    inventoryEntries.add(
                        InventoryEntry(
                            voucherId = 0,
                            stockItemId = stockItemId,
                            quantity = qty,
                            rate = rate,
                            amount = gross.rupeesToPaise(),
                            gstRate = gstRate,
                            taxableAmount = taxable.rupeesToPaise(),
                            taxAmount = tax.rupeesToPaise(),
                            totalAmount = gross.rupeesToPaise()
                        )
                    )
                }
            }

            // Inter-state vs intra-state from the party ledger's state code vs the company's GSTIN.
            val isSales = type.equals("Sales", true)
            val isPurchase = type.equals("Purchase", true)
            val partyLedgerId = if (isSales) debitLedgerId else creditLedgerId
            val partyLedger = db.ledgerDao().getLedgerByIdSync(partyLedgerId)
            val company = db.companyDao().getCompanyByIdSync(companyId)
            val compState = company?.gstin?.take(2)
            val partyState = partyLedger?.stateCode ?: partyLedger?.gstin?.take(2)
            val interState = compState != null && partyState != null && compState != partyState

            // Stamp the item-level CGST/SGST/IGST rates and reconcile the tax split to the paisa.
            val allLedgers = db.ledgerDao().getAllLedgersSync(companyId)
            val cgstLedger = allLedgers.find { it.name.equals("CGST", true) }
            val sgstLedger = allLedgers.find { it.name.equals("SGST", true) }
            val igstLedger = allLedgers.find { it.name.equals("IGST", true) }
            val (cgstAmt, sgstAmt) = GstComplianceRules.splitTax(taxTotal)

            for (idx in inventoryEntries.indices) {
                val it = inventoryEntries[idx]
                inventoryEntries[idx] = it.copy(
                    cgstRate = if (interState) 0.0 else it.gstRate / 2.0,
                    sgstRate = if (interState) 0.0 else it.gstRate / 2.0,
                    igstRate = if (interState) it.gstRate else 0.0
                )
            }

            val gross = if (inventoryEntries.isNotEmpty()) GstComplianceRules.round2(taxableTotal + taxTotal) else amount
            val taxable = GstComplianceRules.round2(taxableTotal)

            // Full GST split when there is tax and the CGST/SGST/IGST ledgers exist.
            val canSplitTax = taxTotal > 0.0 && (isSales || isPurchase) &&
                    (if (interState) igstLedger != null else (cgstLedger != null && sgstLedger != null))

            val entries = mutableListOf<VoucherEntry>()
            if (canSplitTax) {
                if (interState) {
                    if (isSales) {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = gross.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = taxable.rupeesToPaise(), isDebit = false))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = igstLedger!!.id, amount = taxTotal.rupeesToPaise(), isDebit = false))
                    } else {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = taxable.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = igstLedger!!.id, amount = taxTotal.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = gross.rupeesToPaise(), isDebit = false))
                    }
                } else {
                    if (isSales) {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = gross.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = taxable.rupeesToPaise(), isDebit = false))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger!!.id, amount = cgstAmt.rupeesToPaise(), isDebit = false))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger!!.id, amount = sgstAmt.rupeesToPaise(), isDebit = false))
                    } else {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = taxable.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger!!.id, amount = cgstAmt.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger!!.id, amount = sgstAmt.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = gross.rupeesToPaise(), isDebit = false))
                    }
                }
            } else {
                // No tax (or tax ledgers not configured): simple balanced double-entry.
                entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = gross.rupeesToPaise(), isDebit = true))
                entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = gross.rupeesToPaise(), isDebit = false))
            }

            val createdId = voucherRepo.insertVoucherWithAllEntries(
                voucher = voucher,
                voucherEntries = entries,
                inventoryEntries = inventoryEntries
            )

            val resp = JSONObject().apply {
                put("success", true)
                put("voucherId", createdId)
                put("voucherNumber", voucherNumber)
            }
            sendResponse(out, 200, "OK", "application/json", resp.toString())
        } catch (e: Exception) {
            Log.e(tag, "Failed to create voucher via web: ${e.message}", e)
            val err = JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Failed to insert voucher")
            }
            sendResponse(out, 400, "Bad Request", "application/json", err.toString())
        }
    }

    private fun handleTrialBalance(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
        val arr = JSONArray()

        ledgers.forEach { l ->
            val bal = db.ledgerDao().getClosingBalance(l.id, companyId, Date()).firstOrNull() ?: l.openingBalance
            if (Math.abs(bal) > 0.001) {
                arr.put(JSONObject().apply {
                    put("name", l.name)
                    put("group", l.group)
                    put("debit", if (bal > 0) bal else 0.0)
                    put("credit", if (bal < 0) Math.abs(bal) else 0.0)
                })
            }
        }
        val json = JSONObject().apply { put("rows", arr) }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handlePrintVoucher(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val id = params["id"]?.toIntOrNull() ?: 0
        val vwe = db.voucherDao().getVoucherWithAllEntriesById(id)
        if (vwe == null) {
            sendResponse(out, 404, "Not Found", "text/plain", "Voucher not found")
            return@runBlocking
        }

        val companyId = vwe.voucher.companyId
        val company = db.companyDao().getCompanyByIdSync(companyId)
        val type = vwe.voucher.type

        // Sales/Purchase are real invoices on the phone, so render them through the exact same
        // OpenPDF invoice templates (via ExportUtils.buildInvoicePdfBytes). The PC print is then
        // pixel-identical to the phone's invoice, instead of the old simplified HTML mock.
        if (type.equals("Sales", true) || type.equals("Purchase", true)) {
            val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
            val partyEntry = if (type.equals("Sales", true)) {
                vwe.voucherEntries.firstOrNull { it.isDebit }
            } else {
                vwe.voucherEntries.firstOrNull { !it.isDebit }
            }
            val partyLedger = partyEntry?.let { entry -> ledgers.find { it.id == entry.ledgerId } }

            val pdf = ExportUtils.buildInvoicePdfBytes(
                context = context,
                voucher = vwe.voucher,
                customerLedger = partyLedger,
                inventoryEntries = vwe.inventoryEntries,
                stockItems = db.stockItemDao().getAllStockItems(companyId),
                units = db.unitDao().getAllUnits(companyId),
                company = company,
                voucherEntries = vwe.voucherEntries
            )

            if (pdf != null) {
                sendBytes(out, 200, "OK", "application/pdf", pdf)
            } else {
                sendResponse(out, 500, "Internal Server Error", "text/plain", "Failed to generate invoice PDF")
            }
            return@runBlocking
        }

        // Non-invoice voucher types (Receipt/Payment/Journal/Contra) render as the same generic
        // voucher report the phone produces, instead of the old simplified HTML mock.
        val companyName = company?.name ?: "Teller Accounting"
        val allLedgers = db.ledgerDao().getAllLedgersSync(companyId)
        val headers = listOf("Particulars", "Debit", "Credit")
        val data = vwe.voucherEntries.map { entry ->
            val ledger = allLedgers.find { it.id == entry.ledgerId }
            listOf(
                ledger?.name ?: "Unknown",
                if (entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else "",
                if (!entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else ""
            )
        }

        val pdf = ExportUtils.buildReportPdfBytes(
            context = context,
            fileName = "Voucher_${vwe.voucher.voucherNumber.replace("/", "_")}.pdf",
            subtitle = companyName,
            title = "Voucher: ${vwe.voucher.voucherNumber}",
            headers = headers,
            widths = floatArrayOf(3f, 1.5f, 1.5f),
            data = data
        )

        if (pdf != null) {
            sendBytes(out, 200, "OK", "application/pdf", pdf)
        } else {
            sendResponse(out, 500, "Internal Server Error", "text/plain", "Failed to generate voucher PDF")
        }
    }

    private fun handlePrintStationHtml(out: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Teller - Counter Print Station</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                .card { background: #1e293b; border: 1px solid #334155; border-radius: 16px; padding: 36px 32px; max-width: 520px; width: 100%; text-align: center; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5); }
                .badge { background: #064e3b; color: #34d399; font-weight: 700; font-size: 13px; letter-spacing: 0.5px; padding: 6px 14px; border-radius: 20px; display: inline-flex; align-items: center; gap: 8px; margin-bottom: 20px; border: 1px solid #059669; }
                .status-dot { width: 10px; height: 10px; background: #34d399; border-radius: 50%; box-shadow: 0 0 10px #34d399; }
                h1 { margin: 0 0 10px; font-size: 24px; font-weight: 700; }
                p { color: #94a3b8; font-size: 15px; line-height: 1.6; margin: 0 0 24px; }
                .printer-info { background: #0f172a; border: 1px solid #334155; border-radius: 10px; padding: 14px; font-size: 13px; color: #cbd5e1; text-align: left; margin-bottom: 20px; line-height: 1.6; }
                .log-box { background: #0b1120; border: 1px solid #1e293b; border-radius: 8px; padding: 14px; max-height: 180px; overflow-y: auto; text-align: left; font-family: monospace; font-size: 12px; color: #94a3b8; line-height: 1.5; }
                .log-entry { margin-bottom: 4px; }
                .log-entry.print { color: #38bdf8; font-weight: 600; }
                iframe { display: none; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="badge"><span class="status-dot"></span> CONNECTED &amp; LISTENING</div>
                <h1>Counter Print Station</h1>
                <p>This computer is paired with your Teller phone app. Whenever you tap <b>"Print to Counter PC"</b> on your phone, invoices will automatically print here via your Windows printer.</p>
                <div class="printer-info">
                  <b>&#128424; Printer Target:</b> Windows Default Printer (HP, Canon, Epson, Brother, etc.)<br>
                  <b>&#9889; Mode:</b> 1-Click Zero Setup
                </div>
                <div id="logs" class="log-box">
                  <div class="log-entry">Waiting for print jobs from phone...</div>
                </div>
              </div>
              <iframe id="printFrame"></iframe>
              <script>
                const logs = document.getElementById('logs');
                // Token comes from ?token= (stored for this PC) or from an earlier visit to the main page.
                (function () {
                  const t = new URLSearchParams(location.search).get('token');
                  if (t) { try { localStorage.setItem('teller_token', t); } catch (e) {} history.replaceState(null, '', location.pathname); }
                })();
                function authHeaders() {
                  let t = ''; try { t = localStorage.getItem('teller_token') || ''; } catch (e) {}
                  return { 'Authorization': 'Bearer ' + t };
                }
                function addLog(msg, isPrint) {
                  const d = new Date().toLocaleTimeString();
                  const div = document.createElement('div');
                  div.className = 'log-entry' + (isPrint ? ' print' : '');
                  div.textContent = '[' + d + '] ' + msg;
                  logs.insertBefore(div, logs.firstChild);
                }
                async function pollQueue() {
                  try {
                    const res = await fetch('/api/print-queue', { headers: authHeaders() });
                    if (res.status === 401) { addLog('Not paired: open the Teller desktop link from your phone in this browser once, then reload.'); setTimeout(pollQueue, 5000); return; }
                    if (res.ok) {
                      const data = await res.json();
                      if (data && data.voucherId) {
                        addLog('Incoming invoice #' + data.voucherId + '! Loading PDF...', true);
                        const frame = document.getElementById('printFrame');
                        // Fetch with the auth header, then show it via a same-origin blob URL
                        // (an iframe src can't carry an Authorization header).
                        const pr = await fetch('/api/voucher-print?id=' + data.voucherId, { headers: authHeaders() });
                        const blob = new Blob([await pr.text()], { type: 'text/html' });
                        frame.src = URL.createObjectURL(blob);
                        frame.onload = () => {
                          setTimeout(() => {
                            try {
                              frame.contentWindow.focus();
                              frame.contentWindow.print();
                              addLog('Sent invoice #' + data.voucherId + ' to printer spooler!', true);
                            } catch(e) {
                              addLog('Print dialog error: ' + e.message);
                            }
                          }, 600);
                        };
                      }
                    }
                  } catch (e) {}
                  setTimeout(pollQueue, 1500);
                }
                pollQueue();
              </script>
            </body>
            </html>
        """.trimIndent()
        sendResponse(out, 200, "OK", "text/html; charset=UTF-8", html)
    }

    private fun handlePrintQueue(out: OutputStream) {
        val nextJobId = WebCompanionManager.pollPrintJob()
        val json = JSONObject().apply {
            if (nextJobId != null) {
                put("voucherId", nextJobId)
            } else {
                put("voucherId", JSONObject.NULL)
            }
        }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    private fun handleQueuePrintJob(out: OutputStream, body: String) {
        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val voucherId = json.optInt("voucherId", 0)
        if (voucherId > 0) {
            WebCompanionManager.queuePrintJob(voucherId)
            sendResponse(out, 200, "OK", "application/json", """{"status":"queued","voucherId":$voucherId}""")
        } else {
            sendResponse(out, 400, "Bad Request", "application/json", """{"error":"Invalid voucherId"}""")
        }
    }

    /** One row of a voucher register: id, number, type, date, particulars, debit/credit totals. */
    private fun voucherRow(vwe: VoucherWithAllEntries, ledgers: Map<Int, Ledger>, sdf: SimpleDateFormat): JSONObject {
        val totalDr = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
        val totalCr = vwe.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }
        val firstEntry = vwe.voucherEntries.firstOrNull()
        val particulars = firstEntry?.let { ledgers[it.ledgerId]?.name } ?: ""
        return JSONObject().apply {
            put("id", vwe.voucher.id)
            put("voucherNumber", vwe.voucher.voucherNumber)
            put("type", vwe.voucher.type)
            put("date", sdf.format(vwe.voucher.date))
            put("particulars", particulars)
            put("particularsLedgerId", firstEntry?.ledgerId ?: 0)
            put("narration", vwe.voucher.narration ?: "")
            put("totalDebit", totalDr)
            put("totalCredit", totalCr)
        }
    }

    /** Generic register: all vouchers, optionally filtered by type (Sales/Purchase/Journal/…). */
    private fun handleGetVouchersByType(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val type = params["type"]?.takeIf { it.isNotBlank() }
        val from = parseDate(params["from"])
        val to = parseDate(params["to"])
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId).associateBy { it.id }
        val vouchers = db.voucherDao().getAllVouchersWithAllEntries(companyId)
            .filter { type == null || it.voucher.type.equals(type, true) }
            .filter { from == null || it.voucher.date >= from }
            .filter { to == null || it.voucher.date <= to }
        val arr = JSONArray()
        vouchers.forEach { vwe -> arr.put(voucherRow(vwe, ledgers, sdf)) }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("type", type ?: "All")
            put("vouchers", arr)
        }.toString())
    }

    /** Statement of accounts for a single ledger, with a running balance. */
    private fun handleLedgerStatement(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val ledgerId = params["ledgerId"]?.toIntOrNull() ?: 0
        val ledger = db.ledgerDao().getLedgerByIdSync(ledgerId)
        if (ledger == null) {
            sendResponse(out, 404, "Not Found", "application/json", """{"error":"Ledger not found"}""")
            return@runBlocking
        }
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val vouchers = db.voucherDao().getAllVouchersWithAllEntriesForLedger(companyId, ledgerId)
            .sortedBy { it.voucher.date }
        val arr = JSONArray()
        var running = ledger.openingBalance
        vouchers.forEach { vwe ->
            val entry = vwe.voucherEntries.firstOrNull { it.ledgerId == ledgerId } ?: return@forEach
            val dr = if (entry.isDebit) entry.amount else 0L
            val cr = if (!entry.isDebit) entry.amount else 0L
            running += dr - cr
            arr.put(JSONObject().apply {
                put("voucherNumber", vwe.voucher.voucherNumber)
                put("type", vwe.voucher.type)
                put("date", sdf.format(vwe.voucher.date))
                put("debit", dr.paiseToRupees())
                put("credit", cr.paiseToRupees())
                put("balance", running.paiseToRupees())
            })
        }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("ledger", JSONObject().apply {
                put("id", ledger.id)
                put("name", ledger.name)
                put("group", ledger.group)
                put("openingBalance", ledger.openingBalance.paiseToRupees())
            })
            put("entries", arr)
            put("closingBalance", running.paiseToRupees())
        }.toString())
    }

    /** Outstanding receivables (debit) and payables (credit), split for the dashboard. */
    private fun handleOutstanding(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val all = voucherRepo.getAllOutstandingVouchers(companyId)
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val receivables = JSONArray()
        val payables = JSONArray()
        all.forEach { o ->
            val obj = JSONObject().apply {
                put("voucherNumber", o.voucherNumber)
                put("date", sdf.format(o.date))
                put("pendingAmount", o.pendingAmount)
                put("originalAmount", o.originalAmount)
                put("ledgerId", o.ledgerId)
                put("party", o.customerName ?: o.refDisplayName ?: "")
                put("interestAmount", o.interestAmount)
            }
            if (o.isDebit) receivables.put(obj) else payables.put(obj)
        }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("receivables", receivables)
            put("payables", payables)
        }.toString())
    }

    /** Stock summary: opening + inward − outward = closing per item. */
    private fun handleStockSummary(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val items = db.stockItemDao().getAllStockItems(companyId)
        val movements = db.inventoryEntryDao().getStockMovements(0L, Date().time, companyId).associateBy { it.itemId }
        val arr = JSONArray()
        items.forEach { item ->
            val mv = movements[item.id]
            val inward = mv?.inwardQty ?: 0.0
            val outward = mv?.outwardQty ?: 0.0
            val unit = db.unitDao().getUnitById(item.unitId)?.name ?: "Nos"
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("unit", unit)
                put("opening", item.openingStock)
                put("inward", inward)
                put("outward", outward)
                put("closing", item.openingStock + inward - outward)
            })
        }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply { put("items", arr) }.toString())
    }

    /** Simplified Profit & Loss: income/expense grouped by ledger group over the given period. */
    private fun handleProfitLoss(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val from = parseDate(params["from"])
        val to = parseDate(params["to"]) ?: Date()
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
        val incomes = mutableMapOf<String, Double>()
        val expenses = mutableMapOf<String, Double>()
        ledgers.forEach { ledger ->
            val balTo = db.ledgerDao().getClosingBalance(ledger.id, companyId, to).firstOrNull() ?: ledger.openingBalance
            val balFrom = if (from != null) {
                db.ledgerDao().getClosingBalance(ledger.id, companyId, Date(from.time - 1)).firstOrNull() ?: ledger.openingBalance
            } else ledger.openingBalance
            val bal = balTo - balFrom
            val gp = ledger.group.lowercase()
            val isIncome = gp.contains("sales") || gp.contains("income") || gp.contains("revenue")
            val isExpense = gp.contains("purchase") || gp.contains("expense") || gp.contains("cost")
            when {
                isIncome && bal < 0.0 -> incomes[ledger.group] = (incomes[ledger.group] ?: 0.0) + (-bal)
                isExpense && bal > 0.0 -> expenses[ledger.group] = (expenses[ledger.group] ?: 0.0) + bal
            }
        }
        val incomeArr = JSONArray()
        incomes.forEach { (g, a) -> incomeArr.put(JSONObject().put("group", g).put("amount", a)) }
        val expenseArr = JSONArray()
        expenses.forEach { (g, a) -> expenseArr.put(JSONObject().put("group", g).put("amount", a)) }
        val totalIncome = incomes.values.sum()
        val totalExpense = expenses.values.sum()
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("incomes", incomeArr)
            put("expenses", expenseArr)
            put("totalIncome", totalIncome)
            put("totalExpense", totalExpense)
            put("netProfit", totalIncome - totalExpense)
        }.toString())
    }

    /** Simplified Balance Sheet: debit balances → assets, credit balances → liabilities (excl. P&L). */
    private fun handleBalanceSheet(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val to = parseDate(params["to"]) ?: Date()
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
        val assets = mutableMapOf<String, Double>()
        val liabilities = mutableMapOf<String, Double>()
        ledgers.forEach { ledger ->
            val balPaise = db.ledgerDao().getClosingBalance(ledger.id, companyId, to).firstOrNull() ?: ledger.openingBalance
            val bal = balPaise?.paiseToRupees() ?: 0.0
            val gp = ledger.group.lowercase()
            val isPnL = gp.contains("sales") || gp.contains("income") || gp.contains("revenue") ||
                    gp.contains("purchase") || gp.contains("expense") || gp.contains("cost")
            if (isPnL) return@forEach
            when {
                bal > 0.0 -> assets[ledger.group] = (assets[ledger.group] ?: 0.0) + bal
                bal < 0.0 -> liabilities[ledger.group] = (liabilities[ledger.group] ?: 0.0) + (-bal)
            }
        }
        val assetArr = JSONArray()
        assets.forEach { (g, a) -> assetArr.put(JSONObject().put("group", g).put("amount", a)) }
        val liabilityArr = JSONArray()
        liabilities.forEach { (g, a) -> liabilityArr.put(JSONObject().put("group", g).put("amount", a)) }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("assets", assetArr)
            put("liabilities", liabilityArr)
            put("totalAssets", assets.values.sum())
            put("totalLiabilities", liabilities.values.sum())
        }.toString())
    }

    /** Parse an optional yyyy-MM-dd query param; null when absent or malformed. */
    private fun parseDate(s: String?): Date? {
        if (s.isNullOrBlank()) return null
        return try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) } catch (_: Exception) { null }
    }

    /** Common success/error JSON responses for mutation endpoints. */
    private fun ok(out: OutputStream, extra: JSONObject? = null) {
        val resp = extra ?: JSONObject()
        resp.put("success", true)
        sendResponse(out, 200, "OK", "application/json", resp.toString())
    }

    private fun err(out: OutputStream, msg: String) {
        sendResponse(out, 400, "Bad Request", "application/json",
            JSONObject().apply { put("success", false); put("error", msg) }.toString())
    }

    private fun handleGetUnits(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val units = db.unitDao().getAllUnits(companyId)
        val arr = JSONArray()
        units.forEach { u -> arr.put(JSONObject().apply { put("id", u.id); put("name", u.name) }) }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply { put("units", arr) }.toString())
    }

    private fun handleLedgerCreate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        try {
            val j = JSONObject(body)
            val name = j.getString("name").trim()
            if (name.isEmpty()) { err(out, "Ledger name is required"); return@runBlocking }
            val id = db.ledgerDao().insert(Ledger(
                name = name,
                alias = j.optString("alias").takeIf { it.isNotBlank() },
                group = j.optString("group", "Sundry Debtors").ifBlank { "Sundry Debtors" },
                openingBalance = j.optDouble("openingBalance", 0.0).rupeesToPaise(),
                maintainBillWise = j.optBoolean("maintainBillWise", false),
                phoneNumber = j.optString("phoneNumber").takeIf { it.isNotBlank() },
                companyId = companyId,
                gstin = j.optString("gstin").takeIf { it.isNotBlank() },
                stateCode = j.optString("stateCode").takeIf { it.isNotBlank() }
            )).toInt()
            ok(out, JSONObject().apply { put("id", id) })
        } catch (e: Exception) {
            err(out, e.message ?: "Failed to create ledger")
        }
    }

    private fun handleLedgerUpdate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        try {
            val j = JSONObject(body)
            val id = j.getInt("id")
            val existing = db.ledgerDao().getLedgerByIdSync(id)
                ?: run { err(out, "Ledger not found"); return@runBlocking }
            db.ledgerDao().update(existing.copy(
                name = j.getString("name").trim(),
                alias = j.optString("alias").takeIf { it.isNotBlank() },
                group = j.optString("group", existing.group),
                openingBalance = if (j.has("openingBalance")) j.getDouble("openingBalance").rupeesToPaise() else existing.openingBalance,
                maintainBillWise = j.optBoolean("maintainBillWise", existing.maintainBillWise),
                phoneNumber = j.optString("phoneNumber").takeIf { it.isNotBlank() },
                gstin = j.optString("gstin").takeIf { it.isNotBlank() },
                stateCode = j.optString("stateCode").takeIf { it.isNotBlank() }
            ))
            ok(out)
        } catch (e: Exception) {
            err(out, e.message ?: "Failed to update ledger")
        }
    }

    private fun handleLedgerDelete(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        try {
            val j = JSONObject(body)
            val id = j.getInt("id")
            val existing = db.ledgerDao().getLedgerByIdSync(id)
                ?: run { err(out, "Ledger not found"); return@runBlocking }
            db.ledgerDao().delete(existing)
            ok(out)
        } catch (e: Exception) {
            err(out, "Cannot delete ledger (may have transactions): ${e.message}")
        }
    }

    private fun handleStockCreate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        try {
            val j = JSONObject(body)
            val name = j.getString("name").trim()
            if (name.isEmpty()) { err(out, "Item name is required"); return@runBlocking }
            var hsn = j.optString("hsnCode").takeIf { it.isNotBlank() }
            var gst = j.optDouble("gstRate", 0.0)
            if (hsn == null || gst == 0.0) {
                try {
                    SmartTaxService.getInstance(context).getTaxInfoForItem(name)?.let { info ->
                        if (hsn == null) hsn = info.hsnCode
                        if (gst == 0.0) gst = info.gstRate
                    }
                } catch (_: Exception) {}
            }
            val id = db.stockItemDao().insert(StockItem(
                name = name,
                alias = j.optString("alias").takeIf { it.isNotBlank() },
                unitId = j.optInt("unitId", 1),
                openingStock = j.optDouble("openingStock", 0.0),
                rate = j.optDouble("rate", 0.0),
                companyId = companyId,
                hsnCode = hsn,
                gstRate = gst,
                cgstRate = gst / 2.0,
                sgstRate = gst / 2.0,
                igstRate = 0.0,
                hsnAutoDetected = hsn != null && j.optString("hsnCode").isBlank()
            )).toInt()
            ok(out, JSONObject().apply { put("id", id); put("hsnCode", hsn ?: ""); put("gstRate", gst) })
        } catch (e: Exception) {
            err(out, e.message ?: "Failed to create item")
        }
    }

    private fun handleStockUpdate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        try {
            val j = JSONObject(body)
            val id = j.getInt("id")
            val existing = db.stockItemDao().getStockItemById(id)
                ?: run { err(out, "Item not found"); return@runBlocking }
            val name = j.getString("name").trim()
            var hsn = j.optString("hsnCode").takeIf { it.isNotBlank() }
            var gst = j.optDouble("gstRate", existing.gstRate)
            if (hsn == null || gst == 0.0) {
                try {
                    SmartTaxService.getInstance(context).getTaxInfoForItem(name)?.let { info ->
                        if (hsn == null) hsn = info.hsnCode
                        if (gst == 0.0) gst = info.gstRate
                    }
                } catch (_: Exception) {}
            }
            db.stockItemDao().update(existing.copy(
                name = name,
                alias = j.optString("alias").takeIf { it.isNotBlank() },
                unitId = j.optInt("unitId", existing.unitId),
                openingStock = j.optDouble("openingStock", existing.openingStock),
                rate = j.optDouble("rate", existing.rate),
                hsnCode = hsn,
                gstRate = gst,
                cgstRate = gst / 2.0,
                sgstRate = gst / 2.0
            ))
            ok(out)
        } catch (e: Exception) {
            err(out, e.message ?: "Failed to update item")
        }
    }

    private fun handleStockDelete(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        try {
            val j = JSONObject(body)
            val id = j.getInt("id")
            val existing = db.stockItemDao().getStockItemById(id)
                ?: run { err(out, "Item not found"); return@runBlocking }
            db.stockItemDao().delete(existing)
            ok(out)
        } catch (e: Exception) {
            err(out, "Cannot delete item (may have transactions): ${e.message}")
        }
    }

    private fun handleUnitCreate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        try {
            val j = JSONObject(body)
            val name = j.getString("name").trim()
            if (name.isEmpty()) { err(out, "Unit name is required"); return@runBlocking }
            val id = db.unitDao().insert(Unit(
                name = name,
                formalName = j.optString("formalName").takeIf { it.isNotBlank() },
                companyId = companyId
            )).toInt()
            ok(out, JSONObject().apply { put("id", id) })
        } catch (e: Exception) {
            err(out, e.message ?: "Failed to create unit")
        }
    }

    private fun handleUnitDelete(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        try {
            val j = JSONObject(body)
            val id = j.getInt("id")
            val unit = db.unitDao().getUnitById(id)
                ?: run { err(out, "Unit not found"); return@runBlocking }
            db.unitDao().delete(unit)
            ok(out)
        } catch (e: Exception) {
            err(out, "Cannot delete unit (in use): ${e.message}")
        }
    }

    /** Full details of one voucher (entries + items) for the view/edit dialog. */
    private fun handleVoucherGet(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val id = params["id"]?.toIntOrNull() ?: 0
        val vwe = db.voucherDao().getVoucherWithAllEntriesById(id)
        if (vwe == null) {
            sendResponse(out, 404, "Not Found", "application/json", """{"error":"Voucher not found"}""")
            return@runBlocking
        }
        val companyId = vwe.voucher.companyId
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId).associateBy { it.id }
        val stockItems = db.stockItemDao().getAllStockItems(companyId).associateBy { it.id }
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val entriesArr = JSONArray()
        vwe.voucherEntries.forEach { e ->
            entriesArr.put(JSONObject().apply {
                put("ledger", ledgers[e.ledgerId]?.name ?: "Unknown")
                put("ledgerId", e.ledgerId)
                put("isDebit", e.isDebit)
                put("amount", e.amount)
            })
        }
        val itemsArr = JSONArray()
        vwe.inventoryEntries.forEach { ie ->
            itemsArr.put(JSONObject().apply {
                put("name", stockItems[ie.stockItemId]?.name ?: "Item")
                put("stockItemId", ie.stockItemId)
                put("quantity", ie.quantity)
                put("rate", ie.rate)
                put("amount", ie.amount)
                put("gstRate", ie.gstRate)
            })
        }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("id", vwe.voucher.id)
            put("voucherNumber", vwe.voucher.voucherNumber)
            put("type", vwe.voucher.type)
            put("date", sdf.format(vwe.voucher.date))
            put("narration", vwe.voucher.narration ?: "")
            put("entries", entriesArr)
            put("items", itemsArr)
        }.toString())
    }

    private fun handleVoucherDelete(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        try {
            val j = JSONObject(body)
            val id = j.getInt("id")
            val voucher = db.voucherDao().getVoucherById(id)
                ?: run { err(out, "Voucher not found"); return@runBlocking }
            voucherRepo.deleteVoucherWithAllEntries(voucher)
            ok(out)
        } catch (e: Exception) {
            err(out, "Failed to delete voucher: ${e.message}")
        }
    }

    private fun handleVoucherUpdate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        try {
            val json = JSONObject(body)
            val id = json.getInt("id")
            val existing = db.voucherDao().getVoucherById(id)
                ?: run { err(out, "Voucher not found"); return@runBlocking }

            val type = json.getString("type")
            val dateStr = json.optString("date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            val voucherNumber = json.getString("voucherNumber")
            val debitLedgerId = json.getInt("debitLedgerId")
            val creditLedgerId = json.getInt("creditLedgerId")
            val amount = json.getDouble("amount")
            val narration = if (json.has("narration") && !json.isNull("narration")) json.getString("narration") else null
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = try { sdf.parse(dateStr) ?: Date() } catch (_: Exception) { Date() }

            val voucher = existing.copy(type = type, date = date, voucherNumber = voucherNumber, narration = narration)

            val inventoryEntries = mutableListOf<InventoryEntry>()
            var taxableTotal = 0.0
            var taxTotal = 0.0
            if (json.has("items")) {
                val itemsArr = json.getJSONArray("items")
                for (i in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(i)
                    val stockItemId = itemObj.getInt("stockItemId")
                    val qty = itemObj.getDouble("quantity")
                    val rate = itemObj.getDouble("rate")
                    val gstRate = itemObj.optDouble("gstRate", 0.0)
                    val gross = GstComplianceRules.round2(qty * rate)
                    val taxable = if (gstRate > 0.0) GstComplianceRules.round2(gross / (1.0 + gstRate / 100.0)) else gross
                    val tax = GstComplianceRules.round2(gross - taxable)
                    taxableTotal += taxable
                    taxTotal += tax
                    inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = stockItemId, quantity = qty, rate = rate, amount = gross.rupeesToPaise(), gstRate = gstRate, taxableAmount = taxable.rupeesToPaise(), taxAmount = tax.rupeesToPaise(), totalAmount = gross.rupeesToPaise()))
                }
            }

            val isSales = type.equals("Sales", true)
            val isPurchase = type.equals("Purchase", true)
            val partyLedgerId = if (isSales) debitLedgerId else creditLedgerId
            val partyLedger = db.ledgerDao().getLedgerByIdSync(partyLedgerId)
            val company = db.companyDao().getCompanyByIdSync(companyId)
            val compState = company?.gstin?.take(2)
            val partyState = partyLedger?.stateCode ?: partyLedger?.gstin?.take(2)
            val interState = compState != null && partyState != null && compState != partyState

            val allLedgers = db.ledgerDao().getAllLedgersSync(companyId)
            val cgstLedger = allLedgers.find { it.name.equals("CGST", true) }
            val sgstLedger = allLedgers.find { it.name.equals("SGST", true) }
            val igstLedger = allLedgers.find { it.name.equals("IGST", true) }
            val (cgstAmt, sgstAmt) = GstComplianceRules.splitTax(taxTotal)

            for (idx in inventoryEntries.indices) {
                val it = inventoryEntries[idx]
                inventoryEntries[idx] = it.copy(
                    cgstRate = if (interState) 0.0 else it.gstRate / 2.0,
                    sgstRate = if (interState) 0.0 else it.gstRate / 2.0,
                    igstRate = if (interState) it.gstRate else 0.0
                )
            }

            val gross = if (inventoryEntries.isNotEmpty()) GstComplianceRules.round2(taxableTotal + taxTotal) else amount
            val taxable = GstComplianceRules.round2(taxableTotal)
            val canSplitTax = taxTotal > 0.0 && (isSales || isPurchase) &&
                    (if (interState) igstLedger != null else (cgstLedger != null && sgstLedger != null))
            val entries = mutableListOf<VoucherEntry>()
            if (canSplitTax) {
                if (interState) {
                    if (isSales) {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = gross.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = taxable.rupeesToPaise(), isDebit = false))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = igstLedger!!.id, amount = taxTotal.rupeesToPaise(), isDebit = false))
                    } else {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = taxable.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = igstLedger!!.id, amount = taxTotal.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = gross.rupeesToPaise(), isDebit = false))
                    }
                } else {
                    if (isSales) {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = gross.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = taxable.rupeesToPaise(), isDebit = false))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger!!.id, amount = cgstAmt.rupeesToPaise(), isDebit = false))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger!!.id, amount = sgstAmt.rupeesToPaise(), isDebit = false))
                    } else {
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = taxable.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger!!.id, amount = cgstAmt.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger!!.id, amount = sgstAmt.rupeesToPaise(), isDebit = true))
                        entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = gross.rupeesToPaise(), isDebit = false))
                    }
                }
            } else {
                entries.add(VoucherEntry(voucherId = 0, ledgerId = debitLedgerId, amount = gross.rupeesToPaise(), isDebit = true))
                entries.add(VoucherEntry(voucherId = 0, ledgerId = creditLedgerId, amount = gross.rupeesToPaise(), isDebit = false))
            }

            voucherRepo.updateVoucherWithAllEntries(voucher, entries, inventoryEntries)
            ok(out)
        } catch (e: Exception) {
            err(out, e.message ?: "Failed to update voucher")
        }
    }

    /** GST summary: HSN-wise output tax + input tax credit (mirrors the mobile GST report). */
    private fun handleGstReport(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val allVouchers = db.voucherDao().getAllVouchersWithAllEntries(companyId)
        val map = LinkedHashMap<String, MutableList<InventoryEntry>>()
        allVouchers.filter { it.voucher.type.equals("Sales", true) }.forEach { vwe ->
            vwe.inventoryEntries.forEach { entry ->
                val hsn = entry.hsnCode?.takeIf { it.isNotBlank() } ?: "9999"
                map.getOrPut(hsn) { mutableListOf() }.add(entry)
            }
        }
        val arr = JSONArray()
        var totalTaxable = 0.0; var totalCgst = 0.0; var totalSgst = 0.0; var totalIgst = 0.0
        map.forEach { (hsn, entries) ->
            var taxable = 0.0; var cgst = 0.0; var sgst = 0.0; var igst = 0.0; var qty = 0.0
            entries.forEach { e ->
                taxable += GstComplianceRules.taxableValue(e)
                val (c, s, i) = GstComplianceRules.taxSplit(e)
                cgst += c; sgst += s; igst += i; qty += e.quantity
            }
            totalTaxable += taxable; totalCgst += cgst; totalSgst += sgst; totalIgst += igst
            arr.put(JSONObject().apply {
                put("hsn", hsn); put("quantity", qty); put("taxableValue", taxable)
                put("cgst", cgst); put("sgst", sgst); put("igst", igst)
            })
        }
        val itc = allVouchers.filter { it.voucher.type.equals("Purchase", true) }
            .sumOf { vwe -> vwe.inventoryEntries.sumOf { GstComplianceRules.lineTax(it) } }
        val outputTax = totalCgst + totalSgst + totalIgst
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("rows", arr)
            put("totalTaxable", totalTaxable)
            put("outputTax", outputTax)
            put("inputTaxCredit", itc)
            put("netTax", outputTax - itc)
        }.toString())
    }

    /** e-Way Bill bulk-upload JSON for a Sales voucher. */
    private fun handleEwayBill(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        try {
            val id = params["id"]?.toIntOrNull() ?: 0
            val vwe = db.voucherDao().getVoucherWithAllEntriesById(id)
                ?: run { sendResponse(out, 404, "Not Found", "application/json", """{"error":"Voucher not found"}"""); return@runBlocking }
            val company = db.companyDao().getCompanyByIdSync(vwe.voucher.companyId)
                ?: run { sendResponse(out, 400, "Bad Request", "application/json", """{"error":"Company not found"}"""); return@runBlocking }
            val partyLedger = vwe.voucherEntries.firstOrNull { it.isDebit }
                ?.let { db.ledgerDao().getLedgerByIdSync(it.ledgerId) }
                ?: run { sendResponse(out, 400, "Bad Request", "application/json", """{"error":"Party ledger not found"}"""); return@runBlocking }
            val stockMap = db.stockItemDao().getAllStockItems(vwe.voucher.companyId).associateBy { it.id }
            val unitsMap = db.unitDao().getAllUnits(vwe.voucher.companyId).associateBy { it.id }
            val problems = GstEwayBillExporter.validateForGovernmentPortal(
                vwe, partyLedger, stockMap, params["address"], params["pincode"], forEInvoice = false
            )
            if (problems.isNotEmpty()) {
                sendResponse(out, 400, "Bad Request", "application/json", """{"error":"${problems.joinToString("; ")}"}""")
                return@runBlocking
            }
            val json = GstEwayBillExporter.generateEwayBillJson(
                company, vwe, partyLedger, stockMap,
                partyAddress = params["address"],
                partyPlace = params["place"],
                partyPincode = params["pincode"],
                transportDistanceKm = params["distance"]?.toIntOrNull(),
                unitsMap = unitsMap
            )
            sendResponse(out, 200, "OK", "application/json", json.toString(2))
        } catch (e: Exception) {
            sendResponse(out, 400, "Bad Request", "application/json", """{"error":"${e.message}"}""")
        }
    }

    /** e-Invoice (IRP) JSON for a Sales voucher. */
    private fun handleEInvoice(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        try {
            val id = params["id"]?.toIntOrNull() ?: 0
            val vwe = db.voucherDao().getVoucherWithAllEntriesById(id)
                ?: run { sendResponse(out, 404, "Not Found", "application/json", """{"error":"Voucher not found"}"""); return@runBlocking }
            val company = db.companyDao().getCompanyByIdSync(vwe.voucher.companyId)
                ?: run { sendResponse(out, 400, "Bad Request", "application/json", """{"error":"Company not found"}"""); return@runBlocking }
            val partyLedger = vwe.voucherEntries.firstOrNull { it.isDebit }
                ?.let { db.ledgerDao().getLedgerByIdSync(it.ledgerId) }
                ?: run { sendResponse(out, 400, "Bad Request", "application/json", """{"error":"Party ledger not found"}"""); return@runBlocking }
            val stockMap = db.stockItemDao().getAllStockItems(vwe.voucher.companyId).associateBy { it.id }
            val unitsMap = db.unitDao().getAllUnits(vwe.voucher.companyId).associateBy { it.id }
            val problems = GstEwayBillExporter.validateForGovernmentPortal(
                vwe, partyLedger, stockMap, params["address"], params["pincode"], forEInvoice = true
            )
            if (problems.isNotEmpty()) {
                sendResponse(out, 400, "Bad Request", "application/json", """{"error":"${problems.joinToString("; ")}"}""")
                return@runBlocking
            }
            val json = GstEwayBillExporter.generateEInvoiceJson(
                company, vwe, partyLedger, stockMap,
                partyAddress = params["address"],
                partyPlace = params["place"],
                partyPincode = params["pincode"],
                transportDistanceKm = params["distance"]?.toIntOrNull(),
                unitsMap = unitsMap
            )
            sendResponse(out, 200, "OK", "application/json", json.toString(2))
        } catch (e: Exception) {
            sendResponse(out, 400, "Bad Request", "application/json", """{"error":"${e.message}"}""")
        }
    }

    /** Drain pending phone→PC trackpad/keyboard events. */
    private fun handleInputEvents(out: OutputStream, params: Map<String, String>) {
        val since = params["since"]?.toLongOrNull() ?: 0L
        // since < 0 means a freshly opened PC page: skip anything queued earlier (no replaying old
        // taps and keys) and just report the current position.
        val events: List<WebCompanionManager.InputEvent> =
            if (since < 0) emptyList() else WebCompanionManager.drainEvents(since)
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("seq", e.seq); put("type", e.type); put("x", e.x); put("y", e.y); put("key", e.key)
            })
        }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("events", arr)
            put("lastSeq", if (since < 0) WebCompanionManager.currentInputSeq() else (events.lastOrNull()?.seq ?: since))
            // Tells the PC page whether to poll fast (trackpad on) or slowly (idle).
            put("active", WebCompanionManager.isTrackpadActive())
        }.toString())
    }

    /**
     * A PC asks to be paired. Open by design (the PC has no token yet) but harmless on its own: it only
     * creates a pending request that the phone owner must Allow, and the token is handed out once,
     * to whoever holds the secret request id, after approval.
     */
    private fun handlePairRequest(out: OutputStream, socket: Socket, headers: Map<String, String>) {
        val ip = socket.inetAddress?.hostAddress ?: "unknown"
        val req = WebCompanionManager.createPairRequest(ip, describeBrowser(headers["user-agent"]))
        if (req == null) {
            sendResponse(out, 429, "Too Many Requests", "application/json",
                """{"error":"Too many pending requests. Please wait a minute and try again."}""")
            return
        }
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("id", req.id)
            put("code", req.code)
            put("expiresInSec", 120)
        }.toString())
    }

    // ── Quick Bill (simple PC billing) ──────────────────────────────────────────
    // A bill is numbered and saved under this lock, so a double-click or a second PC can never get the
    // same invoice number (GST needs invoice numbers to be unique and in order).
    private val billMutex = kotlinx.coroutines.sync.Mutex()

    /** Next free "INV-<n>": one more than the highest INV- number already used by a Sales bill. */
    private suspend fun nextSalesNumber(companyId: Int): String {
        val prefix = "INV-"
        val highest = db.voucherDao().getSalesVoucherNumbers(companyId)
            .mapNotNull { if (it.startsWith(prefix)) it.removePrefix(prefix).trim().toIntOrNull() else null }
            .maxOrNull() ?: 0
        var n = highest + 1
        while (db.voucherDao().getVoucherByNumber("$prefix$n", companyId) != null) n++
        return "$prefix$n"
    }

    private fun handleNextNumber(out: OutputStream) = runBlocking(Dispatchers.IO) {
        val number = nextSalesNumber(getActiveCompanyId())
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply { put("voucherNumber", number) }.toString())
    }

    /**
     * One-step bill from the PC: items + how the customer pays (+ optional name). The phone works out the
     * ledgers, GST and the bill number itself, then reuses [handleCreateVoucher] so the bill is saved
     * exactly like any other sale.
     */
    private fun handleQuickBillCreate(out: OutputStream, body: String) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        try {
            val j = JSONObject(body)
            val itemsIn = j.optJSONArray("items")
            if (itemsIn == null || itemsIn.length() == 0) {
                err(out, "Add at least one item to the bill.")
                return@runBlocking
            }
            val payment = j.optString("payment", "cash").lowercase(Locale.US)
            val customer = j.optString("customerName", "").trim()
            if (payment == "credit" && customer.isEmpty()) {
                err(out, "Enter the customer's name for a credit (pay later) bill.")
                return@runBlocking
            }

            // Validate the items first, taking GST from the item itself rather than trusting the PC.
            val stock = db.stockItemDao().getAllStockItems(companyId)
            val itemsOut = JSONArray()
            var total = 0.0
            for (i in 0 until itemsIn.length()) {
                val row = itemsIn.getJSONObject(i)
                val item = stock.firstOrNull { it.id == row.optInt("stockItemId", -1) }
                if (item == null) {
                    err(out, "One of the items is no longer in Teller. Refresh the page and try again.")
                    return@runBlocking
                }
                val qty = row.optDouble("quantity", 0.0)
                val rate = row.optDouble("rate", item.rate)
                if (qty.isNaN() || rate.isNaN() || qty <= 0.0 || rate < 0.0) {
                    err(out, "Check the quantity and price of ${item.name}.")
                    return@runBlocking
                }
                total += GstComplianceRules.round2(qty * rate)
                itemsOut.put(JSONObject().apply {
                    put("stockItemId", item.id)
                    put("quantity", qty)
                    put("rate", rate)
                    put("gstRate", item.gstRate)
                })
            }
            total = GstComplianceRules.round2(total)
            if (total <= 0.0) {
                err(out, "The bill total must be more than zero.")
                return@runBlocking
            }

            billMutex.withLock {
                val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
                val salesLedger = ledgers.firstOrNull { it.group.equals("Sales Accounts", true) }
                    ?: ledgers.firstOrNull { it.group.contains("Sales", true) }
                if (salesLedger == null) {
                    err(out, "No Sales account found. Open the Teller phone app and add one once.")
                    return@runBlocking
                }

                // Where the money goes: Cash, the bank/UPI account, or (pay later) the customer's own account.
                val debitLedgerId: Int
                when (payment) {
                    "upi" -> {
                        val banks = ledgers.filter { it.group.equals("Bank Accounts", true) }
                        val bank = banks.firstOrNull { it.name.contains("upi", true) } ?: banks.firstOrNull()
                        if (bank == null) {
                            err(out, "No bank or UPI account found. Add one in the Teller phone app, or choose Cash.")
                            return@runBlocking
                        }
                        debitLedgerId = bank.id
                    }
                    "credit" -> {
                        val existing = ledgers.firstOrNull {
                            it.group.contains("Debtor", true) && it.name.trim().equals(customer, true)
                        }
                        debitLedgerId = existing?.id ?: db.ledgerDao().insert(
                            Ledger(name = customer, group = "Sundry Debtors", openingBalance = 0L, companyId = companyId)
                        ).toInt()
                    }
                    else -> {
                        val cashLedgers = ledgers.filter { it.group.equals("Cash-in-hand", true) }
                        val cash = cashLedgers.firstOrNull { it.name.equals("Cash", true) } ?: cashLedgers.firstOrNull()
                        if (cash == null) {
                            err(out, "No Cash account found. Open the Teller phone app and add one once.")
                            return@runBlocking
                        }
                        debitLedgerId = cash.id
                    }
                }

                val payload = JSONObject().apply {
                    put("type", "Sales")
                    put("voucherNumber", nextSalesNumber(companyId))
                    put("debitLedgerId", debitLedgerId)
                    put("creditLedgerId", salesLedger.id)
                    put("amount", total)
                    if (customer.isNotEmpty()) put("customField", customer)
                    put("items", itemsOut)
                }
                handleCreateVoucher(out, payload.toString())
            }
        } catch (e: Exception) {
            Log.e(tag, "Quick bill failed: ${e.message}", e)
            err(out, e.message ?: "Could not save the bill")
        }
    }

    /** The PC checks whether the phone owner answered. */
    private fun handlePairStatus(out: OutputStream, params: Map<String, String>) {
        val id = params["id"].orEmpty()
        val req = if (id.isBlank()) null else WebCompanionManager.takePairResult(id)
        val json = JSONObject()
        when (req?.status) {
            null -> json.put("status", "expired")
            "approved" -> {
                json.put("status", "approved")
                json.put("token", CompanionAuth.getOrCreateToken(context))
            }
            "denied" -> json.put("status", "denied")
            else -> json.put("status", "pending")
        }
        sendResponse(out, 200, "OK", "application/json", json.toString())
    }

    /** "Chrome on Windows" - shown on the phone so the owner can tell which computer is asking. */
    private fun describeBrowser(ua: String?): String {
        val s = ua.orEmpty()
        val browser = when {
            s.contains("Edg/") -> "Edge"
            s.contains("OPR/") -> "Opera"
            s.contains("Firefox/") -> "Firefox"
            s.contains("Chrome/") -> "Chrome"
            s.contains("Safari/") -> "Safari"
            else -> "A browser"
        }
        val os = when {
            s.contains("Windows") -> "Windows"
            s.contains("Android") -> "an Android device"
            s.contains("iPhone") || s.contains("iPad") -> "an iPhone/iPad"
            s.contains("Mac OS X") -> "Mac"
            s.contains("Linux") -> "Linux"
            else -> "a computer"
        }
        return "$browser on $os"
    }

    /** The PC reports whether it currently needs the phone's virtual keyboard. */
    private fun handleKeyboardStatus(out: OutputStream, body: String) {
        try {
            val j = JSONObject(body)
            WebCompanionManager.setKeyboardRequested(j.optBoolean("needed", false))
            ok(out)
        } catch (e: Exception) {
            err(out, e.message ?: "Invalid request")
        }
    }

    private fun handleKeyboardGet(out: OutputStream) {
        sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
            put("needed", WebCompanionManager.isKeyboardRequested())
        }.toString())
    }

    private fun monthStart(): Date {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.time
    }

    /** GSTR-1 (outward supplies) JSON for the given month, reusing the mobile return builder. */
    private fun handleGstr1(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val from = parseDate(params["from"]) ?: monthStart()
        val to = parseDate(params["to"]) ?: Date()
        val ret = GstReturnBuilder.buildGstReturn(
            db.voucherDao().getAllVouchersWithAllEntries(companyId),
            db.ledgerDao().getAllLedgersSync(companyId),
            db.companyDao().getCompanyByIdSync(companyId),
            from, to
        )
        sendResponse(out, 200, "OK", "application/json", Gstr1Json.toJson(ret).toString(2))
    }

    /** GSTR-3B (summary) JSON for the given month. */
    private fun handleGstr3b(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val companyId = getActiveCompanyId()
        val from = parseDate(params["from"]) ?: monthStart()
        val to = parseDate(params["to"]) ?: Date()
        val ret = GstReturnBuilder.buildGstReturn(
            db.voucherDao().getAllVouchersWithAllEntries(companyId),
            db.ledgerDao().getAllLedgersSync(companyId),
            db.companyDao().getCompanyByIdSync(companyId),
            from, to
        )
        sendResponse(out, 200, "OK", "application/json", Gstr3bJson.toJson(ret).toString(2))
    }

    /** Auto-detect HSN + GST rate for an item name (reuses the phone's SmartTaxService). */
    private fun handleStockTaxInfo(out: OutputStream, params: Map<String, String>) = runBlocking(Dispatchers.IO) {
        val name = params["name"]?.takeIf { it.isNotBlank() }
        if (name == null) { err(out, "name is required"); return@runBlocking }
        val info = try { SmartTaxService.getInstance(context).getTaxInfoForItem(name) } catch (_: Exception) { null }
        if (info == null) {
            sendResponse(out, 200, "OK", "application/json", """{"found":false}""")
        } else {
            sendResponse(out, 200, "OK", "application/json", JSONObject().apply {
                put("found", true)
                put("hsnCode", info.hsnCode)
                put("gstRate", info.gstRate)
                put("description", info.description)
            }.toString())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun isAuthorized(queryParams: Map<String, String>, headers: Map<String, String>): Boolean {
        // Read the live token every time so "Disconnect all computers" (CompanionAuth.rotateToken)
        // takes effect immediately without restarting the server.
        val token = CompanionAuth.getToken(context) ?: return false
        val auth = headers["authorization"] ?: return false
        val presented = auth.removePrefix("Bearer ").trim()
        // Constant-time comparison so the token can't be guessed byte-by-byte from response timing.
        return java.security.MessageDigest.isEqual(
            presented.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8)
        )
    }

    private fun getActiveCompanyId(): Int = runBlocking(Dispatchers.IO) {
        val current = SessionManager.currentCompanyId.value
        if (current != null && current != 0) return@runBlocking current
        val first = db.companyDao().getAllCompanies().firstOrNull()?.firstOrNull()
        first?.id ?: 1
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        if (query.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                result[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
        }
        return result
    }

    private fun sendResponse(
        out: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n" +
                SECURITY_HEADERS +
                "\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }

    /**
     * No CORS headers: the desktop UI is served from this same origin, so cross-origin access is
     * never needed. The old "Access-Control-Allow-Origin: *" let any website open in the PC's browser
     * call these APIs. no-referrer stops the page URL leaking to other sites; no-store keeps
     * accounting data out of the browser cache.
     */
    private val SECURITY_HEADERS =
        "X-Content-Type-Options: nosniff\r\n" +
        "Referrer-Policy: no-referrer\r\n" +
        "X-Frame-Options: SAMEORIGIN\r\n" +
        "Cache-Control: no-store\r\n"

    /** Write raw bytes (e.g. a static asset) with the common security headers. */
    private fun sendBytes(
        out: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        bodyBytes: ByteArray
    ) {
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n" +
                SECURITY_HEADERS +
                "\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bodyBytes)
        out.flush()
    }
}
