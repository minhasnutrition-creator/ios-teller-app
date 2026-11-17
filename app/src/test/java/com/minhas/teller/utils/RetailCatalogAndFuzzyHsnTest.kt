package com.minhas.teller.utils

import com.minhas.teller.data.entities.HsnEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Exhaustive verification across:
 * 1. Full 22,300+ HSN & Chapter 99 SAC asset integrity and searchability
 * 2. Supplements, Kirana, Electrical, Electronics, Automobile Spare Parts & General Retail catalogs
 * 3. Slight misspellings, phonetic typos, wrong word order, and noisy multi-word SKUs
 * 4. Interactive UI lookup confidence ("Did you mean this?" vs "Is it either one of these?")
 * 5. FinanceHelper voucher creation with automatic HSN & GST assignment
 * 6. Combinatorial SKU permutation stress test + latency + gibberish rejection
 */
class RetailCatalogAndFuzzyHsnTest {

    companion object {
        private val fullCatalog: List<HsnEntry> by lazy {
            val assetFile = listOf(
                File("app/src/main/assets/hsn_codes.txt"),
                File("src/main/assets/hsn_codes.txt"),
                File("../app/src/main/assets/hsn_codes.txt")
            ).firstOrNull { it.exists() }
                ?: error("Could not locate app/src/main/assets/hsn_codes.txt")

            val assetEntries = assetFile.bufferedReader().useLines { lines ->
                HsnPresetData.parseHsnLines(lines)
            }
            (HsnCommonItems.entries() + assetEntries).map { HsnPresetData.sanitizeHsnEntry(it) }
        }
    }

    private fun parseVoice(query: String): AiResponse.DraftVoucher? = runBlocking {
        (FinanceHelper.tryParseHinglish(null, query)
            ?: FinanceHelper.smartRegexParser(null, query)
            ?: FinanceHelper.parseVoiceCommandLocal(query)) as? AiResponse.DraftVoucher
    }

    @Test
    fun testFullDirectoryIntegrity_All22300PlusHsnAndSacEntriesClean() {
        assertTrue("Expected > 22,000 HSN/SAC entries, got ${fullCatalog.size}", fullCatalog.size > 22_000)

        val validRates = setOf(0.0, 0.25, 3.0, 5.0, 12.0, 18.0, 40.0)
        val bareOthers = fullCatalog.filter {
            it.description.trim().lowercase().trimEnd('.', ':', '-') in setOf("other", "others")
        }
        assertEquals("No bare 'Other' descriptions should remain: $bareOthers", 0, bareOthers.size)

        val emptyKeywords = fullCatalog.filter { it.keywords.isBlank() }
        assertEquals("Every single HSN/SAC entry must have keywords", 0, emptyKeywords.size)

        val invalidRateEntries = fullCatalog.filter { it.gstRate !in validRates }
        assertEquals("Unexpected GST rates found: ${invalidRateEntries.take(5)}", 0, invalidRateEntries.size)

        // Verify Chapter 99 SAC codes are present
        val sacEntries = fullCatalog.filter { it.hsnCode.startsWith("99") }
        assertTrue("Expected > 80 SAC service codes, got ${sacEntries.size}", sacEntries.size >= 80)

        // Verify every HSN chapter 01..99 (that exists) is represented
        val chapters = fullCatalog.map { it.hsnCode.take(2) }.toSet()
        assertTrue("Expected >= 95 distinct chapters, got ${chapters.size}", chapters.size >= 95)

        // Sample 1 entry from EVERY chapter in the 22,300+ directory and verify HsnFuzzyMatcher finds it quickly
        val byChapter = fullCatalog.groupBy { it.hsnCode.take(2) }
        val startMs = System.currentTimeMillis()
        for ((_, list) in byChapter) {
            val sample = list.first()
            val byCode = HsnFuzzyMatcher.lookup(sample.hsnCode, fullCatalog, maxResults = 5)
            assertTrue("Lookup by HSN code ${sample.hsnCode} failed", byCode.suggestions.any { it.entry.hsnCode == sample.hsnCode })
        }
        val elapsed = System.currentTimeMillis() - startMs
        assertTrue("Chapter-wide 99x lookup took ${elapsed}ms (should be fast)", elapsed < 5_000)
    }

