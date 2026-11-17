package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.AccountGroup
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.GstComplianceRules
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * One answer to "what did the goods I sold actually cost me?", shared by the Profit & Loss report,
 * the Sales Profitability dialog, Item Profitability, the dashboard loss warning and the Balance
 * Sheet's profit line, so they can never disagree with each other.
 *
 * How profit is worked out (the way a shop owner would do it by hand):
 *  - Sale value is the bill amount WITHOUT GST. GST collected belongs to the government and is not
 *    income; GST paid on purchases comes back as input credit, so cost is also taken without GST.
 *  - Cost of an item sold = the weighted-average price paid for the stock on hand on the day of the
 *    sale (opening stock + every purchase so far, minus what already went out). Not the latest
 *    purchase price, and never a purchase made after the sale (unless nothing was bought before it —
 *    see the fallback below).
 *  - Returns undo the original entry: a sales return takes back the sale value AND the cost it went
 *    out at; a purchase return takes the goods out at the price they were bought for.
 *  - Purchases not linked to a stock item (a bill saved without items, a "Money Out" booked to the
 *    Purchase account, a scanned bill with no item) can't be counted in stock, so they are cost in
 *    the period they were made — the same as Tally when no closing stock is recorded for them.
 *  - Sold before the purchase was entered (very common in shops): the item's average purchase price
 *    across all its purchases is used. If an item was never bought and has no opening value, its
 *    cost is UNKNOWN and is reported as such — it is never silently treated as free (100% margin).
 *
 * Money is in paise (Long); per-unit costs are carried as Double paise and rounded once per line.
 *
 * Pure logic: no database or Android access, so it can be unit-tested on the JVM.
 */
object ProfitCalculator {

    /** Label of the cost-of-goods-sold line in the Profit & Loss report. */
    const val COGS_LABEL = "Cost of Goods Sold"

    private const val QTY_EPS = 1e-7

    // ---------------------------------------------------------------------------------------------
    // Which ledgers are Sales / Purchase / Income / Expense
    // ---------------------------------------------------------------------------------------------

    private val SALES_ROOTS = setOf("sales accounts", "sales account", "sales", "revenue")
    private val PURCHASE_ROOTS = setOf("purchase accounts", "purchase account", "purchase", "purchases", "cost of goods sold", "cogs")
    private val OTHER_PNL_ROOTS = setOf(
        "direct incomes", "direct income", "indirect incomes", "indirect income",
        "direct expenses", "direct expense", "indirect expenses", "indirect expense"
    )

    data class LedgerRoles(
        val typeById: Map<Int, PrimaryAccountType>,
        /** Income ledgers that are trading sales (Sales Accounts and groups under it). */
        val salesLedgerIds: Set<Int>,
        /** Expense ledgers that hold purchases of goods — replaced by Cost of Goods Sold in the P&L. */
        val purchaseLedgerIds: Set<Int>
    ) {
        companion object {
            val EMPTY = LedgerRoles(emptyMap(), emptySet(), emptySet())
        }
    }

