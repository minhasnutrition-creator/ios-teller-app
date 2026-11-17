package com.minhas.teller.utils

import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Unit tests for the deterministic GST compliance rules. Pure logic — runs on JVM, no device.
 * These pin the turnover thresholds and output/input tax math that drive the CA scrutiny warnings.
 */
class GstComplianceRulesTest {

    private val today = Date()

    private fun sale(
        id: Int,
        amount: Double,
        taxAmount: Double,
        totalAmount: Double = amount + taxAmount
    ) = VoucherWithAllEntries(
        voucher = Voucher(id = id, type = "Sales", date = today, voucherNumber = "S$id"),
        voucherEntries = listOf(
            VoucherEntry(id = id, voucherId = id, ledgerId = 1, amount = totalAmount.rupeesToPaise(), isDebit = true),
            VoucherEntry(id = id + 100, voucherId = id, ledgerId = 2, amount = amount.rupeesToPaise(), isDebit = false)
        ),
        inventoryEntries = listOf(
            InventoryEntry(
                id = id, voucherId = id, stockItemId = 1, quantity = 1.0, rate = amount,
                amount = amount.rupeesToPaise(), taxableAmount = amount.rupeesToPaise(), taxAmount = taxAmount.rupeesToPaise(), totalAmount = totalAmount.rupeesToPaise()
            )
        )
    )

    private fun purchase(id: Int, taxAmount: Double) = VoucherWithAllEntries(
        voucher = Voucher(id = id, type = "Purchase", date = today, voucherNumber = "P$id"),
        voucherEntries = listOf(VoucherEntry(id = id, voucherId = id, ledgerId = 1, amount = taxAmount.rupeesToPaise(), isDebit = true)),
        inventoryEntries = listOf(
            InventoryEntry(id = id, voucherId = id, stockItemId = 1, quantity = 1.0, rate = 1000.0, amount = 1000.0.rupeesToPaise(), taxableAmount = 1000.0.rupeesToPaise(), taxAmount = taxAmount.rupeesToPaise(), totalAmount = (1000.0 + taxAmount).rupeesToPaise())
        )
    )

    @Test
    fun `annual turnover excludes GST`() {
        val turnover = GstComplianceRules.annualTurnover(
            listOf(sale(1, 1000.0, 180.0), sale(2, 500.0, 90.0), purchase(3, 50.0))
        )
        assertEquals(1000.0 + 500.0, turnover, 0.001)
    }

    @Test
    fun `output and input tax come from inventory tax amounts`() {
        val vouchers = listOf(sale(1, 1000.0, 180.0), purchase(2, 60.0))
        assertEquals(180.0, GstComplianceRules.outputTax(vouchers), 0.001)
        assertEquals(60.0, GstComplianceRules.inputTax(vouchers), 0.001)
        assertEquals(120.0, GstComplianceRules.netLiability(180.0, 60.0), 0.001)
    }

    @Test
    fun `turnover above 40 lakh without gstin is a violation`() {
        val f = GstComplianceRules.registrationFinding(50_00_000.0, hasGstin = false)
        assertNotNull(f)
        assertEquals(GstComplianceRules.Severity.VIOLATION, f!!.severity)
    }

    @Test
    fun `registration finding is null when gstin present or below limit`() {
        assertNull(GstComplianceRules.registrationFinding(50_00_000.0, hasGstin = true))
        assertNull(GstComplianceRules.registrationFinding(30_00_000.0, hasGstin = false))
    }

    @Test
    fun `collecting tax without gstin is a violation`() {
        assertNotNull(GstComplianceRules.collectingTaxWithoutGstinFinding(10_000.0, hasGstin = false))
        assertNull(GstComplianceRules.collectingTaxWithoutGstinFinding(10_000.0, hasGstin = true))
        assertNull(GstComplianceRules.collectingTaxWithoutGstinFinding(0.0, hasGstin = false))
    }

    @Test
    fun `composition limit exceeded is a warning`() {
        assertNotNull(GstComplianceRules.compositionFinding(1_60_00_000.0))
        assertNull(GstComplianceRules.compositionFinding(1_00_00_000.0))
    }

    @Test
    fun `audit threshold over 5 crore is informational`() {
        assertEquals(GstComplianceRules.Severity.INFO, GstComplianceRules.auditFinding(6_00_00_000.0)!!.severity)
        assertNull(GstComplianceRules.auditFinding(4_00_00_000.0))
    }

    @Test
    fun `indian currency formatting groups correctly`() {
        assertEquals("₹999", GstComplianceRules.inr(999.0))
        assertEquals("₹5,00,00,000", GstComplianceRules.inr(5_00_00_000.0))
        assertEquals("₹1,50,000", GstComplianceRules.inr(1_50_000.0))
    }

