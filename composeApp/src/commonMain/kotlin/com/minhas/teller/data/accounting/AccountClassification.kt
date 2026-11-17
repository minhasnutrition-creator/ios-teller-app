package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.AccountGroup

/**
 * The 5 Fundamental Primary Account Heads of Double-Entry Accounting
 * according to IFRS and Indian GAAP.
 */
enum class PrimaryAccountType(val normalBalanceIsDebit: Boolean) {
    ASSET(normalBalanceIsDebit = true),
    LIABILITY(normalBalanceIsDebit = false),
    EQUITY(normalBalanceIsDebit = false),
    INCOME(normalBalanceIsDebit = false),
    EXPENSE(normalBalanceIsDebit = true)
}

object AccountHierarchy {

    // Standard pre-defined groups mapped to their primary classification
    private val standardGroupMappings = mapOf(
        // Assets
        "fixed assets" to PrimaryAccountType.ASSET,
        "current assets" to PrimaryAccountType.ASSET,
        "bank accounts" to PrimaryAccountType.ASSET,
        "cash-in-hand" to PrimaryAccountType.ASSET,
        "cash in hand" to PrimaryAccountType.ASSET,
        "stock-in-hand" to PrimaryAccountType.ASSET,
        "stock in hand" to PrimaryAccountType.ASSET,
        "sundry debtors" to PrimaryAccountType.ASSET,
        "debtors" to PrimaryAccountType.ASSET,
        "loans & advances (asset)" to PrimaryAccountType.ASSET,
        "deposits (asset)" to PrimaryAccountType.ASSET,
        "investments" to PrimaryAccountType.ASSET,
        "misc. expenses (asset)" to PrimaryAccountType.ASSET,

        // Liabilities
        "loans (liability)" to PrimaryAccountType.LIABILITY,
        "current liabilities" to PrimaryAccountType.LIABILITY,
        "sundry creditors" to PrimaryAccountType.LIABILITY,
        "creditors" to PrimaryAccountType.LIABILITY,
        "duties & taxes" to PrimaryAccountType.LIABILITY,
        "duties and taxes" to PrimaryAccountType.LIABILITY,
        "provisions" to PrimaryAccountType.LIABILITY,
        "bank od a/c" to PrimaryAccountType.LIABILITY,
        "bank occ a/c" to PrimaryAccountType.LIABILITY,
        "secured loans" to PrimaryAccountType.LIABILITY,
        "unsecured loans" to PrimaryAccountType.LIABILITY,
        "finance companies" to PrimaryAccountType.LIABILITY,

        // Equity / Capital
        "capital account" to PrimaryAccountType.EQUITY,
        "capital" to PrimaryAccountType.EQUITY,
        "reserves & surplus" to PrimaryAccountType.EQUITY,
        "reserves and surplus" to PrimaryAccountType.EQUITY,
        "retained earnings" to PrimaryAccountType.EQUITY,
        "drawings" to PrimaryAccountType.EQUITY,

        // Income
        "sales accounts" to PrimaryAccountType.INCOME,
        "sales" to PrimaryAccountType.INCOME,
        "direct incomes" to PrimaryAccountType.INCOME,
        "direct income" to PrimaryAccountType.INCOME,
        "indirect incomes" to PrimaryAccountType.INCOME,
        "indirect income" to PrimaryAccountType.INCOME,
        "revenue" to PrimaryAccountType.INCOME,

        // Expenses
        "purchase accounts" to PrimaryAccountType.EXPENSE,
        "purchase" to PrimaryAccountType.EXPENSE,
        "purchases" to PrimaryAccountType.EXPENSE,
        "direct expenses" to PrimaryAccountType.EXPENSE,
        "direct expense" to PrimaryAccountType.EXPENSE,
        "indirect expenses" to PrimaryAccountType.EXPENSE,
        "indirect expense" to PrimaryAccountType.EXPENSE,
        "cost of goods sold" to PrimaryAccountType.EXPENSE,
        "cogs" to PrimaryAccountType.EXPENSE
    )

    /**
     * Resolves an account group name to its primary accounting type.
     * Recursively traverses parent groups if defined in [allGroups], or uses
     * semantic heuristics if unknown.
     */
    fun classifyGroup(groupName: String, allGroups: List<AccountGroup> = emptyList()): PrimaryAccountType {
        val normalized = groupName.trim().lowercase()

        // 1. Direct standard group match
        standardGroupMappings[normalized]?.let { return it }

        // 2. Traversal up parent hierarchy
        if (allGroups.isNotEmpty()) {
            val groupMap = allGroups.associateBy { it.name.trim().lowercase() }
            var currentName = normalized
            val visited = mutableSetOf<String>()

            while (visited.add(currentName)) {
                val group = groupMap[currentName] ?: break
                val parent = group.parentGroup?.trim()?.lowercase() ?: break
                if (parent == "primary" || parent.isBlank()) break

                standardGroupMappings[parent]?.let { return it }
                currentName = parent
            }
        }

        // 3. Robust keyword heuristics
        return when {
            normalized.contains("sale") || normalized.contains("revenue") || normalized.contains("income") ->
                PrimaryAccountType.INCOME

            normalized.contains("purchase") || normalized.contains("expense") || normalized.contains("cost") ||
            normalized.contains("salary") || normalized.contains("rent") || normalized.contains("depreciation") ->
                PrimaryAccountType.EXPENSE

            normalized.contains("capital") || normalized.contains("equity") || normalized.contains("reserve") ||
            normalized.contains("drawing") || normalized.contains("retained") ->
                PrimaryAccountType.EQUITY

            normalized.contains("creditor") || normalized.contains("payable") || normalized.contains("loan") ||
            normalized.contains("tax") || normalized.contains("duty") || normalized.contains("liability") ||
            normalized.contains("overdraft") || normalized.contains("borrowing") || normalized.contains("provision") ->
                PrimaryAccountType.LIABILITY

            normalized.contains("debtor") || normalized.contains("receivable") || normalized.contains("asset") ||
            normalized.contains("bank") || normalized.contains("cash") || normalized.contains("stock") ||
            normalized.contains("inventory") || normalized.contains("deposit") || normalized.contains("investment") ->
                PrimaryAccountType.ASSET

            else -> PrimaryAccountType.ASSET
        }
    }

    /**
     * Checks if a group belongs to the Balance Sheet (Assets, Liabilities, Equity).
     */
    fun isBalanceSheetGroup(groupName: String, allGroups: List<AccountGroup> = emptyList()): Boolean {
        val type = classifyGroup(groupName, allGroups)
        return type == PrimaryAccountType.ASSET || type == PrimaryAccountType.LIABILITY || type == PrimaryAccountType.EQUITY
    }

    /**
     * Checks if a group belongs to the Profit & Loss Statement (Income, Expense).
     */
    fun isProfitAndLossGroup(groupName: String, allGroups: List<AccountGroup> = emptyList()): Boolean {
        val type = classifyGroup(groupName, allGroups)
        return type == PrimaryAccountType.INCOME || type == PrimaryAccountType.EXPENSE
    }
}