    /**
     * Classifies ledgers with the same rules the Balance Sheet uses ([AccountHierarchy]), so a custom
     * group (e.g. "Shop Rent" under Indirect Expenses) lands in the right place in both reports.
     */
    fun classifyLedgers(ledgers: List<Ledger>, groups: List<AccountGroup> = emptyList()): LedgerRoles {
        val parentOf = HashMap<String, String?>()
        groups.forEach { parentOf[it.name.trim().lowercase()] = it.parentGroup?.trim()?.lowercase() }

        // First standard P&L group found walking up from the ledger's group, or null.
        fun standardRoot(groupName: String): String? {
            var current = groupName.trim().lowercase()
            val seen = HashSet<String>()
            while (seen.add(current)) {
                if (current in SALES_ROOTS || current in PURCHASE_ROOTS || current in OTHER_PNL_ROOTS) return current
                val parent = parentOf[current] ?: return null
                if (parent.isBlank() || parent == "primary") return null
                current = parent
            }
            return null
        }

        val typeCache = HashMap<String, PrimaryAccountType>()
        val types = HashMap<Int, PrimaryAccountType>()
        val sales = HashSet<Int>()
        val purchases = HashSet<Int>()
        for (ledger in ledgers) {
            val type = typeCache.getOrPut(ledger.group) { AccountHierarchy.classifyGroup(ledger.group, groups) }
            types[ledger.id] = type
            val root = standardRoot(ledger.group)
            val group = ledger.group.trim().lowercase()
            when (type) {
                PrimaryAccountType.INCOME -> {
                    val isSales = if (root != null) root in SALES_ROOTS
                                  else group.contains("sale") || group.contains("revenue")
                    if (isSales) sales += ledger.id
                }
                PrimaryAccountType.EXPENSE -> {
                    val byGroup = if (root != null) root in PURCHASE_ROOTS
                                  else group.contains("purchase") || group.contains("cogs") || group.contains("cost of goods")
                    // The voucher screens pick "the ledger whose name says Purchase" for purchase bills,
                    // so a "Purchase" ledger filed under e.g. Direct Expenses is still the purchase account
                    // (otherwise it would be counted twice: once in full and once as cost of goods sold).
                    val byName = ledger.name.trim().lowercase().startsWith("purchase")
                    if (byGroup || byName) purchases += ledger.id
                }
                else -> Unit
            }
        }
        return LedgerRoles(types, sales, purchases)
    }

    // ---------------------------------------------------------------------------------------------
    // Cost of every item sold, valued at the moment it was sold
    // ---------------------------------------------------------------------------------------------

    /** One sold or returned item line. Quantity and money are signed: + sold, − returned. */
    data class CostedLine(
        val voucherId: Int,
        val date: Date,
        /** Same-named stock items count as one item; this is the id they are grouped under. 0 = no stock item. */
        val itemKey: Int,
        val quantity: Double,
        /** Sale value without GST, in paise. */
        val revenuePaise: Long,
        /** What the goods cost (without GST), in paise. 0 when [costKnown] is false. */
        val costPaise: Long,
        /** False when the item was never bought and has no opening value, so its cost is unknown. */
        val costKnown: Boolean,
        /** True when the bill line carried GST (so the sale value is lower than the bill amount). */
        val hadTax: Boolean
    )

    /** Purchase value that never went into tracked stock (signed: returns are negative). */
    data class UntrackedPurchase(val voucherId: Int, val date: Date, val amountPaise: Long)

    class CostLedger(
        val lines: List<CostedLine>,
        val untrackedPurchases: List<UntrackedPurchase>,
        /** itemKey -> item name. */
        val itemNames: Map<Int, String>
    ) {
        companion object {
            val EMPTY = CostLedger(emptyList(), emptyList(), emptyMap())
        }
    }

    private fun norm(type: String) = type.trim().lowercase()
    private fun isPurchase(t: String) = t == "purchase"
    private fun isPurchaseReturn(t: String) = t == "purchase return" || t == "debit note"
    private fun isSale(t: String) = t == "sales" || t == "sale"
    private fun isSalesReturn(t: String) = t == "sales return" || t == "credit note"
    private fun isJournal(t: String) = t == "journal"

    /** Same day: stock comes in first, then goes out, then sales returns (they follow their sale). */
    private fun dayOrder(t: String): Int = when {
        isPurchase(t) || isJournal(t) -> 0
        isSale(t) || isPurchaseReturn(t) -> 1
        isSalesReturn(t) -> 2
        else -> 3
    }