    @Test
    fun `runAll returns findings for a high turnover unregistered business`() {
        val findings = GstComplianceRules.runAll(
            turnover = 6_00_00_000.0,
            outputTax = 5_00_000.0,
            inputTax = 2_00_000.0,
            hasGstin = false
        )
        val titles = findings.map { it.title }
        assert(titles.contains("GST Registration Required"))
        assert(titles.contains("GST Collected Without Registration"))
        assert(titles.contains("Composition Scheme Limit Exceeded"))
        assert(titles.contains("GSTR-9C Reconciliation & Audit Required"))
    }

    @Test
    fun `runAll is empty for a registered business under all limits`() {
        val findings = GstComplianceRules.runAll(
            turnover = 10_00_000.0,
            outputTax = 50_000.0,
            inputTax = 10_000.0,
            hasGstin = true
        )
        assertEquals(0, findings.size)
    }

    // ---- tax-line classification helpers ---------------------------------------------

    private fun entry(
        cgstRate: Double,
        sgstRate: Double,
        igstRate: Double,
        gstRate: Double = if (cgstRate + sgstRate > 0.0) cgstRate + sgstRate else igstRate,
        taxableAmount: Double = 0.0,
        taxAmount: Double = 0.0,
        amount: Double = 118.0
    ) = InventoryEntry(
        voucherId = 1, stockItemId = 1, quantity = 1.0, rate = amount, amount = amount.rupeesToPaise(),
        gstRate = gstRate, cgstRate = cgstRate, sgstRate = sgstRate, igstRate = igstRate,
        taxableAmount = taxableAmount.rupeesToPaise(), taxAmount = taxAmount.rupeesToPaise(), totalAmount = (amount + taxAmount).rupeesToPaise()
    )

    @Test
    fun `inter state is cgst and sgst both zero`() {
        // The historical bug: intra-state lines stored the FULL rate in igstRate (9+9+18=36%).
        assertFalse(GstComplianceRules.isInterState(entry(cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0)))
        assertTrue(GstComplianceRules.isInterState(entry(cgstRate = 0.0, sgstRate = 0.0, igstRate = 18.0)))
    }

    @Test
    fun `lineTax does not double count full igstRate on intra state rows`() {
        // Legacy row with no stored taxAmount but a full igstRate: tax must be 18%, not 36%.
        val legacy = entry(cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0, taxableAmount = 100.0)
        assertEquals(18.0, GstComplianceRules.lineTax(legacy), 0.001)
    }

    @Test
    fun `lineTax prefers the posted taxAmount`() {
        val posted = entry(cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0, taxableAmount = 100.0, taxAmount = 18.0)
        assertEquals(18.0, GstComplianceRules.lineTax(posted), 0.001)
    }

    @Test
    fun `taxSplit splits intra state into cgst and sgst`() {
        val (cgst, sgst, igst) = GstComplianceRules.taxSplit(
            entry(cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0, taxableAmount = 100.0, taxAmount = 18.0)
        )
        assertEquals(9.0, cgst, 0.001)
        assertEquals(9.0, sgst, 0.001)
        assertEquals(0.0, igst, 0.001)
    }

    @Test
    fun `taxSplit puts whole tax in igst inter state`() {
        val (cgst, sgst, igst) = GstComplianceRules.taxSplit(
            entry(cgstRate = 0.0, sgstRate = 0.0, igstRate = 18.0, taxableAmount = 100.0, taxAmount = 18.0)
        )
        assertEquals(0.0, cgst, 0.001)
        assertEquals(0.0, sgst, 0.001)
        assertEquals(18.0, igst, 0.001)
    }

    @Test
    fun `splitTax reconciles exactly to the total`() {
        val (cgst, sgst) = GstComplianceRules.splitTax(10.0)
        assertEquals(5.0, cgst, 0.001)
        assertEquals(5.0, sgst, 0.001)
        assertEquals(10.0, cgst + sgst, 0.001)

        // Odd paisa: heads must still sum to the total even when one carries the full paisa.
        val (c, s) = GstComplianceRules.splitTax(0.01)
        assertEquals(0.01, c + s, 0.0001)
    }

    @Test
    fun `taxableValue backs out tax for legacy rows`() {
        assertEquals(100.0, GstComplianceRules.taxableValue(
            entry(cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0, amount = 118.0)
        ), 0.001)
        // Taxable amount already stored wins.
        assertEquals(100.0, GstComplianceRules.taxableValue(
            entry(cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0, taxableAmount = 100.0, amount = 118.0)
        ), 0.001)
    }
}