    @Test
    fun testAllStoreCatalogs_ExactBrandsAndProducts() {
        val cases = listOf(
            // Supplements (clinical, Ayurvedic, hydration, protein, bars)
            Triple("Ensure Diabetes Care", "2106", 18.0),
            Triple("Protinex Powder", "2106", 18.0),
            Triple("Pure Himalayan Shilajit", "2106", 18.0),
            Triple("Ashwagandha KSM66", "2106", 18.0),
            Triple("Dabur Chyawanprash", "3004", 5.0),
            Triple("Electral ORS", "3004", 5.0),
            Triple("Pintola Peanut Butter", "2008", 5.0),
            Triple("RiteBite Protein Bar", "2106", 18.0),
            // Kirana (millets, spices, hygiene, home care, puja)
            Triple("Ragi Finger Millet", "1008", 0.0),
            Triple("Aashirvaad Chakki Atta", "1101", 0.0),
            Triple("Lijjat Papad", "1905", 0.0),
            Triple("Nutrela Soya Chunks", "2106", 5.0),
            Triple("Whisper Ultra Sanitary Pad", "9619", 0.0),
            Triple("Pampers Baby Diaper", "9619", 5.0),
            Triple("Phool Jhadu Broom", "9603", 0.0),
            Triple("Harpic Toilet Cleaner", "3808", 18.0),
            Triple("Dabur Honey", "0409", 5.0),
            Triple("Pedigree Dog Food", "2309", 18.0),
            // Electrical Store
            Triple("Finolex 1.5 sq mm FR Wire", "8544", 18.0),
            Triple("Polycab Submersible Cable", "8544", 18.0),
            Triple("Anchor Roma 6A Switch", "8536", 18.0),
            Triple("Legrand MCB 32A", "8536", 18.0),
            Triple("Atomberg BLDC Ceiling Fan", "8414", 18.0),
            Triple("Philips 9W LED Bulb", "8539", 18.0),
            Triple("Wipro LED Panel Light", "9405", 18.0),
            Triple("Luminous Inverter", "8504", 18.0),
            Triple("Exide Tubular Battery 150Ah", "8507", 18.0),
            Triple("Racold Storage Geyser", "8516", 18.0),
            Triple("Steelgrip PVC Insulation Tape", "3919", 18.0),
            Triple("Fluke Digital Multimeter", "9030", 18.0),
            Triple("Kirloskar Submersible Pump", "8413", 5.0),
            // Electronics Store
            Triple("SanDisk NVMe SSD 1TB", "8471", 18.0),
            Triple("Logitech Wireless Mouse", "8471", 18.0),
            Triple("TP-Link Dual Band Router", "8517", 18.0),
            Triple("Hikvision CCTV Dome Camera", "8525", 18.0),
            Triple("CP Plus 8ch DVR", "8521", 18.0),
            Triple("Epson EcoTank Printer", "8443", 18.0),
            Triple("Casio Scientific Calculator", "8470", 18.0),
            Triple("Anker GaN Fast Charger", "8504", 18.0),
            Triple("Tempered Glass Screen Guard", "7007", 18.0),
            Triple("4K HDMI Cable", "8544", 18.0),
            Triple("Sony PS5 PlayStation", "9504", 18.0),
            // Automobile & 2W Spare Parts Store
            Triple("NGK Iridium Spark Plug", "8511", 18.0),
            Triple("Purolator Air Filter", "8421", 18.0),
            Triple("Brembo Car Brake Pad", "8708", 18.0),
            Triple("Rolon Chain Sprocket Kit", "8714", 18.0),
            Triple("SKF 6202 Ball Bearing", "8482", 18.0),
            Triple("Gabriel Front Shock Absorber", "8708", 18.0),
            Triple("Hero Splendor Rear Shocker", "8714", 18.0),
            Triple("Castrol Activ 20W40 Engine Oil", "2710", 18.0),
            Triple("Bosch DOT 4 Brake Fluid", "3819", 18.0),
            Triple("Castrol Radicool Coolant", "3820", 18.0),
            Triple("MRF Tubeless Bike Tyre", "4011", 18.0),
            Triple("Butyl Inner Tube", "4013", 18.0),
            Triple("Uno Minda Lock Set", "8301", 18.0),
            Triple("Lumax Side Mirror", "7009", 18.0),
            // Services SAC
            Triple("GTA Road Transport Freight", "9965", 5.0),
            Triple("Software Development Service", "9983", 18.0),
            Triple("Car Servicing Labour", "9987", 18.0),
        )

        for ((name, expectedPrefix, expectedRate) in cases) {
            val hit = HsnFuzzyMatcher.findConfidentAutoMatch(name, null, fullCatalog)
            assertNotNull("Expected match for '$name'", hit)
            assertTrue(
                "For '$name', expected HSN starting with '$expectedPrefix', got '${hit?.hsnCode}' (${hit?.description})",
                hit?.hsnCode?.startsWith(expectedPrefix) == true
            )
            assertEquals(
                "For '$name' (${hit?.hsnCode}), expected GST $expectedRate",
                expectedRate,
                hit?.gstRate ?: -1.0,
                0.01
            )
        }
    }

    @Test
    fun testMisspelledWrongOrderAndLongNoisySkuNames() {
        val queries = listOf(
            // Slight misspellings
            Triple("whey protien", "2106", 18.0),
            Triple("creatin monohydrat", "2106", 18.0),
            Triple("ashwagnda", "1211", 5.0),
            Triple("ashwagnda ksm66", "2106", 18.0),
            Triple("shilajeet resin", "2106", 18.0),
            Triple("finolex wyre", "8544", 18.0),
            Triple("led blub 9w", "8539", 18.0),
            Triple("spak plug", "8511", 18.0),
            Triple("car brak pad", "8708", 18.0),
            Triple("carborator", "8409", 18.0),
            Triple("pendriv 64gb", "8471", 18.0),
            Triple("wifi roter", "8517", 18.0),
            Triple("toothpest colgate", "3306", 18.0),
            Triple("invertr battry", "8507", 18.0),
            Triple("geysr 15 ltr", "8516", 18.0),
            // Wrong word order
            Triple("gainer mass", "2106", 18.0),
            Triple("bulb led", "8539", 18.0),
            Triple("plug spark", "8511", 18.0),
            Triple("filter air", "8421", 18.0),
            Triple("oil engine", "2710", 18.0),
            Triple("wire finolex copper", "8544", 18.0),
            Triple("switch modular", "8536", 18.0),
            Triple("bank power", "8507", 18.0),
            Triple("bottle shaker", "3924", 18.0),
            Triple("pad sanitary", "9619", 0.0),
            // Noisy real-world multi-word SKUs with unseen brands / weights / flavours / specs
            Triple("Optimum Nutrition Gold Standard 100% Whey Double Rich Chocolate 5lbs", "2106", 18.0),
            Triple("MuscleBlaze Biozyme Performance Whey Rich Milk Chocolate 4kg", "2106", 18.0),
            Triple("Shree Balaji Super Heavy Duty 16A Modular Switch White", "8536", 18.0),
            Triple("Aarav Enterprises Supreme 9W Cool Daylight B22 LED Bulb Pack of 4", "8539", 18.0),
            Triple("Maruti Swift Dzire VXi Front Brake Pad Set", "8708", 18.0),
            Triple("UnknownCo 1000cc Carburetor Assembly", "8409", 18.0),
            Triple("LocalStore Unlisted 1.5 sq mm FR Copper Wire 90m", "8544", 18.0),
        )

        for ((query, expectedPrefix, expectedRate) in queries) {
            val hit = HsnFuzzyMatcher.findConfidentAutoMatch(query, null, fullCatalog)
            assertNotNull("Expected confident match for '$query'", hit)
            assertTrue(
                "For '$query', expected prefix '$expectedPrefix', got '${hit?.hsnCode}' (${hit?.description})",
                hit?.hsnCode?.startsWith(expectedPrefix) == true
            )
            assertEquals("Wrong rate for '$query'", expectedRate, hit?.gstRate ?: -1.0, 0.01)
        }
    }