    /**
     * Walks every voucher of the company in date order and values each sale / sales-return line.
     *
     * @param allVouchers every voucher of the company (all dates) — cost depends on history.
     * @param purchaseLedgerIds purchase-account ledgers ([LedgerRoles.purchaseLedgerIds]). Needed only
     *        for the P&L, to count purchases that were not entered item-wise.
     */
    fun buildCostLedger(
        stockItems: List<StockItem>,
        allVouchers: List<VoucherWithAllEntries>,
        purchaseLedgerIds: Set<Int> = emptySet()
    ): CostLedger {
        // 1. Same-named items are one item (duplicates from scans / imports / delete-and-recreate).
        val keyByName = HashMap<String, Int>()
        val keyById = HashMap<Int, Int>()
        val names = HashMap<Int, String>()
        stockItems.sortedWith(compareBy<StockItem>({ it.isDeleted }, { it.id })).forEach { si ->
            val nm = si.name.trim().lowercase()
            val key = if (nm.isEmpty()) si.id else keyByName.getOrPut(nm) { si.id }
            keyById[si.id] = key
            if (!names.containsKey(key)) names[key] = si.name.trim()
        }
        fun keyOf(stockItemId: Int): Int = if (stockItemId <= 0) 0 else keyById[stockItemId] ?: stockItemId

        val qty = HashMap<Int, Double>()
        val value = HashMap<Int, Double>()       // paise
        val lastUnit = HashMap<Int, Double>()    // paise per unit: last known average cost

        fun avgNow(k: Int): Double? {
            val q = qty[k] ?: 0.0
            val v = value[k] ?: 0.0
            return if (q > QTY_EPS && v > 0.0) v / q else null
        }
        fun settle(k: Int) {
            if (abs(qty[k] ?: 0.0) < QTY_EPS) { qty[k] = 0.0; value[k] = 0.0 }
            avgNow(k)?.let { lastUnit[k] = it }
        }
        fun stockIn(k: Int, q: Double, v: Double) {
            qty[k] = (qty[k] ?: 0.0) + q
            value[k] = (value[k] ?: 0.0) + v
            settle(k)
        }
        fun stockOut(k: Int, q: Double, unit: Double) {
            qty[k] = (qty[k] ?: 0.0) - q
            value[k] = (value[k] ?: 0.0) - q * unit
            settle(k)
        }

        // 2. Opening stock at the item's opening rate (deleted items no longer hold stock).
        stockItems.forEach { si ->
            if (!si.isDeleted && si.openingStock > QTY_EPS) {
                val k = keyOf(si.id)
                qty[k] = (qty[k] ?: 0.0) + si.openingStock
                value[k] = (value[k] ?: 0.0) + si.openingStock * si.rate * 100.0
            }
        }
        qty.keys.toList().forEach { settle(it) }

        // 3. Average purchase price over ALL purchases — used only when an item is sold before any
        //    stock of it was recorded (purchase bill entered after the sale).
        val boughtQty = HashMap<Int, Double>()
        val boughtValue = HashMap<Int, Double>()
        allVouchers.forEach { vw ->
            if (vw.voucher.isDeleted || !isPurchase(norm(vw.voucher.type))) return@forEach
            vw.inventoryEntries.forEach { e ->
                val k = keyOf(e.stockItemId)
                val tx = GstComplianceRules.taxablePaise(e)
                if (k > 0 && e.quantity > QTY_EPS && tx > 0L) {
                    boughtQty[k] = (boughtQty[k] ?: 0.0) + e.quantity
                    boughtValue[k] = (boughtValue[k] ?: 0.0) + tx
                }
            }
        }
        fun allTimeAvg(k: Int): Double? {
            val q = boughtQty[k] ?: return null
            return if (q > QTY_EPS) (boughtValue[k] ?: 0.0) / q else null
        }
        fun unitCostOut(k: Int): Double? = avgNow(k) ?: lastUnit[k] ?: allTimeAvg(k)

        // 4. Walk in date order.
        val cal = Calendar.getInstance()
        fun dayKey(d: Date): Int {
            cal.time = d
            return cal.get(Calendar.YEAR) * 10000 + cal.get(Calendar.MONTH) * 100 + cal.get(Calendar.DAY_OF_MONTH)
        }
        val ordered = allVouchers
            .filter { !it.voucher.isDeleted }
            .map { Triple(it, dayKey(it.voucher.date), dayOrder(norm(it.voucher.type))) }
            .sortedWith(compareBy({ it.second }, { it.third }, { it.first.voucher.date.time }, { it.first.voucher.id }))
            .map { it.first }

        val lines = ArrayList<CostedLine>()
        val untracked = ArrayList<UntrackedPurchase>()
        val saleUnitCost = HashMap<Pair<Int, Int>, Double>() // (sale voucher id, itemKey) -> unit cost

        for (vw in ordered) {
            val v = vw.voucher
            val t = norm(v.type)
            val inv = vw.inventoryEntries
            when {
                isPurchase(t) -> inv.forEach { e ->
                    val k = keyOf(e.stockItemId)
                    val tx = GstComplianceRules.taxablePaise(e)
                    if (k <= 0) {
                        if (tx != 0L) untracked += UntrackedPurchase(v.id, v.date, tx)
                    } else {
                        stockIn(k, e.quantity, tx.toDouble())
                    }
                }

                isPurchaseReturn(t) -> inv.forEach { e ->
                    val k = keyOf(e.stockItemId)
                    val tx = GstComplianceRules.taxablePaise(e)
                    if (k <= 0) {
                        if (tx != 0L) untracked += UntrackedPurchase(v.id, v.date, -tx)
                    } else {
                        val ownUnit = if (abs(e.quantity) > QTY_EPS && tx > 0L) tx / e.quantity else null
                        stockOut(k, e.quantity, ownUnit ?: unitCostOut(k) ?: 0.0)
                    }
                }

                isSale(t) -> inv.forEach { e ->
                    val k = keyOf(e.stockItemId)
                    val revenue = GstComplianceRules.taxablePaise(e)
                    val hadTax = revenue != e.amount
                    val q = e.quantity
                    if (k <= 0) {
                        lines += CostedLine(v.id, v.date, 0, q, revenue, 0L, costKnown = false, hadTax = hadTax)
                        return@forEach
                    }
                    val qtyBefore = qty[k] ?: 0.0
                    val valueBefore = value[k] ?: 0.0
                    val unit = unitCostOut(k)
                    val cost: Long = when {
                        unit == null -> 0L
                        // Selling the last pieces: all the value left in stock is their cost (no paisa drift).
                        q > QTY_EPS && qtyBefore > QTY_EPS && abs(qtyBefore - q) < QTY_EPS && valueBefore > 0.0 ->
                            valueBefore.roundToLong()
                        else -> (q * unit).roundToLong()
                    }
                    qty[k] = qtyBefore - q
                    if (unit != null) {
                        value[k] = valueBefore - cost
                        saleUnitCost[v.id to k] = unit
                    }
                    settle(k)
                    lines += CostedLine(v.id, v.date, k, q, revenue, cost, costKnown = unit != null || abs(q) < QTY_EPS, hadTax = hadTax)
                }

                isSalesReturn(t) -> inv.forEach { e ->
                    val k = keyOf(e.stockItemId)
                    val revenue = GstComplianceRules.taxablePaise(e)
                    val hadTax = revenue != e.amount
                    val q = e.quantity
                    if (k <= 0) {
                        lines += CostedLine(v.id, v.date, 0, -q, -revenue, 0L, costKnown = false, hadTax = hadTax)
                        return@forEach
                    }
                    // Goods come back at the cost they went out at.
                    val unit = v.returnOfVoucherId?.let { saleUnitCost[it to k] } ?: unitCostOut(k)
                    val cost = if (unit != null) (q * unit).roundToLong() else 0L
                    stockIn(k, q, cost.toDouble())
                    lines += CostedLine(v.id, v.date, k, -q, -revenue, -cost, costKnown = unit != null || abs(q) < QTY_EPS, hadTax = hadTax)
                }

                isJournal(t) -> inv.forEach { e ->
                    val k = keyOf(e.stockItemId)
                    if (k <= 0 || abs(e.quantity) < QTY_EPS) return@forEach
                    if (e.quantity > 0) {
                        val stated = abs(e.amount).toDouble()
                        val valueIn = if (stated > 0.0) stated else (unitCostOut(k) ?: 0.0) * e.quantity
                        stockIn(k, e.quantity, valueIn)
                    } else {
                        stockOut(k, -e.quantity, unitCostOut(k) ?: 0.0)
                    }
                }
            }

            // Purchase account used outside an item-wise purchase bill (bill without items, "Money Out"
            // to Purchase, journal): that money can't be in tracked stock, so it is cost right away.
            val itemWisePurchase = (isPurchase(t) || isPurchaseReturn(t)) && inv.isNotEmpty()
            if (purchaseLedgerIds.isNotEmpty() && !itemWisePurchase && t != "quotation") {
                var net = 0L
                vw.voucherEntries.forEach { en ->
                    if (en.ledgerId in purchaseLedgerIds) net += if (en.isDebit) en.amount else -en.amount
                }
                if (net != 0L) untracked += UntrackedPurchase(v.id, v.date, net)
            }
        }

        return CostLedger(lines, untracked, names)
    }

