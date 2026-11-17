package com.minhas.teller.data.repositories

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the outstanding-eligibility classification logic.
 * Pure logic — no Room database needed. Runs on JVM, no device required.
 *
 * A bug here means customers silently missing from (or wrongly appearing in)
 * the Outstanding Payments report, which directly affects collections.
 */
class OutstandingClassificationTest {

    // ── Group eligibility ──────────────────────────────────────────────────

    @Test
    fun `party groups are eligible`() {
        val eligible = listOf(
            "Sundry Debtors", "Sundry Creditors", "Suppliers",
            "Customers", "Finance Companies", "Loans (Liability)",
            "Loans & Advances (Asset)"
        )
        for (group in eligible) {
            assertTrue("'$group' should be eligible", VoucherRepository.isOutstandingEligible(group))
        }
    }

    @Test
    fun `finance companies group is always eligible`() {
        assertTrue(VoucherRepository.isOutstandingEligible("Finance Companies"))
        assertTrue(VoucherRepository.isOutstandingEligible("Finance Companies (Loans)"))
    }

    @Test
    fun `income and expense groups are NOT eligible`() {
        val notEligible = listOf(
            "Sales", "Purchase", "Expenses", "Income", "Revenue",
            "Cash", "Bank", "Capital", "Stock", "Inventory",
            "Fixed Assets", "Current Assets", "Current Liabilities",
            "Suspense", "Misc"
        )
        for (group in notEligible) {
            assertFalse("'$group' should NOT be eligible", VoucherRepository.isOutstandingEligible(group))
        }
    }

    @Test
    fun `tax groups are NOT eligible`() {
        assertFalse(VoucherRepository.isOutstandingEligible("GST"))
        assertFalse(VoucherRepository.isOutstandingEligible("VAT"))
        assertFalse(VoucherRepository.isOutstandingEligible("Duty"))
    }

    @Test
    fun `trimmed and cased input works`() {
        assertTrue(VoucherRepository.isOutstandingEligible("  Sundry Debtors  "))
        assertTrue(VoucherRepository.isOutstandingEligible("SUNDRY DEBTORS"))
        assertTrue(VoucherRepository.isOutstandingEligible("sundry creditors"))
    }

    // ── Party ledger name filter ──────────────────────────────────────────

    @Test
    fun `party names are valid`() {
        assertTrue(VoucherRepository.isPartyLedgerName("ABC Traders"))
        assertTrue(VoucherRepository.isPartyLedgerName("Rajesh Kumar"))
        // Bank name under Finance Companies group should pass
        assertTrue(VoucherRepository.isPartyLedgerName("IDFC Bank", "Finance Companies"))
        assertTrue(VoucherRepository.isPartyLedgerName("HDFC Ltd", "Finance Companies"))
    }

    @Test
    fun `system ledger names are NOT party ledgers`() {
        val invalid = listOf(
            "Sales", "Purchase", "Expenses", "Income",
            "Cash", "Capital", "CGST", "SGST", "IGST",
            "VAT Payable", "Opening Stock", "Closing Stock",
            "Depreciation", "Profit & Loss"
        )
        for (name in invalid) {
            assertFalse("'$name' should NOT be a party name", VoucherRepository.isPartyLedgerName(name))
        }
    }

    @Test
    fun `regular name with excluded keyword substring is still valid`() {
        // "CashKaro Traders" contains "cash" but is a real party name
        assertTrue(VoucherRepository.isPartyLedgerName("CashKaro Traders"))
    }

    @Test
    fun `name that is exactly an excluded keyword is blocked`() {
        assertFalse(VoucherRepository.isPartyLedgerName("cash"))
        assertFalse(VoucherRepository.isPartyLedgerName("SALES"))
    }
}