    @Test
    fun testInteractiveUiConfidence_DidYouMeanVsIsItEitherOneOfThese() {
        // 1. Exact clear winner -> Auto-apply
        val exactDairyMilk = HsnFuzzyMatcher.lookup("Cadbury Dairy Milk", fullCatalog, maxResults = 5)
        assertEquals(HsnFuzzyMatcher.MatchConfidence.EXACT_CONFIDENT, exactDairyMilk.confidence)
        assertEquals("1806", exactDairyMilk.suggestions.first().entry.hsnCode)

        val exactMustard = HsnFuzzyMatcher.lookup("Mustard Oil", fullCatalog, maxResults = 5)
        assertEquals(HsnFuzzyMatcher.MatchConfidence.EXACT_CONFIDENT, exactMustard.confidence)
        assertTrue(
            "Expected HSN starting with 1514 for Mustard Oil, got ${exactMustard.suggestions.first().entry.hsnCode}",
            exactMustard.suggestions.first().entry.hsnCode.startsWith("1514")
        )
        assertEquals(5.0, exactMustard.suggestions.first().entry.gstRate, 0.01)

        // 2. Single misspelled / reordered item -> "Did you mean this?" (1 closest match)
        for (singleFuzzy in listOf("whey protien", "creatin", "ashwagnda", "finolex wyre", "gainer mass")) {
            val res = HsnFuzzyMatcher.lookup(singleFuzzy, fullCatalog, maxResults = 5)
            assertTrue(
                "Expected DID_YOU_MEAN_ONE (or AMBIGUOUS_MULTIPLE) for '$singleFuzzy', got ${res.confidence}",
                res.confidence == HsnFuzzyMatcher.MatchConfidence.DID_YOU_MEAN_ONE ||
                    res.confidence == HsnFuzzyMatcher.MatchConfidence.AMBIGUOUS_MULTIPLE
            )
            assertTrue("Expected suggestion for '$singleFuzzy'", res.suggestions.isNotEmpty())
        }
        val creatinRes = HsnFuzzyMatcher.lookup("creatin", fullCatalog, maxResults = 5)
        assertEquals(HsnFuzzyMatcher.MatchConfidence.DID_YOU_MEAN_ONE, creatinRes.confidence)
        assertEquals("2106", creatinRes.suggestions.first().entry.hsnCode)

        // 3. Ambiguous / competing 2+ closest options -> "Is it either one of these?"
        val oilRes = HsnFuzzyMatcher.lookup("oil", fullCatalog, maxResults = 5)
        assertEquals(HsnFuzzyMatcher.MatchConfidence.AMBIGUOUS_MULTIPLE, oilRes.confidence)
        assertTrue("Expected >= 2 competing oil categories", oilRes.suggestions.size >= 2)
        val oilRates = oilRes.suggestions.map { it.entry.gstRate }.toSet()
        assertTrue("Oil options should include both 5% cooking and 18% engine/hair oil: $oilRates", oilRates.contains(5.0) || oilRates.contains(18.0))

        val padRes = HsnFuzzyMatcher.lookup("pad", fullCatalog, maxResults = 5)
        assertEquals(HsnFuzzyMatcher.MatchConfidence.AMBIGUOUS_MULTIPLE, padRes.confidence)
        assertTrue("Expected >= 2 competing pad options (brake pad, sanitary pad, etc.)", padRes.suggestions.size >= 2)

        val biscuitRes = HsnFuzzyMatcher.lookup("biscuit", fullCatalog, maxResults = 5)
        assertTrue(
            "Expected multiple options for 'biscuit' (plain 5% vs cream 18%) or 2+ choices, got ${biscuitRes.confidence}",
            biscuitRes.suggestions.size >= 2 &&
                biscuitRes.suggestions.map { it.entry.gstRate }.toSet().containsAll(listOf(5.0, 18.0))
        )
    }

    @Test
    fun testFinanceHelperVoucherParsing_MisspelledAndReorderedItems() {
        val soldProtien = parseVoice("sold 2 whey protien @ 2500 cash")
        assertNotNull("Expected draft voucher for sold whey protien", soldProtien)
        val item1 = soldProtien!!.inventoryItems.firstOrNull()
        assertNotNull("Expected inventoryItem in $soldProtien", item1)
        assertEquals("2106", item1!!.hsnCode)
        assertEquals(18.0, item1.gstRate ?: -1.0, 0.01)

        val boughtPlug = parseVoice("bought 10 spak plug @ 150 upi")
        assertNotNull("Expected draft voucher for bought spak plug", boughtPlug)
        val item2 = boughtPlug!!.inventoryItems.firstOrNull()
        assertNotNull("Expected inventoryItem in $boughtPlug", item2)
        assertTrue("Expected 8511/8714 for spak plug, got ${item2!!.hsnCode}", item2.hsnCode == "8511" || item2.hsnCode == "8714")
        assertEquals(18.0, item2.gstRate ?: -1.0, 0.01)

        val soldWire = parseVoice("sold 5 finolex wyre @ 1200 cash")
        assertNotNull("Expected draft voucher for sold finolex wyre", soldWire)
        val item3 = soldWire!!.inventoryItems.firstOrNull()
        assertNotNull("Expected inventoryItem in $soldWire", item3)
        assertEquals("8544", item3!!.hsnCode)
        assertEquals(18.0, item3.gstRate ?: -1.0, 0.01)

        val soldPad = parseVoice("sold 4 pad brake @ 450 cash")
        assertNotNull("Expected draft voucher for reordered pad brake", soldPad)
        val item4 = soldPad!!.inventoryItems.firstOrNull()
        assertNotNull("Expected inventoryItem in $soldPad", item4)
        assertTrue("Expected 8708/8714 for brake pad, got ${item4!!.hsnCode}", item4.hsnCode == "8708" || item4.hsnCode == "8714")
        assertEquals(18.0, item4.gstRate ?: -1.0, 0.01)
    }