    // ---------------------------------------------------------------------------------------------
    // Period summaries
    // ---------------------------------------------------------------------------------------------

    /** Lines/purchases dated within [from]..[to] (inclusive; null = open-ended). */
    fun inRange(from: Date?, to: Date?): (Int, Date) -> Boolean =
        { _, d -> (from == null || !d.before(from)) && (to == null || !d.after(to)) }

    /** Lines/purchases belonging to the given vouchers. */
    fun inVouchers(voucherIds: Set<Int>): (Int, Date) -> Boolean = { id, _ -> id in voucherIds }

    data class CogsSummary(
        /** Total cost of goods sold for the period = [stockCostPaise] + [untrackedPurchasePaise]. */
        val cogsPaise: Long,
        /** Cost of stock items sold, net of returns. */
        val stockCostPaise: Long,
        /** Purchases not entered item-wise (net of their returns). */
        val untrackedPurchasePaise: Long,
        /** Item-wise sale value without GST, net of returns. */
        val itemSalesPaise: Long,
        /** Sale value of items whose cost is unknown (never bought, no opening value). */
        val salesWithoutCostPaise: Long,
        /** Number of such items. */
        val itemsWithoutCost: Int
    ) {
        companion object {
            val ZERO = CogsSummary(0L, 0L, 0L, 0L, 0L, 0)
        }
    }

