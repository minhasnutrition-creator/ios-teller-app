package com.minhas.teller.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.accounting.ReturnVoucherBuilder
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.formatMoney
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Returns, kept deliberately simple:
 *   1. Customer returned / I returned to supplier
 *   2. Date of the original bill
 *   3. Pick the bill from the list
 *   4. Tap Return  (whole bill by default; "Only some items?" for a partial return)
 *
 * Behind the scenes it saves a "Sales Return" / "Purchase Return" voucher that mirrors the bill
 * (see ReturnVoucherBuilder): stock goes back, GST and the party's balance are reversed.
 */
private enum class ReturnKind { SALE, PURCHASE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val repo = remember {
        VoucherRepository(
            database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(),
            database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database
        )
    }
    val companyId by SessionManager.currentCompanyId.collectAsState()

    var kind by remember { mutableStateOf(ReturnKind.SALE) }
    var billDate by remember { mutableStateOf(startOfDay(Date())) }
    var bills by remember { mutableStateOf<List<VoucherWithAllEntries>>(emptyList()) }
    var earlierReturns by remember { mutableStateOf<List<VoucherWithAllEntries>>(emptyList()) }
    var ledgers by remember { mutableStateOf<Map<Int, Ledger>>(emptyMap()) }
    var stockItems by remember { mutableStateOf<Map<Int, StockItem>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<VoucherWithAllEntries?>(null) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var someItemsOnly by remember { mutableStateOf(false) }
    val typedQty = remember { mutableStateMapOf<Int, String>() } // original inventory-entry id -> qty text
    var saving by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(companyId) {
        val cid = companyId ?: return@LaunchedEffect
        ledgers = database.ledgerDao().getAllLedgersSync(cid).associateBy { it.id }
        stockItems = database.stockItemDao().getAllStockItems(cid).associateBy { it.id }
    }

    LaunchedEffect(companyId, kind, billDate, reloadKey) {
        val cid = companyId ?: return@LaunchedEffect
        loading = true
        selected = null
        someItemsOnly = false
        typedQty.clear()
        val originalType = if (kind == ReturnKind.SALE) "Sales" else "Purchase"
        bills = repo.getAllVouchersWithAllEntriesBetweenDates(cid, billDate, endOfDay(billDate))
            .filter { it.voucher.type.equals(originalType, ignoreCase = true) }
            .sortedBy { it.voucher.date }
        // Returns are always dated on/after the bill, so this window holds every earlier return.
        earlierReturns = if (bills.isEmpty()) emptyList() else
            repo.getAllVouchersWithAllEntriesBetweenDates(cid, billDate, Date(FAR_FUTURE))
                .filter { ReturnVoucherBuilder.isReturn(it.voucher.type) && it.voucher.returnOfVoucherId != null }
        loading = false
    }

    fun partyName(vw: VoucherWithAllEntries): String {
        vw.voucher.refDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        val isSale = vw.voucher.type.equals("Sales", true)
        val party = vw.voucherEntries
            .filter { it.isDebit == isSale }
            .mapNotNull { ledgers[it.ledgerId] }
            .firstOrNull { l ->
                val g = l.group.lowercase()
                !g.contains("sales") && !g.contains("purchase") && !g.contains("duties") && !g.contains("tax")
            }
        return party?.name ?: vw.voucher.customField.orEmpty()
    }

    fun billTotal(vw: VoucherWithAllEntries): Long = vw.voucherEntries.filter { it.isDebit }.sumOf { it.amount }

    fun alreadyReturned(vw: VoucherWithAllEntries): Map<Int, Double> =
        ReturnVoucherBuilder.alreadyReturned(vw, earlierReturns)

    fun isFullyReturned(vw: VoucherWithAllEntries): Boolean {
        val done = alreadyReturned(vw)
        return if (vw.inventoryEntries.isEmpty()) {
            earlierReturns.any { it.voucher.returnOfVoucherId == vw.voucher.id }
        } else {
            vw.inventoryEntries.all { e -> (done[e.id] ?: 0.0) >= e.quantity - 1e-9 }
        }
    }

    fun billLabel(vw: VoucherWithAllEntries): String {
        val name = partyName(vw).ifBlank { TerminologyManager.getTerm("Walk-in Customer") }
        return "#${vw.voucher.voucherNumber}  ·  $name  ·  ${billTotal(vw).formatMoney()}"
    }

    val partyLedgerIds = remember(ledgers) {
        ledgers.values.filter { it.maintainBillWise && VoucherRepository.isOutstandingEligible(it.group) }.map { it.id }.toSet()
    }

    // Live preview of what will be saved (cheap: one bill).
    val current = selected
    val requestedQty: Map<Int, Double>? = if (current == null || !someItemsOnly) null else
        current.inventoryEntries.associate { e -> e.id to (typedQty[e.id]?.toDoubleOrNull() ?: 0.0) }
    val preview = remember(current, requestedQty, earlierReturns, partyLedgerIds) {
        if (current == null) null else try {
            ReturnVoucherBuilder.build(
                original = current,
                returnQty = requestedQty,
                voucherNumber = "",
                date = Date(),
                partyLedgerIds = partyLedgerIds,
                alreadyReturnedQty = alreadyReturned(current)
            )
        } catch (_: Exception) {
            null
        }
    }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val onBg = MaterialTheme.colorScheme.onBackground

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Return"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Who is returning
            Text(TerminologyManager.getTerm("What is being returned?"), color = onBg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ReturnKind.values().forEach { k ->
                    val label = if (k == ReturnKind.SALE) TerminologyManager.getTerm("Customer returned items")
                                else TerminologyManager.getTerm("I returned items to supplier")
                    val modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    if (kind == k) {
                        Button(onClick = {}, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(onClick = { kind = k }, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
                            Text(label, color = onBg)
                        }
                    }
                }
            }

            // 2. Date of the original bill
            Text(TerminologyManager.getTerm("Date of the bill"), color = onBg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { time = billDate }
                    DatePickerDialog(context, { _, y, m, d ->
                        val c = Calendar.getInstance()
                        c.set(y, m, d, 0, 0, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        billDate = c.time
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(dateFmt.format(billDate), color = onBg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            // 3. Pick the bill
            Text(TerminologyManager.getTerm("Which bill?"), color = onBg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            when {
                loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                bills.isEmpty() -> Text(
                    TerminologyManager.getTerm("No bills on this date. Pick another date."),
                    color = onBg.copy(alpha = 0.7f)
                )
                else -> ExposedDropdownMenuBox(expanded = dropdownOpen, onExpandedChange = { dropdownOpen = it }) {
                    OutlinedTextField(
                        value = selected?.let { billLabel(it) } ?: TerminologyManager.getTerm("Tap to choose the bill"),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                        bills.forEach { b ->
                            val done = isFullyReturned(b)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (done) billLabel(b) + "  (" + TerminologyManager.getTerm("already returned") + ")" else billLabel(b)
                                    )
                                },
                                enabled = !done,
                                onClick = {
                                    selected = b
                                    someItemsOnly = false
                                    typedQty.clear()
                                    dropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // 4. Confirm
            if (current != null) {
                val done = alreadyReturned(current)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.inventoryEntries.isEmpty()) {
                            Text(TerminologyManager.getTerm("The whole bill will be returned."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        current.inventoryEntries.forEach { e ->
                            val remaining = (e.quantity - (done[e.id] ?: 0.0)).coerceAtLeast(0.0)
                            val name = stockItems[e.stockItemId]?.name ?: TerminologyManager.getTerm("Item")
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "$name  ×  ${fmtQty(remaining)}",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                                if (someItemsOnly && remaining > 0.0) {
                                    OutlinedTextField(
                                        value = typedQty[e.id] ?: "",
                                        onValueChange = { v -> typedQty[e.id] = v.filter { it.isDigit() || it == '.' } },
                                        placeholder = { Text("0") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(96.dp)
                                    )
                                }
                            }
                        }
                        if (current.inventoryEntries.isNotEmpty()) {
                            TextButton(onClick = {
                                someItemsOnly = !someItemsOnly
                                typedQty.clear()
                            }) {
                                Text(
                                    if (someItemsOnly) TerminologyManager.getTerm("Return the whole bill instead")
                                    else TerminologyManager.getTerm("Only some items came back?")
                                )
                            }
                        }
                    }
                }

                Button(
                    enabled = !saving && preview != null,
                    onClick = {
                        val cid = companyId ?: return@Button
                        scope.launch {
                            saving = true
                            try {
                                val prefix = if (kind == ReturnKind.SALE) "SR" else "PR"
                                var n = 1
                                while (database.voucherDao().getVoucherByNumber("$prefix-$n", cid) != null) n++
                                val result = ReturnVoucherBuilder.build(
                                    original = current,
                                    returnQty = requestedQty,
                                    voucherNumber = "$prefix-$n",
                                    date = Date(),
                                    partyLedgerIds = partyLedgerIds,
                                    alreadyReturnedQty = alreadyReturned(current)
                                )
                                repo.insertVoucherWithAllEntries(result.voucher, result.entries, result.inventory, result.allocations)
                                Toast.makeText(
                                    context,
                                    TerminologyManager.getTerm("Return saved") + " (${result.voucher.voucherNumber})",
                                    Toast.LENGTH_LONG
                                ).show()
                                reloadKey++
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    TerminologyManager.getTerm("Could not save the return") + ": ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        TerminologyManager.getTerm("Return") + "  " + (preview?.totalPaise ?: 0L).formatMoney(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val FAR_FUTURE = 4_102_444_800_000L // 2100-01-01

private fun startOfDay(d: Date): Date = Calendar.getInstance().apply {
    time = d
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.time

private fun endOfDay(d: Date): Date = Calendar.getInstance().apply {
    time = d
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.time

private fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else String.format(Locale.US, "%.2f", q)