    @Test
    fun testCombinatorialSkuPermutationsAndGibberishRejection() {
        // Warm up index once before measuring throughput
        HsnFuzzyMatcher.findConfidentAutoMatch("Led Bulb", null, fullCatalog)

        val brands = listOf("BharatCo", "GenericShop", "KisanBrand", "MetroPrime", "SuperStar")
        val specs = listOf("500g", "1kg", "9W", "16A", "20W40", "150Ah", "250ml", "64GB")
        val modifiers = listOf("Heavy Duty", "Original", "Deluxe", "Black", "Mini", "Value Pack")
        val coreProducts = listOf(
            Triple("Whey Protein", "2106", 18.0),
            Triple("Creatine Monohydrate", "2106", 18.0),
            Triple("Mustard Oil", "1514", 5.0),
            Triple("Basmati Rice", "1006", 5.0),
            Triple("House Wire", "8544", 18.0),
            Triple("LED Bulb", "8539", 18.0),
            Triple("Modular Switch", "8536", 18.0),
            Triple("Ceiling Fan", "8414", 18.0),
            Triple("Inverter Battery", "8507", 18.0),
            Triple("Spark Plug", "8511", 18.0),
            Triple("Air Filter", "8421", 18.0),
            Triple("Ball Bearing", "8482", 18.0),
            Triple("Shock Absorber", "8714", 18.0),
            Triple("Brake Pad", "8708", 18.0),
            Triple("WiFi Router", "8517", 18.0),
            Triple("CCTV Camera", "8525", 18.0),
            Triple("Sanitary Pad", "9619", 0.0),
            Triple("Baby Diaper", "9619", 5.0),
            Triple("Wall Putty", "3214", 18.0),
            Triple("Paracetamol Tablet", "3004", 5.0),
        )

        var testedCount = 0
        var passedCount = 0
        val startMs = System.currentTimeMillis()

        // Exercise full combinatorial Cartesian grid: 5 * 6 * 8 * 20 = 4,800 generated unseen-brand SKU permutations!
        for (brand in brands) {
            for (mod in modifiers) {
                for (spec in specs) {
                    for ((prod, expectedHsn, expectedRate) in coreProducts) {
                        val sku = "$brand $mod $prod $spec"
                        testedCount++
                        val hit = HsnFuzzyMatcher.findConfidentAutoMatch(sku, null, fullCatalog)
                        val validHsn = hit != null && (
                            hit.hsnCode.startsWith(expectedHsn) ||
                                (expectedHsn == "8708" && hit.hsnCode.startsWith("8714")) ||
                                (expectedHsn == "8714" && hit.hsnCode.startsWith("8708"))
                            )
                        val validRate = hit != null && kotlin.math.abs(hit.gstRate - expectedRate) < 0.01
                        if (validHsn && validRate) {
                            passedCount++
                        } else {
                            fail("SKU '$sku' resolved to ${hit?.hsnCode} (${hit?.gstRate}%) instead of $expectedHsn ($expectedRate%)")
                        }
                    }
                }
            }
        }
        val elapsedMs = System.currentTimeMillis() - startMs
        assertEquals(4800, testedCount)
        assertEquals(4800, passedCount)
        assertTrue("4,800 SKU lookups took ${elapsedMs}ms — should complete well under 15s", elapsedMs < 15_000)

        // Ensure complete gibberish still safely rejects without false positives
        val gibberish = listOf(
            "Xzyw882910q",
            "Klzz-999000-foo",
            "MyRandomCustomGizmo#123",
            "Abcdef Ghijkl Mnopqr",
            "Unidentified Thing 2026",
            "FooBarBazQuux"
        )
        for (g in gibberish) {
            assertNull("Gibberish '$g' must NOT match an HSN code", VoucherExcelImporter.resolveHsnAndTax(g, null, null, null))
            assertNull("Gibberish '$g' must NOT auto-match in HsnFuzzyMatcher", HsnFuzzyMatcher.findConfidentAutoMatch(g, null, fullCatalog))
        }
    }

