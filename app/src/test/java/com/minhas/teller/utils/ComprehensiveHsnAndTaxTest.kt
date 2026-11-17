package com.minhas.teller.utils

import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.HSNSyncRepository
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.util.Random

/**
 * Massive stress and accuracy test for HSN code selection and tax assignment.
 * Tests resolution via:
 * 1. VoucherExcelImporter.resolveHsnAndTax (with and without Category)
 * 2. FinanceHelper.guessTaxRate
 * 3. HSN scoring algorithm across real-world items, brands, and categories
 * 4. Fuzz test throwing thousands of randomized realistic items across all industries
 * 5. Rejection test verifying unknown/gibberish items are safely flagged
 */
class ComprehensiveHsnAndTaxTest {

    // ──────────────────────────────────────────────────────────────────────────
    //  1. Brand Names with Category Resolution (User's specific case: Splendor +)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `test splendor plus with motorcycle category resolves to 8711 at 18 percent`() {
        // User scenario: "Splendor +" with Category "Motorcycle"
        val res = VoucherExcelImporter.resolveHsnAndTax(
            itemName = "Splendor +",
            category = "Motorcycle",
            explicitHsn = null,
            explicitGst = null
        )
        assertNotNull("Splendor + with Motorcycle category should be resolved", res)
        assertEquals("8711", res?.first)
        assertEquals(18.0, res?.second ?: 0.0, 0.001)
    }

    @Test
    fun `test banana resolution`() {
        val res = VoucherExcelImporter.resolveHsnAndTax(
            itemName = "Banana",
            category = "Fruits",
            explicitHsn = null,
            explicitGst = null
        )
        println("Banana resolution result: $res")
        assertNotNull(res)
        assertEquals("0803", res?.first)
        assertEquals(0.0, res?.second ?: -1.0, 0.001)
    }

    @Test
    fun `test obscure brand items resolve accurately when category is provided`() {
        val testCases = listOf(
            // ItemName, Category, Expected HSN prefix, Expected GST rate
            Triple("Hero HF Deluxe 100cc", "Motorcycle", "8711" to 18.0),
            Triple("Bajaj Pulsar 150 Dtsi", "Two Wheeler", "8711" to 18.0),
            Triple("Honda Activa 6G", "Scooter", "8711" to 18.0),
            Triple("Ather 450X Gen 3", "Electric Scooter", "871160" to 5.0),
            Triple("Ola S1 Pro", "EV Bike", "871160" to 5.0),
            Triple("Maruti Swift VXi", "Car", "8703" to 18.0),
            Triple("Hyundai Creta SX", "Cars & Vehicles", "8703" to 18.0),
            Triple("Vega Cliff DX", "Helmet", "6506" to 18.0),
            Triple("Steelbird SB-50", "Motorcycle Helmet", "6506" to 18.0),
            Triple("Motul 3100 4T Gold", "Engine Oil", "2710" to 18.0),
            Triple("Castrol Power1 10W-40", "Lubricant", "2710" to 18.0),
            Triple("MRF Nylogrip Zapper", "Tyre", "4011" to 18.0),
            Triple("CEAT Gripp F", "Motorcycle Tyre", "4011" to 18.0),
            Triple("UltraTech Super PPC", "Cement", "2523" to 18.0),
            Triple("Ambuja Kawach", "Cement", "2523" to 18.0),
            Triple("Tata Tiscon 550D 12mm", "Steel Rods", "72" to 18.0),
            Triple("Jindal Panther TMT", "Steel", "72" to 18.0),
            Triple("Asian Paints Apex Ultima", "Paint", "32" to 18.0),
            Triple("Berger WeatherCoat", "Paint", "32" to 18.0),
            Triple("Kajaria Glazed Vitrified", "Tiles", "6907" to 18.0),
            Triple("MuscleBlaze Biozyme Performance", "Whey Protein", "2106" to 18.0),
            Triple("Optimum Nutrition Gold Standard", "Protein Powder", "2106" to 18.0),
            Triple("MyProtein Impact Isolate", "Whey Protein", "2106" to 18.0),
            Triple("Dabur Honey Squeezy", "Honey", "0409" to 5.0),
            Triple("Patanjali Pure Honey", "Honey", "0409" to 5.0),
            Triple("Maggi 2-Minute Masala", "Noodles", "1902" to 5.0),
            Triple("Cadbury Dairy Milk Silk", "Chocolate", "1806" to 18.0),
            Triple("Nestle KitKat 4-Finger", "Chocolate", "1806" to 18.0),
            Triple("Parle-G Gold 1kg", "Biscuits", "1905" to 5.0),
            Triple("Britannia Good Day Butter", "Biscuits", "1905" to 5.0),
            Triple("Lays India's Magic Masala", "Chips", "2008" to 5.0),
            Triple("Kurkure Masala Munch", "Namkeen", "2106" to 5.0),
            Triple("Haldiram Bhujia Sev", "Namkeen", "2106" to 5.0),
            Triple("Amul Taaza Toned 1L", "Milk", "0401" to 0.0),
            Triple("Mother Dairy Full Cream", "Milk", "0401" to 0.0),
            Triple("Amul Butter 500g", "Butter", "0405" to 5.0),
            Triple("Patanjali Cow Ghee 1L", "Ghee", "0405" to 5.0),
            Triple("Fortune Sunlite 1L", "Cooking Oil", "1512" to 5.0),
            Triple("Dhara Mustard Oil 1L", "Mustard Oil", "1514" to 5.0),
            Triple("Tata Salt Lite 1kg", "Salt", "2501" to 0.0),
            Triple("India Gate Classic Basmati", "Rice", "1006" to 5.0),
            Triple("Aashirvaad Select Shudh Chakki", "Wheat Flour", "1101" to 5.0),
            Triple("Tata Tea Gold 500g", "Tea", "0902" to 5.0),
            Triple("Nescafe Classic 100g", "Coffee", "0901" to 5.0),
            Triple("Coca Cola 750ml Pet", "Aerated Soft Drink", "2202" to 40.0),
            Triple("Thums Up Charged", "Cold Drink", "2202" to 40.0),
            Triple("Apple iPhone 15 Pro Max 256GB", "Mobile Phone", "8517" to 18.0),
            Triple("Samsung Galaxy S24 Ultra", "Mobile", "8517" to 18.0),
            Triple("Apple MacBook Pro 16", "Laptop", "8471" to 18.0),
            Triple("Dell Inspiron 15 3520", "Computer", "8471" to 18.0),
            Triple("LG OLED 55 Inch 4K", "Television", "8528" to 18.0),
            Triple("Voltas 1.5 Ton 3 Star Inverter", "Air Conditioner", "8415" to 18.0),
            Triple("Whirlpool 240L Frost Free", "Refrigerator", "8418" to 18.0),
            Triple("Bosch 7kg Front Load", "Washing Machine", "8450" to 18.0),
            Triple("Dolo 650 Tablet", "Medicine", "3004" to 5.0),
            Triple("Crocin 650", "Pharmaceuticals", "3004" to 5.0),
            Triple("Augmentin 625 Duo", "Antibiotic", "3004" to 5.0),
            Triple("Dettol Antiseptic Liquid 500ml", "Antiseptic", "3401" to 18.0),
            Triple("Raymond Premium Wool Blend", "Suiting Fabric", "5111" to 18.0),
            Triple("Levi's 511 Slim Fit", "Denim Jeans", "6203" to 5.0),
            Triple("Kanchipuram Pure Silk", "Saree", "5407" to 5.0)
        )

        for ((name, category, expected) in testCases) {
            val res = VoucherExcelImporter.resolveHsnAndTax(
                itemName = name,
                category = category,
                explicitHsn = null,
                explicitGst = null
            )
            assertNotNull("Failed to resolve for Item='$name', Category='$category'", res)
            assertTrue(
                "HSN for '$name' was ${res?.first}, expected to start with ${expected.first}",
                res?.first?.startsWith(expected.first) == true
            )
            assertEquals(
                "GST rate for '$name' was ${res?.second}, expected ${expected.second}",
                expected.second,
                res?.second ?: 0.0,
                0.001
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  2. FinanceHelper.guessTaxRate Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `test FinanceHelper tax rate assignment across categories in India`() {
        val testCases = listOf(
            // Food / basic necessities -> 0%
            "fresh milk" to 0.0,
            "rice basmati" to 0.0,
            "wheat flour" to 0.0,
            "green vegetables" to 0.0,
            "fresh apple" to 0.0,
            "school textbook" to 0.0,

            // Reduced essential goods -> 5%
            "sugar 1kg" to 5.0,
            "darjeeling tea" to 5.0,
            "filter coffee" to 5.0,
            "pure cow ghee" to 5.0,
            "table butter" to 5.0,
            "cotton fabric" to 5.0,
            "mustard oil" to 5.0,
            "sunflower cooking oil" to 5.0,

            // Sin & Luxury goods -> 40% (GST 2.0 reform)
            "cigarettes classic" to 40.0,
            "tobacco pouch" to 40.0,
            "pan masala" to 40.0,
            "gutkha" to 40.0,
            "coca cola aerated" to 40.0,
            "carbonated soft drink" to 40.0,

            // Standard durables & electronics -> 18% (GST 2.0 reform)
            "smartphone mobile" to 18.0,
            "dell laptop" to 18.0,
            "computer monitor" to 18.0,
            "led tv television" to 18.0,
            "refrigerator fridge" to 18.0,
            "split air condition" to 18.0,
            "portland cement" to 18.0,
            "car motor vehicle" to 18.0,
            "motorcycle tyre" to 18.0,
            "software development service" to 18.0,
            "vehicle repair maintenance" to 18.0
        )

        for ((item, expected) in testCases) {
            val rate = FinanceHelper.guessTaxRate(null, item)
            assertEquals("Tax rate for '$item' was $rate, expected $expected", expected, rate, 0.001)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  3. Rejection / Unrecognized Items Test
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `test unrecognized items without category or HSN safely return null`() {
        val unknownItems = listOf(
            "Xzyw882910q",
            "Klzz-999000-foo",
            "MyRandomCustomGizmo#123",
            "Abcdef Ghijkl Mnopqr",
            "Unidentified Thing 2026",
            "FooBarBazQuux"
        )

        for (item in unknownItems) {
            val res = VoucherExcelImporter.resolveHsnAndTax(
                itemName = item,
                category = null,
                explicitHsn = null,
                explicitGst = null
            )
            assertNull("Item '$item' should not be recognized and must return null", res)
        }
    }

    @Test
    fun `test explicit HSN and tax take precedence even for unknown items`() {
        val res = VoucherExcelImporter.resolveHsnAndTax(
            itemName = "Custom Specialized Industrial Valve X-500",
            category = null,
            explicitHsn = "84818030",
            explicitGst = 18.0
        )
        assertNotNull(res)
        assertEquals("84818030", res?.first)
        assertEquals(18.0, res?.second ?: 0.0, 0.001)
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  4. Massive Randomized Fuzz Test (Thousands of items)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `massive random stress test of items across diverse sectors`() {
        val brands = listOf(
            "Hero", "Honda", "Bajaj", "TVS", "Yamaha", "Royal Enfield", "Suzuki",
            "Apple", "Samsung", "Dell", "HP", "Lenovo", "Asus", "Acer", "LG", "Sony",
            "Amul", "Mother Dairy", "Nestle", "Cadbury", "Britannia", "Parle", "Haldiram",
            "Tata", "Fortune", "Aashirvaad", "Dabur", "Patanjali", "MDH", "Everest",
            "UltraTech", "Ambuja", "ACC", "Jindal", "SAIL", "Asian Paints", "Berger",
            "Sun Pharma", "Cipla", "Dr Reddy", "Abbott", "Mankind", "Alkem", "Lupin"
        )

        val categoriesAndExpected = listOf(
            Triple("Motorcycle", "8711", 18.0),
            Triple("Scooter", "8711", 18.0),
            Triple("Car", "8703", 18.0),
            Triple("Helmet", "6506", 18.0),
            Triple("Engine Oil", "2710", 18.0),
            Triple("Tyre", "4011", 18.0),
            Triple("Whey Protein", "2106", 18.0),
            Triple("Chocolate", "1806", 18.0),
            Triple("Biscuits", "1905", 5.0),
            Triple("Noodles", "1902", 5.0),
            Triple("Honey", "0409", 5.0),
            Triple("Milk", "0401", 0.0),
            Triple("Butter", "0405", 5.0),
            Triple("Ghee", "0405", 5.0),
            Triple("Mobile Phone", "8517", 18.0),
            Triple("Laptop", "8471", 18.0),
            Triple("Television", "8528", 18.0),
            Triple("Air Conditioner", "8415", 18.0),
            Triple("Cement", "2523", 18.0),
            Triple("Steel Rods", "72", 18.0),
            Triple("Medicine", "3004", 5.0)
        )

        val modifiers = listOf(
            "Pro", "Max", "Plus", "Ultra", "Lite", "Prime", "Gold", "Classic", "Special",
            "Premium", "Deluxe", "Power", "Super", "Advanced", "100ml", "500g", "1kg", "5kg", "Pack of 2"
        )

        val rng = Random(42) // Fixed seed for reproducibility
        var successfulMatches = 0
        val totalIterations = 2000

        for (i in 0 until totalIterations) {
            val brand = brands[rng.nextInt(brands.size)]
            val (category, expectedHsn, expectedGst) = categoriesAndExpected[rng.nextInt(categoriesAndExpected.size)]
            val modifier = modifiers[rng.nextInt(modifiers.size)]
            val generatedItemName = "$brand Model-$i $modifier"

            val res = VoucherExcelImporter.resolveHsnAndTax(
                itemName = generatedItemName,
                category = category,
                explicitHsn = null,
                explicitGst = null
            )

            assertNotNull("Failed to resolve for Generated Item '$generatedItemName', Category '$category'", res)
            assertTrue("Expected HSN prefix $expectedHsn but got ${res?.first}", res?.first?.startsWith(expectedHsn) == true)
            // Valid GST rate in standard Indian slabs
            assertTrue("Rate ${res?.second} must be valid GST slab", res?.second in listOf(0.0, 5.0, 12.0, 18.0, 28.0, 40.0))
            successfulMatches++
        }

        assertEquals(totalIterations, successfulMatches)
        println("Successfully tested $successfulMatches randomized item configurations across all product sectors!")
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  5. FinanceHelper Voucher Creation with Random World Items Test
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `test FinanceHelper smart entry creation auto-assigns HSN and Tax for items`() = runBlocking {
        val financeHelperEntries = listOf(
            Triple("Sold Splendor Plus to Rahul for 75000", "8711", 18.0),
            Triple("Sold 2 T-shirts to Aman for 1000", "6109", 5.0),
            Triple("Sold 10 packets Parle-G to Suresh for 100", "1905", 5.0),
            Triple("Bought 50 bags Cement from UltraTech for 17500", "2523", 18.0),
            Triple("Sold 1 iPhone 15 to Priya for 80000", "8517", 18.0),
            Triple("Sold 1 boAt Earbuds to Sunny for 1499", "8518", 18.0),
            Triple("Sold 10 bottles Thums Up to Ajay for 400", "2202", 40.0),
            Triple("Sold 2 Motul Engine Oil to Bikers for 1000", "2710", 18.0),
            Triple("Sold 10 Cello Pens to Student for 100", "9608", 18.0),
            Triple("Sold 2 Whey Protein to Athlete for 4000", "2106", 18.0),
            Triple("Sold 1 Havells Ceiling Fan to Home for 2200", "8414", 18.0),
            Triple("Sold 2 Exide Battery to Fleet for 9000", "8506", 18.0),
            Triple("Sold 1 HP Laptop to Tech Hub for 55000", "8471", 18.0),
            Triple("Sold 1 Samsung Smart TV to Customer for 35000", "8528", 18.0),
            Triple("Sold 1 Voltas Split AC to Client for 42000", "8415", 18.0),
            Triple("Sold 10 Dairy Milk to Kids for 500", "1806", 18.0),
            Triple("Sold 20 packets Maggi to Canteen for 280", "1902", 5.0),
            Triple("Sold 5 kg Amul Butter to Bakery for 2500", "0405", 5.0),
            Triple("Sold 10 Dettol Soap to Hotel for 400", "3401", 18.0),
            Triple("Sold 5 Head and Shoulders Shampoo to Salon for 1500", "3305", 18.0)
        )

        for ((query, expectedHsnPrefix, expectedGst) in financeHelperEntries) {
            val draft = FinanceHelper.smartRegexParser(null, query)
            assertNotNull("FinanceHelper should parse query: '$query'", draft)
            val inv = draft?.inventoryItems?.firstOrNull()
            assertNotNull("Inventory item should be created for: '$query'", inv)
            assertTrue(
                "HSN for '$query' was ${inv?.hsnCode}, expected to start with $expectedHsnPrefix",
                inv?.hsnCode?.startsWith(expectedHsnPrefix) == true
            )
            assertEquals(
                "GST rate for '$query' was ${inv?.gstRate}, expected $expectedGst",
                expectedGst,
                inv?.gstRate ?: 0.0,
                0.001
            )
        }
    }

    @Test
    fun testUnitAndQuantityExtractionForMilkAndSaunf() = runBlocking {
        // Query 1: 20 liter milk sold for 5000 rs
        val milkDraft = FinanceHelper.smartRegexParser(null, "20 liter milk sold for 5000 rs")
        assertNotNull("Milk query should parse", milkDraft)
        val milkInv = milkDraft?.inventoryItems?.firstOrNull()
        assertNotNull("Milk inventory should be created", milkInv)
        assertEquals("Quantity should be 20.0", 20.0, milkInv?.quantity ?: 0.0, 0.001)
        assertEquals("Unit should be Liter", "Liter", milkInv?.unit)
        assertEquals("Amount should be 5000.0", 5000.0, milkInv?.amount ?: 0.0, 0.001)

        // Query 2: 200gm saunf bechi 20rs ki ankit ko
        val saunfDraft = FinanceHelper.tryParseHinglish(null, "200gm saunf bechi 20rs ki ankit ko") as? AiResponse.DraftVoucher
        assertNotNull("Saunf query should parse", saunfDraft)
        val saunfInv = saunfDraft?.inventoryItems?.firstOrNull()
        assertNotNull("Saunf inventory should be created", saunfInv)
        assertEquals("Quantity should be 200.0", 200.0, saunfInv?.quantity ?: 0.0, 0.001)
        assertEquals("Unit should be Gram", "Gram", saunfInv?.unit)
        assertEquals("Party should be Ankit", "Ankit", saunfDraft?.partyName)
        assertEquals("Amount should be 20.0", 20.0, saunfInv?.amount ?: 0.0, 0.001)

        // Query 3: 20 meter kapda beche 1500 ke rahul ko
        val meterDraft = FinanceHelper.tryParseHinglish(null, "20 meter kapda beche 1500 ke rahul ko") as? AiResponse.DraftVoucher
        assertNotNull("Meter query should parse", meterDraft)
        val meterInv = meterDraft?.inventoryItems?.firstOrNull()
        assertEquals("Quantity should be 20.0", 20.0, meterInv?.quantity ?: 0.0, 0.001)
        assertEquals("Unit should be Meter", "Meter", meterInv?.unit)

        // Query 4: 10 foot pipe sold for 300
        val footDraft = FinanceHelper.smartRegexParser(null, "10 foot pipe sold for 300")
        assertNotNull("Foot query should parse", footDraft)
        val footInv = footDraft?.inventoryItems?.firstOrNull()
        assertEquals("Quantity should be 10.0", 10.0, footInv?.quantity ?: 0.0, 0.001)
        assertEquals("Unit should be Foot", "Foot", footInv?.unit)
    }

    @Test
    fun testBillionsOfCommonItemsBareResolution() {
        val bareItemTestCases = listOf(
            // Dairy
            Triple("Milk", "0401", listOf(0.0)),
            Triple("Curd", "0403", listOf(0.0, 5.0)),
            Triple("Butter", "0405", listOf(5.0)),
            Triple("Ghee", "0405", listOf(5.0)),
            Triple("Paneer", "0406", listOf(0.0, 5.0)),
            Triple("Egg", "0407", listOf(0.0)),

            // Fresh Fruits & Veg
            Triple("Potato", "0701", listOf(0.0)),
            Triple("Tomato", "0702", listOf(0.0)),
            Triple("Onion", "0703", listOf(0.0)),
            Triple("Garlic", "0703", listOf(0.0)),
            Triple("Carrot", "0706", listOf(0.0)),
            Triple("Cucumber", "0707", listOf(0.0)),
            Triple("Apple", "0808", listOf(0.0)),
            Triple("Banana", "0803", listOf(0.0)),
            Triple("Mango", "0804", listOf(0.0)),
            Triple("Orange", "0805", listOf(0.0)),
            Triple("Grapes", "0806", listOf(0.0)),
            Triple("Watermelon", "0807", listOf(0.0)),
            Triple("Ginger", "0910", listOf(0.0)),

            // Grains & Staples
            Triple("Rice", "1006", listOf(0.0, 5.0)),
            Triple("Wheat", "1001", listOf(0.0)),
            Triple("Atta", "1101", listOf(0.0, 5.0)),
            Triple("Sugar", "1701", listOf(5.0)),
            Triple("Salt", "2501", listOf(0.0)),
            Triple("Tea", "0902", listOf(5.0)),
            Triple("Coffee", "0901", listOf(5.0)),
            Triple("Cooking Oil", "151", listOf(5.0)),
            Triple("Mustard Oil", "151", listOf(5.0)),

            // Snacks & Processed
            Triple("Maggi", "1902", listOf(5.0)),
            Triple("Noodles", "1902", listOf(5.0)),
            Triple("Biscuits", "1905", listOf(5.0, 18.0)),
            Triple("Namkeen", "2106", listOf(5.0)),
            Triple("Chips", "2008", listOf(5.0)),
            Triple("Chocolate", "1806", listOf(18.0)),
            Triple("Honey", "0409", listOf(5.0)),

            // Personal Care & Cleaning
            Triple("Soap", "3401", listOf(18.0)),
            Triple("Shampoo", "3305", listOf(18.0)),
            Triple("Hair Oil", "3305", listOf(18.0)),
            Triple("Toothpaste", "3306", listOf(18.0)),
            Triple("Detergent", "3402", listOf(18.0)),

            // Textiles & Apparel
            Triple("Cotton Fabric", "5208", listOf(5.0)),
            Triple("T-shirt", "6109", listOf(5.0)),
            Triple("Shirt", "6205", listOf(5.0)),
            Triple("Jeans", "6203", listOf(5.0)),
            Triple("Shoes", "6403", listOf(18.0)),

            // Electronics & Appliances
            Triple("Mobile Phone", "8517", listOf(18.0)),
            Triple("Laptop", "8471", listOf(18.0)),
            Triple("Television", "8528", listOf(18.0)),
            Triple("Refrigerator", "8418", listOf(18.0)),
            Triple("Washing Machine", "8450", listOf(18.0)),
            Triple("Air Conditioner", "8415", listOf(18.0)),
            Triple("Ceiling Fan", "8414", listOf(18.0)),
            Triple("LED Bulb", "8539", listOf(18.0)),
            Triple("Battery", "850", listOf(18.0)),

            // Construction & Hardware
            Triple("Cement", "2523", listOf(18.0)),
            Triple("Steel Rods", "72", listOf(18.0)),
            Triple("Paint", "32", listOf(18.0)),
            Triple("Tiles", "6907", listOf(18.0)),
            Triple("PVC Pipe", "3917", listOf(18.0)),

            // Automotive
            Triple("Motorcycle", "8711", listOf(18.0)),
            Triple("Scooter", "8711", listOf(18.0)),
            Triple("Car", "8703", listOf(18.0)),
            Triple("Tyre", "4011", listOf(18.0)),
            Triple("Engine Oil", "2710", listOf(18.0)),
            Triple("Helmet", "6506", listOf(18.0)),

            // Pharma
            Triple("Medicine", "3004", listOf(5.0)),
            Triple("Paracetamol", "3004", listOf(5.0)),

            // Books
            Triple("Book", "4901", listOf(0.0)),
            Triple("Notebook", "4820", listOf(0.0)),
            Triple("Pen", "9608", listOf(18.0))
        )

        val failures = mutableListOf<String>()
        for ((item, expectedHsnPrefix, allowedGst) in bareItemTestCases) {
            val res = VoucherExcelImporter.resolveHsnAndTax(
                itemName = item,
                category = null,
                explicitHsn = null,
                explicitGst = null
            )
            if (res == null) {
                failures.add("Item '$item' failed to resolve any HSN (returned null)")
            } else {
                val (hsn, gst) = res
                if (hsn == null || !hsn.startsWith(expectedHsnPrefix)) {
                    failures.add("Item '$item': expected HSN prefix '$expectedHsnPrefix', but got '$hsn' (gst=$gst%)")
                } else if (gst !in allowedGst) {
                    failures.add("Item '$item': HSN '$hsn' has rate $gst%, expected one of $allowedGst")
                }
            }
        }

        if (failures.isNotEmpty()) {
            println("Failures in bare item HSN resolution (${failures.size} / ${bareItemTestCases.size}):")
            failures.forEach { println(" - $it") }
        }
        assertEquals("Failures found in bare item HSN resolution:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `test phonetic Hinglish variants of aloo and vegetables resolve correctly`() = runBlocking {
        val potatoVariants = listOf(
            "aalloo", "aaloo", "alloo", "aalu", "allu", "aalo", "allo", "alu", "batata", "potato"
        )
        for (variant in potatoVariants) {
            val translated = LanguageTranslationUtils.translateItem(variant)
            assertEquals("Variant '$variant' should translate to Potato", "Potato", translated)

            val res = VoucherExcelImporter.resolveHsnAndTax(
                itemName = variant,
                category = null,
                explicitHsn = null,
                explicitGst = null
            )
            assertNotNull("Variant '$variant' must resolve HSN", res)
            assertEquals("Variant '$variant' must have HSN 0701", "0701", res?.first)
            assertEquals("Variant '$variant' must have 0% GST", 0.0, res?.second ?: -1.0, 0.001)
        }

        // Test voice sentence with aalloo through FinanceHelper
        val parsed = FinanceHelper.tryParseHinglish(null, "5 kg aalloo 100 me") as? AiResponse.DraftVoucher
        assertNotNull("Should parse '5 kg aalloo 100 me'", parsed)
        val item = parsed!!.inventoryItems.firstOrNull()
        assertNotNull("Should have inventory item", item)
        assertEquals("Item should be resolved as Potato", "Potato", item!!.name)
        assertEquals(5.0, item.quantity, 0.001)
        assertEquals(100.0, item.amount, 0.001)
        assertEquals("0701", item.hsnCode)
        assertEquals(0.0, item.gstRate ?: -1.0, 0.001)

        // Test milk vs milk powder
        val milkRes = VoucherExcelImporter.resolveHsnAndTax("milk", null, null, null)
        assertNotNull(milkRes)
        assertEquals("0401", milkRes?.first)
        assertEquals(0.0, milkRes?.second ?: -1.0, 0.001)

        val milkPowderRes = VoucherExcelImporter.resolveHsnAndTax("milk powder", null, null, null)
        assertNotNull(milkPowderRes)
        assertEquals("0402", milkPowderRes?.first)
        assertEquals(5.0, milkPowderRes?.second ?: -1.0, 0.001)
    }
}
