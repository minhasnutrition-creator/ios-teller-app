package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test suite for FinanceHelper's voice/text → voucher parsing pipeline.
 *
 * Tests the full chain:
 *  1. normalizeHindiNumbers: Hindi number words, Devanagari, ASR variants → digits
 *  2. parseVoiceCommandLocal: lightweight keyword-based voucher routing
 *  3. smartRegexParser: positional regex voucher extraction (English + Hinglish)
 *  4. tryParseHinglish: advanced Hinglish verb-first / verb-last pattern matching
 *  5. parseDateFromQuery: date extraction from natural language
 *  6. canonicalUnit: unit normalization
 *
 * All tests run with null Context (HSN/tax lookups will use heuristic fallbacks).
 * The focus is correctness of: voucher type, party name, amount, item name, quantity.
 */
class FinanceHelperParsingTest {

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 1: normalizeHindiNumbers  ══════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    // ── Hindi number words → digits ──
    @Test fun `normalize 'ek hazar' → 1000`() = assertNormalized("ek hazar", "1000")
    @Test fun `normalize 'do hazar' → 2000`() = assertNormalized("do hazar", "2000")
    @Test fun `normalize 'teen hazar' → 3000`() = assertNormalized("teen hazar", "3000")
    @Test fun `normalize 'panch hazar' → 5000`() = assertNormalized("panch hazar", "5000")
    @Test fun `normalize 'das hazar' → 10000`() = assertNormalized("das hazar", "10000")
    @Test fun `normalize 'bees hazar' → 20000`() = assertNormalized("bees hazar", "20000")
    @Test fun `normalize 'pachas hazar' → 50000`() = assertNormalized("pachas hazar", "50000")
    @Test fun `normalize 'ek lakh' → 100000`() = assertNormalized("ek lakh", "100000")
    @Test fun `normalize 'dhai lakh' → 250000`() = assertNormalized("dhai lakh", "250000")
    @Test fun `normalize 'dedh lakh' → 150000`() = assertNormalized("dedh lakh", "150000")
    @Test fun `normalize 'sava lakh' → 125000`() = assertNormalized("sava lakh", "125000")

    // ── Misspelled multipliers ──
    @Test fun `normalize 'hazaar' → 1000`() = assertNormalized("ek hazaar", "1000")
    @Test fun `normalize 'hazzar' → 1000`() = assertNormalized("ek hazzar", "1000")
    @Test fun `normalize 'hajar' → 1000`() = assertNormalized("ek hajar", "1000")

    // ── Hundreds ──
    @Test fun `normalize 'ekso' → 100`() = assertNormalized("ekso", "100")
    @Test fun `normalize 'doso' → 200`() = assertNormalized("doso", "200")
    @Test fun `normalize 'teenso' → 300`() = assertNormalized("teenso", "300")
    @Test fun `normalize 'charso' → 400`() = assertNormalized("charso", "400")
    @Test fun `normalize 'panchso' → 500`() = assertNormalized("panchso", "500")

    // ── Compound numbers (Indian numbering) ──
    @Test fun `normalize '5000 500' → 5500`() = assertNormalized("5000 500", "5500")
    @Test fun `normalize '10000 200' → 10200`() = assertNormalized("10000 200", "10200")

    // ── Currency stripping ──
    @Test fun `normalize strips rupees`() {
        val result = FinanceHelper.normalizeHindiNumbers("rupees 500")
        assertTrue("Should contain '500'", result.contains("500"))
        assertFalse("Should not contain 'rupees'", result.contains("rupees"))
    }
    @Test fun `normalize strips rs`() {
        val result = FinanceHelper.normalizeHindiNumbers("rs 1000")
        assertTrue("Should contain '1000'", result.contains("1000"))
    }