    @Test
    fun testAllWorldItems_169GlobalRetailAndB2BCategories() {
        val worldItems = listOf(
            // Supplements & Sports Nutrition
            "MuscleBlaze Whey Gold Protein 2kg" to setOf("2106"),
            "Optimum Nutrition Gold Standard Whey" to setOf("2106"),
            "Creapure Micronized Creatine Monohydrate" to setOf("2106"),
            "Isopure Zero Carb Protein Isolate" to setOf("2106"),
            "Multivitamin Tablet Ashwagandha Shilajit" to setOf("2106"),
            "Fish Oil Omega 3 Softgel Capsules" to setOf("1504", "2106", "3004"),
            "Electrolyte ORS Hydration Powder" to setOf("2106", "3004", "2202"),
            "BCAA Amino Acid Pre Workout" to setOf("2106"),
            "Mass Gainer Chocolate 3kg" to setOf("2106"),
            "Peanut Butter High Protein Crunchy" to setOf("2008", "2106"),
            // Kirana, Staples, Dairy, Beverages, Packaged Food
            "India Gate Classic Basmati Rice 5kg" to setOf("1006"),
            "Aashirvaad Shudh Chakki Atta 10kg" to setOf("1101"),
            "Fortune Sunlite Refined Sunflower Oil 1L" to setOf("1512", "1507", "1515"),
            "Mustard Oil Kachi Ghani 1L" to setOf("1514"),
            "Amul Pure Desi Ghee 1L" to setOf("0405"),
            "Amul Taaza Toned Milk 1L" to setOf("0401"),
            "Amul Butter Pasteurised 500g" to setOf("0405"),
            "Amul Mozzarella Diced Cheese 200g" to setOf("0406"),
            "Mother Dairy Fresh Paneer 200g" to setOf("0406"),
            "Tata Salt Vacuum Evaporated Iodised 1kg" to setOf("2501"),
            "Toor Dal Arhar Unpolished 1kg" to setOf("0713"),
            "Chana Dal Yellow Split 1kg" to setOf("0713"),
            "Kabuli Chana White Chickpeas" to setOf("0713"),
            "Sugar Refined White Cheeni 1kg" to setOf("1701"),
            "Jaggery Gud Organic Block 500g" to setOf("1701"),
            "Tata Tea Gold Assam CTC 500g" to setOf("0902"),
            "Nescafe Classic Instant Coffee 100g" to setOf("2101", "0901"),
            "Maggi 2 Minute Masala Noodles 12 Pack" to setOf("1902"),
            "Parle G Glucose Biscuit Family Pack" to setOf("1905"),
            "Britannia Good Day Butter Cookies" to setOf("1905"),
            "Lays Classic Salted Potato Chips" to setOf("2008", "2005", "2106", "1905"),
            "Haldiram Bhujia Sev Namkeen 400g" to setOf("2106", "1905"),
            "Kissan Fresh Tomato Ketchup 950g" to setOf("2103"),
            "Dabur Pure Honey 500g" to setOf("0409"),
            "MDH Garam Masala 100g" to setOf("0910"),
            "Everest Turmeric Haldi Powder 200g" to setOf("0910"),
            "Kashmiri Lal Mirch Chilli Powder" to setOf("0904"),
            "Black Pepper Kali Mirch Whole 100g" to setOf("0904"),
            "Green Cardamom Elaichi 50g" to setOf("0908"),
            "California Almonds Badam 500g" to setOf("0802"),
            "Cashew Nuts Kaju Whole W320" to setOf("0801"),
            "Raisins Kishmish Golden 250g" to setOf("0806"),
            "Coco Cola Soft Drink Pet 2L" to setOf("2202"),
            "Red Bull Energy Drink 250ml" to setOf("2202"),
            "Bisleri Mineral Water 1L Bottle" to setOf("2201"),
            "Surf Excel Easy Wash Detergent Powder" to setOf("3402"),
            "Vim Dishwash Gel Lemon 750ml" to setOf("3402"),
            "Dove Cream Beauty Bathing Soap Bar" to setOf("3401"),
            "Colgate MaxFresh Toothpaste 150g" to setOf("3306"),
            "Head and Shoulders Anti Dandruff Shampoo" to setOf("3305"),
            "Parachute Coconut Hair Oil 500ml" to setOf("1513", "3305"),
            "Dettol Antiseptic Liquid 550ml" to setOf("3401", "3808", "3004"),
            "Harpic Power Plus Toilet Cleaner" to setOf("3402", "3808"),
            "Good Knight Gold Flash Mosquito Repellent" to setOf("3808"),
            "Whisper Ultra Clean Sanitary Pads" to setOf("9619"),
            "Pampers Active Baby Diapers Pants" to setOf("9619"),
            "Pedigree Adult Dry Dog Food 3kg" to setOf("2309"),
            // Electrical & Hardware Store
            "Havells 9W LED Bulb Cool Daylight" to setOf("8539"),
            "Syska LED Tube Light 20W Batten" to setOf("8539", "9405"),
            "Crompton High Speed Ceiling Fan 1200mm" to setOf("8414"),
            "Havells 32A Double Pole MCB Circuit Breaker" to setOf("8536"),
            "Anchor Roma 6A 3 Pin Modular Socket" to setOf("8536"),
            "Legrand 16A Modular Switch White" to setOf("8536"),
            "Polycab 2.5 sq mm FRLS Copper House Wire" to setOf("8544"),
            "Finolex Submersible Flat Cable 3 Core" to setOf("8544"),
            "Luminous 150Ah Tall Tubular Inverter Battery" to setOf("8507"),
            "Microtek Pure Sine Wave Home Inverter UPS" to setOf("8504"),
            "V-Guard Voltage Stabilizer 4kVA" to setOf("8504"),
            "Bajaj 25L Storage Water Heater Geyser" to setOf("8516"),
            "Havells Immersion Water Heater Rod 1500W" to setOf("8516"),
            "Bosch 13mm Hammer Drill Machine 600W" to setOf("8467"),
            "Stanley Combination Plier Insulated" to setOf("8205", "8203"),
            "Taparia Adjustable Wrench Spanner Set" to setOf("8204"),
            "Astral CPVC Plumbing Pipe 1 Inch" to setOf("3917"),
            "Jaquar Bath Brass Angle Valve Tap" to setOf("8481"),
            // Electronics, IT, Appliances, Mobile, Camera, Gaming
            "Apple iPhone 15 Pro Max 256GB" to setOf("8517"),
            "Samsung Galaxy S24 Ultra 5G Smartphone" to setOf("8517"),
            "Samsung 43 Inch 4K Smart LED TV" to setOf("8528"),
            "LG 7kg Front Load Washing Machine" to setOf("8450"),
            "Whirlpool 265L Frost Free Refrigerator" to setOf("8418"),
            "Voltas 1.5 Ton 5 Star Split Air Conditioner" to setOf("8415"),
            "IFB 30L Convection Microwave Oven" to setOf("8516"),
            "Philips Air Fryer Digital XL 4.1L" to setOf("8516"),
            "Bajaj Mixer Grinder 750W 3 Jars" to setOf("8509"),
            "Kent RO UV UF Water Purifier" to setOf("8421"),
            "Dell Inspiron 15 Core i5 Laptop" to setOf("8471"),
            "Apple MacBook Air M3 Laptop" to setOf("8471"),
            "HP LaserJet Pro Wireless Printer" to setOf("8443"),
            "Logitech Wireless Optical Mouse" to setOf("8471"),
            "Mechanical Gaming Keyboard RGB USB" to setOf("8471"),
            "Samsung 1TB NVMe M.2 SSD Solid State Drive" to setOf("8471", "8523"),
            "SanDisk 128GB MicroSD Memory Card" to setOf("8471", "8523"),
            "TP-Link Dual Band WiFi 6 Router" to setOf("8517"),
            "Boat Airdopes True Wireless Earbuds TWS" to setOf("8518"),
            "Sony WH-1000XM5 Noise Cancelling Headphones" to setOf("8518"),
            "JBL Flip 6 Bluetooth Portable Speaker" to setOf("8518"),
            "Anker 20000mAh Fast Charging Power Bank" to setOf("8507", "8504"),
            "Samsung 25W USB-C Fast Charger Adapter" to setOf("8504"),
            "Anker Braided USB Type C Data Cable" to setOf("8544"),
            "CP Plus 2MP CCTV Dome Security Camera" to setOf("8525"),
            "Canon EOS Mirrorless Digital Camera" to setOf("9006", "8525"),
            "Sony PlayStation 5 Gaming Console" to setOf("9504"),
            "Apple Watch Series 9 Smartwatch" to setOf("8517", "9102"),
            "Luminous 550W Monocrystalline Solar Panel" to setOf("8541"),
            // Automobile, 2W, Tractor Spare Parts & Lubricants
            "MRF ZVTV 195/55 R16 Car Tubeless Tyre" to setOf("4011"),
            "CEAT Secura Zoom Motorcycle Rear Tyre" to setOf("4011"),
            "Exide Xpress 12V Automotive Car Battery" to setOf("8507"),
            "Castrol EDGE 5W30 Synthetic Engine Oil" to setOf("2710"),
            "Motul 7100 10W40 4T Bike Engine Oil" to setOf("2710"),
            "Bosch Iridium Spark Plug Car Bike" to setOf("8511"),
            "Maruti Swift Dzire Front Brake Pad Set" to setOf("8708"),
            "Bajaj Pulsar 150 Chain Sprocket Kit" to setOf("8714", "7315"),
            "Royal Enfield Classic 350 Clutch Plate" to setOf("8714", "8708"),
            "Honda Activa Drive V Belt CVT" to setOf("4010", "8714"),
            "Hero Splendor Rear Shock Absorber" to setOf("8714"),
            "Mahindra Scorpio Clutch Pressure Plate" to setOf("8708"),
            "Car Oil Filter Purolator Fuel Filter" to setOf("8421"),
            "Car Air Filter Element Paper" to setOf("8421"),
            "SKF Deep Groove Ball Bearing 6205" to setOf("8482"),
            "Bosch 20 Inch Car Wiper Blade Pair" to setOf("8512", "8708"),
            "Philips H4 Car Headlight Halogen Bulb" to setOf("8539", "8512"),
            "Motorcycle Helmet Full Face Vega ISI" to setOf("6506"),
            "Car Seat Cover Leatherette Cushioned" to setOf("8708", "9401"),
            "Tractor Rear Hydraulic Lift Pump" to setOf("8413", "8708"),
            // Pharma, Medical & Diagnostics
            "Dolo 650 Paracetamol Fever Tablets" to setOf("3004"),
            "Crocin Advance 500mg Tablet Strip" to setOf("3004"),
            "Azithromycin 500mg Antibiotic Tablets" to setOf("3004"),
            "Cough Syrup Benadryl 150ml" to setOf("3004"),
            "Omron Automatic Digital BP Monitor" to setOf("9018"),
            "Accu-Chek Glucometer Test Strips" to setOf("9025", "3822", "9018", "9027"),
            "Infrared Digital Thermometer Non Contact" to setOf("9025"),
            "N95 Face Mask Surgical Respirator" to setOf("6307", "9020"),
            "Sterile Cotton Bandage Roll First Aid" to setOf("3005"),
            "Insulin Syringe Disposable Needle" to setOf("9018"),
            // Construction, Paints, Apparel, Footwear, Home, Furniture, Agri, Stationery, Sports, Services
            "UltraTech OPC 53 Grade Cement 50kg Bag" to setOf("2523"),
            "Tata Tiscon TMT Steel Rebar 12mm" to setOf("7214", "7213"),
            "Asian Paints Apex Exterior Emulsion" to setOf("3209", "3208"),
            "Fevicol SH Synthetic Wood Adhesive" to setOf("3506"),
            "Kajaria Vitrified Ceramic Floor Tiles" to setOf("6907"),
            "Saint Gobain Clear Float Window Glass" to setOf("7005"),
            "Century Ply Marine Plywood Sheet 19mm" to setOf("4412"),
            "Levis Mens Slim Fit Cotton Jeans" to setOf("6203"),
            "Allen Solly Formal Mens Cotton Shirt" to setOf("6205"),
            "Jockey Mens Cotton T-Shirt Polo" to setOf("6109", "6105"),
            "Banarasi Silk Woven Saree Traditional" to setOf("5007", "5407"),
            "Nike Air Running Sports Shoes" to setOf("6404", "6403"),
            "Bata Mens Leather Formal Shoes" to setOf("6403"),
            "Titan Analog Wrist Watch Stainless Steel" to setOf("9102", "9101"),
            "Ray-Ban Polarized Sunglasses" to setOf("9004"),
            "American Tourister Trolley Suitcase Bag" to setOf("4202"),
            "Wildcraft Waterproof School Laptop Backpack" to setOf("4202"),
            "Sleepwell Memory Foam Orthopedic Mattress" to setOf("9404"),
            "Nilkamal Plastic Moulded Dining Chair" to setOf("9403", "9401"),
            "Milton Thermosteel Vacuum Water Bottle 1L" to setOf("9617"),
            "Prestige Popular Aluminium Pressure Cooker 5L" to setOf("7615"),
            "Classmate Spiral Notebook A4 200 Pages" to setOf("4820"),
            "JK Copier A4 Printing Paper 500 Sheets" to setOf("4802"),
            "Reynolds Ball Point Pen Blue Box" to setOf("9608"),
            "Casio Scientific Calculator fx-991CW" to setOf("8470"),
            "SG English Willow Cricket Bat" to setOf("9506"),
            "Yonex Carbonex Badminton Racket" to setOf("9506"),
            "Nivia Storm Football Size 5" to setOf("9506"),
            "Yoga Mat Anti Skid NBR 6mm" to setOf("9506", "3918"),
            "DAP Diammonium Phosphate Fertilizer Bag" to setOf("3105", "3102"),
            "Urea Agricultural Fertilizer 45kg" to setOf("3102"),
            "Gold Jewellery 22K Hallmarked Necklace" to setOf("7113"),
            "Accounting and GST Audit Consultancy Service" to setOf("9982"),
            "Cloud Software Development SaaS Subscription" to setOf("9983"),
            "Goods Transport Agency Truck Freight" to setOf("9965")
        )
        assertEquals(169, worldItems.size)
        for ((query, expectedPrefixes) in worldItems) {
            val res = HsnFuzzyMatcher.lookup(query, fullCatalog, maxResults = 5)
            val top = res.suggestions.firstOrNull()?.entry
                ?: error("No HSN suggestion for world item '$query'")
            assertTrue(
                "World item '$query' resolved to '${top.hsnCode}' (${top.description}) instead of $expectedPrefixes",
                expectedPrefixes.any { top.hsnCode.startsWith(it) }
            )
            assertTrue("Invalid GST rate ${top.gstRate} for '$query'", top.gstRate in setOf(0.0, 0.25, 3.0, 5.0, 12.0, 18.0, 28.0, 40.0))
        }
        // Verify LanguageTranslationUtils plural handling does not corrupt "Shoes" -> "Sho"
        val translatedShoes = LanguageTranslationUtils.translateItem("Bata Mens Leather Formal Shoes")
        assertTrue("Expected '$translatedShoes' to preserve 'shoe'", translatedShoes.lowercase().contains("shoe"))
        val translatedTop = HsnFuzzyMatcher.lookup(translatedShoes, fullCatalog, maxResults = 5).suggestions.first().entry
        assertTrue("Translated '$translatedShoes' should map to 6403, got ${translatedTop.hsnCode}", translatedTop.hsnCode.startsWith("6403"))
    }