    fun cogs(ledger: CostLedger, include: (Int, Date) -> Boolean): CogsSummary {
        var stockCost = 0L
        var itemSales = 0L
        var noCostSales = 0L
        val noCostItems = HashSet<Int>()
        for (l in ledger.lines) {
            if (!include(l.voucherId, l.date)) continue
            itemSales += l.revenuePaise
            if (l.itemKey > 0) {
                if (l.costKnown) stockCost += l.costPaise
                else { noCostSales += l.revenuePaise; noCostItems += l.itemKey }
            }
        }
        var untracked = 0L
        for (u in ledger.untrackedPurchases) if (include(u.voucherId, u.date)) untracked += u.amountPaise
        return CogsSummary(stockCost + untracked, stockCost, untracked, itemSales, noCostSales, noCostItems.size)
    }

    data class ItemProfit(
        val itemKey: Int,
        val name: String,
        /** Net quantity sold (sales minus returns). */
        val quantity: Double,
        /** Sale value without GST, paise. */
        val revenuePaise: Long,
        /** Cost of those goods without GST, paise (only meaningful when [costKnown]). */
        val costPaise: Long,
        /** False when at least part of what was sold has no known purchase price. */
        val costKnown: Boolean,
        /** True when the sales carried GST. */
        val hadTax: Boolean
    ) {
        val profitPaise: Long get() = revenuePaise - costPaise
        /** Rupees per unit, without GST. */
        val avgSaleRate: Double get() = if (abs(quantity) > 1e-9) revenuePaise / 100.0 / quantity else 0.0
        /** Rupees per unit, without GST. */
        val avgCostRate: Double get() = if (abs(quantity) > 1e-9) costPaise / 100.0 / quantity else 0.0
        /** Profit as % of sale value (negative = sold at a loss). */
        val marginPercent: Double get() = if (revenuePaise > 0L) profitPaise * 100.0 / revenuePaise else 0.0
    }