    // ── Action verb normalization ──
    @Test fun `normalize 'becha' → sold`() {
        val result = FinanceHelper.normalizeHindiNumbers("becha 5000")
        assertTrue("Should contain 'sold'", result.contains("sold"))
    }
    @Test fun `normalize 'kharida' → bought`() {
        val result = FinanceHelper.normalizeHindiNumbers("kharida 5000")
        assertTrue("Should contain 'bought'", result.contains("bought"))
    }
    @Test fun `normalize 'diye' → paid`() {
        val result = FinanceHelper.normalizeHindiNumbers("diye 5000")
        assertTrue("Should contain 'paid'", result.contains("paid"))
    }
    @Test fun `normalize 'mila' → received`() {
        val result = FinanceHelper.normalizeHindiNumbers("mila 5000")
        assertTrue("Should contain 'received'", result.contains("received"))
    }

    // ── English number words ──
    @Test fun `normalize 'five thousand' → 5000`() = assertNormalized("five thousand", "5000")
    @Test fun `normalize 'twenty thousand' → 20000`() = assertNormalized("twenty thousand", "20000")
    @Test fun `normalize 'nine thousand' → 9000`() = assertNormalized("nine thousand", "9000")

    // ── Shorthand: 5k, 1cr, 2.5lakh ──
    @Test fun `normalize '5k' → 5000`() = assertNormalized("5k", "5000")
    @Test fun `normalize '10k' → 10000`() = assertNormalized("10k", "10000")
    @Test fun `normalize '2_5k' → 2500`() = assertNormalized("2.5k", "2500")
    @Test fun `normalize '1cr' → 10000000`() = assertNormalized("1cr", "10000000")
    @Test fun `normalize '2_5lakh' → 250000`() = assertNormalized("2.5lakh", "250000")

    // ── "kroad" ASR misspelling of "crore" (was silently left untranslated) ──
    @Test fun `normalize '2 kroad' → 20000000`() = assertNormalized("2 kroad", "20000000")
    @Test fun `normalize '2 kroar' → 20000000`() = assertNormalized("2 kroar", "20000000")

    // ── Unit normalization ──
    @Test fun `normalize 'kilogram' → kg`() {
        val result = FinanceHelper.normalizeHindiNumbers("5 kilogram rice")
        assertTrue("Should contain 'kg'", result.contains("kg"))
    }
    @Test fun `normalize 'litre' → ltr`() {
        val result = FinanceHelper.normalizeHindiNumbers("10 litre milk")
        assertTrue("Should contain 'ltr'", result.contains("ltr"))
    }
    @Test fun `normalize 'pieces' → pcs`() {
        val result = FinanceHelper.normalizeHindiNumbers("20 pieces pen")
        assertTrue("Should contain 'pcs'", result.contains("pcs"))
    }

    // ── Filler word removal ──
    @Test fun `normalize strips 'bhaiya'`() {
        val result = FinanceHelper.normalizeHindiNumbers("bhaiya 500 sold")
        assertFalse("Should not contain 'bhaiya'", result.contains("bhaiya"))
    }
    @Test fun `normalize strips 'sir'`() {
        val result = FinanceHelper.normalizeHindiNumbers("sir 1000 paid")
        assertFalse("Should not contain 'sir'", result.contains("sir"))
    }

    // ── Typo correction ──
    @Test fun `normalize 'sopld' → sold`() {
        val result = FinanceHelper.normalizeHindiNumbers("sopld 500")
        assertTrue("Should contain 'sold'", result.contains("sold"))
    }
    @Test fun `normalize 'piad' → paid`() {
        val result = FinanceHelper.normalizeHindiNumbers("piad 500")
        assertTrue("Should contain 'paid'", result.contains("paid"))
    }
    @Test fun `normalize 'recieved' → received`() {
        val result = FinanceHelper.normalizeHindiNumbers("recieved 500")
        assertTrue("Should contain 'received'", result.contains("received"))
    }

    // ── Devanagari numbers ──
    @Test fun `normalize Devanagari digits '५००' → 500`() {
        val result = FinanceHelper.normalizeHindiNumbers("५०० sold")
        assertTrue("Should contain '500'", result.contains("500"))
    }

