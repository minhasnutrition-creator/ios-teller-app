package com.minhas.teller.utils

import com.minhas.teller.utils.FinanceHelper
import com.minhas.teller.utils.VoucherExcelImporter
import com.minhas.teller.utils.LanguageTranslationUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Ultra-comprehensive verification test for FinanceHelper:
 * 1. HSN code and GST tax rate resolution for new items across domains
 * 2. Correct voucher creation (Sales, Purchase, Payment, Receipt, Expense)
 * 3. Speech patterns across English, Hinglish, and Hindi (Devanagari)
 * 4. Multi-item cart parsing with item-level HSN & tax assignment
 * 5. Payment mode deduction (Cash vs Bank / UPI / Cheque)
 */
class FinanceHelperComprehensiveSpeechTest {

    private fun parseVoice(query: String): AiResponse.DraftVoucher? = runBlocking {
        (FinanceHelper.tryParseHinglish(null, query)
            ?: FinanceHelper.smartRegexParser(null, query)
            ?: FinanceHelper.parseVoiceCommandLocal(query)) as? AiResponse.DraftVoucher
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. HSN Code and Tax Rate Resolution for New Items Across Industries
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testHsnAndTax_ConstructionAndHardware() {
        // Cement: HSN 2523, 18%
        val cement = VoucherExcelImporter.resolveHsnAndTax("Cement", "Construction", null, null)
        assertNotNull("Cement must resolve HSN", cement)
        assertTrue(cement?.first?.startsWith("2523") == true)
        assertEquals(18.0, cement?.second ?: 0.0, 0.01)

        // Steel / Iron rods / TMT: HSN 7214 / 72, 18%
        val tmt = VoucherExcelImporter.resolveHsnAndTax("Tata Tiscon TMT Bar", "Steel", null, null)
        assertNotNull("TMT must resolve HSN", tmt)
        assertTrue(tmt?.first?.startsWith("72") == true)
        assertEquals(18.0, tmt?.second ?: 0.0, 0.01)

        // Paint: HSN 3208 / 3209 / 32, 18%
        val paint = VoucherExcelImporter.resolveHsnAndTax("Asian Paints Apex", "Paint", null, null)
        assertNotNull("Paint must resolve HSN", paint)
        assertTrue(paint?.first?.startsWith("32") == true)
        assertEquals(18.0, paint?.second ?: 0.0, 0.01)

        // Tiles: HSN 6907, 18%
        val tiles = VoucherExcelImporter.resolveHsnAndTax("Ceramic Floor Tiles", "Tiles", null, null)
        assertNotNull("Tiles must resolve HSN", tiles)
        assertTrue(tiles?.first?.startsWith("6907") == true)
        assertEquals(18.0, tiles?.second ?: 0.0, 0.01)
    }

    @Test
    fun testHsnAndTax_GroceriesAndEssentials() {
        // Rice: HSN 1006, 5%
        val rice = VoucherExcelImporter.resolveHsnAndTax("Basmati Rice", "Rice", null, null)
        assertNotNull("Rice must resolve HSN", rice)
        assertTrue(rice?.first?.startsWith("1006") == true)
        assertEquals(5.0, rice?.second ?: 0.0, 0.01)

        // Sugar: HSN 1701, 5%
        val sugar = VoucherExcelImporter.resolveHsnAndTax("Refined Sugar", "Sugar", null, null)
        assertNotNull("Sugar must resolve HSN", sugar)
        assertTrue(sugar?.first?.startsWith("1701") == true)
        assertEquals(5.0, sugar?.second ?: 0.0, 0.01)

        // Fresh Milk: HSN 0401, 0% (exempt)
        val milk = VoucherExcelImporter.resolveHsnAndTax("Pasteurised Toned Milk", "Dairy", null, null)
        assertNotNull("Milk must resolve HSN", milk)
        assertTrue(milk?.first?.startsWith("0401") == true)
        assertEquals(0.0, milk?.second ?: 0.0, 0.01)

        // Mustard Oil / Cooking Oil: HSN 1514 / 1512, 5%
        val mustardOil = VoucherExcelImporter.resolveHsnAndTax("Mustard Oil", "Cooking Oil", null, null)
        assertNotNull("Mustard Oil must resolve HSN", mustardOil)
        assertTrue(mustardOil?.first?.startsWith("15") == true)
        assertEquals(5.0, mustardOil?.second ?: 0.0, 0.01)

        // Honey: HSN 0409, 5%
        val honey = VoucherExcelImporter.resolveHsnAndTax("Pure Honey", "Honey", null, null)
        assertNotNull("Honey must resolve HSN", honey)
        assertTrue(honey?.first?.startsWith("0409") == true)
        assertEquals(5.0, honey?.second ?: 0.0, 0.01)
    }

    @Test
    fun testHsnAndTax_ElectronicsAndAutomotive() {
        // Mobile Phones: HSN 8517, 18%
        val phone = VoucherExcelImporter.resolveHsnAndTax("Samsung Galaxy S24", "Mobile Phone", null, null)
        assertNotNull("Mobile phone must resolve HSN", phone)
        assertTrue(phone?.first?.startsWith("8517") == true)
        assertEquals(18.0, phone?.second ?: 0.0, 0.01)

        // Laptop / Computer: HSN 8471, 18%
        val laptop = VoucherExcelImporter.resolveHsnAndTax("Dell Inspiron Laptop", "Laptop", null, null)
        assertNotNull("Laptop must resolve HSN", laptop)
        assertTrue(laptop?.first?.startsWith("8471") == true)
        assertEquals(18.0, laptop?.second ?: 0.0, 0.01)

        // Two-Wheeler / Motorcycle: HSN 8711, 18%
        val bike = VoucherExcelImporter.resolveHsnAndTax("Hero Splendor Plus", "Motorcycle", null, null)
        assertNotNull("Motorcycle must resolve HSN", bike)
        assertTrue(bike?.first?.startsWith("8711") == true)
        assertEquals(18.0, bike?.second ?: 0.0, 0.01)

        // Motor car / Passenger Vehicle: HSN 8703, 18%
        val car = VoucherExcelImporter.resolveHsnAndTax("Maruti Dzire", "Car", null, null)
        assertNotNull("Car must resolve HSN", car)
        assertTrue(car?.first?.startsWith("8703") == true)
        assertEquals(18.0, car?.second ?: 0.0, 0.01)
    }

    @Test
    fun testHsnAndTax_HealthcareAndPharma() {
        // Medicines / Pharma: HSN 3004
        val pharma = VoucherExcelImporter.resolveHsnAndTax("Paracetamol 650 Tablet", "Medicine", null, null)
        assertNotNull("Medicine must resolve HSN", pharma)
        assertTrue(pharma?.first?.startsWith("3004") == true)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Sales Voucher Parsing: English, Hinglish, Hindi (Devanagari)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testSales_EnglishSpeech() {
        // Pattern 1: Sold with party, item, and qty
        val v1 = parseVoice("sold 10 bags cement to Ramesh for 3500")
        assertNotNull(v1)
        assertEquals("Sales", v1?.type)
        assertEquals(3500.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Ramesh", v1?.partyName)
        assertEquals("Cement", v1?.inventoryItems?.firstOrNull()?.name)
        assertEquals(10.0, v1?.inventoryItems?.firstOrNull()?.quantity ?: 0.0, 0.01)
        // Verify HSN auto-assignment on item
        assertTrue("Cement HSN should start with 2523", v1?.inventoryItems?.firstOrNull()?.hsnCode?.startsWith("2523") == true)

        // Pattern 2: Sold cash (no party)
        val v2 = parseVoice("sold 5 bananas for 200")
        assertNotNull(v2)
        assertEquals("Sales", v2?.type)
        assertEquals(200.0, v2?.amount ?: 0.0, 0.01)
        assertEquals("Cash-in-hand", v2?.partyName)

        // Pattern 3: Sold per-unit rate
        val v3 = parseVoice("sold 12 mangoes 20 each")
        assertNotNull(v3)
        assertEquals("Sales", v3?.type)
        assertEquals(240.0, v3?.amount ?: 0.0, 0.01)
    }

    @Test
    fun testSales_HinglishSpeech() {
        // Hinglish: Party ko [Qty] [Item] [Amt] me becha
        val v1 = parseVoice("Ramesh ko 10 bori cement 3500 me becha")
        assertNotNull(v1)
        assertEquals("Sales", v1?.type)
        assertEquals(3500.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Ramesh", v1?.partyName)
        assertEquals("Cement", v1?.inventoryItems?.firstOrNull()?.name)
        assertEquals(10.0, v1?.inventoryItems?.firstOrNull()?.quantity ?: 0.0, 0.01)
        assertTrue(v1?.inventoryItems?.firstOrNull()?.hsnCode?.startsWith("2523") == true)

        // Hinglish: [Qty] [Item] beche [Party] ko [Amt] ke
        val v2 = parseVoice("50 kela beche Amit ko 5000 ki")
        assertNotNull(v2)
        assertEquals("Sales", v2?.type)
        assertEquals(5000.0, v2?.amount ?: 0.0, 0.01)
        assertEquals("Amit", v2?.partyName)
        assertEquals("Banana", v2?.inventoryItems?.firstOrNull()?.name)

        // Hinglish: Generic goods sale (maal becha)
        val v3 = parseVoice("5000 ka maal becha cash me")
        assertNotNull(v3)
        assertEquals("Sales", v3?.type)
        assertEquals(5000.0, v3?.amount ?: 0.0, 0.01)

        // Hinglish: Hindi compound words / Lakh
        val v4 = parseVoice("2 lakh ka maal becha Suresh ko")
        assertNotNull(v4)
        assertEquals("Sales", v4?.type)
        assertEquals(200000.0, v4?.amount ?: 0.0, 0.01)
        assertEquals("Suresh", v4?.partyName)
    }

    @Test
    fun testSales_HindiDevanagari() {
        // Devanagari sales
        val v1 = parseVoice("50 केला बेचा राम को 9000 की")
        assertNotNull(v1)
        assertEquals("Sales", v1?.type)
        assertEquals(9000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Banana", v1?.inventoryItems?.firstOrNull()?.name)

        // Devanagari cement sale
        val v2 = parseVoice("रमेश को 10 सीमेंट 3500 में बेचा")
        assertNotNull(v2)
        assertEquals("Sales", v2?.type)
        assertEquals(3500.0, v2?.amount ?: 0.0, 0.01)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Purchase Voucher Parsing: English, Hinglish, Hindi (Devanagari)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testPurchase_EnglishSpeech() {
        // English purchase with party, item, and qty
        val v1 = parseVoice("bought 10 kg rice from Ramesh for 5000")
        println("DEBUG testPurchase_EnglishSpeech v1: $v1, inv: ${v1?.inventoryItems}")
        assertNotNull(v1)
        assertEquals("Purchase", v1?.type)
        assertEquals(5000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Ramesh", v1?.partyName)
        assertEquals("Rice", v1?.inventoryItems?.firstOrNull()?.name)
        assertEquals(10.0, v1?.inventoryItems?.firstOrNull()?.quantity ?: 0.0, 0.01)
        assertTrue("HSN was ${v1?.inventoryItems?.firstOrNull()?.hsnCode}", v1?.inventoryItems?.firstOrNull()?.hsnCode?.startsWith("1006") == true)

        // English purchase cash
        val v2 = parseVoice("purchased 20 apples for 1000")
        assertNotNull(v2)
        assertEquals("Purchase", v2?.type)
        assertEquals(1000.0, v2?.amount ?: 0.0, 0.01)
        assertEquals("Cash-in-hand", v2?.partyName)
    }

    @Test
    fun testPurchase_HinglishSpeech() {
        // Hinglish: Party se [Qty] [Item] [Amt] me kharida
        val v1 = parseVoice("Gupta se 10 bori cement 3500 me kharida")
        println("DEBUG testPurchase_HinglishSpeech v1: $v1")
        assertNotNull(v1)
        assertEquals("Purchase", v1?.type)
        assertEquals(3500.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Gupta", v1?.partyName)
        assertEquals("Cement", v1?.inventoryItems?.firstOrNull()?.name)
        assertTrue(v1?.inventoryItems?.firstOrNull()?.hsnCode?.startsWith("2523") == true)

        // Hinglish: [Qty] [Item] kharida [Party] se [Amt] ki
        val v2 = parseVoice("50 seb kharida Ram se 10000 ki")
        assertNotNull(v2)
        assertEquals("Purchase", v2?.type)
        assertEquals(10000.0, v2?.amount ?: 0.0, 0.01)
        assertEquals("Ram", v2?.partyName)
        assertEquals("Apple", v2?.inventoryItems?.firstOrNull()?.name)

        // Hinglish: Amount first with model number ("500000 ke 50 iphone 12 kharide amrit se")
        val v3 = parseVoice("500000 ke 50 iphone 12 kharide amrit se")
        assertNotNull(v3)
        assertEquals("Purchase", v3?.type)
        assertEquals(500000.0, v3?.amount ?: 0.0, 0.01)
        assertEquals("Amrit", v3?.partyName)
        assertEquals("Iphone 12", v3?.inventoryItems?.firstOrNull()?.name)
        assertEquals(50.0, v3?.inventoryItems?.firstOrNull()?.quantity ?: 0.0, 0.01)
    }

    @Test
    fun testPurchase_HindiDevanagari() {
        val v1 = parseVoice("50 सेब खरीदा राम से 10000 की")
        assertNotNull(v1)
        assertEquals("Purchase", v1?.type)
        assertEquals(10000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Apple", v1?.inventoryItems?.firstOrNull()?.name)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Payment Voucher Parsing: English, Hinglish, Hindi (Devanagari)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testPayment_EnglishSpeech() {
        val v1 = parseVoice("paid 5000 to Suresh")
        assertNotNull(v1)
        assertEquals("Payment", v1?.type)
        assertEquals(5000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Suresh", v1?.partyName)

        val v2 = parseVoice("paid 200 for tea")
        assertNotNull(v2)
        assertEquals("Payment", v2?.type)
        assertEquals(200.0, v2?.amount ?: 0.0, 0.01)
        assertTrue(v2?.entries?.any { it.ledgerName == "Staff Welfare" } == true)
    }

    @Test
    fun testPayment_HinglishSpeech() {
        // Direct party payment
        val v1 = parseVoice("Ramesh ko 5000 diye")
        assertNotNull(v1)
        assertEquals("Payment", v1?.type)
        assertEquals(5000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Ramesh", v1?.partyName)

        // Expense payment (rent)
        val v2 = parseVoice("rent diya 15000 cheque se")
        assertNotNull(v2)
        assertEquals("Payment", v2?.type)
        assertEquals(15000.0, v2?.amount ?: 0.0, 0.01)
        assertTrue(v2?.entries?.any { it.ledgerName == "Rent Expense" } == true)
        assertTrue(v2?.entries?.any { it.ledgerName == "Bank Account" } == true)

        // Expense payment (electricity)
        val v3 = parseVoice("bijli ka bill bhara 2500")
        assertNotNull(v3)
        assertEquals("Payment", v3?.type)
        assertEquals(2500.0, v3?.amount ?: 0.0, 0.01)
        assertTrue(v3?.entries?.any { it.ledgerName == "Electricity Expense" } == true)

        // Salary payment
        val v4 = parseVoice("salary diya 12000")
        assertNotNull(v4)
        assertEquals("Payment", v4?.type)
        assertEquals(12000.0, v4?.amount ?: 0.0, 0.01)
        assertTrue(v4?.entries?.any { it.ledgerName == "Salary Expense" } == true)
    }

    @Test
    fun testPayment_HindiDevanagari() {
        val v1 = parseVoice("राम को 5000 दिए")
        assertNotNull(v1)
        assertEquals("Payment", v1?.type)
        assertEquals(5000.0, v1?.amount ?: 0.0, 0.01)

        val v2 = parseVoice("किराया दिया 800")
        assertNotNull(v2)
        assertEquals("Payment", v2?.type)
        assertEquals(800.0, v2?.amount ?: 0.0, 0.01)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Receipt Voucher Parsing: English, Hinglish, Hindi (Devanagari)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testReceipt_EnglishSpeech() {
        val v1 = parseVoice("received 2000 from Rahul")
        assertNotNull(v1)
        assertEquals("Receipt", v1?.type)
        assertEquals(2000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Rahul", v1?.partyName)

        val v2 = parseVoice("got 500 from Amit")
        assertNotNull(v2)
        assertEquals("Receipt", v2?.type)
        assertEquals(500.0, v2?.amount ?: 0.0, 0.01)
        assertEquals("Amit", v2?.partyName)
    }

    @Test
    fun testReceipt_HinglishSpeech() {
        val v1 = parseVoice("Gagan se 2000 liya")
        assertNotNull(v1)
        assertEquals("Receipt", v1?.type)
        assertEquals(2000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Gagan", v1?.partyName)

        val v2 = parseVoice("2000 liye Gagan se")
        assertNotNull(v2)
        assertEquals("Receipt", v2?.type)
        assertEquals(2000.0, v2?.amount ?: 0.0, 0.01)
        assertEquals("Gagan", v2?.partyName)

        val v3 = parseVoice("Gagan se 2000 mila")
        assertNotNull(v3)
        assertEquals("Receipt", v3?.type)
        assertEquals(2000.0, v3?.amount ?: 0.0, 0.01)
        assertEquals("Gagan", v3?.partyName)

        val v4 = parseVoice("Sunita ne 500 wapas kiye")
        assertNotNull(v4)
        assertEquals("Receipt", v4?.type)
        assertEquals(500.0, v4?.amount ?: 0.0, 0.01)
        assertEquals("Sunita", v4?.partyName)
    }

    @Test
    fun testReceipt_HindiDevanagari() {
        val v1 = parseVoice("गगन से 2000 लिया")
        assertNotNull(v1)
        assertEquals("Receipt", v1?.type)
        assertEquals(2000.0, v1?.amount ?: 0.0, 0.01)
        assertEquals("Gagan", v1?.partyName)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Multi-Item Billing with Automatic HSN and Tax Assignment
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testMultiItem_WithHsnAndTaxAssignment() {
        val draft = parseVoice("sell 10 tape aur 8 bulb")
        assertNotNull(draft)
        assertEquals("Sales", draft?.type)
        assertEquals(2, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.get(0)
        assertEquals(10.0, item1?.quantity ?: 0.0, 0.001)

        val item2 = draft?.inventoryItems?.get(1)
        assertEquals(8.0, item2?.quantity ?: 0.0, 0.001)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. Spoken Hindi Number Words
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testHindiNumberWords() {
        val v1 = parseVoice("pachis hazaar ka maal becha Suresh ko")
        assertNotNull(v1)
        assertEquals("Sales", v1?.type)
        assertEquals(25000.0, v1?.amount ?: 0.0, 0.01)

        val v2 = parseVoice("ek lakh ka maal kharida Verma se")
        assertNotNull(v2)
        assertEquals("Purchase", v2?.type)
        assertEquals(100000.0, v2?.amount ?: 0.0, 0.01)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. MuMu Player Live Captured Voice Queries
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testMuMuCapturedQuery1_SoldBooksTablesErasersWithTotalAmount() {
        val draft = parseVoice("sold 50 books 90 tables and 40 erasers for 800000 to ankit")
        assertNotNull("Draft voucher must be parsed", draft)
        assertEquals("Sales", draft?.type)
        assertEquals("Ankit", draft?.partyName)
        assertEquals(800000.0, draft?.amount ?: 0.0, 0.01)
        assertEquals(3, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.find { it.name.equals("Book", true) }
        assertNotNull("Book must be present", item1)
        assertEquals(50.0, item1?.quantity ?: 0.0, 0.01)
        assertTrue("Book HSN should start with 4901", item1?.hsnCode?.startsWith("4901") == true)
        assertEquals("Book category should be Stationery", "Stationery", item1?.category)

        val item2 = draft?.inventoryItems?.find { it.name.equals("Table", true) }
        assertNotNull("Table must be present", item2)
        assertEquals(90.0, item2?.quantity ?: 0.0, 0.01)
        assertTrue("Table HSN should start with 9403", item2?.hsnCode?.startsWith("9403") == true)
        assertEquals(18.0, item2?.gstRate ?: 0.0, 0.01)
        assertEquals("Table category should be Furniture", "Furniture", item2?.category)

        val item3 = draft?.inventoryItems?.find { it.name.equals("Eraser", true) }
        assertNotNull("Eraser must be present", item3)
        assertEquals(40.0, item3?.quantity ?: 0.0, 0.01)
        assertTrue("Eraser HSN should start with 4016", item3?.hsnCode?.startsWith("4016") == true)

        val totalItemAmt = draft?.inventoryItems?.sumOf { it.amount } ?: 0.0
        assertEquals(800000.0, totalItemAmt, 0.5)
    }

    @Test
    fun testMuMuCapturedQuery2_AmitBooksAndRegisterWithKeSuffix() {
        val draft = parseVoice("amit ko 40 books aur 40 register beche 4000 ke")
        assertNotNull("Draft voucher must be parsed", draft)
        assertEquals("Sales", draft?.type)
        assertEquals("Amit", draft?.partyName)
        assertEquals(4000.0, draft?.amount ?: 0.0, 0.01)
        assertEquals(2, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.find { it.name.equals("Book", true) }
        assertNotNull("Book must be present", item1)
        assertEquals(40.0, item1?.quantity ?: 0.0, 0.01)
        assertTrue("Book HSN should start with 4901", item1?.hsnCode?.startsWith("4901") == true)
        assertEquals(2000.0, item1?.amount ?: 0.0, 0.01)

        val item2 = draft?.inventoryItems?.find { it.name.equals("Register", true) }
        assertNotNull("Register must be present", item2)
        assertEquals(40.0, item2?.quantity ?: 0.0, 0.01)
        assertTrue("Register HSN should start with 4820", item2?.hsnCode?.startsWith("4820") == true)
        assertEquals(2000.0, item2?.amount ?: 0.0, 0.01)

        val totalItemAmt = draft?.inventoryItems?.sumOf { it.amount } ?: 0.0
        assertEquals(4000.0, totalItemAmt, 0.01)
    }

    @Test
    fun testMuMuCapturedQuery_HindiDevanagari() {
        val draft = parseVoice("अंकित को 50 किताबें 90 टेबल और 40 रबर 800000 में बेचे")
        assertNotNull("Devanagari multi-item voucher must parse", draft)
        assertEquals("Sales", draft?.type)
        assertEquals("Ankit", draft?.partyName)
        assertEquals(800000.0, draft?.amount ?: 0.0, 0.01)
        assertEquals(3, draft?.inventoryItems?.size)

        assertTrue(draft?.inventoryItems?.any { it.name.equals("Book", true) } == true)
        assertTrue(draft?.inventoryItems?.any { it.name.equals("Table", true) } == true)
        assertTrue(draft?.inventoryItems?.any { it.name.equals("Eraser", true) } == true)
    }

    @Test
    fun testMultiItem_WithoutAnyConjunction() {
        val draft = parseVoice("sold 50 books 90 tables 40 erasers for 800000 to ankit")
        assertNotNull("Draft voucher without conjunction must parse", draft)
        assertEquals("Sales", draft?.type)
        assertEquals("Ankit", draft?.partyName)
        assertEquals(800000.0, draft?.amount ?: 0.0, 0.01)
        assertEquals(3, draft?.inventoryItems?.size)
    }
}