    /** Profit per stock item for the selected lines (items without a stock item are not listed). */
    fun itemProfits(ledger: CostLedger, include: (Int, Date) -> Boolean): List<ItemProfit> {
        class Acc { var q = 0.0; var revenue = 0L; var cost = 0L; var known = true; var tax = false }
        val acc = LinkedHashMap<Int, Acc>()
        for (l in ledger.lines) {
            if (l.itemKey <= 0 || !include(l.voucherId, l.date)) continue
            val a = acc.getOrPut(l.itemKey) { Acc() }
            a.q += l.quantity
            a.revenue += l.revenuePaise
            a.cost += l.costPaise
            if (!l.costKnown) a.known = false
            if (l.hadTax) a.tax = true
        }
        return acc.mapNotNull { (key, a) ->
            if (abs(a.q) < QTY_EPS && a.revenue == 0L && a.cost == 0L) null
            else ItemProfit(key, ledger.itemNames[key] ?: "Item #$key", a.q, a.revenue, a.cost, a.known, a.tax)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Profit & Loss for a period
    // ---------------------------------------------------------------------------------------------

    data class PnlTotals(
        /** Group name -> income for the period (paise, positive = income). */
        val incomesByGroup: Map<String, Long>,
        /** Group name -> expense for the period (paise); purchases replaced by [COGS_LABEL]. */
        val expensesByGroup: Map<String, Long>,
        /** Ledger id -> period movement (paise, debit positive). */
        val ledgerMovement: Map<Int, Long>,
        /** Operating-expense ledgers with their period amount (paise), purchases excluded. */
        val expenseLedgers: List<Pair<Ledger, Long>>,
        /** Trading sales (Sales Accounts), net of returns, without GST. */
        val salesPaise: Long,
        val totalIncomePaise: Long,
        val totalExpensePaise: Long,
        val cogs: CogsSummary
    ) {
        val grossProfitPaise: Long get() = salesPaise - cogs.cogsPaise
        val netProfitPaise: Long get() = totalIncomePaise - totalExpensePaise

        companion object {
            val ZERO = PnlTotals(emptyMap(), emptyMap(), emptyMap(), emptyList(), 0L, 0L, 0L, CogsSummary.ZERO)
        }
    }

    /**
     * Profit & Loss for [periodVouchers]:
     *   Gross profit = Sales − Cost of goods sold
     *   Net profit   = Gross profit + Other income − Expenses
     * The Purchase account is NOT an expense in full: unsold goods are still stock (an asset). It is
     * replaced by the cost of the goods actually sold in the period.
     *
     * @param costLedger built from ALL of the company's vouchers ([buildCostLedger]).
     */
    fun profitAndLoss(
        ledgers: List<Ledger>,
        periodVouchers: List<VoucherWithAllEntries>,
        roles: LedgerRoles,
        costLedger: CostLedger
    ): PnlTotals {
        val movement = HashMap<Int, Long>()
        val periodIds = HashSet<Int>()
        for (vw in periodVouchers) {
            if (vw.voucher.isDeleted || norm(vw.voucher.type) == "quotation") continue
            periodIds += vw.voucher.id
            for (e in vw.voucherEntries) {
                movement[e.ledgerId] = (movement[e.ledgerId] ?: 0L) + if (e.isDebit) e.amount else -e.amount
            }
        }
        val cogs = cogs(costLedger, inVouchers(periodIds))

        val incomes = LinkedHashMap<String, Long>()
        val expenses = LinkedHashMap<String, Long>()
        if (cogs.cogsPaise != 0L) expenses[COGS_LABEL] = cogs.cogsPaise
        val expenseLedgers = ArrayList<Pair<Ledger, Long>>()
        var sales = 0L

        for (ledger in ledgers) {
            val m = movement[ledger.id] ?: 0L
            if (m == 0L) continue
            val type = roles.typeById[ledger.id] ?: AccountHierarchy.classifyGroup(ledger.group)
            when (type) {
                PrimaryAccountType.INCOME -> {
                    incomes[ledger.group] = (incomes[ledger.group] ?: 0L) - m // credit balance = income
                    if (ledger.id in roles.salesLedgerIds) sales -= m
                }
                PrimaryAccountType.EXPENSE -> {
                    if (ledger.id in roles.purchaseLedgerIds) continue // replaced by cost of goods sold
                    expenses[ledger.group] = (expenses[ledger.group] ?: 0L) + m
                    expenseLedgers += ledger to m
                }
                else -> Unit // assets, liabilities, capital belong to the Balance Sheet
            }
        }

        val finalIncomes = incomes.filterValues { it != 0L }
        val finalExpenses = expenses.filterValues { it != 0L }
        return PnlTotals(
            incomesByGroup = finalIncomes,
            expensesByGroup = finalExpenses,
            ledgerMovement = movement,
            expenseLedgers = expenseLedgers,
            salesPaise = sales,
            totalIncomePaise = finalIncomes.values.sum(),
            totalExpensePaise = finalExpenses.values.sum(),
            cogs = cogs
        )
    }
}