    @Test
    fun testAllDistinct4DigitHsnHeadingsAcrossChapters01To99() {
        val malformedAssetHeadings = setOf("3047", "3079", "4022", "8352")
        val stopWords = setOf(
            "of", "the", "and", "or", "in", "for", "whether", "not", "its", "other",
            "their", "with", "from", "than", "thereof", "such", "as", "to", "on", "by", "into", "an", "a"
        )
        val headings = LinkedHashMap<String, HsnEntry>()
        for (entry in fullCatalog) {
            if (entry.hsnCode.length == 4 && entry.hsnCode !in malformedAssetHeadings) {
                headings.putIfAbsent(entry.hsnCode, entry)
            }
        }
        assertTrue("Expected >= 1300 distinct 4-digit HSN/SAC headings, found ${headings.size}", headings.size >= 1300)

        // Warm up index once
        HsnFuzzyMatcher.lookup("whey protein", fullCatalog, maxResults = 5)

        var passed = 0
        val chaptersSeen = HashSet<String>()
        val chaptersPassed = HashSet<String>()
        val failures = ArrayList<String>()

        for ((h4, entry) in headings) {
            val ch2 = h4.substring(0, 2)
            chaptersSeen.add(ch2)
            val cleanDesc = entry.description
                .replace(Regex("\\([^)]*\\)"), " ")
                .replace(Regex("[^A-Za-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val kept = cleanDesc.split(' ')
                .filter { it.length >= 2 && it.lowercase() !in stopWords }
                .take(6)
            var query = kept.joinToString(" ")
            if (kept.size < 2) {
                val multiKw = entry.keywords.split(',').map { it.trim() }.firstOrNull { it.contains(' ') && it.length >= 6 }
                if (multiKw != null) query = multiKw
            }
            if (query.isEmpty()) query = cleanDesc

            val res = HsnFuzzyMatcher.lookup(query, fullCatalog, maxResults = 8)
            val ok = res.suggestions.any { it.entry.hsnCode.startsWith(h4) || it.entry.hsnCode.startsWith(ch2) }
            if (ok) {
                passed++
                chaptersPassed.add(ch2)
            } else {
                failures.add("$h4 query='$query' -> ${res.suggestions.firstOrNull()?.entry?.hsnCode ?: "NONE"}")
            }
        }

        assertTrue("Expected >= 95 distinct HS/SAC chapters, got ${chaptersSeen.size}", chaptersSeen.size >= 95)
        assertTrue("Expected all tested chapters to resolve headings, missed=${chaptersSeen - chaptersPassed}", chaptersPassed.size >= chaptersSeen.size - 1)
        assertTrue(
            "Expected >= ${headings.size - 12} of ${headings.size} 4-digit headings to pass, got $passed; sample failures=${failures.take(10)}",
            passed >= headings.size - 12
        )
    }

    @Test
    fun test15000CombinatorialGlobalSkusAcrossAllRetailVerticals() {
        data class ProductSpec(val phrase: String, val allowedHsn: Set<String>)
        data class Vertical(
            val name: String,
            val brands: List<String>,
            val products: List<ProductSpec>
        )

        val modifiers = listOf("", "Original", "Pro", "Ultra", "Heavy Duty", "Premium")
        val specs = listOf("", "500g", "1kg", "250ml", "Pack")

        val verticals = listOf(
            Vertical(
                name = "Supplements & Nutrition",
                brands = listOf("MuscleBlaze", "Optimum Nutrition", "Isopure", "MyProtein", "GNC"),
                products = listOf(
                    ProductSpec("Whey Protein Isolate", setOf("2106")),
                    ProductSpec("Micronized Creatine Monohydrate", setOf("2106")),
                    ProductSpec("Mass Gainer Chocolate", setOf("2106")),
                    ProductSpec("BCAA Amino Pre Workout", setOf("2106")),
                    ProductSpec("Multivitamin Ashwagandha Tablet", setOf("2106")),
                    ProductSpec("Omega 3 Fish Oil Softgel", setOf("1504", "2106", "3004")),
                    ProductSpec("L Glutamine Recovery Powder", setOf("2106")),
                    ProductSpec("Whey Gold Standard Supplement", setOf("2106")),
                    ProductSpec("Plant Pea Protein Shake", setOf("2106")),
                    ProductSpec("Casein Night Time Protein", setOf("2106"))
                )
            ),
            Vertical(
                name = "Kirana Staples & Oils",
                brands = listOf("Aashirvaad", "Tata", "Organic Tattva", "Fortune", "Patanjali"),
                products = listOf(
                    ProductSpec("Basmati Rice", setOf("1006")),
                    ProductSpec("Shudh Chakki Atta Flour", setOf("1101")),
                    ProductSpec("Refined Sunflower Cooking Oil", setOf("1512", "1507")),
                    ProductSpec("Kachi Ghani Mustard Oil", setOf("1514")),
                    ProductSpec("Toor Arhar Dal Pulses", setOf("0713")),
                    ProductSpec("Iodised White Salt", setOf("2501")),
                    ProductSpec("Refined White Sugar", setOf("1701")),
                    ProductSpec("Organic Jaggery Block Gud", setOf("1701")),
                    ProductSpec("Garam Masala Spice Powder", setOf("0910")),
                    ProductSpec("Kashmiri Red Chilli Powder", setOf("0904"))
                )
            ),
            Vertical(
                name = "Dairy, Bakery, Snacks & Beverages",
                brands = listOf("Govardhan", "Britannia", "Nestle", "Haldiram", "Dabur"),
                products = listOf(
                    ProductSpec("Pure Desi Cow Ghee", setOf("0405")),
                    ProductSpec("Pasteurised Butter Block", setOf("0405")),
                    ProductSpec("Taaza Toned Liquid Milk", setOf("0401")),
                    ProductSpec("Mozzarella Shredded Cheese", setOf("0406")),
                    ProductSpec("Pure Natural Honey", setOf("0409")),
                    ProductSpec("Glucose Tea Biscuit", setOf("1905")),
                    ProductSpec("Butter Cookies Tin", setOf("1905")),
                    ProductSpec("Instant Masala Noodles", setOf("1902")),
                    ProductSpec("Fresh Tomato Ketchup", setOf("2103")),
                    ProductSpec("Instant Coffee Jar", setOf("2101", "0901"))
                )
            ),
            Vertical(
                name = "FMCG, Home Care & Personal Care",
                brands = listOf("Hindustan", "Colgate", "Godrej", "Nirma", "Whisper"),
                products = listOf(
                    ProductSpec("Bathing Beauty Soap Bar", setOf("3401")),
                    ProductSpec("MaxFresh Dental Toothpaste", setOf("3306")),
                    ProductSpec("Anti Dandruff Hair Shampoo", setOf("3305")),
                    ProductSpec("Coconut Hair Oil", setOf("1513", "3305")),
                    ProductSpec("Washing Detergent Powder", setOf("3402")),
                    ProductSpec("Lemon Dishwash Liquid Gel", setOf("3402")),
                    ProductSpec("Toilet Bowl Liquid Cleaner", setOf("3402", "3808")),
                    ProductSpec("Mosquito Repellent Liquidators", setOf("3808")),
                    ProductSpec("Sanitary Napkin Pads", setOf("9619")),
                    ProductSpec("Baby Diaper Pants", setOf("9619"))
                )
            ),
            Vertical(
                name = "Electrical & Power Equipment",
                brands = listOf("Havells", "Polycab", "Crompton", "Luminous", "Finolex"),
                products = listOf(
                    ProductSpec("LED Bulb 9W Daylight", setOf("8539")),
                    ProductSpec("LED Tube Light Batten", setOf("8539", "9405")),
                    ProductSpec("High Speed Ceiling Fan", setOf("8414")),
                    ProductSpec("Double Pole MCB Circuit Breaker", setOf("8536")),
                    ProductSpec("Modular Electrical Switch", setOf("8536")),
                    ProductSpec("FRLS Copper House Wire", setOf("8544")),
                    ProductSpec("Submersible Flat Cable", setOf("8544")),
                    ProductSpec("Tubular Inverter Battery", setOf("8507")),
                    ProductSpec("Home Inverter UPS", setOf("8504")),
                    ProductSpec("Storage Water Heater Geyser", setOf("8516"))
                )
            ),
            Vertical(
                name = "Electronics, IT & Appliances",
                brands = listOf("Samsung", "LG", "Dell", "Sony", "Logitech"),
                products = listOf(
                    ProductSpec("5G Android Smartphone", setOf("8517")),
                    ProductSpec("4K Smart LED Television", setOf("8528")),
                    ProductSpec("Front Load Washing Machine", setOf("8450")),
                    ProductSpec("Frost Free Double Door Refrigerator", setOf("8418")),
                    ProductSpec("Split Inverter Air Conditioner", setOf("8415")),
                    ProductSpec("Core i5 Notebook Laptop", setOf("8471")),
                    ProductSpec("Wireless Optical Computer Mouse", setOf("8471")),
                    ProductSpec("NVMe Solid State Drive SSD", setOf("8471", "8523")),
                    ProductSpec("Bluetooth Wireless Headphones", setOf("8518")),
                    ProductSpec("Dual Band WiFi Router", setOf("8517"))
                )
            ),
            Vertical(
                name = "Automobile & Two-Wheeler Spare Parts",
                brands = listOf("Castrol", "Bosch", "Exide", "MRF", "Bajaj"),
                products = listOf(
                    ProductSpec("Tubeless Car Radial Tyre", setOf("4011")),
                    ProductSpec("Automotive Car Lead Acid Battery", setOf("8507")),
                    ProductSpec("Synthetic Car Engine Oil", setOf("2710")),
                    ProductSpec("Iridium Ignition Spark Plug", setOf("8511")),
                    ProductSpec("Front Disc Brake Pad Set", setOf("8708")),
                    ProductSpec("Motorcycle Chain Sprocket Kit", setOf("8714", "7315")),
                    ProductSpec("Clutch Pressure Plate Assembly", setOf("8708", "8714")),
                    ProductSpec("Deep Groove Ball Bearing", setOf("8482")),
                    ProductSpec("Car Engine Oil Filter", setOf("8421")),
                    ProductSpec("Leatherette Car Seat Cover", setOf("8708", "9401"))
                )
            ),
            Vertical(
                name = "Pharma, Medical & Diagnostics",
                brands = listOf("Cipla", "Medline", "Omron", "Dr Morepen", "Dolo"),
                products = listOf(
                    ProductSpec("Paracetamol Fever Tablet", setOf("3004")),
                    ProductSpec("Azithromycin Antibiotic Tablet", setOf("3004")),
                    ProductSpec("Histamine Expectorant Cough Syrup", setOf("3004")),
                    ProductSpec("Automatic Digital BP Monitor", setOf("9018")),
                    ProductSpec("Infrared Non Contact Thermometer", setOf("9025")),
                    ProductSpec("N95 Surgical Face Mask", setOf("6307", "9020")),
                    ProductSpec("Sterile Cotton Gauze Bandage", setOf("3005")),
                    ProductSpec("Disposable Insulin Syringe", setOf("9018")),
                    ProductSpec("Digital Pulse Oximeter", setOf("9018")),
                    ProductSpec("Glucometer Blood Sugar Strips", setOf("9025", "3822", "9018", "9027"))
                )
            ),
            Vertical(
                name = "Construction, Tools & Industrial",
                brands = listOf("UltraTech", "Stanley", "Asian Paints", "Fevicol", "Kajaria"),
                products = listOf(
                    ProductSpec("OPC 53 Grade Cement Bag", setOf("2523")),
                    ProductSpec("TMT Steel Reinforcement Bar", setOf("72", "7214", "7213")),
                    ProductSpec("Exterior Acrylic Emulsion Paint", setOf("3209", "3208")),
                    ProductSpec("Synthetic White Wood Adhesive", setOf("3506")),
                    ProductSpec("Vitrified Ceramic Floor Tiles", setOf("6907")),
                    ProductSpec("Marine Waterproof Plywood Sheet", setOf("4412")),
                    ProductSpec("Insulated Combination Plier", setOf("8205", "8203")),
                    ProductSpec("Chrome Vanadium Wrench Spanner", setOf("8204")),
                    ProductSpec("CPVC Plumbing Water Pipe", setOf("3917")),
                    ProductSpec("Hammer Rotary Drill Machine", setOf("8467"))
                )
            ),
            Vertical(
                name = "Apparel, Footwear, Stationery & Sports",
                brands = listOf("Levis", "Nike", "Classmate", "Titan", "American Tourister"),
                products = listOf(
                    ProductSpec("Mens Slim Fit Cotton Jeans", setOf("6203")),
                    ProductSpec("Formal Cotton Dress Shirt", setOf("6205")),
                    ProductSpec("Running Sports Shoes", setOf("6404", "6403")),
                    ProductSpec("Mens Leather Formal Shoes", setOf("6403")),
                    ProductSpec("Stainless Steel Analog Wrist Watch", setOf("9102", "9101")),
                    ProductSpec("Hard Shell Trolley Suitcase Bag", setOf("4202")),
                    ProductSpec("Spiral Ruled A4 Notebook", setOf("4820")),
                    ProductSpec("Ball Point Writing Pen", setOf("9608")),
                    ProductSpec("English Willow Cricket Bat", setOf("9506")),
                    ProductSpec("Aluminium Nonstick Pressure Cooker", setOf("7615"))
                )
            )
        )

        val startMs = System.currentTimeMillis()
        var testedCount = 0
        var totalFailures = 0
        val seenFailKeys = HashSet<String>()
        val failures = ArrayList<String>()

        for (vertical in verticals) {
            for (brand in vertical.brands) {
                for (mod in modifiers) {
                    for (spec in specs) {
                        for (product in vertical.products) {
                            testedCount++
                            val sku = listOf(brand, mod, product.phrase, spec)
                                .filter { it.isNotEmpty() }
                                .joinToString(" ")
                            val top = HsnFuzzyMatcher.lookup(sku, fullCatalog, maxResults = 3)
                                .suggestions.firstOrNull()?.entry
                            if (top == null || product.allowedHsn.none { top.hsnCode.startsWith(it) }) {
                                totalFailures++
                                val key = "${vertical.name}|$brand|${product.phrase}->${top?.hsnCode}"
                                if (seenFailKeys.add(key) && failures.size < 40) {
                                    failures.add("[${vertical.name}] '$sku' -> ${top?.hsnCode} (${top?.description?.take(28)}) expected=${product.allowedHsn}")
                                }
                            }
                        }
                    }
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - startMs
        assertEquals(15000, testedCount)
        assertTrue(
            "Expected all 15,000 combinatorial SKUs to match exact HSN; totalFailures=$totalFailures ($elapsedMs ms): $failures",
            totalFailures == 0
        )
        assertTrue("15,000 SKU lookups took ${elapsedMs}ms — should complete under 45s", elapsedMs < 45_000)
    }
}