    // ── 'do' disambiguation (number vs verb) ──
    @Test fun `normalize 'do hazar' treats do as 2`() = assertNormalized("do hazar", "2000")
    @Test fun `normalize 'kar do' preserves do as verb`() {
        val result = FinanceHelper.normalizeHindiNumbers("entry kar do")
        assertTrue("Should preserve 'kar do' not replace 'do' with 2", result.contains("kar do"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 2: parseVoiceCommandLocal — Voucher Type Routing  ══════
    // ══════════════════════════════════════════════════════════════════════════

    // ── Sales ──
    @Test fun `voice 'sold goods 5000' → Sales voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("sold goods 5000")
        assertTrue("Should be DraftVoucher", result is AiResponse.DraftVoucher)
        assertEquals("Sales", (result as AiResponse.DraftVoucher).type)
        assertEquals(5000.0, result.amount ?: 0.0, 0.01)
    }

    @Test fun `voice 'sold goods to Ramesh 10000' → Sales with party Ramesh`() {
        val result = FinanceHelper.parseVoiceCommandLocal("sold goods to Ramesh 10000")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Sales", voucher.type)
        assertEquals(10000.0, voucher.amount ?: 0.0, 0.01)
        assertTrue("Party should contain 'Ramesh'", voucher.partyName?.contains("Ramesh") == true)
    }

    @Test fun `voice 'becha maal 15000' → Sales voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("becha maal 15000")
        assertTrue("Should be DraftVoucher", result is AiResponse.DraftVoucher)
        assertEquals("Sales", (result as AiResponse.DraftVoucher).type)
    }

    @Test fun `voice 'sold amount 0' → asks for amount`() {
        val result = FinanceHelper.parseVoiceCommandLocal("sold items")
        assertTrue("Should ask for amount", result is AiResponse.Message)
    }

    // ── Purchases ──
    @Test fun `voice 'bought goods 8000' → Purchase voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("bought goods 8000")
        assertTrue(result is AiResponse.DraftVoucher)
        assertEquals("Purchase", (result as AiResponse.DraftVoucher).type)
        assertEquals(8000.0, result.amount ?: 0.0, 0.01)
    }

    @Test fun `voice 'purchased from Suresh 20000' → Purchase with party`() {
        val result = FinanceHelper.parseVoiceCommandLocal("purchased from Suresh 20000")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Purchase", voucher.type)
    }

    @Test fun `voice 'kharida maal 25000' → Purchase voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("kharida maal 25000")
        assertTrue(result is AiResponse.DraftVoucher)
        assertEquals("Purchase", (result as AiResponse.DraftVoucher).type)
    }

    // ── Payments ──
    @Test fun `voice 'paid rent 5000' → Payment voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("paid rent 5000")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertTrue("Expense type should be Rent", voucher.entries.any { it.ledgerName == "Rent Expense" })
    }

    @Test fun `voice 'paid salary 15000' → Payment with Salary Expense`() {
        val result = FinanceHelper.parseVoiceCommandLocal("paid salary 15000")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertTrue(voucher.entries.any { it.ledgerName == "Salary Expense" })
    }

    @Test fun `voice 'paid chai 200' → Payment with Staff Welfare`() {
        val result = FinanceHelper.parseVoiceCommandLocal("paid chai 200")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertTrue(voucher.entries.any { it.ledgerName == "Staff Welfare" })
    }

    @Test fun `voice 'paid petrol 500' → Payment with Conveyance`() {
        val result = FinanceHelper.parseVoiceCommandLocal("paid petrol 500")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertTrue(voucher.entries.any { it.ledgerName == "Conveyance Expense" })
    }

    @Test fun `voice 'paid bijli 2000' → Payment with Electricity`() {
        val result = FinanceHelper.parseVoiceCommandLocal("paid bijli 2000")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertTrue(voucher.entries.any { it.ledgerName == "Electricity Expense" })
    }

    // ── Receipts ──
    @Test fun `voice 'received from Amit 30000' → Receipt voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("received from Amit 30000")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Receipt", voucher.type)
        assertEquals(30000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `voice 'got 50000 from Suresh' → Receipt voucher`() {
        val result = FinanceHelper.parseVoiceCommandLocal("got 50000 from Suresh")
        assertTrue(result is AiResponse.DraftVoucher)
        assertEquals("Receipt", (result as AiResponse.DraftVoucher).type)
    }

    // ── Ledger creation ──
    @Test fun `voice 'create party Rahul' → DraftLedger`() {
        val result = FinanceHelper.parseVoiceCommandLocal("create party Rahul")
        assertTrue("Should be DraftLedger", result is AiResponse.DraftLedger)
        assertTrue((result as AiResponse.DraftLedger).name.contains("Rahul", ignoreCase = true))
    }

    @Test fun `voice 'add supplier Mohan' → DraftLedger as Creditor`() {
        val result = FinanceHelper.parseVoiceCommandLocal("add supplier Mohan")
        assertTrue(result is AiResponse.DraftLedger)
        assertEquals("Sundry Creditors", (result as AiResponse.DraftLedger).group)
    }

    @Test fun `voice 'create customer Ravi' → DraftLedger as Debtor`() {
        val result = FinanceHelper.parseVoiceCommandLocal("create customer Ravi")
        assertTrue(result is AiResponse.DraftLedger)
        assertEquals("Sundry Debtors", (result as AiResponse.DraftLedger).group)
    }

    // ── Reports / queries ──
    @Test fun `voice 'aaj ki sale' → QuerySales`() {
        val result = FinanceHelper.parseReportCommandLocal("aaj ki sale")
        assertTrue(result is AiResponse.QuerySales)
        assertEquals("today", (result as AiResponse.QuerySales).period)
    }

    @Test fun `voice 'how much is balance' → QueryBalance`() {
        val result = FinanceHelper.parseReportCommandLocal("how much is balance")
        assertTrue(result is AiResponse.QueryBalance)
    }

    // ── Questions NOT parsed as transactions ──
    @Test fun `voice 'is paying cash above 10000 illegal' → null (question, not voucher)`() {
        val result = FinanceHelper.parseVoiceCommandLocal("is paying cash above 10000 illegal?")
        assertNull("Questions should NOT be parsed as transactions", result)
    }

    @Test fun `voice 'what is gst rate' → null (question)`() {
        val result = FinanceHelper.parseVoiceCommandLocal("what is gst rate on cement?")
        assertNull(result)
    }

    // ── Payment mode detection ──
    @Test fun `voice 'paid to Ramesh 5000 via bank' → Bank Account`() {
        val result = FinanceHelper.parseVoiceCommandLocal("paid to Ramesh 5000 via bank")
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertTrue("Should use Bank Account", voucher.entries.any { it.ledgerName == "Bank Account" })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 3: smartRegexParser — English phrases  ═════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `smartRegex 'sold 10 kg rice for 600' → Sales`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "sold 10 kg rice for 600")
        assertNotNull("Should parse a sale", result)
        assertEquals("Sales", result!!.type)
        assertEquals(600.0, result.amount ?: 0.0, 0.01)
        assertTrue(result.inventoryItems.isNotEmpty())
        assertEquals("Rice", result.inventoryItems[0].name)
    }

    @Test fun `smartRegex 'bought 5 cement bags for 2500' → Purchase`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "bought 5 cement bags for 2500")
        assertNotNull(result)
        assertEquals("Purchase", result!!.type)
    }

    @Test fun `smartRegex 'sold to Ramesh 50 fans 100000' → Sales with party`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "sold to Ramesh 50 fans 100000")
        assertNotNull(result)
        assertEquals("Sales", result!!.type)
        assertTrue(result.partyName?.contains("Ramesh") == true)
    }

    @Test fun `smartRegex 'paid 10000 to Suresh' → Payment`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "paid 10000 to Suresh")
        assertNotNull(result)
        assertEquals("Payment", result!!.type)
        assertEquals(10000.0, result.amount ?: 0.0, 0.01)
    }

    @Test fun `smartRegex 'received 5000 from Amit' → Receipt`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "received 5000 from Amit")
        assertNotNull(result)
        assertEquals("Receipt", result!!.type)
        assertEquals(5000.0, result.amount ?: 0.0, 0.01)
    }

    @Test fun `smartRegex 'sold 10 rice aur 5 sugar for 800' → multi-item Sales`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "sold 10 rice aur 5 sugar for 800")
        assertNotNull(result)
        assertEquals("Sales", result!!.type)
        assertTrue("Should have multiple items", result.inventoryItems.size >= 2)
    }

    @Test fun `smartRegex 'splendor bechi cash mein 2 kroad ki' → single item, no cash leak`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "50 splendor bechi cash mein 2 kroad ki")
        assertNotNull("Should parse a sale", result)
        assertEquals("Sales", result!!.type)
        assertEquals("Cash-in-hand", result.partyName)
        assertEquals(1, result.inventoryItems.size)
        assertFalse(
            "Item name must not absorb the payment-mode words 'cash mein'",
            result.inventoryItems[0].name.contains("cash", ignoreCase = true)
        )
        assertEquals(50.0, result.inventoryItems[0].quantity, 0.01)
        assertEquals(20000000.0, result.amount ?: 0.0, 0.01)
    }

    @Test fun `smartRegex ignores questions`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "how to file GST return?")
        assertNull("Questions should return null", result)
    }

    @Test fun `smartRegex ignores loan terms`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "financed bike from HDFC for 80000")
        assertNull("Loan/finance should return null", result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 4: tryParseHinglish — Hinglish phrases  ═══════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `hinglish 'Ramesh ko 10 kg rice beche 600 me' → Sales to Ramesh`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Ramesh ko 10 kg rice beche 600 me")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Sales", voucher.type)
        assertTrue(voucher.partyName?.contains("Ramesh") == true)
    }

    @Test fun `hinglish 'Suresh se 5 bag cement kharida 3500 me' → Purchase from Suresh`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Suresh se 5 bag cement kharida 3500 me")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Purchase", voucher.type)
        assertTrue(voucher.partyName?.contains("Suresh") == true)
    }

    @Test fun `hinglish 'Amit ko 5000 diya' → Payment to Amit`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Amit ko 5000 diya")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertEquals(5000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `hinglish 'Ritu se 10000 liya' → Receipt from Ritu`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Ritu se 10000 liya")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Receipt", voucher.type)
        assertEquals(10000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `hinglish 'Mohan se 50 splendor kharidi 49000 me' → Purchase`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Mohan se 50 splendor kharidi 49000 me")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        assertEquals("Purchase", (result as AiResponse.DraftVoucher).type)
    }

    @Test fun `hinglish 'beche 20 fan 1000 ke' → Sales`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "beche 20 fan 1000 ke")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        assertEquals("Sales", (result as AiResponse.DraftVoucher).type)
    }

    @Test fun `hinglish with Hindi numbers 'Ramesh ko panch hazar diya' → Payment 5000`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Ramesh ko panch hazar diya")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertEquals(5000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `hinglish 'Amit se das hazar liya' → Receipt 10000`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "Amit se das hazar liya")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Receipt", voucher.type)
        assertEquals(10000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `hinglish question 'kya hai GST rate' → null (not transaction)`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "kya hai GST rate cement par?")
        assertNull(result)
    }

    // ── Multi-party transfer ──
    @Test fun `hinglish transfer 'Kaale ko 10000 diye Ritu se lekar' → Journal`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "Kaale ko 10000 diye Ritu se lekar")
        assertNotNull(result)
        assertEquals("Journal", result!!.type)
        assertEquals(10000.0, result.amount ?: 0.0, 0.01)
    }

    // ── Multi-item transactions ──
    @Test fun `multi-item 'sold 10 rice aur 5 sugar 4000 ke' → 2 items`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "sold 10 rice aur 5 sugar 4000 ke")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertTrue("Should have ≥ 2 items", voucher.inventoryItems.size >= 2)
    }

    @Test fun `multi-item 'beche 10 rice 600 aur 5 sugar 200' → items with individual prices`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "beche 10 rice 600 aur 5 sugar 200")
        assertNotNull(result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertTrue(voucher.inventoryItems.size >= 2)
        assertEquals(600.0 + 200.0, voucher.amount ?: 0.0, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 5: parseDateFromQuery  ═════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `date 'today' → today`() = assertEquals("today", FinanceHelper.parseDateFromQuery("sold rice today 500"))
    @Test fun `date 'aaj' → today`() = assertEquals("today", FinanceHelper.parseDateFromQuery("aaj maal becha 500"))
    @Test fun `date 'yesterday' → yesterday`() = assertEquals("yesterday", FinanceHelper.parseDateFromQuery("sold rice yesterday 500"))
    @Test fun `date 'kal' → yesterday`() = assertEquals("yesterday", FinanceHelper.parseDateFromQuery("kal 500 diya"))
    @Test fun `date 'dd-mm-yyyy' → parsed`() {
        val result = FinanceHelper.parseDateFromQuery("sold rice on 15/10/2026 for 500")
        assertNotNull(result)
        assertTrue("Should contain 2026-10-15", result!!.contains("2026-10-15"))
    }
    @Test fun `date '5th this month' → parsed`() {
        val result = FinanceHelper.parseDateFromQuery("sold rice on 5th this month for 500")
        assertNotNull(result)
    }
    @Test fun `date 'is mahine ki 10' → parsed`() {
        val result = FinanceHelper.parseDateFromQuery("is mahine ki 10 tarikh ko 500 diya")
        assertNotNull(result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 6: canonicalUnit  ══════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `canonicalUnit 'kg' → Kg`() = assertEquals("Kg", FinanceHelper.canonicalUnit("kg"))
    @Test fun `canonicalUnit 'kilo' → Kg`() = assertEquals("Kg", FinanceHelper.canonicalUnit("kilo"))
    @Test fun `canonicalUnit 'kilogram' → Kg`() = assertEquals("Kg", FinanceHelper.canonicalUnit("kilogram"))
    @Test fun `canonicalUnit 'killo' → Kg`() = assertEquals("Kg", FinanceHelper.canonicalUnit("killo"))
    @Test fun `canonicalUnit 'litre' → Liter`() = assertEquals("Liter", FinanceHelper.canonicalUnit("litre"))
    @Test fun `canonicalUnit 'liter' → Liter`() = assertEquals("Liter", FinanceHelper.canonicalUnit("liter"))
    @Test fun `canonicalUnit 'ltr' → Liter`() = assertEquals("Liter", FinanceHelper.canonicalUnit("ltr"))
    @Test fun `canonicalUnit 'gram' → Gram`() = assertEquals("Gram", FinanceHelper.canonicalUnit("gram"))
    @Test fun `canonicalUnit 'gm' → Gram`() = assertEquals("Gram", FinanceHelper.canonicalUnit("gm"))
    @Test fun `canonicalUnit 'pcs' → Pcs`() = assertEquals("Pcs", FinanceHelper.canonicalUnit("pcs"))
    @Test fun `canonicalUnit 'piece' → Pcs`() = assertEquals("Pcs", FinanceHelper.canonicalUnit("piece"))
    @Test fun `canonicalUnit 'nag' → Pcs`() = assertEquals("Pcs", FinanceHelper.canonicalUnit("nag"))
    @Test fun `canonicalUnit 'dozen' → Dozen`() = assertEquals("Dozen", FinanceHelper.canonicalUnit("dozen"))
    @Test fun `canonicalUnit 'darjan' → Dozen`() = assertEquals("Dozen", FinanceHelper.canonicalUnit("darjan"))
    @Test fun `canonicalUnit 'bottle' → Bottle`() = assertEquals("Bottle", FinanceHelper.canonicalUnit("bottle"))
    @Test fun `canonicalUnit 'btl' → Bottle`() = assertEquals("Bottle", FinanceHelper.canonicalUnit("btl"))
    @Test fun `canonicalUnit 'packet' → Pack`() = assertEquals("Pack", FinanceHelper.canonicalUnit("packet"))
    @Test fun `canonicalUnit 'pkt' → Pack`() = assertEquals("Pack", FinanceHelper.canonicalUnit("pkt"))
    @Test fun `canonicalUnit 'bag' → Bag`() = assertEquals("Bag", FinanceHelper.canonicalUnit("bag"))
    @Test fun `canonicalUnit 'bori' → Bag`() = assertEquals("Bag", FinanceHelper.canonicalUnit("bori"))
    @Test fun `canonicalUnit 'box' → Box`() = assertEquals("Box", FinanceHelper.canonicalUnit("box"))
    @Test fun `canonicalUnit 'peti' → Box`() = assertEquals("Box", FinanceHelper.canonicalUnit("peti"))
    @Test fun `canonicalUnit 'pair' → Pair`() = assertEquals("Pair", FinanceHelper.canonicalUnit("pair"))
    @Test fun `canonicalUnit 'jodi' → Pair`() = assertEquals("Pair", FinanceHelper.canonicalUnit("jodi"))
    @Test fun `canonicalUnit 'meter' → Meter`() = assertEquals("Meter", FinanceHelper.canonicalUnit("meter"))
    @Test fun `canonicalUnit 'mtr' → Meter`() = assertEquals("Meter", FinanceHelper.canonicalUnit("mtr"))
    @Test fun `canonicalUnit 'foot' → Foot`() = assertEquals("Foot", FinanceHelper.canonicalUnit("foot"))
    @Test fun `canonicalUnit 'feet' → Foot`() = assertEquals("Foot", FinanceHelper.canonicalUnit("feet"))
    @Test fun `canonicalUnit null → null`() = assertNull(FinanceHelper.canonicalUnit(null))
    @Test fun `canonicalUnit blank → null`() = assertNull(FinanceHelper.canonicalUnit("  "))

    // ── Hindi unit words ──
    @Test fun `canonicalUnit 'किलो' → Kg`() = assertEquals("Kg", FinanceHelper.canonicalUnit("किलो"))
    @Test fun `canonicalUnit 'लीटर' → Liter`() = assertEquals("Liter", FinanceHelper.canonicalUnit("लीटर"))
    @Test fun `canonicalUnit 'ग्राम' → Gram`() = assertEquals("Gram", FinanceHelper.canonicalUnit("ग्राम"))
    @Test fun `canonicalUnit 'पीस' → Pcs`() = assertEquals("Pcs", FinanceHelper.canonicalUnit("पीस"))
    @Test fun `canonicalUnit 'दर्जन' → Dozen`() = assertEquals("Dozen", FinanceHelper.canonicalUnit("दर्जन"))
    @Test fun `canonicalUnit 'बोतल' → Bottle`() = assertEquals("Bottle", FinanceHelper.canonicalUnit("बोतल"))

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 7: Bulk end-to-end — Hinglish phrases → voucher type  ═
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Bulk test: each (phrase, expectedType) pair is sent through the full pipeline
     * (tryParseHinglish → smartRegexParser → parseVoiceCommandLocal) and verified.
     */
    @Test fun `bulk Hinglish phrases → correct voucher type`() = runBlocking {
        val expectations = listOf(
            // Sales (English)
            "sold 10 rice 600" to "Sales",
            "sold items to Ramesh 5000" to "Sales",
            "sold 20 kg sugar 1000" to "Sales",
            "sold books 2000" to "Sales",
            // Sales (Hinglish)
            "Ramesh ko 10 rice beche 600 me" to "Sales",
            "beche 5 fan 5000 ke" to "Sales",
            "50 chappal becha 10000 me" to "Sales",
            // Purchases (English)
            "bought 5 cement 2500" to "Purchase",
            "purchased from Mohan 10000" to "Purchase",
            "bought 10 bags rice from Suresh 8000" to "Purchase",
            // Purchases (Hinglish)
            "Suresh se 10 rice kharida 5000 me" to "Purchase",
            "kharida 20 bag cement 10000 me" to "Purchase",
            // Payments (English)
            "paid 5000 to Amit" to "Payment",
            "paid Ravi 10000" to "Payment",
            // Payments (Hinglish)
            "Amit ko 5000 diya" to "Payment",
            "Raju ko 10000 diye" to "Payment",
            // Receipts (English)
            "received 5000 from Amit" to "Receipt",
            "got 10000 from Suresh" to "Receipt",
            // Receipts (Hinglish)
            "Ritu se 10000 liya" to "Receipt",
            "Amit se 5000 mila" to "Receipt"
        )

        val failures = mutableListOf<String>()
        for ((phrase, expectedType) in expectations) {
            val result = (FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher)
                ?: FinanceHelper.smartRegexParser(null, phrase)
                ?: (FinanceHelper.parseVoiceCommandLocal(phrase) as? AiResponse.DraftVoucher)

            if (result == null) {
                failures.add("MISS: '$phrase' → null (expected $expectedType)")
            } else if (result.type != expectedType) {
                failures.add("WRONG TYPE: '$phrase' → ${result.type} (expected $expectedType)")
            }
        }
        assertTrue("Voucher type mismatches:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    /**
     * Verify that knowledge/legal/tax questions are NEVER parsed as transactions.
     */
    @Test fun `bulk questions never become vouchers`() = runBlocking {
        val questions = listOf(
            "is paying cash above 10000 illegal?",
            "what is the GST rate on cement?",
            "how to file GST return?",
            "kya hai GST rate soap par?",
            "is it against the law to accept cash above 2 lakh?",
            "explain the difference between CGST and SGST",
            "how do I claim input tax credit?",
            "what does section 194C mean?",
            "batao GST kitna lagta hai gold par?",
            "can I reverse input credit?",
            "should I register for GST?",
            "is there a penalty for late GST filing?"
        )
        for (q in questions) {
            val r1 = FinanceHelper.tryParseHinglish(null, q)
            val r2 = FinanceHelper.smartRegexParser(null, q)
            assertNull("Question should NOT be parsed by tryParseHinglish: '$q'", r1)
            assertNull("Question should NOT be parsed by smartRegexParser: '$q'", r2)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 8: Amount correctness  ════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `amount 'sold rice 5k' → 5000`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "sold rice 5k")
        assertNotNull(result)
        assertEquals(5000.0, result!!.amount ?: 0.0, 0.01)
    }

    @Test fun `amount 'sold rice 2_5 lakh' → 250000`() = runBlocking {
        val result = FinanceHelper.smartRegexParser(null, "sold rice 2.5 lakh")
        assertNotNull(result)
        assertEquals(250000.0, result!!.amount ?: 0.0, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 9: Devanagari (full Hindi) phrases  ═══════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `Devanagari 'रमेश को पाँच हज़ार दिया' → Payment 5000`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "रमेश को पाँच हज़ार दिया")
        assertNotNull("Should parse Devanagari input", result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Payment", voucher.type)
        assertEquals(5000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `Devanagari 'अमित से दस हज़ार लिया' → Receipt 10000`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "अमित से दस हज़ार लिया")
        assertNotNull("Should parse Devanagari input", result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Receipt", voucher.type)
        assertEquals(10000.0, voucher.amount ?: 0.0, 0.01)
    }

    @Test fun `Devanagari 'बीस किलो चावल बेचे तीन हज़ार में' → Sales 3000`() = runBlocking {
        val result = FinanceHelper.tryParseHinglish(null, "बीस किलो चावल बेचे तीन हज़ार में")
        assertNotNull("Should parse Devanagari sale", result)
        assertTrue(result is AiResponse.DraftVoucher)
        val voucher = result as AiResponse.DraftVoucher
        assertEquals("Sales", voucher.type)
        assertEquals(3000.0, voucher.amount ?: 0.0, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  Helpers  ═══════════════════════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    private fun assertNormalized(input: String, expectedSubstring: String) {
        val result = FinanceHelper.normalizeHindiNumbers(input)
        assertTrue(
            "normalizeHindiNumbers('$input') = '$result' should contain '$expectedSubstring'",
            result.contains(expectedSubstring)
        )
    }
}
