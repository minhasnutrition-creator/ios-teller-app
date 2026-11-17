package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.data.entities.HsnEntry

/**
 * Single source of truth for the offline HSN code + GST rate dataset bundled with the app.
 *
 * Rates reflect the GST 2.0 rate rationalisation announced at the 56th GST Council meeting,
 * effective 22 September 2025 (CBIC Notification No. 9/2025-Central Tax (Rate)). The old
 * five-slab structure (0%, 5%, 12%, 18%, 28%) was collapsed to three core slabs — 0%, 5%, 18% —
 * with a 40% rate for select luxury/sin goods, and narrow legacy rates (1.5%, 3%) kept for
 * diamonds/precious metals. Pan masala & tobacco (cigarettes, gutkha, chewing tobacco) sat on
 * the pre-reform 28% rate on a transitional basis until the compensation-cess loan obligations
 * were discharged; the cess was abolished and these moved to 40% effective 1 Feb 2026 (replaced
 * for cigarettes by a per-stick Additional Excise Duty on top of GST). Bidi was carved out to
 * 18% rather than following cigarettes/pan masala to 40%. There is no longer a 28% slab anywhere
 * in this dataset — treat any 28% entry surfacing from an external/legacy source as stale.
 *
 * This is a hand-curated reference covering the HSN chapters most relevant to a small Indian
 * retail/wholesale/kirana business — not a line-by-line mirror of the full ~1,300+ item official
 * notification (that level of per-code precision would need the actual CBIC schedule text, which
 * isn't available as a clean bulk file; scraping a third-party aggregator wasn't the ask either).
 * Where the source material gave conflicting or price-conditional rates (e.g. apparel/footwear
 * at 5% up to Rs 2,500/piece, 18% above; a packaged vs. loose distinction for staples), the more
 * common small-business case is used as the keyword-matched default, with the alternate rate
 * still present as a second entry so it's reachable.
 *
 * This is the ONLY place default/offline HSN data should be defined. Both HSNDataManager
 * (embedded fallback + category browser) and HSNSyncRepository (first-run pre-population) read
 * from this list so the app never again ends up with two different offline datasets that
 * disagree with each other (that mismatch was the root cause of common items like "apple"
 * failing to auto-populate).
 *
 * GST rates are legislated and do change over time. Treat this as a strong offline default,
 * not a guarantee — encourage users to verify unusual/high-value items against the current
 * CBIC rate schedule, especially for anything not covered here.
 */
object HsnPresetData {

    /**
     * Bump this whenever gstRate/description values in [getEntries] change. HSNDataManager
     * compares this against a stored preference to force a re-sync (and re-run of
     * sanitizeHsnEntry) on already-installed devices, even when their local HSN table already
     * has >= the current row count — otherwise rate corrections made here would never reach a
     * device that already has data loaded (see ensureDefaultHSNLoaded()).
     *
     * v2 (2026-07-21): fixed water pump (8413) and paper cup/plate (4823) — both were sitting at
     * the 5% rate under the 18% section by mistake. Split the tobacco entry to reflect the
     * 1 Feb 2026 change: cigarettes/pan masala/gutkha now 40%, bidi carved out to 18%.
     */
    /** Bump whenever entries or rates change. See ensureDefaultHSNLoaded(). */
    const val DATA_VERSION = 17
    // v16 (2026-10): added Clinical Laboratory Tests (0% exempt SAC 9993), Laboratory Supplies & Reagents (18% HSN 3822/9018/3926/7017/9027/9011), and Paints & Allied Materials (Asian Paints, Berger, Nerolac, Dulux, Wall Putty, Primers, Waterproofing, Adhesives).
    // v15 (2026-10): added exhaustive catalogs for Mobile Phones, Watches, Sunglasses, Headphones, Restaurant items, and 50+ supplement brands.
    // v14 (2026-10): added all domestic and international supplement brands (50+ brands) and exhaustive Kirana catalog.
    // v13 (2026-10): added comprehensive bodybuilding & fitness supplement brands (Mutant, ON, MB, Dymatize, etc.) to HsnCommonItems under HSN 2106 (18% GST).
    // v12 (2026-10): added common Furniture (table 9403, chair 9401) and Eraser (4016) aliases to HsnCommonItems.
    // v11 (2026-10): ensure all entries from getEntries and getFallbackEntries are sanitized through
    // sanitizeHsnEntry and correctMisPricedHeading; corrected legacy personal care rates to 18%.
    // v10 (2026-10): corrected fresh bananas (08039010) to 0% exempt rate in full directory.
    // v9 (2026-09): corrected personal care / home care / ice cream / candy — soap 3401, shampoo
    // & hair oil 3305, toothpaste 3306, detergent & dishwash 3402, ice cream 2105, sugar
    // confectionery 1704 → 18%.
    // v8 (2026-09): corrected mis-priced headings via correctMisPricedHeading — books 4901→0%,
    // chocolate 1806 / supplements 2106 / chargers 8504 / vehicle parts 8714 / tyres 4011 /
    // engine oil 2710 → 18%.
    // v7 (2026-09): added HsnCommonItems brand/common-name aliases (Splendor→motorcycle,
    // Cadbury Dairy Milk→chocolate, etc.) merged into getEntries().
    // v6 (2026-08-14): full GST 2.0 goods directory — 21,517 codes (the complete CBIC tariff) loaded
    // from the bundled asset app/src/main/assets/hsn_codes.txt. This is now the primary source; the
    // hand-curated list below is kept only as an offline fallback if the asset is missing/parseable.

    @Volatile
    private var cachedEntries: List<HsnEntry>? = null

    @Volatile
    private var cachedFallback: List<HsnEntry>? = null

    /**
     * Primary offline HSN dataset — the full 21,517-code GST 2.0 goods directory, loaded from the
     * bundled asset, plus the HsnCommonItems brand/common-name aliases. Falls back to
     * [legacyEntries] if the asset can't be read or parsed.
     */
    fun getEntries(context: Context): List<HsnEntry> {
        cachedEntries?.let { return it }
        synchronized(this) {
            cachedEntries?.let { return it }
            val common = HsnCommonItems.entries()
            val raw = try {
                common + loadFullHsnAsset(context)
            } catch (e: Exception) {
                common + legacyEntries()
            }
            val sanitized = raw.map { sanitizeHsnEntry(it) }
            cachedEntries = sanitized
            return sanitized
        }
    }

    fun getFallbackEntries(): List<HsnEntry> {
        cachedFallback?.let { return it }
        synchronized(this) {
            cachedFallback?.let { return it }
            val sanitized = (HsnCommonItems.entries() + legacyEntries()).map { sanitizeHsnEntry(it) }
            cachedFallback = sanitized
            return sanitized
        }
    }

    fun correctMisPricedHeading(entry: HsnEntry): HsnEntry {
        val corrected = when {
            entry.hsnCode.startsWith("4901") -> 0.0   // printed books — nil-rated
            entry.hsnCode.startsWith("4820") -> {
                val text = (entry.description + " " + entry.keywords).lowercase()
                if (text.contains("notebook") || text.contains("exercise book") || text.contains("copy")) {
                    0.0
                } else if (listOf("diary", "journal", "planner", "appointment").any { text.contains(it) }) {
                    18.0
                } else {
                    entry.gstRate
                }
            }
            entry.hsnCode.startsWith("1806") -> 18.0  // chocolate
            entry.hsnCode.startsWith("2106") -> {
                val text = (entry.description + " " + entry.keywords).lowercase()
                if (listOf("namkeen", "bhujia", "mixture", "snack", "chabena", "sev", "soya", "soyabean", "nutrela").any { text.contains(it) }) {
                    5.0
                } else {
                    18.0
                }
            }
            entry.hsnCode.startsWith("8504") -> 18.0  // chargers, adapters, transformers, inverters
            entry.hsnCode.startsWith("8708") -> {
                val text = (entry.description + " " + entry.keywords).lowercase()
                if (entry.description.lowercase().contains("tractor")) 5.0 else 18.0
            }
            entry.hsnCode.startsWith("8714") -> 18.0  // vehicle parts & accessories
            entry.hsnCode.startsWith("4011") -> 18.0  // tyres
            entry.hsnCode.startsWith("2710") -> 18.0  // lubricants / engine oil
            entry.hsnCode.startsWith("3401") -> 18.0  // soap
            entry.hsnCode.startsWith("3305") -> 18.0  // shampoo / hair oil
            entry.hsnCode.startsWith("3306") -> 18.0  // toothpaste / oral hygiene
            entry.hsnCode.startsWith("3307") -> 18.0  // shaving cream / cosmetics
            entry.hsnCode.startsWith("3402") -> 18.0  // detergent / dishwash
            entry.hsnCode.startsWith("2105") -> 18.0  // ice cream
            entry.hsnCode.startsWith("1704") -> 18.0  // sugar confectionery / candy
            else -> null
        }
        return if (corrected != null && entry.gstRate != corrected) entry.copy(gstRate = corrected) else entry
    }

    fun sanitizeHsnEntry(entry: HsnEntry): HsnEntry {
        val entry = correctMisPricedHeading(entry)

        if (entry.hsnCode.startsWith("8711")) {
            var correctGstRate = entry.gstRate
            var finalDescription = entry.description
            val descLower = entry.description.lowercase()

            if (entry.hsnCode.startsWith("871160") || descLower.contains("electric") || descLower.contains("ev ")) {
                correctGstRate = 5.0
                if (!finalDescription.contains("5%")) finalDescription = "$finalDescription (Electric, GST 5%)"
            } else if (descLower.contains("exceeding 350") || descLower.contains("above 350") || 
                       descLower.contains("over 350") || descLower.contains("> 350") || 
                       descLower.contains("350cc+") || entry.hsnCode.startsWith("87114") || 
                       entry.hsnCode.startsWith("87115")) {
                correctGstRate = 40.0
                if (!finalDescription.contains("40%")) finalDescription = "$finalDescription (Engine > 350cc, GST 40%)"
            } else {
                correctGstRate = 18.0
                if (!finalDescription.contains("18%")) finalDescription = "$finalDescription (Engine <= 350cc, GST 18%)"
            }
            return entry.copy(gstRate = correctGstRate, description = finalDescription)
        }

        // GST 2.0 (effective 22 Sep 2025): the 28% slab was abolished.
        // ~90% of those goods dropped to 18%; sin & luxury goods rose to 40%.
        if (entry.gstRate == 28.0) {
            val text = (entry.description + " " + entry.keywords + " " + entry.hsnCode).lowercase()
            val isBidi = listOf("bidi", "biri", "beedi").any { text.contains(it) }
            val sinOrLuxury = !isBidi && listOf(
                "tobacco", "cigarette", "cigar", "pan masala", "gutkha", "hookah", "zarda", "khaini",
                "aerated", "carbonated", "soft drink", "cola", "soda", "energy drink",
                "gambling", "casino", "betting", "lottery", "yacht", "aircraft", "luxury", "suv"
            ).any { text.contains(it) }
            val newRate = if (sinOrLuxury) 40.0 else 18.0
            val tag = if (newRate == 40.0) "GST 40%" else "GST 18%"
            val newDesc = if (entry.description.contains("%")) entry.description else entry.description + " (" + tag + ")"
            return entry.copy(gstRate = newRate, description = newDesc)
        }
        return entry
    }

    private fun loadFullHsnAsset(context: Context): List<HsnEntry> =
        context.assets.open("hsn_codes.txt").bufferedReader().useLines { parseHsnLines(it) }

    internal fun parseHsnLines(lines: Sequence<String>): List<HsnEntry> {
        val entries = mutableListOf<HsnEntry>()
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('|')
            if (parts.size < 4) return@forEach
            val code = parts[0].trim()
            val description = parts[1].trim()
            val rate = parts[2].trim().toDoubleOrNull() ?: 0.0
            val category = parts[3].trim().ifBlank { "General" }
            if (code.isBlank() || description.isBlank()) return@forEach
            val extraKeywords = if (parts.size >= 5) parts[4].trim() else ""
            val mergedKeywords = if (extraKeywords.isNotBlank()) {
                val auto = generateKeywords(description)
                if (auto.isNotBlank() && !extraKeywords.contains(auto)) "$extraKeywords, $auto" else extraKeywords
            } else {
                generateKeywords(description)
            }
            entries.add(
                HsnEntry(
                    hsnCode = code,
                    description = description,
                    keywords = mergedKeywords,
                    gstRate = rate,
                    category = category
                )
            )
        }
        return entries
    }

    private fun generateKeywords(description: String): String {
        val stopWords = setOf(
            "and", "or", "the", "of", "in", "on", "at", "to", "for", "with", "by",
            "not", "other", "whether", "their", "its", "than", "under", "them", "this", "that"
        )
        return description.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
            .joinToString(", ")
    }

    private fun legacyEntries(): List<HsnEntry> = listOf(

        // ================= 0% / Nil-rated =================

        // -- Fresh produce --
        HsnEntry(hsnCode = "0808", description = "Apples, fresh", keywords = "apple, apples, seb, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0803", description = "Bananas, fresh", keywords = "banana, bananas, kela, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0804", description = "Mangoes, fresh", keywords = "mango, mangoes, aam, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0804", description = "Guavas, fresh", keywords = "guava, guavas, amrud, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0804", description = "Pineapples, fresh", keywords = "pineapple, pineapples, ananas, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0805", description = "Oranges and other citrus fruit, fresh", keywords = "orange, oranges, citrus, mausambi, lemon, nimbu, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0806", description = "Grapes, fresh", keywords = "grape, grapes, angoor, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0807", description = "Watermelons and papayas, fresh", keywords = "watermelon, watermelons, papaya, papayas, tarbooj, melon, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0809", description = "Apricots, plums and peaches, fresh", keywords = "apricot, plum, plums, peach, peaches, aadu, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0810", description = "Strawberries and other fresh fruit (kiwi, pomegranate, litchi)", keywords = "strawberry, strawberries, berry, berries, kiwi, pomegranate, anar, litchi, lychee, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0801", description = "Coconuts, fresh", keywords = "coconut, coconuts, nariyal, fresh", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0802", description = "Other fresh nuts (almonds, walnuts, pistachios), unshelled", keywords = "almond, walnut, pistachio, fresh nuts, badam", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0701", description = "Potatoes, fresh or chilled", keywords = "potato, potatoes, aloo, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0702", description = "Tomatoes, fresh or chilled", keywords = "tomato, tomatoes, tamatar, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0703", description = "Onions, shallots and garlic, fresh", keywords = "onion, onions, pyaz, garlic, lahsun, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0704", description = "Cabbages and cauliflowers, fresh", keywords = "cabbage, cabbages, cauliflower, cauliflowers, gobi, patta gobi, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0706", description = "Carrots, turnips and radishes, fresh", keywords = "carrot, carrots, gajar, radish, mooli, turnip, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0707", description = "Cucumbers and gherkins, fresh", keywords = "cucumber, cucumbers, kheera, gherkin, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0708", description = "Fresh leguminous vegetables (peas, beans)", keywords = "peas, matar, beans fresh, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Other fresh vegetables (spinach, brinjal, capsicum)", keywords = "spinach, palak, brinjal, baingan, capsicum, shimla mirch, greens, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0910", description = "Fresh ginger and turmeric", keywords = "ginger, adrak, turmeric, haldi fresh", gstRate = 0.0, category = "Fruits & Vegetables"),

        // -- Meat, fish & dairy (fresh) --
        HsnEntry(hsnCode = "0201", description = "Fresh or chilled meat (mutton, beef, pork)", keywords = "meat, mutton, gosht, beef, pork, fresh meat", gstRate = 0.0, category = "Meat & Fish"),
        HsnEntry(hsnCode = "0207", description = "Fresh or chilled chicken and poultry", keywords = "chicken, murgi, poultry, fresh chicken", gstRate = 0.0, category = "Meat & Fish"),
        HsnEntry(hsnCode = "0302", description = "Fresh or chilled fish", keywords = "fish, machli, fresh fish", gstRate = 0.0, category = "Meat & Fish"),
        HsnEntry(hsnCode = "0401", description = "Milk, fresh, pasteurised or UHT (loose/unbranded)", keywords = "milk, doodh, dairy, dudh", gstRate = 0.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0403", description = "Curd, lassi and buttermilk, non-packaged", keywords = "curd, dahi, lassi, buttermilk, chaas", gstRate = 0.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0406", description = "Paneer / chena, fresh or packaged", keywords = "paneer, chena, cottage cheese, fresh", gstRate = 0.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0407", description = "Eggs, fresh", keywords = "egg, eggs, anda, fresh", gstRate = 0.0, category = "Dairy Products"),

        // -- Grains, flours & staples --
        HsnEntry(hsnCode = "1006", description = "Rice, unbranded/loose", keywords = "rice, chawal, biryani rice, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1001", description = "Wheat, unbranded/loose", keywords = "wheat, gehun, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1003", description = "Barley, unbranded/loose", keywords = "barley, jau, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1005", description = "Maize / corn, unbranded/loose", keywords = "maize, corn, makka, bhutta, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1008", description = "Millet, ragi, jowar and bajra, unbranded/loose", keywords = "millet, ragi, jowar, bajra, sorghum, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "0713", description = "Dried pulses / dal, unbranded/loose (moong, chana, urad, masoor)", keywords = "dal, pulses, moong, chana, urad, masoor, arhar, toor, lentil, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1101", description = "Atta / wheat flour, unbranded/loose", keywords = "atta, flour, maida, gehun ka atta, unbranded", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "2501", description = "Salt, including table salt", keywords = "salt, namak, table salt", gstRate = 0.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "1905", description = "Roti, chapati, paratha and pizza bread", keywords = "roti, chapati, paratha, phulka, pizza base, khakhra, bread", gstRate = 0.0, category = "Bakery"),
        HsnEntry(hsnCode = "1904", description = "Papad", keywords = "papad, papadum", gstRate = 0.0, category = "Food Products"),

        // -- Books, stationery & education --
        HsnEntry(hsnCode = "4901", description = "Printed books, novels, textbooks", keywords = "book, books, textbook, novel, printed book", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4902", description = "Newspapers and journals", keywords = "newspaper, newspapers, journal, magazine", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4820", description = "Exercise books and notebooks", keywords = "notebook, notebooks, exercise book, copy, register, khata", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "9609", description = "Pencils, crayons, pastels and sharpeners", keywords = "pencil, pencils, crayon, crayons, pastel, sharpener, slate pencil", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4016", description = "Eraser", keywords = "eraser, erasers, rubber eraser", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4905", description = "Maps, charts and globes", keywords = "map, maps, chart, globe, atlas", gstRate = 0.0, category = "Books & Stationery"),

        // -- Health, hygiene & misc nil items --
        HsnEntry(hsnCode = "9021", description = "Hearing aids and parts", keywords = "hearing aid, hearing aids", gstRate = 0.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9619", description = "Sanitary napkins and towels", keywords = "sanitary napkin, sanitary pad, sanitary towel", gstRate = 0.0, category = "Personal Care"),
        HsnEntry(hsnCode = "4014", description = "Contraceptives and condoms", keywords = "condom, contraceptive", gstRate = 0.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "6702", description = "Puja items (rudraksha, kalava, kumkum, sindoor, bindi, agarbatti holder)", keywords = "rudraksha, kalava, kumkum, sindoor, bindi, puja samagri, roli", gstRate = 0.0, category = "Religious Items"),
        HsnEntry(hsnCode = "6810", description = "Rakhi (non-precious)", keywords = "rakhi, raksha bandhan", gstRate = 0.0, category = "Religious Items"),
        HsnEntry(hsnCode = "6913", description = "Idols and deities (clay, stone, marble, wood)", keywords = "idol, idols, murti, deity, bhagwan ki murti, statue", gstRate = 0.0, category = "Religious Items"),
        HsnEntry(hsnCode = "9603", description = "Traditional hand-made broom (sarkanda)", keywords = "broom, jhadu, sarkanda broom, hand broom", gstRate = 0.0, category = "Cleaning"),
        HsnEntry(hsnCode = "4401", description = "Firewood and charcoal", keywords = "firewood, charcoal, lakdi, koyla", gstRate = 0.0, category = "Fuels"),

        // ================= 5% slab =================

        // -- Sweeteners, tea, coffee, spices --
        HsnEntry(hsnCode = "1701", description = "Sugar and jaggery", keywords = "sugar, chini, jaggery, gur, sucrose, sweet", gstRate = 5.0, category = "Sugars"),
        HsnEntry(hsnCode = "0902", description = "Tea", keywords = "tea, chai, leaf, beverage", gstRate = 5.0, category = "Beverages"),
        HsnEntry(hsnCode = "0901", description = "Coffee", keywords = "coffee, cafe, beans, beverage", gstRate = 5.0, category = "Beverages"),
        HsnEntry(hsnCode = "0904", description = "Pepper and chilli powder", keywords = "pepper, kali mirch, chilli powder, lal mirch, spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0906", description = "Turmeric powder and cinnamon", keywords = "turmeric powder, haldi powder, cinnamon, dalchini, spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0909", description = "Cumin, coriander and mustard seeds", keywords = "cumin, jeera, coriander, dhaniya, mustard, sarson, spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0908", description = "Cardamom, cloves and nutmeg", keywords = "cardamom, elaichi, clove, laung, nutmeg, jaiphal, spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "2103", description = "Sauces, ketchup and condiments", keywords = "sauce, ketchup, chutney powder, condiment", gstRate = 5.0, category = "Food Products"),

        // -- Dairy, oils & staples (packaged) --
        HsnEntry(hsnCode = "0405", description = "Butter, ghee and dairy spreads", keywords = "butter, makhan, ghee, dairy fat, spread", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0406", description = "Cheese (packaged)", keywords = "cheese, packaged cheese", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0403", description = "Yogurt, packaged", keywords = "yogurt, yoghurt, flavoured curd, packaged curd", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0402", description = "Condensed and powdered milk", keywords = "condensed milk, milk powder, powdered milk", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0409", description = "Honey, packaged", keywords = "honey, shahad, packaged honey", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "1101", description = "Atta / flour, packaged branded", keywords = "packaged atta, branded flour, maida, sooji, rava, besan", gstRate = 5.0, category = "Flour"),
        HsnEntry(hsnCode = "1512", description = "Cooking oil (sunflower, mustard, groundnut, soyabean)", keywords = "oil, cooking oil, tel, sarson oil, sunflower oil, groundnut oil, soyabean oil", gstRate = 5.0, category = "Cooking Oils"),

        // -- Prepared / packaged foods --
        HsnEntry(hsnCode = "1905", description = "Biscuits, bread, cakes and bakery items", keywords = "biscuit, biscuits, bread, cake, toast, rusk, bakery", gstRate = 5.0, category = "Bakery"),
        HsnEntry(hsnCode = "1902", description = "Maggi, pasta, noodles and macaroni", keywords = "maggi, pasta, noodles, macaroni, vermicelli, sevaiyan", gstRate = 5.0, category = "Noodles"),
        HsnEntry(hsnCode = "1904", description = "Cornflakes and breakfast cereal", keywords = "cornflakes, breakfast cereal, muesli, oats", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2106", description = "Namkeen, bhujia, mixtures and snack food (packaged)", keywords = "namkeen, bhujia, mixture, snacks, chips, packaged food", gstRate = 5.0, category = "Food Preparations"),
        HsnEntry(hsnCode = "2007", description = "Jam, jelly and fruit preserves", keywords = "jam, jelly, marmalade, fruit jam, pickle, achar", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2009", description = "Fruit juice and non-aerated beverages", keywords = "juice, fruit juice, non-aerated drink", gstRate = 5.0, category = "Beverages"),
        HsnEntry(hsnCode = "2201", description = "Packaged drinking water / mineral water (non-aerated)", keywords = "packaged water, mineral water, mineral water bottle, drinking water bottle, bottled water, bisleri, bisleri water bottle", gstRate = 5.0, category = "Beverages"),
        HsnEntry(hsnCode = "0801", description = "Dry fruits (cashew, almond, dried coconut)", keywords = "cashew, kaju, almond, badam, dryfruit, dry fruit", gstRate = 5.0, category = "Dry Fruits"),

        // -- Personal care & hygiene --
        HsnEntry(hsnCode = "3401", description = "Soap and soap bars", keywords = "soap, sabun, bathing soap, bar soap", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "3305", description = "Shampoo and hair oil", keywords = "shampoo, hair oil, hair care", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "3306", description = "Toothpaste and tooth brushes", keywords = "toothpaste, tooth brush, toothbrush, dant manjan", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "3307", description = "Shaving cream and shaving products", keywords = "shaving cream, razor foam, shaving", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "3605", description = "Matchbox / safety matches", keywords = "matchbox, matches, safety matches, machis", gstRate = 18.0, category = "Personal Care"),

        // -- Household, baby & agricultural --
        HsnEntry(hsnCode = "7323", description = "Steel utensils and kitchenware", keywords = "utensil, utensils, steel plate, steel bartan, kitchenware", gstRate = 5.0, category = "Utensils"),
        HsnEntry(hsnCode = "3924", description = "Feeding bottles and baby napkins/diapers", keywords = "feeding bottle, baby diaper, napkin, clinical diaper", gstRate = 5.0, category = "Baby Care"),
        HsnEntry(hsnCode = "8452", description = "Sewing machines and parts", keywords = "sewing machine, silai machine", gstRate = 5.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "Solar cooker", keywords = "solar cooker", gstRate = 5.0, category = "Appliances"),
        HsnEntry(hsnCode = "8541", description = "Solar panels / photovoltaic cells", keywords = "solar panel, solar plate, photovoltaic, solar cell", gstRate = 5.0, category = "Renewable Energy"),
        HsnEntry(hsnCode = "2711", description = "LPG domestic cylinder", keywords = "lpg, gas cylinder, cooking gas, domestic gas", gstRate = 5.0, category = "Fuels"),
        HsnEntry(hsnCode = "3101", description = "Organic fertilisers and manure", keywords = "organic fertiliser, organic fertilizer, manure, khaad, compost, vermicompost", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3102", description = "Urea and nitrogenous fertilisers", keywords = "urea, agricultural urea, neem coated urea, urea fertilizer, urea fertiliser, nitrogenous fertilizer", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3105", description = "DAP, NPK and complex fertilisers", keywords = "dap, diammonium phosphate, diammonium phosphate fertilizer, fertilizer bag, fertiliser bag, npk, complex fertiliser, fertilizer", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "4011", description = "Tractor and agricultural tyres", keywords = "tractor tyre, agri tyre, farm tyre", gstRate = 5.0, category = "Automotive"),
        HsnEntry(hsnCode = "8701", description = "Tractors", keywords = "tractor, tractors, farm vehicle", gstRate = 5.0, category = "Automotive"),
        HsnEntry(hsnCode = "6901", description = "Sand lime / clay bricks", keywords = "brick, bricks, sand lime brick, clay brick", gstRate = 5.0, category = "Mineral Products"),

        // -- Medical, optical & pharma --
        HsnEntry(hsnCode = "9004", description = "Spectacles and corrective goggles", keywords = "goggles, specs, spectacles, sunglasses, lens, corrective glasses", gstRate = 5.0, category = "Optical"),
        HsnEntry(hsnCode = "9025", description = "Thermometer and glucometer", keywords = "thermometer, glucometer, test strips, medical device", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "3005", description = "Bandages, first aid and dressings", keywords = "bandage, first aid, dressing, cotton roll, band aid", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9018", description = "Syringes and needles", keywords = "syringe, needle, injection", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "3002", description = "Vaccines", keywords = "vaccine, vaccines, immunisation", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "Medicines and pharmaceutical drugs (general)", keywords = "medicine, medicines, drug, medicine tablet, pharma, capsule, dawai", gstRate = 5.0, category = "Pharmaceuticals"),

        // -- Toys, sports & festive --
        HsnEntry(hsnCode = "9503", description = "Toys, balloons, board games (non-electronic)", keywords = "toy, toys, doll, balloon, board game, ludo, chess, carrom, kids", gstRate = 5.0, category = "Toys"),
        HsnEntry(hsnCode = "9506", description = "Sports goods (cricket bat, football, badminton racket)", keywords = "cricket bat, football, badminton, racket, sports goods, ball, bat", gstRate = 5.0, category = "Sports Goods"),
        HsnEntry(hsnCode = "9508", description = "Kite", keywords = "kite, kites, patang", gstRate = 5.0, category = "Toys"),
        HsnEntry(hsnCode = "3307", description = "Agarbatti / incense sticks", keywords = "agarbatti, incense, dhoop, incense stick", gstRate = 5.0, category = "Religious Items"),
        HsnEntry(hsnCode = "9603", description = "Plastic / packaged broom", keywords = "plastic broom, nylon broom, plastic jhadu, packaged broom", gstRate = 5.0, category = "Cleaning"),

        // -- Vehicles (concessional) --
        HsnEntry(hsnCode = "8712", description = "Bicycles and cycle parts", keywords = "bicycle, cycle, cycle parts", gstRate = 5.0, category = "Automotive"),
        HsnEntry(hsnCode = "871160", description = "Electric two-wheeler (EV scooter/bike)", keywords = "electric two wheeler, ev, e-scooter, e-bike, ev vehicle, battery scooter", gstRate = 5.0, category = "Automotive"),

        // -- Apparel, footwear & textiles (mass-market default) --
        HsnEntry(hsnCode = "6109", description = "Apparel and clothing (up to Rs 2,500 per piece)", keywords = "clothing, tshirt, t-shirt, shirt, apparel, wear, kapde, kurta, dress, saree, sari", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "6115", description = "Socks and innerwear", keywords = "socks, innerwear, undergarment, banian, vest", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "6403", description = "Footwear (up to Rs 2,500 per pair)", keywords = "footwear, shoes, sandal, chappal, slipper, jute", gstRate = 5.0, category = "Footwear"),
        HsnEntry(hsnCode = "5208", description = "Cotton fabric and textiles", keywords = "cotton, fabric, cloth, textile", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "5702", description = "Carpets and rugs", keywords = "carpet, rug, dari, floor mat textile", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6301", description = "Quilt, razai and blankets (up to Rs 2,500)", keywords = "quilt, razai, blanket, kambal", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6302", description = "Bedsheet and pillow cover", keywords = "bedsheet, bed sheet, pillow cover, linen", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6302", description = "Towel", keywords = "towel, tauliya, bath towel", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6303", description = "Curtain", keywords = "curtain, pardaa, window curtain", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6601", description = "Umbrella", keywords = "umbrella, chatri", gstRate = 5.0, category = "Apparel"),

        // ================= 18% slab =================

        // -- Electronics & appliances --
        HsnEntry(hsnCode = "8517", description = "Mobile phones, smartphones and network devices", keywords = "mobile, phone, smartphone, cell, mobile phone", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8517", description = "Mobile accessories (charger, cable, power bank, earphones)", keywords = "charger, cable, power bank, earphone, earphones, headphone, mobile cover, mobile case", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8471", description = "Computers, laptops and data processing machines", keywords = "computer, laptop, cpu, keyboard, pc, desktop", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8443", description = "Printer, scanner and photocopier", keywords = "printer, scanner, photocopier, xerox machine", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8517", description = "Router and networking equipment", keywords = "router, modem, wifi, networking device", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8525", description = "Camera and CCTV", keywords = "camera, cctv, dslr, security camera, webcam", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8518", description = "Speaker and headphone (standalone audio equipment)", keywords = "speaker, bluetooth speaker, sound box, audio system", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8528", description = "Television, monitors and projectors", keywords = "tv, television, led tv, monitor, projector", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8415", description = "Air conditioners", keywords = "ac, air conditioner, cooling, split ac", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8418", description = "Refrigerators and freezers", keywords = "fridge, refrigerator, freezer", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8450", description = "Washing machines and dishwashers", keywords = "washing machine, dishwasher, laundry machine", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8508", description = "Vacuum cleaners", keywords = "vacuum, cleaner, dust", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "Geysers, irons and toasters", keywords = "geyser, iron, water heater, toaster, electric kettle", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "Microwave oven, mixer grinder and induction cooktop", keywords = "microwave, mixer grinder, induction, oven, blender", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8507", description = "Battery (dry cell, inverter, automotive)", keywords = "battery, batteries, inverter battery, car battery, dry cell", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8504", description = "UPS and inverter", keywords = "ups, inverter, power backup", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8544", description = "Electrical wire and cable", keywords = "wire, cable, electrical wire, copper wire", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8536", description = "Switches, sockets and MCB", keywords = "switch, socket, mcb, electrical fitting, switchboard", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8539", description = "LED bulb and tube light", keywords = "led bulb, bulb, tube light, lamp light", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "7321", description = "Gas stove / chulha", keywords = "gas stove, chulha, cooking stove, burner", gstRate = 18.0, category = "Appliances"),

        // -- Furniture & furnishings --
        HsnEntry(hsnCode = "9403", description = "Furniture (wooden, metal, plastic)", keywords = "furniture, table, chair, bed, sofa, wood, almirah, wardrobe", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9404", description = "Mattress", keywords = "mattress, gaddi, bed mattress, foam mattress", gstRate = 18.0, category = "Furniture"),

        // -- Construction materials --
        HsnEntry(hsnCode = "2523", description = "Cement (Portland, aluminous, slag)", keywords = "cement, building, concrete, construction", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "6907", description = "Ceramic and marble floor/wall tiles", keywords = "kajaria, somany, vitrified, glazed vitrified, tiles, ceramic, marble, floor tiles, wall tiles", gstRate = 18.0, category = "Tiles"),
        HsnEntry(hsnCode = "2515", description = "Marble and granite (raw/slabs)", keywords = "marble, granite, stone slab, building stone", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "7009", description = "Glass and mirrors", keywords = "glass, mirror, window glass, safety glass", gstRate = 18.0, category = "Glass"),
        // Split into distinct, separately selectable items under their own "Sanitaryware"
        // category — previously bundled into one line under "Tiles", so nothing showed up when
        // picking a category for a wash basin or toilet seat.
        HsnEntry(hsnCode = "6910", description = "Wash basin (ceramic)", keywords = "wash basin, washbasin, basin, sink, hand wash basin, ceramic basin", gstRate = 18.0, category = "Sanitaryware"),
        HsnEntry(hsnCode = "6910", description = "Toilet / WC / commode (ceramic)", keywords = "toilet, wc, commode, western commode, indian commode, water closet", gstRate = 18.0, category = "Sanitaryware"),
        HsnEntry(hsnCode = "6910", description = "Urinal (ceramic)", keywords = "urinal, urinal pot", gstRate = 18.0, category = "Sanitaryware"),
        HsnEntry(hsnCode = "6910", description = "Flush tank / cistern", keywords = "flush tank, cistern, flush cistern, overhead flush", gstRate = 18.0, category = "Sanitaryware"),
        HsnEntry(hsnCode = "3922", description = "Toilet seat and seat cover (plastic)", keywords = "toilet seat, seat cover, commode seat, wc seat, plastic seat, toilet seat cover", gstRate = 18.0, category = "Sanitaryware"),
        HsnEntry(hsnCode = "6910", description = "Bidet", keywords = "bidet", gstRate = 18.0, category = "Sanitaryware"),
        HsnEntry(hsnCode = "3917", description = "PVC pipes and fittings", keywords = "pvc pipe, pipe, plumbing pipe, fitting", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "2701", description = "Coal and lignite", keywords = "coal, lignite, koyla", gstRate = 18.0, category = "Fuels"),

        // -- Personal care (non-essential / cosmetics) --
        HsnEntry(hsnCode = "3304", description = "Cosmetics, makeup and skin care", keywords = "makeup, cream, lotion, lipstick, beauty, cosmetics, face cream", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3303", description = "Perfume and deodorant", keywords = "perfume, deodorant, deo, fragrance, attar", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3304", description = "Nail polish and hair colour/dye", keywords = "nail polish, hair colour, hair dye, mehendi", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3402", description = "Detergent and washing powder", keywords = "detergent, washing powder, surf, cleaning powder", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "9613", description = "Lighter", keywords = "lighter, cigarette lighter", gstRate = 18.0, category = "Personal Care"),

        // -- Stationery, books-adjacent office supplies --
        HsnEntry(hsnCode = "9608", description = "Pens and markers", keywords = "pen, ball pen, marker, sketch pen, ink pen", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4802", description = "Paper (uncoated writing/printing, non-exercise-book)", keywords = "paper, page, sheet, ream, printing paper", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "3926", description = "Files, folders and office stationery", keywords = "file, folder, stapler, glue stick, office stationery, clip", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "9202", description = "Musical instruments (guitar, harmonium, tabla, keyboard)", keywords = "guitar, harmonium, tabla, keyboard music, musical instrument, violin, flute", gstRate = 18.0, category = "Musical Instruments"),
        HsnEntry(hsnCode = "9506", description = "Gym and fitness equipment", keywords = "gym equipment, treadmill, dumbbell, fitness machine, exercise machine", gstRate = 18.0, category = "Sports Goods"),

        // -- Utensils, hardware & tools --
        HsnEntry(hsnCode = "8215", description = "Spoons, forks, knives and cutlery", keywords = "spoon, fork, knife, cutlery, chaku", gstRate = 18.0, category = "Utensils"),
        HsnEntry(hsnCode = "8212", description = "Razor, blade and scissors", keywords = "razor, blade, scissors, hair clipper, shaving razor", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "7318", description = "Nails, screws, bolts, nuts and hardware fasteners", keywords = "nails, screws, bolts, nuts, hardware, fastener, washer", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8301", description = "Locks, padlocks and keys", keywords = "lock, padlock, key, door lock", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8302", description = "Hinges and door fittings", keywords = "hinge, door fitting, handle, bracket", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "9102", description = "Wrist watches and clocks", keywords = "watch, wrist watch, clock, wall clock", gstRate = 18.0, category = "Watches"),
        HsnEntry(hsnCode = "7310", description = "Steel water bottle / flask", keywords = "water bottle, flask, thermos, steel bottle", gstRate = 18.0, category = "Utensils"),
        HsnEntry(hsnCode = "3208", description = "Paint and varnish", keywords = "paint, varnish, enamel, wall paint", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "3506", description = "Adhesive and glue (industrial/consumer)", keywords = "adhesive, glue, fevicol, gum", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "3604", description = "Fireworks / pataka", keywords = "fireworks, pataka, crackers, firecracker", gstRate = 18.0, category = "Festive"),

        // -- Apparel, footwear & bags (premium / above threshold) --
        HsnEntry(hsnCode = "6403", description = "Footwear (above Rs 2,500 per pair)", keywords = "premium footwear, branded footwear, designer footwear above 2500, luxury footwear", gstRate = 18.0, category = "Footwear"),
        HsnEntry(hsnCode = "6109", description = "Apparel and clothing (above Rs 2,500 per piece)", keywords = "premium clothing, branded apparel, designer wear above 2500", gstRate = 18.0, category = "Apparel"),
        HsnEntry(hsnCode = "4202", description = "School bag, backpack, travel bag and suitcase", keywords = "school bag, backpack, travel bag, suitcase, luggage, bag", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4202", description = "Wallet and purse", keywords = "wallet, purse, money bag", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4203", description = "Leather belt", keywords = "belt, leather belt", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "6506", description = "Helmet", keywords = "helmet, safety helmet, bike helmet", gstRate = 18.0, category = "Automotive"),

        // -- Vehicles, parts & standard automotive --
        HsnEntry(hsnCode = "8708", description = "Car parts and motor vehicle accessories", keywords = "car parts, motor parts, spare parts, auto parts", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8711", description = "Motorcycles, scooters and two-wheelers (up to 350cc)", keywords = "two wheeler, two wheelers, motorcycle, bike, scooter, scooty, moped, gearless scooter, vehicle, commuter bike", gstRate = 18.0, category = "Two Wheelers"),
        HsnEntry(hsnCode = "8714", description = "Motorcycle / two-wheeler parts and accessories", keywords = "motorcycle spare parts, scooter parts, two wheeler accessories, bike parts", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8703", description = "Three-wheeler / auto-rickshaw (passenger)", keywords = "three wheeler, auto rickshaw, autorickshaw, tempo passenger, rickshaw", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8704", description = "Motor vehicles for transport of goods (mini-truck, pickup, goods tempo)", keywords = "goods carrier, mini truck, pickup truck, tempo goods, commercial vehicle, loading vehicle", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8703", description = "Small petrol/CNG/diesel cars (within small-car limits)", keywords = "car, small car, hatchback, sedan, vehicle", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "4011", description = "Car / bike tyre (non-tractor)", keywords = "tyre, tire, car tyre, bike tyre, wheel tyre", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "2710", description = "Engine oil and lubricant", keywords = "engine oil, lubricant, motor oil, gear oil", gstRate = 18.0, category = "Automotive"),

        // -- Plastics & metals --
        HsnEntry(hsnCode = "3926", description = "Plastic containers, buckets and boxes", keywords = "plastic, bucket, container, box, plastic item", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "7208", description = "Steel and iron sheets/rods", keywords = "steel, iron, metal, sheet, rod, tmt bar", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "4412", description = "Plywood and laminated wood boards", keywords = "plywood, wood, board, laminate, mdf", gstRate = 18.0, category = "Wood Products"),

        // -- Food (non-essential prepared) --
        HsnEntry(hsnCode = "1806", description = "Chocolate and cocoa products", keywords = "dairy milk, cadbury dairy milk, dairymilk, cadbury, kitkat, chocolate, cocoa, choco, candy chocolate", gstRate = 18.0, category = "Food Products"),
        HsnEntry(hsnCode = "2105", description = "Ice cream and edible ice", keywords = "ice cream, kulfi, edible ice", gstRate = 18.0, category = "Food Products"),

        // ================= Precious metals & stones (niche rates) =================
        HsnEntry(hsnCode = "7108", description = "Gold, unwrought or in semi-manufactured forms", keywords = "gold, sona", gstRate = 3.0, category = "Precious Metals"),
        HsnEntry(hsnCode = "7106", description = "Silver, unwrought or in semi-manufactured forms", keywords = "silver, chandi", gstRate = 3.0, category = "Precious Metals"),
        HsnEntry(hsnCode = "7110", description = "Platinum", keywords = "platinum", gstRate = 3.0, category = "Precious Metals"),
        HsnEntry(hsnCode = "7113", description = "Jewellery and articles of precious metal", keywords = "jewelry, jewellery, gold jewellery, silver jewellery, ring, chain, necklace, gold ring", gstRate = 3.0, category = "Precious Metals"),
        HsnEntry(hsnCode = "7102", description = "Diamonds (cut, polished or rough)", keywords = "diamond, diamonds, heera", gstRate = 1.5, category = "Precious Metals"),

        // ================= 40% slab (sin / luxury goods) =================
        HsnEntry(hsnCode = "2202", description = "Aerated waters, soft drinks and carbonated beverages", keywords = "thums up, thumsup, colddrink, soft drink, soda, water bottle aerated, pepsi, coke, aerated drink", gstRate = 40.0, category = "Beverages"),
        HsnEntry(hsnCode = "2202", description = "Caffeinated and energy beverages", keywords = "energy drink, caffeinated drink, red bull", gstRate = 40.0, category = "Beverages"),
        HsnEntry(hsnCode = "8711", description = "Motorcycles above 350cc", keywords = "superbike, bullet 350cc plus, big bike, high cc motorcycle", gstRate = 40.0, category = "Automotive"),
        HsnEntry(hsnCode = "8703", description = "Large / luxury passenger cars", keywords = "luxury car, suv, big car, premium vehicle", gstRate = 40.0, category = "Automotive"),
        HsnEntry(hsnCode = "8903", description = "Yachts and pleasure vessels", keywords = "yacht, pleasure boat, luxury boat", gstRate = 40.0, category = "Luxury Goods"),
        // Tobacco/pan masala/gutkha moved off the old 28% rate to 40% effective 1 Feb 2026, once the
        // GST compensation-cess loan obligations were cleared and the cess itself was abolished
        // (replaced by a per-stick Additional Excise Duty on cigarettes). Bidi was carved out to the
        // 18% slab instead of following cigarettes/pan masala to 40% — keep it as a separate entry.
        HsnEntry(hsnCode = "2403", description = "Cigarettes and other smoking tobacco (not bidi)", keywords = "tobacco, cigarette, smoking tobacco, chewing tobacco, zarda, khaini", gstRate = 40.0, category = "Tobacco Products"),
        HsnEntry(hsnCode = "2106", description = "Pan masala and gutkha", keywords = "pan masala, gutkha", gstRate = 40.0, category = "Tobacco Products"),

        // ================= 18% slab (tobacco carve-out) =================
        HsnEntry(hsnCode = "2403", description = "Bidi / biri", keywords = "bidi, biri, beedi", gstRate = 18.0, category = "Tobacco Products"),

        // =====================================================================
        // ================  EXPANDED COVERAGE (GST 2.0)  ======================
        // Broad add-on set for kirana/general/hardware/electrical/stationery/
        // cosmetic/auto-parts/building-material shops. Agricultural MACHINERY
        // (tractors, tillers, threshers) is intentionally excluded — those
        // dealers run dedicated DMS. Agri-input consumables are kept.
        // Rates follow the 22 Sep 2025 GST 2.0 schedule (0 / 5 / 18 / 40 %).
        // =====================================================================

        // ---------- 0% : fresh produce (additional) ----------
        HsnEntry(hsnCode = "0709", description = "Okra / ladyfinger, fresh", keywords = "okra, ladyfinger, bhindi, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Bottle gourd, bitter gourd and pumpkin, fresh", keywords = "bottle gourd, lauki, ghiya, bitter gourd, karela, pumpkin, kaddu, gourd, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Green chilli and capsicum, fresh", keywords = "green chilli, hari mirch, capsicum, shimla mirch, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Mushrooms, fresh", keywords = "mushroom, mushrooms, khumb, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Drumstick, beans and other fresh vegetables", keywords = "drumstick, sahjan, beans, french beans, tinda, parwal, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0714", description = "Sweet potato, colocasia, yam and tapioca, fresh", keywords = "sweet potato, shakarkandi, arbi, colocasia, yam, jimikand, tapioca, vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Fresh coriander, mint and curry leaves", keywords = "coriander leaves, hara dhaniya, mint, pudina, curry leaves, kadi patta, herbs", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0808", description = "Pears and quinces, fresh", keywords = "pear, pears, nashpati, quince, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0810", description = "Custard apple, sapota (chikoo) and jackfruit, fresh", keywords = "custard apple, sitaphal, sapota, chikoo, chiku, jackfruit, kathal, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0810", description = "Muskmelon, jamun and ber, fresh", keywords = "muskmelon, kharbooja, jamun, ber, phalsa, fresh fruit", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "1212", description = "Sugarcane, fresh", keywords = "sugarcane, ganna, fresh", gstRate = 0.0, category = "Fruits & Vegetables"),

        // ---------- 0% : grains, pulses & staples (additional) ----------
        HsnEntry(hsnCode = "0713", description = "Rajma, lobia and other dried beans, loose", keywords = "rajma, kidney beans, lobia, cowpea, moth, dried beans, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "0713", description = "Kabuli chana and white peas, loose", keywords = "kabuli chana, chickpea, safed chana, white peas, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1004", description = "Oats grain, loose", keywords = "oats grain, jai, cereal, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1904", description = "Puffed rice, murmura and poha (loose/unbranded)", keywords = "puffed rice, murmura, lai, poha, chidwa, flattened rice, sattu", gstRate = 0.0, category = "Food Products"),
        HsnEntry(hsnCode = "1102", description = "Maize flour, jowar and bajra flour, unbranded", keywords = "maize flour, makki atta, jowar flour, bajra flour, corn flour unbranded, grain flour", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1208", description = "Besan / gram flour, unbranded", keywords = "besan, gram flour, chana atta, unbranded", gstRate = 0.0, category = "Grains"),

        // ---------- 0% : education & misc ----------
        HsnEntry(hsnCode = "9610", description = "Slate, chalk stick and writing chalk", keywords = "slate, slate board, chalk, chalk stick, writing chalk, blackboard chalk", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "1209", description = "Seeds for sowing (vegetable, crop, flower)", keywords = "seed, seeds, sowing seeds, beej, vegetable seeds, crop seeds, hybrid seeds", gstRate = 0.0, category = "Agriculture"),
        HsnEntry(hsnCode = "8201", description = "Agricultural hand tools (spade, sickle, khurpi, hoe, axe)", keywords = "spade, kudaal, sickle, hasiya, khurpi, hoe, kudal, axe, kulhari, pickaxe, gainti, rake, hand tool, farm tool", gstRate = 0.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3101", description = "Organic manure and vermicompost", keywords = "organic manure, vermicompost, compost, gobar khaad, cow dung manure", gstRate = 0.0, category = "Agriculture"),

        // ---------- 5% : packaged staples & processed food ----------
        HsnEntry(hsnCode = "1006", description = "Rice, branded/packaged", keywords = "packaged rice, branded rice, basmati packet, chawal packet", gstRate = 5.0, category = "Grains"),
        HsnEntry(hsnCode = "1106", description = "Sabudana / sago", keywords = "sabudana, sago, tapioca pearl", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1902", description = "Vermicelli, sevaiyan and instant noodles", keywords = "vermicelli, sevaiyan, seviyan, instant noodles, macaroni", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1901", description = "Custard powder, corn flour and instant mixes", keywords = "custard powder, corn flour, cornflour, instant mix, idli mix, dosa mix, gulab jamun mix", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2102", description = "Baking powder, yeast and baking soda", keywords = "baking powder, yeast, baking soda, meetha soda, eno", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2209", description = "Vinegar", keywords = "vinegar, sirka", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "0910", description = "Garam masala and blended spice mixes", keywords = "garam masala, chaat masala, sambhar masala, chicken masala, spice mix, masala powder", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0908", description = "Asafoetida (hing), fennel and carom seeds", keywords = "hing, asafoetida, saunf, fennel, ajwain, carom, methi seed, fenugreek, spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0904", description = "Whole dry red chilli", keywords = "dry red chilli, sabut lal mirch, whole chilli, spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0813", description = "Tamarind and dry mango (amchur)", keywords = "tamarind, imli, amchur, dry mango powder, dried fruit spice", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "1513", description = "Coconut oil and edible oil (packaged)", keywords = "coconut oil, nariyal tel, sesame oil, til oil, rice bran oil, refined oil, palm oil, edible oil", gstRate = 5.0, category = "Cooking Oils"),
        HsnEntry(hsnCode = "1516", description = "Vanaspati / dalda", keywords = "vanaspati, dalda, hydrogenated oil, cooking fat", gstRate = 5.0, category = "Cooking Oils"),
        HsnEntry(hsnCode = "0401", description = "Flavoured milk and packaged buttermilk", keywords = "flavoured milk, packaged buttermilk, chaas packet, milk drink", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0404", description = "Khoya / mawa and dairy whitener", keywords = "khoya, mawa, dairy whitener, milk solids", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "2106", description = "Indian sweets / mithai (barfi, ladoo, rasgulla, soan papdi)", keywords = "sweets, mithai, barfi, ladoo, laddu, rasgulla, gulab jamun, soan papdi, sweetmeat", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1905", description = "Cookies, rusk, khari and bakery snacks", keywords = "cookies, rusk, khari, cream roll, bun, muffin, fan pastry, bakery snack", gstRate = 5.0, category = "Bakery"),
        HsnEntry(hsnCode = "2106", description = "Ready-to-eat, instant soup and snack mixes", keywords = "ready to eat, instant food, soup, cup noodle, snack mix, sev, mathri, murukku", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "0710", description = "Frozen vegetables and frozen peas", keywords = "frozen vegetables, frozen peas, frozen matar, frozen corn", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1905", description = "Frozen paratha and frozen roti", keywords = "frozen paratha, frozen roti, frozen food, ready paratha", gstRate = 5.0, category = "Food Products"),

        // ---------- 5% : health, baby & personal essentials ----------
        HsnEntry(hsnCode = "3004", description = "Ayurvedic, homeopathy and Unani medicines", keywords = "ayurvedic medicine, homeopathy, unani, herbal medicine, churna, ayurved dawai", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "ORS, glucose and oral rehydration", keywords = "ors, glucose, electral, oral rehydration, glucon", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "9021", description = "Wheelchair, crutches and walker", keywords = "wheelchair, crutches, walker, walking stick medical, orthopaedic aid", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9019", description = "Nebuliser and oxygen concentrator", keywords = "nebuliser, nebulizer, oxygen concentrator, respiratory device", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9018", description = "BP monitor and pulse oximeter", keywords = "bp monitor, blood pressure machine, pulse oximeter, oximeter", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "6307", description = "Surgical mask and face mask", keywords = "mask, surgical mask, face mask, n95, kn95", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "4015", description = "Surgical and examination gloves", keywords = "gloves, surgical gloves, examination gloves, latex gloves, medical gloves", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "1901", description = "Infant / baby food and formula", keywords = "baby food, infant food, cerelac, baby formula, lactogen, milk food", gstRate = 5.0, category = "Baby Care"),
        HsnEntry(hsnCode = "3306", description = "Tooth powder / dant manjan", keywords = "tooth powder, dant manjan, dental powder", gstRate = 5.0, category = "Personal Care"),
        HsnEntry(hsnCode = "9615", description = "Comb and hair band", keywords = "comb, kangi, hair band, hair clip, rubber band hair", gstRate = 5.0, category = "Personal Care"),

        // ---------- 5% : textiles, utensils & household ----------
        HsnEntry(hsnCode = "7323", description = "Pressure cooker and steel cookware", keywords = "pressure cooker, cooker, kadai, tawa, thali, tiffin, lunch box, steel glass, patila, bartan", gstRate = 5.0, category = "Utensils"),
        HsnEntry(hsnCode = "7615", description = "Aluminium utensils and cookware", keywords = "aluminium utensils, aluminium pressure cooker, aluminium cooker, prestige aluminium cooker, aluminium vessel, patila, tope, cookware", gstRate = 5.0, category = "Utensils"),
        HsnEntry(hsnCode = "5407", description = "Saree, dupatta and dress material", keywords = "saree, sari, banarasi saree, silk saree, banarasi silk saree, dupatta, dress material, suit piece, chunni", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "6213", description = "Handkerchief, gamcha and napkin cloth", keywords = "handkerchief, rumaal, gamcha, napkin cloth, angocha", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "5204", description = "Sewing thread and yarn", keywords = "thread, sewing thread, dhaaga, yarn, wool ball, knitting wool", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6305", description = "Jute bag, cotton bag and gunny bag", keywords = "jute bag, cotton bag, cloth bag, gunny bag, bori, thaila", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "5701", description = "Handmade / coir mats and doormats", keywords = "doormat, coir mat, paydan, handmade carpet, floor mat", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "3406", description = "Candles", keywords = "candle, candles, mombatti, wax candle", gstRate = 5.0, category = "Household"),
        HsnEntry(hsnCode = "9403", description = "Bamboo, cane and handicraft articles", keywords = "bamboo, cane, wicker, basket, handicraft, handmade article, showpiece handmade", gstRate = 5.0, category = "Handicrafts"),

        // ---------- 5% : agri-inputs (consumables only) ----------
        HsnEntry(hsnCode = "3105", description = "Micronutrients and bio-fertiliser", keywords = "micronutrient, bio fertiliser, biofertilizer, zinc sulphate, growth promoter", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3808", description = "Bio-pesticide and neem-based pesticide", keywords = "bio pesticide, biopesticide, neem oil, organic pesticide", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "8424", description = "Manual / hand knapsack sprayer", keywords = "hand sprayer, knapsack sprayer, manual sprayer, pump sprayer, spray pump", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3923", description = "Mulching film and crop cover", keywords = "mulching film, crop cover, plastic mulch, agri film", gstRate = 5.0, category = "Agriculture"),

        // ---------- 5% : vehicles (concessional) ----------
        HsnEntry(hsnCode = "8703", description = "Electric car (EV passenger vehicle)", keywords = "electric car, ev car, electric vehicle, ev four wheeler", gstRate = 5.0, category = "Automotive"),
        HsnEntry(hsnCode = "8703", description = "Electric rickshaw / e-rickshaw", keywords = "e-rickshaw, e rickshaw, electric rickshaw, toto, battery rickshaw", gstRate = 5.0, category = "Automotive"),
        HsnEntry(hsnCode = "8712", description = "Bicycle tyre, tube and parts", keywords = "cycle tyre, cycle tube, bicycle parts, cycle chain, cycle seat", gstRate = 5.0, category = "Automotive"),

        // ---------- 18% : electrical & appliances ----------
        HsnEntry(hsnCode = "8414", description = "Electric fan (ceiling, table, pedestal, exhaust)", keywords = "fan, ceiling fan, table fan, pedestal fan, exhaust fan, wall fan, cooler fan", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8479", description = "Air cooler / desert cooler", keywords = "air cooler, cooler, desert cooler, room cooler", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "Room heater, immersion rod and electric kettle", keywords = "room heater, heater, immersion rod, water heater rod, electric kettle", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "Hair dryer, trimmer and electric shaver", keywords = "hair dryer, hair straightener, trimmer, electric shaver, epilator", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8421", description = "Water purifier / RO system", keywords = "water purifier, ro, ro system, aquaguard, water filter", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8414", description = "Kitchen chimney / exhaust hood", keywords = "chimney, kitchen chimney, exhaust hood, cooker hood", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8509", description = "Mixer, juicer, food processor and hand blender", keywords = "mixer, mixer grinder, juicer, food processor, hand blender, chopper, wet grinder", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "OTG, air fryer, sandwich maker and rice cooker", keywords = "otg, air fryer, sandwich maker, rice cooker, electric cooker, roti maker", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8413", description = "Water pump and submersible pump", keywords = "water pump, submersible pump, monoblock pump, motor pump, water motor, borewell pump", gstRate = 18.0, category = "Appliances"),

        // ---------- 18% : computers, mobile & audio-video accessories ----------
        HsnEntry(hsnCode = "8523", description = "Pen drive, memory card, SSD and hard disk", keywords = "pen drive, pendrive, usb drive, memory card, sd card, ssd, hard disk, external hard disk", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8471", description = "Mouse, keyboard and computer accessories", keywords = "mouse, keyboard, computer accessory, usb hub, webcam, laptop stand", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8443", description = "Printer ink, cartridge and toner", keywords = "printer ink, ink cartridge, toner, refill ink, cartridge", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8471", description = "Tablet and iPad", keywords = "tablet, ipad, tab, android tablet", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8518", description = "Earbuds, TWS and headphones", keywords = "earbuds, tws, wireless earbuds, headphone, neckband, airpods", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "9102", description = "Smartwatch and fitness band", keywords = "smartwatch, smart watch, fitness band, fit band, smart band", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8528", description = "Set-top box, DTH and streaming device", keywords = "set top box, settop box, dth, tata sky, streaming device, fire stick", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8519", description = "Home theatre, soundbar and DVD player", keywords = "home theatre, soundbar, dvd player, music system, av receiver", gstRate = 18.0, category = "Electronics"),

        // ---------- 18% : electrical fittings & lighting ----------
        HsnEntry(hsnCode = "8536", description = "Extension board, multi-plug and holder", keywords = "extension board, extension cord, multi plug, spike guard, holder, ceiling rose, connector", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8536", description = "Distribution box, panel board and fan regulator", keywords = "distribution box, db box, panel board, fan regulator, dimmer, changeover", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8531", description = "Doorbell / calling bell", keywords = "doorbell, calling bell, ding dong, wireless bell", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "3919", description = "Insulation tape and electrical tape", keywords = "insulation tape, electrical tape, pvc tape, tape roll", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8539", description = "CFL, bulb, tube light and light fitting", keywords = "cfl, bulb, tube light, night lamp, decorative light, panel light, flood light", gstRate = 18.0, category = "Electronics"),

        // ---------- 18% : hand tools & hardware ----------
        HsnEntry(hsnCode = "8205", description = "Hammer, screwdriver, spanner and wrench", keywords = "hammer, screwdriver, spanner, wrench, plier, pliers, tool, hardware tool, allen key, vice", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8467", description = "Drill machine, angle grinder and power tool", keywords = "drill machine, drill, angle grinder, power tool, cutter machine, jigsaw, screwdriver machine", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8202", description = "Hacksaw, blade and cutting tools", keywords = "hacksaw, saw blade, cutting blade, hacksaw blade, tenon saw", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "9017", description = "Measuring tape and spirit level", keywords = "measuring tape, inch tape, spirit level, try square, measuring", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8311", description = "Welding rod, electrode and welding machine", keywords = "welding rod, welding electrode, welding machine, welding wire, inverter welding", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "6805", description = "Sandpaper, emery and grinding wheel", keywords = "sandpaper, emery paper, grinding wheel, cutting wheel, abrasive", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8302", description = "Door handle, tower bolt, aldrop and fittings", keywords = "door handle, tower bolt, aldrop, kundi, latch, bracket, door fitting, curtain fitting", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "7217", description = "GI wire, binding wire and barbed wire", keywords = "gi wire, binding wire, barbed wire, iron wire, fencing wire, steel wire", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "7314", description = "Wire mesh, chain link and welded mesh", keywords = "wire mesh, chain link, jaali, welded mesh, fencing net metal", gstRate = 18.0, category = "Hardware"),

        // ---------- 18% : building & construction materials ----------
        HsnEntry(hsnCode = "3214", description = "Wall putty, tile adhesive and sealant", keywords = "wall putty, putty, tile adhesive, sealant, silicone sealant, m-seal, gap filler", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "2520", description = "Plaster of Paris (POP) and gypsum", keywords = "plaster of paris, pop, gypsum, gypsum board, false ceiling material", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "2523", description = "White cement and wall care", keywords = "white cement, birla white, wall care putty", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "6810", description = "AAC block, concrete block and paver", keywords = "aac block, concrete block, cement block, paver block, interlocking tile", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "3209", description = "Emulsion paint, distemper and primer", keywords = "emulsion, distemper, primer, wall paint, interior paint, exterior paint, cement paint", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "3814", description = "Thinner, turpentine and paint solvent", keywords = "thinner, turpentine, tarpin, paint solvent, reducer", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "9603", description = "Paint brush, roller and putty knife", keywords = "paint brush, brush, paint roller, roller, putty knife, wall brush", gstRate = 18.0, category = "Paints"),

        // ---------- 18% : pipes, plumbing & sanitary ----------
        HsnEntry(hsnCode = "3917", description = "CPVC / UPVC pipes and fittings", keywords = "cpvc pipe, upvc pipe, plumbing pipe, pipe fitting, elbow, tee, coupling", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "7306", description = "GI pipe and MS pipe", keywords = "gi pipe, ms pipe, steel pipe, iron pipe, galvanised pipe", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "3925", description = "Water tank / overhead tank (plastic)", keywords = "water tank, overhead tank, sintex tank, storage tank, pani ki tanki", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "8481", description = "Tap, faucet, bib cock and valve", keywords = "tap, faucet, bib cock, valve, ball valve, stop cock, health faucet, shower, mixer tap", gstRate = 18.0, category = "Hardware"),

        // ---------- 18% : plastics, packaging & paper ----------
        HsnEntry(hsnCode = "3924", description = "Plastic mug, drum, stool and household plastic", keywords = "plastic mug, plastic drum, plastic stool, plastic chair, jug, tub, plastic ware", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3926", description = "Dustbin, crate, jerry can and plastic storage", keywords = "dustbin, crate, jerry can, plastic box, storage box, container", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3926", description = "Tarpaulin and plastic sheet", keywords = "tarpaulin, tarpolin, plastic sheet, tirpal, pvc sheet, hdpe sheet", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "5607", description = "Nylon rope, plastic rope and twine", keywords = "rope, nylon rope, plastic rope, rassi, twine, sutli", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "4819", description = "Carton box and corrugated packaging", keywords = "carton, carton box, corrugated box, packaging box, cardboard box", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "3923", description = "Polythene bag, packing plastic and bubble wrap", keywords = "polythene, polythene bag, plastic bag, packing material, bubble wrap, stretch film, cling film", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3919", description = "Cello tape, packing tape and BOPP tape", keywords = "cello tape, cellotape, packing tape, bopp tape, tape, gum tape, brown tape", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4818", description = "Tissue paper, toilet roll and paper towel", keywords = "tissue, tissue paper, toilet roll, toilet paper, paper towel, kitchen towel, facial tissue", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4823", description = "Paper cup, paper plate and disposable tableware", keywords = "paper cup, paper plate, disposable plate, dona, paper glass, disposable cup", gstRate = 18.0, category = "Paper Products"),

        // ---------- 18% : stationery & office ----------
        HsnEntry(hsnCode = "9017", description = "Geometry box, scale, ruler and compass", keywords = "geometry box, scale, ruler, compass, divider, protractor, maths box", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "8470", description = "Calculator", keywords = "calculator, calc, scientific calculator, desktop calculator", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "3919", description = "Sticker, label and self-adhesive paper", keywords = "sticker, label, self adhesive, price sticker, address label", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4817", description = "Envelope and postcard", keywords = "envelope, lifafa, postcard, letter cover", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "9608", description = "Highlighter, whiteboard marker and permanent marker", keywords = "highlighter, whiteboard marker, permanent marker, cd marker, ink marker", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "8304", description = "Stapler, punching machine and office metalware", keywords = "stapler, stapler pin, punching machine, paper punch, clip board, pin", gstRate = 18.0, category = "Books & Stationery"),

        // ---------- 18% : cosmetics & personal care (non-essential) ----------
        HsnEntry(hsnCode = "3401", description = "Handwash, liquid soap, antiseptic liquid and hand sanitizer", keywords = "dettol antiseptic, antiseptic liquid, antiseptic, handwash, hand wash, liquid soap, hand sanitizer, sanitizer, sanitiser", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3304", description = "Face wash, sunscreen and body lotion", keywords = "face wash, facewash, sunscreen, body lotion, moisturiser, cold cream, cleanser", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3305", description = "Hair gel, hair serum and hair cream", keywords = "hair gel, hair serum, hair cream, hair wax, hair spray", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3307", description = "Wet wipes and cotton buds", keywords = "wet wipes, baby wipes, cotton buds, ear buds, face wipes", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3304", description = "Talcum powder and compact powder", keywords = "talcum powder, talc, powder, compact powder, face powder", gstRate = 18.0, category = "Cosmetics"),

        // ---------- 18% : cleaning & home care ----------
        HsnEntry(hsnCode = "3402", description = "Dishwash bar, dishwash liquid and dishwash gel", keywords = "dishwash, dish wash, vim bar, dishwash liquid, utensil cleaner, bartan cleaner", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "3402", description = "Phenyl, floor cleaner and toilet cleaner", keywords = "phenyl, floor cleaner, toilet cleaner, harpic, lizol, surface cleaner, disinfectant", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "3402", description = "Glass cleaner and bathroom cleaner", keywords = "glass cleaner, colin, bathroom cleaner, tile cleaner", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "3808", description = "Mosquito repellent, coil, liquid and spray", keywords = "mosquito repellent, mosquito coil, all out, good knight, mosquito liquid, mortein, hit spray", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "3808", description = "Pesticide, insecticide, fungicide and weedicide (chemical)", keywords = "pesticide, insecticide, fungicide, weedicide, herbicide, kitnashak, rat kill", gstRate = 18.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3808", description = "Naphthalene balls and air freshener", keywords = "naphthalene balls, phenyl balls, air freshener, room freshener, odonil", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "9603", description = "Scrubber, cleaning duster, mop and toilet brush", keywords = "scrubber, scrub pad, duster, cleaning cloth, mop, toilet brush, wiper cleaning", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "6304", description = "Mosquito net", keywords = "mosquito net, machardani, bed net", gstRate = 18.0, category = "Household"),

        // ---------- 18% : automotive (standard, non-machinery) ----------
        HsnEntry(hsnCode = "8702", description = "Bus and passenger van (commercial)", keywords = "bus, mini bus, passenger van, tempo traveller, matador", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8704", description = "Truck and lorry (goods carrier)", keywords = "truck, lorry, goods truck, dumper, tipper, heavy vehicle, hcv", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8511", description = "Spark plug, ignition coil and starter", keywords = "spark plug, ignition coil, starter motor, self motor, ignition part", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8708", description = "Brake shoe, clutch plate and suspension parts", keywords = "brake shoe, brake pad, clutch plate, clutch, suspension, shockers, bush", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8512", description = "Headlight, indicator, horn and wiper", keywords = "headlight, tail light, indicator, horn, wiper, bulb auto, fog light", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8708", description = "Car seat cover, floor mat and accessories", keywords = "seat cover, floor mat, car mat, car accessory, mud flap, steering cover", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8482", description = "Ball bearing and bearing", keywords = "bearing, ball bearing, roller bearing, wheel bearing", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "3820", description = "Coolant, brake oil and antifreeze", keywords = "coolant, brake oil, brake fluid, antifreeze, radiator coolant", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "2710", description = "Grease and lubricant grease", keywords = "grease, lubricant grease, wheel grease, chain grease", gstRate = 18.0, category = "Automotive"),

        // ---------- 18% : misc goods ----------
        HsnEntry(hsnCode = "7013", description = "Glassware, crockery and dinner set", keywords = "glassware, crockery, dinner set, ceramic plate, cup saucer, bowl set", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "7615", description = "Aluminium foil and cling wrap", keywords = "aluminium foil, silver foil, foil paper, cling wrap, food wrap", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "9615", description = "Hair dryer accessories and grooming kit", keywords = "grooming kit, manicure set, nail cutter, tweezer", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "9505", description = "Party decoration and festive lighting", keywords = "decoration, party decoration, jhalar, festive light, string light, birthday decoration", gstRate = 18.0, category = "Festive"),
        HsnEntry(hsnCode = "4911", description = "Printed material, banner, flex and visiting card", keywords = "banner, flex, poster, visiting card, printed material, hoarding", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "9504", description = "Video game console and playing cards", keywords = "video game, gaming console, playstation, playing cards, taash", gstRate = 18.0, category = "Toys"),
        HsnEntry(hsnCode = "9503", description = "Electronic and battery-operated toys", keywords = "electronic toy, remote control car, battery toy, rc car, electric toy", gstRate = 18.0, category = "Toys"),
        HsnEntry(hsnCode = "4602", description = "Plastic and cane basket, storage baskets", keywords = "basket, plastic basket, storage basket, laundry basket, tokri", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "6914", description = "Flower pot, planter and garden pot", keywords = "flower pot, gamla, planter, garden pot, ceramic pot", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "2309", description = "Cattle feed, poultry feed and animal fodder", keywords = "cattle feed, poultry feed, animal feed, chicken feed, fodder, chokar, khal", gstRate = 0.0, category = "Agriculture"),
        HsnEntry(hsnCode = "2309", description = "Pet food (dog food, cat food)", keywords = "pet food, dog food, cat food, pedigree, dog biscuit", gstRate = 18.0, category = "Food Products"),

        // ═══════════════════════════════════════════════════════════════
        // MASSIVE EXPANSION v5 — ~500 new entries covering all major
        // HSN chapters relevant to Indian retail/wholesale/trading.
        // Rates follow GST 2.0 (CBIC Notification 9/2025, 22 Sep 2025).
        // ═══════════════════════════════════════════════════════════════

        // ================= 0% / Nil-rated — ADDITIONS =================

        // -- Fresh produce (additional) --
        HsnEntry(hsnCode = "0701", description = "Potatoes, fresh", keywords = "potato, aloo, aalu, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0703", description = "Onions, shallots and garlic, fresh", keywords = "onion, pyaaz, shallot, garlic, lahsun, lehsun, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0702", description = "Tomatoes, fresh", keywords = "tomato, tamatar, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0706", description = "Carrots, radish and beetroot, fresh", keywords = "carrot, gajar, radish, mooli, beetroot, chukandar, turnip, shalgam, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0707", description = "Cucumber, fresh", keywords = "cucumber, kheera, kakdi, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Cauliflower, cabbage and broccoli, fresh", keywords = "cauliflower, gobhi, cabbage, patta gobhi, broccoli, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Spinach, fenugreek leaves and leafy greens, fresh", keywords = "spinach, palak, methi leaves, sarson ka saag, bathua, leafy greens, saag, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0709", description = "Brinjal / eggplant, fresh", keywords = "brinjal, baingan, eggplant, aubergine, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0710", description = "Fresh peas and beans, shelled", keywords = "peas, matar, beans, sem, guar, cluster beans, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0802", description = "Walnuts, pistachios and hazelnuts in shell, fresh/dried", keywords = "walnut, akhrot, pistachio, pista, hazelnut, nut, dry fruit raw", gstRate = 0.0, category = "Dry Fruits"),
        HsnEntry(hsnCode = "0802", description = "Almonds in shell, fresh/dried (unprocessed)", keywords = "almond, badam, nut raw", gstRate = 0.0, category = "Dry Fruits"),
        HsnEntry(hsnCode = "0813", description = "Raisins, dates and figs (dried fruit)", keywords = "raisin, kishmish, date, khajoor, dried fig, anjeer, dry fruit", gstRate = 0.0, category = "Dry Fruits"),
        HsnEntry(hsnCode = "0805", description = "Fresh sweet lime / mosambi", keywords = "sweet lime, mosambi, mausambi, citrus fresh", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0810", description = "Fresh coconut (whole)", keywords = "fresh coconut, nariyal, whole coconut", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0708", description = "Fresh green peas and legumes, shelled or unshelled", keywords = "green peas, hari matar, legumes fresh, chholiya, fresh vegetable", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0910", description = "Fresh ginger and turmeric (raw)", keywords = "fresh ginger, adrak, fresh turmeric, haldi, kachi haldi", gstRate = 0.0, category = "Fruits & Vegetables"),

        // -- Fresh animal products --
        HsnEntry(hsnCode = "0207", description = "Fresh chicken and poultry meat", keywords = "chicken, poultry, murga, broiler, fresh meat", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0204", description = "Fresh mutton / goat meat", keywords = "mutton, goat meat, bakra meat, fresh meat", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0302", description = "Fresh fish (not frozen or processed)", keywords = "fresh fish, machli, rohu, katla, fresh seafood", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0306", description = "Fresh prawns and shrimp", keywords = "prawn, shrimp, jhinga, fresh seafood", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0407", description = "Fresh eggs (hen, duck)", keywords = "egg, eggs, anda, fresh egg", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0401", description = "Fresh milk (unprocessed, not condensed)", keywords = "fresh milk, doodh, cow milk, buffalo milk, raw milk", gstRate = 0.0, category = "Dairy Products"),

        // -- Grains & staples (unbranded/loose) --
        HsnEntry(hsnCode = "1001", description = "Wheat, unbranded/loose", keywords = "wheat, gehun, godam grain, loose wheat", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1006", description = "Rice, unbranded/loose", keywords = "rice, chawal, loose rice, unbranded rice, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1007", description = "Jowar / sorghum, unbranded/loose", keywords = "jowar, sorghum, juar, grain, loose grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1002", description = "Barley, unbranded/loose", keywords = "barley, jau, grain, loose", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "0713", description = "Chickpeas / chana, unbranded/loose", keywords = "chana, chickpea, kala chana, kabuli chana loose, grain", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1003", description = "Ragi / finger millet, loose", keywords = "ragi, finger millet, nachni, mandua, grain, loose", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1207", description = "Mustard seeds, loose", keywords = "mustard seed, sarson, rai, tilhan, loose seed", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1207", description = "Groundnut / peanut, loose unshelled", keywords = "groundnut, moongphali, peanut, sing, loose", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1213", description = "Wheat straw and paddy straw / bhusa", keywords = "straw, bhusa, wheat straw, paddy straw, cattle fodder dry", gstRate = 0.0, category = "Agriculture"),

        // -- Healthcare nil-rated --
        HsnEntry(hsnCode = "3002", description = "Human blood and its components", keywords = "blood, plasma, human blood", gstRate = 0.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "Life-saving drugs (specified list — nil rated)", keywords = "life saving drug, insulin, cancer drug", gstRate = 0.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "9018", description = "Medical / surgical instruments (essential)", keywords = "surgical instrument, scalpel, forceps, stethoscope basic", gstRate = 0.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9021", description = "Artificial limbs and orthopaedic implants", keywords = "artificial limb, implant, orthopaedic implant, prosthetic", gstRate = 0.0, category = "Medical Devices"),

        // -- Education & culture --
        HsnEntry(hsnCode = "9701", description = "Paintings, drawings and original art", keywords = "painting, drawing, art, artwork, original art", gstRate = 0.0, category = "Art & Antiques"),
        HsnEntry(hsnCode = "4903", description = "Children's picture and colouring books", keywords = "children book, picture book, colouring book, kids book", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4904", description = "Sheet music and musical scores (printed)", keywords = "music sheet, musical score, notation book", gstRate = 0.0, category = "Books & Stationery"),

        // ================= 5% slab — ADDITIONS =================

        // -- Dairy (additional) --
        HsnEntry(hsnCode = "0403", description = "Lassi and chaas (packaged)", keywords = "lassi, chaas, buttermilk packaged, flavoured lassi", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0406", description = "Paneer / chena (packaged)", keywords = "paneer, chena, packaged paneer, cottage cheese", gstRate = 5.0, category = "Dairy Products"),
        HsnEntry(hsnCode = "0405", description = "Table margarine and bakery shortening", keywords = "margarine, bakery shortening, table spread", gstRate = 5.0, category = "Dairy Products"),

        // -- Packaged dry fruits & nuts --
        HsnEntry(hsnCode = "0801", description = "Cashew nuts (packaged)", keywords = "cashew, kaju, packaged dry fruit, cashew packet", gstRate = 5.0, category = "Dry Fruits"),
        HsnEntry(hsnCode = "0802", description = "Almonds (packaged)", keywords = "almond packet, badam packet, packaged badam", gstRate = 5.0, category = "Dry Fruits"),
        HsnEntry(hsnCode = "0813", description = "Mixed dry fruits (packaged)", keywords = "mixed dry fruit, dry fruit box, trail mix, muesli dry fruit", gstRate = 5.0, category = "Dry Fruits"),
        HsnEntry(hsnCode = "2008", description = "Roasted and salted nuts (packaged)", keywords = "roasted nut, salted peanut, masala kaju, roasted almond, flavoured nut", gstRate = 5.0, category = "Dry Fruits"),

        // -- Coffee, tea & beverages (packaged) --
        HsnEntry(hsnCode = "2101", description = "Instant coffee and coffee-chicory mixture", keywords = "instant coffee, nescafe, coffee powder, coffee chicory, bru", gstRate = 5.0, category = "Beverages"),
        HsnEntry(hsnCode = "0902", description = "Tea bags and packaged tea (branded)", keywords = "tea bag, taj mahal tea, red label, tetley, branded tea, tea packet", gstRate = 5.0, category = "Beverages"),
        HsnEntry(hsnCode = "2101", description = "Roasted chicory and coffee substitutes", keywords = "chicory, coffee substitute, grain coffee", gstRate = 5.0, category = "Beverages"),

        // -- Spices & condiments (additional) --
        HsnEntry(hsnCode = "0910", description = "Bay leaf, curry leaf powder and mixed herbs", keywords = "bay leaf, tej patta, curry leaf powder, mixed herb, oregano, thyme", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "0910", description = "Kasuri methi and dried fenugreek leaves", keywords = "kasuri methi, dried fenugreek, methi dried leaf", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "2103", description = "Soya sauce and oriental sauces", keywords = "soya sauce, soy sauce, schezwan sauce, red chilli sauce, green chilli sauce, chinese sauce", gstRate = 5.0, category = "Spices"),
        HsnEntry(hsnCode = "2103", description = "Mayonnaise and salad dressing", keywords = "mayonnaise, mayo, salad dressing, sandwich spread", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2104", description = "Soup powder and instant soup mix", keywords = "soup powder, soup mix, tomato soup, sweet corn soup packet", gstRate = 5.0, category = "Food Products"),

        // -- Flours & baking ingredients --
        HsnEntry(hsnCode = "1102", description = "Maida, sooji and rava (packaged branded)", keywords = "maida, sooji, suji, rava, rawa, packaged flour, branded maida", gstRate = 5.0, category = "Flour"),
        HsnEntry(hsnCode = "1105", description = "Potato flour, starch and corn starch", keywords = "corn starch, cornflour, arrowroot, potato starch, starch", gstRate = 5.0, category = "Flour"),

        // -- Rice & grains (packaged) --
        HsnEntry(hsnCode = "1006", description = "Basmati rice (packaged/branded)", keywords = "basmati rice, basmati chawal, packaged basmati, branded rice", gstRate = 5.0, category = "Grains"),
        HsnEntry(hsnCode = "1904", description = "Poha, murmura and puffed grains (packaged)", keywords = "packaged poha, chivda packet, murmura packet, packaged puffed rice, sattu packet", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1904", description = "Breakfast cereal and muesli (packaged)", keywords = "breakfast cereal, muesli, corn flakes packet, oats packet, chocos", gstRate = 5.0, category = "Food Products"),

        // -- Snacks & prepared food --
        HsnEntry(hsnCode = "2106", description = "Papdi, mathri, sev and traditional snacks (packaged)", keywords = "papdi, mathri, sev, gathiya, fafda, khakhra packet, packaged snack, farsan", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1905", description = "Wafers and cream-filled biscuits", keywords = "wafer, cream biscuit, oreo, bourbon, cream filled cookie", gstRate = 5.0, category = "Bakery"),
        HsnEntry(hsnCode = "1905", description = "Pizza bread and burger buns", keywords = "pizza base, burger bun, pav, hot dog bun", gstRate = 5.0, category = "Bakery"),
        HsnEntry(hsnCode = "1704", description = "Toffee, candy and chewing gum", keywords = "toffee, candy, chewing gum, bubble gum, lollipop, éclair", gstRate = 5.0, category = "Food Products"),

        // -- Oils & fats (additional) --
        HsnEntry(hsnCode = "1508", description = "Groundnut oil, packaged", keywords = "groundnut oil, peanut oil, sing tel, packaged oil", gstRate = 5.0, category = "Cooking Oils"),
        HsnEntry(hsnCode = "1514", description = "Mustard oil, packaged", keywords = "mustard oil, sarson ka tel, kachi ghani, packaged oil", gstRate = 5.0, category = "Cooking Oils"),
        HsnEntry(hsnCode = "1511", description = "Palm oil, packaged", keywords = "palm oil, pam oil, packaged oil", gstRate = 5.0, category = "Cooking Oils"),

        // -- Pharmaceuticals (additional) --
        HsnEntry(hsnCode = "3003", description = "Medicated ointment and topical creams", keywords = "ointment, topical cream, burn cream, medicated lotion, boroplus, antiseptic cream", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "Cough syrup and cold medicine", keywords = "cough syrup, cough medicine, cold medicine, benadryl, honitus, corex", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "Pain relief balm, spray and roll-on", keywords = "balm, pain relief, moov, volini, zandu balm, amrutanjan, relief spray", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "Antacid, digestion and stomach relief", keywords = "antacid, digestion, eno, gelusil, digene, pudin hara, stomach medicine", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3005", description = "Cotton wool, gauze and surgical dressings", keywords = "cotton, cotton wool, ruii, gauze, surgical cotton, dressing pad, band aid", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9018", description = "Clinical thermometer (digital/mercury)", keywords = "thermometer, digital thermometer, clinical thermometer, fever thermometer, bukhar machine", gstRate = 5.0, category = "Medical Devices"),

        // -- Medical devices (additional) --
        HsnEntry(hsnCode = "9021", description = "Knee cap, ankle support and orthopaedic braces", keywords = "knee cap, knee support, ankle support, wrist support, orthopaedic belt, brace, cervical collar", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9019", description = "Vaporiser and steam inhaler", keywords = "vaporiser, steam inhaler, steamer medical, vicks vaporizer", gstRate = 5.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9025", description = "Digital weighing scale (personal/medical)", keywords = "weighing scale, digital scale, personal scale, bathroom scale, weighing machine", gstRate = 5.0, category = "Medical Devices"),

        // -- Textiles & apparel (≤₹2500) --
        HsnEntry(hsnCode = "5205", description = "Cotton yarn and sewing thread (retail)", keywords = "cotton thread, yarn, crochet thread, embroidery thread", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6214", description = "Shawl, stole and scarf", keywords = "shawl, stole, scarf, muffler, dupatta winter, woollen wrap", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "6215", description = "Tie, bow and cravat", keywords = "tie, bow tie, necktie, cravat", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "6111", description = "Baby garments and kids' clothing (up to ₹2500)", keywords = "baby clothes, kids dress, baby romper, frock, baby wear, infant clothing", gstRate = 5.0, category = "Apparel"),
        HsnEntry(hsnCode = "6304", description = "Cushion cover and pillow (fabric)", keywords = "cushion, cushion cover, pillow, takiya, throw pillow", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6304", description = "Table cloth, napkin and placemat (fabric)", keywords = "table cloth, table cover, napkin, placemat, dining cloth", gstRate = 5.0, category = "Textiles"),
        HsnEntry(hsnCode = "6307", description = "Apron (fabric)", keywords = "apron, kitchen apron, fabric apron", gstRate = 5.0, category = "Textiles"),

        // -- Footwear (≤₹2500/pair) --
        HsnEntry(hsnCode = "6402", description = "Rubber / plastic slippers and flip-flops", keywords = "slipper, hawai chappal, flip flop, bathroom slipper, rubber chappal", gstRate = 5.0, category = "Footwear"),
        HsnEntry(hsnCode = "6404", description = "Canvas shoes and sports footwear (≤₹2500)", keywords = "canvas shoe, sports shoe basic, sneaker affordable", gstRate = 5.0, category = "Footwear"),

        // -- Household & kitchen --
        HsnEntry(hsnCode = "7323", description = "Non-stick cookware / non-stick tawa", keywords = "non stick, nonstick tawa, nonstick pan, nonstick kadai, nonstick cookware", gstRate = 5.0, category = "Utensils"),
        HsnEntry(hsnCode = "4419", description = "Wooden spoon, rolling pin and kitchen woodenware", keywords = "wooden spoon, chakla, belan, rolling pin, wooden chopping board, wooden kitchen", gstRate = 5.0, category = "Utensils"),
        HsnEntry(hsnCode = "4602", description = "Bamboo, cane and wicker baskets (handicraft)", keywords = "bamboo basket, cane basket, wicker, tokri bamboo, handmade basket", gstRate = 5.0, category = "Handicrafts"),
        HsnEntry(hsnCode = "4601", description = "Coir / coconut fibre products and handicraft mats", keywords = "coir, coconut fibre, kair, coir rope, natural fibre", gstRate = 5.0, category = "Handicrafts"),
        HsnEntry(hsnCode = "6912", description = "Earthen / clay pots, kulhad and matka", keywords = "earthen pot, clay pot, kulhad, matka, surahi, mitti bartan, terracotta", gstRate = 5.0, category = "Household"),

        // -- Religious items (additional) --
        HsnEntry(hsnCode = "3307", description = "Camphor / kapoor and dhoop cups", keywords = "camphor, kapoor, dhoop, dhoop cup, loban, sambrani", gstRate = 5.0, category = "Religious Items"),
        HsnEntry(hsnCode = "3406", description = "Diyas (clay/brass lamp) and puja lamp", keywords = "diya, deepak, puja lamp, akhand jyot, brass lamp, diwali diya", gstRate = 5.0, category = "Religious Items"),
        HsnEntry(hsnCode = "8306", description = "Brass and copper idols, bells and puja articles", keywords = "brass idol, pital ki murti, copper lota, temple bell, ghanti, puja thali, brassware religious", gstRate = 5.0, category = "Religious Items"),

        // -- Agri consumables (additional) --
        HsnEntry(hsnCode = "3808", description = "Organic bio-pesticide / neem oil pesticide", keywords = "neem oil, organic pesticide, bio pesticide, natural pesticide", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3105", description = "NPK fertiliser blend (packaged)", keywords = "npk, fertiliser packet, khaad packet, plant food", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "3926", description = "Seedling tray and nursery polybag", keywords = "seedling tray, nursery bag, polybag nursery, plant bag, grow bag", gstRate = 5.0, category = "Agriculture"),
        HsnEntry(hsnCode = "8432", description = "Hand-operated / manual sprayer and duster", keywords = "manual sprayer, hand duster, crop sprayer hand, pesticide sprayer hand", gstRate = 5.0, category = "Agriculture"),

        // -- Renewable energy (additional) --
        HsnEntry(hsnCode = "8507", description = "Solar battery / solar storage battery", keywords = "solar battery, solar storage, solar inverter battery", gstRate = 5.0, category = "Renewable Energy"),
        HsnEntry(hsnCode = "8419", description = "Solar water heater", keywords = "solar water heater, solar geyser, solar hot water", gstRate = 5.0, category = "Renewable Energy"),

        // ================= 18% slab — MASSIVE ADDITIONS =================

        // ---------- 18% : food processing & commercial kitchen ----------
        HsnEntry(hsnCode = "8438", description = "Flour mill / atta chakki (domestic / small commercial)", keywords = "flour mill, atta chakki, domestic flour mill, mini flour mill", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8419", description = "Commercial food warmer and bain-marie", keywords = "food warmer, bain marie, hot case, buffet warmer", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8509", description = "Commercial blender and food processor (heavy duty)", keywords = "commercial blender, heavy duty mixer, restaurant blender, industrial mixer", gstRate = 18.0, category = "Appliances"),

        // ---------- 18% : chemicals & industrial inputs ----------
        HsnEntry(hsnCode = "2807", description = "Sulphuric acid and oleum", keywords = "sulphuric acid, sulfuric acid, oleum, battery acid, industrial acid", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "2815", description = "Caustic soda / sodium hydroxide", keywords = "caustic soda, sodium hydroxide, naoh, drain cleaner chemical", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "2827", description = "Alum / phitkari and chloride salts", keywords = "alum, phitkari, fitkari, aluminium sulphate, water treatment", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "2836", description = "Soda ash / washing soda and baking soda (bulk)", keywords = "soda ash, washing soda, sodium carbonate, soda bulk", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "2847", description = "Hydrogen peroxide", keywords = "hydrogen peroxide, h2o2, bleaching liquid", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "2905", description = "Glycerine, glycols and industrial alcohols", keywords = "glycerine, glycerol, glycol, ethylene glycol, propylene glycol, industrial alcohol", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "2915", description = "Acetic acid and vinegar acid (industrial)", keywords = "acetic acid, vinegar acid, industrial acid", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "3402", description = "Wetting agent and industrial surfactant", keywords = "wetting agent, surfactant, industrial soap chemical", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "3814", description = "Organic solvent and degreaser", keywords = "solvent, degreaser, organic solvent, cleaning solvent, thinner industrial", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "3824", description = "Laboratory chemicals and reagents", keywords = "lab chemical, reagent, laboratory chemical, test chemical", gstRate = 18.0, category = "Chemicals"),

        // ---------- 18% : paints, coatings & waterproofing ----------
        HsnEntry(hsnCode = "3208", description = "Enamel paint and synthetic paint", keywords = "enamel, synthetic paint, oil paint, metal paint, wood paint", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "3209", description = "Plastic emulsion and acrylic paint", keywords = "plastic paint, acrylic paint, asian paints, nerolac, berger, interior emulsion", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "3210", description = "Varnish, polish and wood finish", keywords = "varnish, polish, wood polish, melamine, french polish, touchwood", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "3214", description = "Waterproofing compound and damp-proof coating", keywords = "waterproofing, damp proof, dr fixit, waterproof compound, roof seal, crack filler", gstRate = 18.0, category = "Paints"),
        HsnEntry(hsnCode = "3204", description = "Synthetic organic colouring matter and dyes", keywords = "dye, colour, coloring, pigment, fabric dye, textile colour", gstRate = 18.0, category = "Chemicals"),
        HsnEntry(hsnCode = "3206", description = "Colour masterbatch and pigment preparations", keywords = "masterbatch, colour masterbatch, pigment, plastic colouring", gstRate = 18.0, category = "Chemicals"),

        // ---------- 18% : metals — iron, steel & articles ----------
        HsnEntry(hsnCode = "7208", description = "Hot-rolled steel sheets and coils", keywords = "hr sheet, hot rolled steel, steel coil, steel plate, ms sheet", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7209", description = "Cold-rolled steel sheets", keywords = "cr sheet, cold rolled steel, crca sheet", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7210", description = "Galvanised / GI sheets and coils", keywords = "gi sheet, galvanised sheet, zinc coated sheet, gp sheet, gc sheet, roofing sheet metal", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7214", description = "MS round bar, square bar and flat bar", keywords = "ms bar, round bar, square bar, flat bar, steel bar, saria, mild steel bar", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7216", description = "Steel angle, channel and beam sections", keywords = "angle, channel, beam, ismb, ismc, isa steel, l-angle, i-beam", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7215", description = "Bright bar and shafting steel", keywords = "bright bar, shafting, steel shaft, polished bar", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7306", description = "ERW / MS / GI pipe and hollow sections", keywords = "erw pipe, ms pipe, gi pipe, hollow section, square pipe, rectangular pipe, steel tube", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7310", description = "Steel drums, barrels and metal containers", keywords = "steel drum, metal container, ms drum, steel barrel, tin container", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7314", description = "Expanded metal, perforated sheet and metal mesh", keywords = "expanded metal, perforated sheet, metal mesh, steel mesh, iron mesh, expanded mesh", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7217", description = "MS wire, steel wire and fencing wire", keywords = "ms wire, steel wire, iron wire, fencing wire, wire roll, tie wire", gstRate = 18.0, category = "Metals"),

        // ---------- 18% : metals — aluminium, copper, brass ----------
        HsnEntry(hsnCode = "7606", description = "Aluminium sheets, plates and coils", keywords = "aluminium sheet, aluminium plate, aluminium patra, aluminium coil, al sheet", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7604", description = "Aluminium bars, rods and profiles", keywords = "aluminium bar, aluminium rod, aluminium section, al extrusion, aluminium profile", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7608", description = "Aluminium pipes and tubes", keywords = "aluminium pipe, aluminium tube, al pipe, aluminium tubing", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7610", description = "Aluminium door, window and partition frames", keywords = "aluminium door, aluminium window, aluminium frame, aluminium partition, al sliding window", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7615", description = "Aluminium foil containers and trays", keywords = "aluminium container, foil tray, al foil container, food tray aluminium", gstRate = 18.0, category = "Utensils"),
        HsnEntry(hsnCode = "7408", description = "Copper wire and copper cable conductor", keywords = "copper wire, tambda tar, copper conductor, cu wire, copper cable", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7407", description = "Copper bars, rods and profiles", keywords = "copper bar, copper rod, copper busbar, cu bar, copper flat", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7409", description = "Copper sheets and plates", keywords = "copper sheet, copper plate, tambda patra, copper strip", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7411", description = "Copper pipes and tubes (AC/refrigeration)", keywords = "copper pipe, copper tube, ac copper pipe, refrigeration copper, cu tube", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7901", description = "Zinc ingots, slabs and zinc-base alloys", keywords = "zinc, zinc ingot, zinc slab, jasta, zinc alloy", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7907", description = "Zinc sheet and roofing sheet (non-galvanised)", keywords = "zinc sheet, zinc roofing, zinc plate", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "8001", description = "Tin ingots and tin-base alloys", keywords = "tin, tin ingot, tin alloy, kalai", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7403", description = "Brass scrap, brass sheets and brass rods", keywords = "brass, pital, brass sheet, brass rod, brass scrap, brass hardware", gstRate = 18.0, category = "Metals"),

        // ---------- 18% : glass & glassware ----------
        HsnEntry(hsnCode = "7005", description = "Float glass and sheet glass (clear/tinted)", keywords = "float glass, sheet glass, window glass, plain glass, clear glass, tinted glass, glass pane", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7007", description = "Toughened / tempered safety glass", keywords = "toughened glass, tempered glass, safety glass, toughened safety glass", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7008", description = "Insulating glass units / double-glazed units", keywords = "double glazed, insulating glass, dgu, igu, thermal glass", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7009", description = "Glass mirror (framed/unframed)", keywords = "glass mirror, sheesha, wall mirror, dressing mirror, bathroom mirror", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7010", description = "Glass bottles, jars and containers", keywords = "glass bottle, kaanch ki bottle, glass jar, glass container, pickle jar glass", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7016", description = "Glass blocks, bricks and paving stones", keywords = "glass block, glass brick, decorative glass block", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7019", description = "Fibreglass and glass fibre articles", keywords = "fibreglass, fiberglass, glass wool, glass fibre, frp", gstRate = 18.0, category = "Glass"),
        HsnEntry(hsnCode = "7020", description = "Glass showcase, aquarium and glass display articles", keywords = "glass showcase, aquarium, glass display, fish tank, glass shelf", gstRate = 18.0, category = "Glass"),

        // ---------- 18% : ceramics & stone ----------
        HsnEntry(hsnCode = "6902", description = "Refractory bricks and fire bricks", keywords = "refractory brick, fire brick, heat brick, kiln brick, bhatti brick", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "6907", description = "Glazed ceramic tiles and vitrified wall tiles", keywords = "glazed tile, glazed vitrified, vitrified, ceramic wall tile, digital tile, glossy tile, bathroom tile, kitchen tile", gstRate = 18.0, category = "Tiles"),
        HsnEntry(hsnCode = "6802", description = "Granite and marble tiles (polished, cut)", keywords = "granite tile, marble tile, stone tile, polished granite, marble flooring tile", gstRate = 18.0, category = "Tiles"),
        HsnEntry(hsnCode = "6911", description = "Ceramic tableware and kitchenware", keywords = "ceramic plate, ceramic cup, bone china, porcelain ware, ceramic bowl, tea set ceramic", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "6914", description = "Ceramic statues, figurines and ornamental articles", keywords = "ceramic statue, figurine, showpiece, decorative ceramic, ceramic art", gstRate = 18.0, category = "Household"),

        // ---------- 18% : wood & wood products ----------
        HsnEntry(hsnCode = "4407", description = "Sawn timber / wooden planks and beams", keywords = "timber, lakdi, wooden plank, sawn wood, beam, wood batten, wooden log sawn", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "4411", description = "MDF / HDF boards and fibreboard", keywords = "mdf, hdf, fibreboard, mdf board, mdf sheet, fiber board", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "4410", description = "Particle board and chipboard", keywords = "particle board, chipboard, particle board sheet", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "4412", description = "Block board, flush door and laminated wood panels", keywords = "block board, flush door, laminated board, wood panel, commercial ply, shuttering ply", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "4418", description = "Wooden door, window frame and builders' joinery", keywords = "wooden door, lakdi ka darwaza, wooden window, door frame wood, wooden frame", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "4419", description = "Wooden furniture parts, legs and mouldings", keywords = "wooden leg, sofa leg wood, furniture moulding, wood beading, wooden handle", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "4421", description = "Wooden handicraft, carving and decorative articles", keywords = "wooden handicraft, wood carving, wooden art, decorative wood, wooden showpiece", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "9406", description = "Prefab wooden shed, cabin and kiosk", keywords = "wooden shed, wooden cabin, kiosk, prefab hut, portable cabin wood", gstRate = 18.0, category = "Furniture"),

        // ---------- 18% : paper & paperboard ----------
        HsnEntry(hsnCode = "4802", description = "A4 / legal photocopy paper and printing paper", keywords = "a4 paper, photocopy paper, printing paper, bond paper, jk paper, century paper, ream", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4804", description = "Kraft paper and uncoated kraft liner", keywords = "kraft paper, brown paper, wrapping paper, liner paper, kraft liner", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4805", description = "Duplex board and grey board", keywords = "duplex board, grey board, cardboard sheet, mill board", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4810", description = "Coated art paper and glossy paper", keywords = "art paper, glossy paper, coated paper, magazine paper, photo paper inkjet", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4811", description = "Thermal paper roll and self-adhesive paper", keywords = "thermal paper, thermal roll, billing roll, atm roll, fax roll", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4817", description = "Letter pad, writing pad and notepad", keywords = "letter pad, writing pad, notepad, memo pad, rough pad", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "4819", description = "Paper bags and paper envelopes (plain)", keywords = "paper bag, paper envelope, brown paper bag, kraft bag, paper carry bag", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4823", description = "Gift wrapping paper and decorative paper", keywords = "gift wrap, wrapping paper, decorative paper, crepe paper, tissue gift", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4820", description = "Diary, journal and appointment book", keywords = "diary, journal, planner, appointment book, personal diary, date book", gstRate = 18.0, category = "Books & Stationery"),

        // ---------- 18% : plastics (additional) ----------
        HsnEntry(hsnCode = "3901", description = "Polyethylene / PE granules and raw material", keywords = "pe granules, polyethylene, ldpe, hdpe, plastic raw material, plastic granules, plastic dana", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3902", description = "Polypropylene / PP granules and raw material", keywords = "pp granules, polypropylene, plastic raw, pp dana, pp raw material", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3904", description = "PVC resin and compound", keywords = "pvc resin, pvc compound, vinyl resin, pvc raw material, pvc dana", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3918", description = "PVC flooring, vinyl tiles and floor covering", keywords = "pvc flooring, vinyl floor, vinyl tile, pvc floor mat, roll flooring, floor sheet", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3919", description = "Self-adhesive plastic sheet, film and vinyl", keywords = "self adhesive sheet, sticker vinyl, wall sticker, vinyl wrap, decorative film", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3920", description = "Plastic sheets, films and laminates", keywords = "plastic sheet, plastic film, laminate sheet, pvc sheet, acrylic sheet, polycarbonate sheet, sun board", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3921", description = "Foam sheet, thermocol and insulation board", keywords = "foam sheet, thermocol, insulation board, styrofoam, eps sheet, sunpack sheet", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3923", description = "Plastic jerry can, drum and industrial container", keywords = "jerry can, plastic drum, plastic can, hdpe drum, chemical drum plastic", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3925", description = "Plastic door, window frame and builders' ware", keywords = "plastic door, upvc door, upvc window, plastic frame, plastic building material", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3926", description = "Plastic stand, display rack and shop fittings", keywords = "display rack, plastic stand, shelf plastic, shop display, brochure stand, acrylic stand", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3926", description = "Plastic name plate, sign board and number plate", keywords = "name plate, sign board plastic, number plate, door plate, acrylic sign", gstRate = 18.0, category = "Plastics"),

        // ---------- 18% : rubber & rubber products ----------
        HsnEntry(hsnCode = "4008", description = "Rubber sheet, mat and gasket sheet", keywords = "rubber sheet, rubber mat, gasket sheet, rubber pad, rubber roll", gstRate = 18.0, category = "Rubber Products"),
        HsnEntry(hsnCode = "4009", description = "Rubber hose, pipe and tubing", keywords = "rubber hose, rubber pipe, rubber tube, radiator hose, fuel hose, air hose", gstRate = 18.0, category = "Rubber Products"),
        HsnEntry(hsnCode = "4010", description = "Conveyor belt, V-belt and transmission belt", keywords = "conveyor belt, v belt, fan belt, transmission belt, flat belt, timing belt industrial", gstRate = 18.0, category = "Rubber Products"),
        HsnEntry(hsnCode = "4016", description = "Rubber gasket, O-ring, seal and bush", keywords = "rubber gasket, o ring, rubber seal, oil seal, rubber bush, rubber washer, grommet", gstRate = 18.0, category = "Rubber Products"),
        HsnEntry(hsnCode = "4016", description = "Rubber mat (industrial/vehicle flooring)", keywords = "rubber mat, vehicle mat, industrial mat, floor mat rubber heavy", gstRate = 18.0, category = "Rubber Products"),

        // ---------- 18% : leather & leather goods ----------
        HsnEntry(hsnCode = "4107", description = "Finished leather / crust leather", keywords = "leather, leather hide, finished leather, crust leather, chamda", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4202", description = "Ladies' handbag and purse", keywords = "handbag, ladies bag, purse leather, sling bag, clutch", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4202", description = "Leather briefcase, office bag and laptop bag", keywords = "briefcase, office bag, laptop bag, leather bag, messenger bag", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4202", description = "Trolley bag and strolley (hard/soft luggage)", keywords = "trolley bag, strolley, hard luggage, soft luggage, vip bag, safari bag, american tourister", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4203", description = "Leather jacket and leather apparel", keywords = "leather jacket, leather coat, leather pant, chamde ki jacket", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "4205", description = "Leather keychain, card holder and small leather goods", keywords = "keychain leather, card holder, leather wallet small, coin purse leather", gstRate = 18.0, category = "Bags & Leather"),

        // ---------- 18% : electrical — cables, switches & panels ----------
        HsnEntry(hsnCode = "8535", description = "High-voltage switchgear and circuit breakers (>1000V)", keywords = "hv switchgear, circuit breaker hv, isolator, vcb, sf6 breaker, high voltage switch", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8536", description = "MCB, RCCB, ELCB and changeover switch", keywords = "mcb, rccb, elcb, rcbo, changeover switch, mccb, isolator switch, distribution board", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8537", description = "Panel board, control panel and PLC", keywords = "panel board, control panel, plc, electrical panel, amf panel, apfc panel, mcc panel", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8538", description = "Electrical busbar, trunking and panel accessories", keywords = "busbar, trunking, cable tray, panel accessory, bus duct, raceway", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8544", description = "Power cable, armoured cable and flexible wire", keywords = "power cable, armoured cable, flexible wire, copper cable, aluminium cable, lt cable, ht cable", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8544", description = "Co-axial cable, LAN cable and data cable", keywords = "coaxial cable, lan cable, ethernet cable, cat6 cable, cat5e, networking cable, tv cable", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8546", description = "Electrical insulator and porcelain insulator", keywords = "insulator, electrical insulator, porcelain insulator, disc insulator, pin insulator, bushing", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8504", description = "Voltage stabilizer and servo stabilizer", keywords = "stabilizer, voltage stabilizer, servo stabilizer, fridge stabilizer, ac stabilizer", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8504", description = "LED driver and electronic ballast", keywords = "led driver, electronic ballast, tube light choke, led power supply, electronic choke", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8516", description = "Storage water heater / storage geyser", keywords = "storage geyser, storage water heater, tank water heater, electric water heater tank", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "9405", description = "Ceiling light, chandelier and decorative lamp", keywords = "ceiling light, chandelier, decorative light, wall lamp, pendant light, fancy light, jhoomar", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "9405", description = "LED street light and flood light (outdoor)", keywords = "street light, flood light, outdoor light, led flood, security light, garden light", gstRate = 18.0, category = "Electronics"),

        // ---------- 18% : plumbing & water systems ----------
        HsnEntry(hsnCode = "3917", description = "Plastic pipe fittings (elbow, tee, coupler, union)", keywords = "pipe fitting, elbow, tee, coupler, union, reducer, end cap, plumbing fitting pvc, tank connector", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "8481", description = "Ball valve, gate valve and butterfly valve", keywords = "ball valve, gate valve, butterfly valve, non return valve, nrv, globe valve, check valve", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "3922", description = "Plastic wash basin and bathroom fittings", keywords = "plastic wash basin, corner basin, bathroom shelf, soap dish, toothbrush holder, towel rod", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "8413", description = "Domestic water pump (0.5–2 HP)", keywords = "water pump, domestic pump, motor pump, monoblock, submersible pump domestic, jet pump", gstRate = 18.0, category = "Appliances"),

        // ---------- 18% : furniture & interior (additional) ----------
        HsnEntry(hsnCode = "9401", description = "Office chair, revolving chair and visitor chair", keywords = "office chair, revolving chair, visitor chair, computer chair, ergonomic chair, executive chair", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "Modular kitchen cabinet and counter", keywords = "modular kitchen, kitchen cabinet, kitchen counter, pantry unit, modular wardrobe", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "Steel almirah, metal cupboard and locker", keywords = "steel almirah, metal cupboard, iron almirah, steel cabinet, metal locker, office cupboard", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "Plastic chair, garden chair and stool (moulded)", keywords = "plastic chair, moulded chair, plastic stool, garden chair, monoblock chair, pvc chair", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "Bookshelf, shoe rack and wall shelf", keywords = "bookshelf, book rack, shoe rack, wall shelf, display shelf, corner shelf, floating shelf", gstRate = 18.0, category = "Furniture"),

        // ---------- 18% : tools & hardware (additional) ----------
        HsnEntry(hsnCode = "8203", description = "Pliers, pincer wrench and hand tools set", keywords = "plier, pliers set, pincer, combination plier, nose plier, wire cutter, circlip plier", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8205", description = "Spade, crowbar, chisel and masonry tools", keywords = "spade, crowbar, sabbal, chisel, chheni, masonry tool, gainti, pick, tarpaulin hook", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8207", description = "Drill bit, hole saw and cutting accessories", keywords = "drill bit, hole saw, masonry bit, hss bit, concrete bit, wood bit, cutting disc", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8205", description = "Vice, G-clamp and work-holding tools", keywords = "vice, bench vice, g clamp, c clamp, pipe vice, carpenter vice, work holding", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8467", description = "Circular saw, jigsaw and woodworking power tools", keywords = "circular saw, jigsaw, power saw, woodworking machine, planer, router wood, sander", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8414", description = "Air compressor and pneumatic tools", keywords = "air compressor, compressor, pneumatic tool, air gun, air pump, tyre inflator", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8424", description = "Pressure washer / jet cleaner", keywords = "pressure washer, jet cleaner, high pressure washer, car washer machine, power washer", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8536", description = "Welding holder, earth clamp and cable connector", keywords = "welding holder, earth clamp, cable connector, welding accessory, lug, crimping tool", gstRate = 18.0, category = "Hardware"),

        // ---------- 18% : automotive (additional) ----------
        HsnEntry(hsnCode = "8708", description = "Car mirror, bumper and body parts", keywords = "car mirror, orvm, bumper, car body part, fender, bonnet, door panel car, tail gate", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8421", description = "Oil filter, air filter and fuel filter", keywords = "oil filter, air filter, fuel filter, ac filter car, cabin filter, filter element", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "7007", description = "Car windshield and automotive safety glass", keywords = "windshield, windscreen, car glass, automotive glass, rear glass, side glass car", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8507", description = "Car battery and automotive battery", keywords = "car battery, automotive battery, amaron, exide, sf sonic, din battery, smf battery", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8708", description = "Exhaust silencer, muffler and exhaust pipe", keywords = "silencer, muffler, exhaust pipe, exhaust system, catalytic converter, bend pipe", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "3819", description = "Brake fluid and hydraulic fluid", keywords = "brake fluid, hydraulic fluid, clutch fluid, dot3, dot4 brake oil", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "3403", description = "Car shampoo, polish and car care products", keywords = "car shampoo, car polish, wax polish, car care, dashboard polish, tyre polish", gstRate = 18.0, category = "Automotive"),

        // ---------- 18% : medical, lab & scientific equipment ----------
        HsnEntry(hsnCode = "9018", description = "Surgical gloves, catheters and medical disposables", keywords = "surgical disposable, catheter, iv set, ryles tube, urine bag, medical tube", gstRate = 18.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9022", description = "X-ray, ultrasound and imaging equipment", keywords = "xray, x-ray, ultrasound, imaging, mri, ct scan, medical imaging", gstRate = 18.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9027", description = "Laboratory instrument, microscope and testing kit", keywords = "microscope, lab instrument, testing kit, ph meter, tds meter, spectrophotometer", gstRate = 18.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "3822", description = "Diagnostic reagent and test kit", keywords = "test kit, diagnostic kit, reagent, rapid test, pregnancy test kit, hiv kit, elisa kit", gstRate = 18.0, category = "Medical Devices"),

        // ---------- 18% : jewellery & imitation ----------
        HsnEntry(hsnCode = "7117", description = "Imitation / fashion jewellery and accessories", keywords = "imitation jewellery, fashion jewellery, artificial jewellery, costume jewellery, fake jewellery, junk jewellery", gstRate = 18.0, category = "Jewellery & Accessories"),
        HsnEntry(hsnCode = "7117", description = "Bangle, bracelet and anklet (non-precious)", keywords = "bangle, chudi, bracelet, anklet, payal, kada, non precious jewellery", gstRate = 18.0, category = "Jewellery & Accessories"),
        HsnEntry(hsnCode = "7117", description = "Earring, nose ring and body jewellery (non-precious)", keywords = "earring, jhumka, nose ring, nath, body jewellery, fashion earring", gstRate = 18.0, category = "Jewellery & Accessories"),
        HsnEntry(hsnCode = "7117", description = "Necklace set and chain (imitation)", keywords = "necklace set, chain, haar, mangalsutra imitation, pendant set", gstRate = 18.0, category = "Jewellery & Accessories"),
        HsnEntry(hsnCode = "9615", description = "Hair accessory, bow, scrunchie and hairband", keywords = "hair accessory, hair bow, scrunchie, hair band, hair pin, clutch hair", gstRate = 18.0, category = "Jewellery & Accessories"),
        HsnEntry(hsnCode = "9113", description = "Watch strap, band and bracelet (non-precious)", keywords = "watch strap, watch band, leather strap, metal strap, silicon strap", gstRate = 18.0, category = "Watches"),

        // ---------- 18% : sports, fitness & outdoor ----------
        HsnEntry(hsnCode = "9506", description = "Cricket kit bag, pads, gloves and helmet (sports protective)", keywords = "cricket kit, batting pads, batting gloves, cricket helmet, wicket keeping gloves, thigh pad", gstRate = 18.0, category = "Sports Goods"),
        HsnEntry(hsnCode = "9506", description = "Swimming pool equipment and accessories", keywords = "swimming pool, pool pump, pool filter, swimming accessory, pool ladder", gstRate = 18.0, category = "Sports Goods"),
        HsnEntry(hsnCode = "4203", description = "Sports gloves (cricket / boxing / cycling)", keywords = "sports gloves, cricket gloves, boxing gloves, cycling gloves, gym gloves", gstRate = 18.0, category = "Sports Goods"),
        HsnEntry(hsnCode = "8903", description = "Inflatable boat, raft and camping gear", keywords = "inflatable boat, raft, camping gear, tent, sleeping bag, camping mattress", gstRate = 18.0, category = "Sports Goods"),
        HsnEntry(hsnCode = "6601", description = "Garden umbrella and beach umbrella", keywords = "garden umbrella, beach umbrella, patio umbrella, solar umbrella, shade umbrella", gstRate = 18.0, category = "Household"),

        // ---------- 18% : printing, advertising & signage ----------
        HsnEntry(hsnCode = "4911", description = "Printed catalogue, brochure and product leaflet", keywords = "catalogue, brochure, leaflet, pamphlet, product flyer, printed ad", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4911", description = "Printed calendar, poster and wall chart", keywords = "calendar, poster, wall chart, printed calendar, photo calendar", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "3919", description = "Vinyl sticker, decal and wall art sticker", keywords = "vinyl sticker, decal, wall sticker, car sticker, laptop skin, bumper sticker", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "9405", description = "Neon sign, illuminated sign and glow sign board", keywords = "neon sign, illuminated sign, glow sign, led sign board, acrylic sign lit, flex board lit", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8310", description = "Metal sign board, name plate and number sign", keywords = "metal sign, brass name plate, ss name plate, number plate metal, signage metal, engraved sign", gstRate = 18.0, category = "Metals"),

        // ---------- 18% : musical instruments & audio ----------
        HsnEntry(hsnCode = "9202", description = "Sitar, sarod, veena and string instruments (Indian)", keywords = "sitar, sarod, veena, tanpura, sarangi, string instrument", gstRate = 18.0, category = "Musical Instruments"),
        HsnEntry(hsnCode = "9205", description = "Bansuri / flute, shehnai and wind instruments", keywords = "flute, bansuri, shehnai, nadaswaram, wind instrument, recorder", gstRate = 18.0, category = "Musical Instruments"),
        HsnEntry(hsnCode = "9206", description = "Dhol, dholak, tabla, mridangam and percussion", keywords = "dhol, dholak, tabla, mridangam, pakhawaj, bongo, drum, khol, percussion", gstRate = 18.0, category = "Musical Instruments"),
        HsnEntry(hsnCode = "9209", description = "Guitar strings, harmonium reeds and musical parts", keywords = "guitar strings, harmonium reed, musical instrument parts, tabla head, drum skin, plectrum", gstRate = 18.0, category = "Musical Instruments"),
        HsnEntry(hsnCode = "8518", description = "Microphone, PA system and amplifier", keywords = "microphone, mike, pa system, amplifier, amp, mixer sound, dj system", gstRate = 18.0, category = "Electronics"),

        // ---------- 18% : personal & grooming ----------
        HsnEntry(hsnCode = "9615", description = "Hair brush, styling comb and mirror compact", keywords = "hair brush, styling comb, compact mirror, hand mirror, make-up mirror", gstRate = 18.0, category = "Personal Care"),
        HsnEntry(hsnCode = "8516", description = "Electric hair straightener, curler and styling tool", keywords = "straightener, curling iron, hair styler, electric curler, hair crimper", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "3304", description = "BB cream, CC cream and tinted moisturiser", keywords = "bb cream, cc cream, tinted moisturizer, foundation cream, concealer", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3304", description = "Lip balm, lip gloss and lip care", keywords = "lip balm, lip gloss, lip tint, lip butter, lip oil", gstRate = 18.0, category = "Cosmetics"),
        HsnEntry(hsnCode = "3307", description = "Room freshener, aroma oil and diffuser", keywords = "room freshener, aroma oil, diffuser, essential oil diffuser, reed diffuser, potpourri", gstRate = 18.0, category = "Cleaning"),

        // ---------- 18% : pet supplies & animal care ----------
        HsnEntry(hsnCode = "2309", description = "Dog food, cat food (packaged commercial)", keywords = "pedigree, whiskas, drools, royal canin, meat up, pet food packet, dog treat", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "4201", description = "Dog leash, collar, harness and pet clothing", keywords = "dog leash, dog collar, dog harness, pet clothing, cat collar, pet belt", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "3926", description = "Pet bowl, feeder and water dispenser", keywords = "pet bowl, dog bowl, cat feeder, water dispenser pet, pet dish, feeding bowl", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "3926", description = "Pet carrier, crate and travel kennel", keywords = "pet carrier, dog crate, cat carrier, pet kennel, travel cage pet, pet transport box", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "9503", description = "Pet toy, chew toy and scratching post", keywords = "pet toy, dog toy, cat toy, chew toy, scratching post, catnip toy, ball thrower", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "9603", description = "Pet grooming brush and deshedding tool", keywords = "dog brush, cat brush, pet grooming, deshedding tool, fur brush, pet comb", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "6307", description = "Pet bed, mat and cushion", keywords = "pet bed, dog bed, cat bed, pet mat, pet cushion, pet mattress", gstRate = 18.0, category = "Pet Supplies"),
        HsnEntry(hsnCode = "3808", description = "Pet shampoo, flea/tick treatment and pet hygiene", keywords = "pet shampoo, dog shampoo, flea treatment, tick powder, pet deodorant, pet hygiene", gstRate = 18.0, category = "Pet Supplies"),

        // ---------- 18% : garden & outdoor living ----------
        HsnEntry(hsnCode = "8201", description = "Garden shears, secateurs and pruning tools", keywords = "garden shear, secateur, pruning shear, hedge trimmer manual, lopper, garden scissor", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "3926", description = "Garden hose, sprinkler and watering accessories", keywords = "garden hose, pipe hose, sprinkler, water spray gun, hose connector, hose reel, garden spray", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "8432", description = "Lawn mower (manual / powered)", keywords = "lawn mower, grass cutter, lawn trimmer, brush cutter, grass cutting machine", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "8436", description = "Garden shredder, chipper and outdoor power equipment", keywords = "garden shredder, chipper, leaf blower, hedge trimmer electric, chain saw", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "9401", description = "Garden bench, swing and outdoor seating", keywords = "garden bench, outdoor chair, garden swing, jhula, outdoor sofa, patio furniture, park bench", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "7308", description = "Garden gate, fence panel and metal trellis", keywords = "garden gate, fence panel, metal trellis, garden fence, boundary gate, iron gate", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "5608", description = "Shade net, green net and agro net", keywords = "shade net, green net, agro net, garden net, sun shade net, construction net", gstRate = 18.0, category = "Garden & Outdoor"),
        HsnEntry(hsnCode = "3926", description = "Artificial grass / lawn and plastic hedging", keywords = "artificial grass, synthetic lawn, artificial turf, plastic hedge, fake grass mat", gstRate = 18.0, category = "Garden & Outdoor"),

        // ---------- 18% : safety & protective equipment ----------
        HsnEntry(hsnCode = "6506", description = "Safety helmet (industrial / construction)", keywords = "safety helmet, hard hat, industrial helmet, construction helmet, safety cap", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "9004", description = "Safety goggles and protective eyewear", keywords = "safety goggles, protective glasses, welding goggles, lab goggles, industrial eyewear", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "6116", description = "Industrial gloves (rubber, leather, cut-resistant)", keywords = "industrial glove, safety glove, rubber glove industrial, leather glove work, cut resistant glove, welding glove", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "6401", description = "Safety shoes, gumboot and steel-toe footwear", keywords = "safety shoe, gumboot, steel toe, safety gumboot, industrial boot, work shoe", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "6307", description = "Safety harness, full body harness and lanyard", keywords = "safety harness, full body harness, lanyard, fall arrest, safety belt industrial, lifeline", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "6210", description = "Reflective jacket and high-visibility vest", keywords = "reflective jacket, hi vis vest, safety vest, reflector jacket, night jacket glow", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "9020", description = "Respirator, dust mask and gas mask", keywords = "respirator, dust mask, gas mask, n95 industrial, respirator mask, chemical mask", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "8530", description = "Fire alarm, smoke detector and safety sensor", keywords = "fire alarm, smoke detector, smoke alarm, heat detector, fire sensor, co detector", gstRate = 18.0, category = "Safety Equipment"),
        HsnEntry(hsnCode = "8424", description = "Fire extinguisher and firefighting equipment", keywords = "fire extinguisher, abc extinguisher, co2 extinguisher, fire bucket, fire blanket, fire hose reel", gstRate = 18.0, category = "Safety Equipment"),

        // ================= 40% slab — ADDITIONS =================

        // -- Luxury beverages --
        HsnEntry(hsnCode = "2202", description = "Packaged flavoured / sweetened drink (non-carbonated sweetened)", keywords = "flavoured drink, sweetened drink, glucose drink packaged, tang, rasna packaged, glucon d pack", gstRate = 40.0, category = "Beverages"),
        HsnEntry(hsnCode = "2203", description = "Beer made from malt", keywords = "beer, malt beer, lager", gstRate = 40.0, category = "Beverages"),
        HsnEntry(hsnCode = "2208", description = "Spirits, liquor and alcoholic beverages (IMFL)", keywords = "spirit, liquor, whisky, rum, vodka, brandy, imfl, alcohol", gstRate = 40.0, category = "Beverages"),

        // -- Luxury vehicles --
        HsnEntry(hsnCode = "8711", description = "Luxury motorcycle / superbike above 350cc", keywords = "harley davidson, ducati, triumph, kawasaki ninja, hayabusa, superbike, big bike", gstRate = 40.0, category = "Automotive"),
        HsnEntry(hsnCode = "8703", description = "SUV, MUV, luxury sedan and premium car above small-car threshold", keywords = "suv, muv, luxury car, fortuner, endeavour, bmw, mercedes, audi, premium car, big car", gstRate = 40.0, category = "Automotive"),

        // -- Firearms --
        HsnEntry(hsnCode = "9303", description = "Revolver, pistol and civilian firearms", keywords = "revolver, pistol, gun, handgun, civilian firearm", gstRate = 40.0, category = "Safety Equipment"),

        // ================= 3% slab — precious metals (clarification) =================
        HsnEntry(hsnCode = "7114", description = "Gold / silver coins and medallions", keywords = "gold coin, silver coin, gold medallion, silver medallion, commemorative coin", gstRate = 3.0, category = "Precious Metals"),
        HsnEntry(hsnCode = "7118", description = "Gold / silver utensils and puja articles", keywords = "silver glass, silver plate, gold plated puja, silver lota, silver thali, silver bowl", gstRate = 3.0, category = "Precious Metals"),
        HsnEntry(hsnCode = "7106", description = "Silver granules, powder and industrial silver", keywords = "silver powder, silver granules, industrial silver, silver chemical", gstRate = 3.0, category = "Precious Metals"),

        // ================= 1.5% — cut & polished gemstones =================
        HsnEntry(hsnCode = "7103", description = "Ruby, emerald, sapphire and precious stones (cut/polished)", keywords = "ruby, manik, emerald, panna, sapphire, neelam, precious stone, gemstone, ratna", gstRate = 1.5, category = "Precious Metals"),
        HsnEntry(hsnCode = "7103", description = "Semi-precious stones (topaz, garnet, amethyst, citrine — cut)", keywords = "semi precious stone, topaz, garnet, amethyst, citrine, opal, turquoise, firoza, stone", gstRate = 1.5, category = "Precious Metals"),
        HsnEntry(hsnCode = "7104", description = "Synthetic / lab-grown gemstones (cut/polished)", keywords = "synthetic stone, lab grown diamond, cubic zirconia, american diamond, imitation stone, cz stone", gstRate = 1.5, category = "Precious Metals"),

        // ================= 0.25% — rough diamonds =================
        HsnEntry(hsnCode = "7102", description = "Rough / uncut diamonds (natural, unsorted)", keywords = "rough diamond, uncut diamond, kacha heera, industrial diamond rough", gstRate = 0.25, category = "Precious Metals"),

        // ═══════════════════════════════════════════════════════════════
        // EXPANSION v5 — ROUND 2: comprehensive coverage of remaining
        // HSN chapters. Food processing, textiles, building materials,
        // industrial supplies, and more.
        // ═══════════════════════════════════════════════════════════════

        // ================= 0% — additional staples & raw foods =================
        HsnEntry(hsnCode = "0201", description = "Fresh beef and buffalo meat", keywords = "beef, buffalo meat, buff, fresh meat", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0303", description = "Frozen whole fish (not pre-packaged branded)", keywords = "frozen fish, whole frozen fish, surmai frozen, pomfret frozen", gstRate = 0.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0709", description = "Fresh asparagus, celery and artichokes", keywords = "asparagus, celery, artichoke, fresh vegetable exotic", gstRate = 0.0, category = "Fruits & Vegetables"),
        HsnEntry(hsnCode = "0713", description = "Green gram / moong whole, loose", keywords = "moong, green gram, sabut moong, mung, grain loose", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "0713", description = "Black gram / urad whole, loose", keywords = "urad, black gram, sabut urad, grain loose", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "0713", description = "Pigeon pea / arhar / toor, loose", keywords = "arhar, toor, tur dal raw, pigeon pea, grain loose", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1204", description = "Linseed / flax seed, loose", keywords = "linseed, alsi, flax seed, tilhan, loose seed", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "1206", description = "Sunflower seed, loose", keywords = "sunflower seed, surajmukhi beej, loose seed", gstRate = 0.0, category = "Grains"),
        HsnEntry(hsnCode = "0805", description = "Fresh kinnow and mandarin orange", keywords = "kinnow, mandarin, tangerine, santra, fresh citrus", gstRate = 0.0, category = "Fruits & Vegetables"),

        // ================= 5% — additional packaged foods & consumer goods =================
        HsnEntry(hsnCode = "2106", description = "Flavoured milk powder and health drink powder", keywords = "horlicks, bournvita, complan, boost, health drink, nutrition powder, protein powder food", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1902", description = "Maggi, instant noodles and cup noodles", keywords = "maggi, instant noodles, cup noodles, yippee, top ramen, chings noodles", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2106", description = "Chyawanprash and herbal health supplements", keywords = "chyawanprash, chyavanprash, dabur chyawanprash, herbal supplement, health tonic", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "3004", description = "Clinical enteral nutrition & therapeutic medical supplement", keywords = "enteral nutrition, clinical nutrition, therapeutic medical supplement, pediatric drops", gstRate = 5.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "1703", description = "Molasses and sugarcane juice (packaged)", keywords = "molasses, sugarcane juice packet, ganna ras packet", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2002", description = "Tomato puree, paste and ketchup (processed)", keywords = "tomato puree, tomato paste, tomato ketchup, kissan, maggi sauce, tomato can", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "2106", description = "Ice cream cone, wafer and topping", keywords = "ice cream cone, waffle cone, chocolate topping, sprinkles, ice cream wafer", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "1901", description = "Malt extract and malt-based food preparations", keywords = "malt, malt extract, maltova, milo, malted milk food", gstRate = 5.0, category = "Food Products"),
        HsnEntry(hsnCode = "3306", description = "Mouthwash and oral care liquid", keywords = "mouthwash, mouth wash, listerine, colgate plax, oral rinse", gstRate = 5.0, category = "Personal Care"),
        HsnEntry(hsnCode = "4818", description = "Baby diapers and sanitary pads (packaged)", keywords = "baby diaper, diaper, pampers, huggies, sanitary pad, whisper, stayfree", gstRate = 5.0, category = "Baby Care"),

        // ================= 18% — additional food, beverages & tobacco products =================
        HsnEntry(hsnCode = "2105", description = "Frozen dessert and frozen yoghurt", keywords = "frozen dessert, frozen yogurt, softy, gelato, sorbet", gstRate = 18.0, category = "Food Products"),
        HsnEntry(hsnCode = "1806", description = "Drinking chocolate and chocolate syrup", keywords = "drinking chocolate, chocolate syrup, cocoa drink, hot chocolate powder, bournvita chocolate", gstRate = 18.0, category = "Food Products"),
        HsnEntry(hsnCode = "2201", description = "Aerated / sparkling water (plain, unsweetened)", keywords = "sparkling water, soda water, club soda, carbonated water plain", gstRate = 18.0, category = "Beverages"),
        HsnEntry(hsnCode = "2106", description = "Chewing gum, bubble gum (non-sugar confectionery)", keywords = "chewing gum, bubble gum, center fruit, big babol, boomer", gstRate = 18.0, category = "Food Products"),
        HsnEntry(hsnCode = "2403", description = "Chewing tobacco, zarda and khaini", keywords = "chewing tobacco, zarda, khaini, surti, tambaku, mishri", gstRate = 40.0, category = "Tobacco Products"),
        HsnEntry(hsnCode = "2402", description = "Cigars, cheroots and cigarillos", keywords = "cigar, cheroot, cigarillo", gstRate = 40.0, category = "Tobacco Products"),
        HsnEntry(hsnCode = "9614", description = "Smoking pipes (including bong, hookah, chillum)", keywords = "smoking pipe, hookah, bong, chillum, hukka, pipe tobacco accessory", gstRate = 40.0, category = "Tobacco Products"),

        // ================= 18% — textiles & fabrics (additional) =================
        HsnEntry(hsnCode = "5007", description = "Silk fabric and silk blended textiles", keywords = "silk, silk fabric, resham, silk cloth, silk saree fabric", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5111", description = "Woollen fabric and suiting material", keywords = "woollen, woollen fabric, suiting, wool cloth, tweed", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5407", description = "Polyester, synthetic and blended fabric", keywords = "polyester fabric, synthetic cloth, terrycot, terrywool, blended fabric, viscose, rayon", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5407", description = "Nylon fabric and nylon blended material", keywords = "nylon fabric, nylon cloth, nylon blend", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5515", description = "Denim fabric", keywords = "denim, jeans fabric, denim cloth, jean material", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5801", description = "Velvet and plush fabric", keywords = "velvet, plush, velvet cloth, makhmal", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5901", description = "Coated, laminated and waterproof textile fabric", keywords = "waterproof fabric, laminated cloth, coated fabric, tarpaulin cloth, raincoat fabric", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "5906", description = "Rubberised textile fabric (except tyre cord)", keywords = "rubberised fabric, rubberised cloth, elastic fabric", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "6302", description = "Bed linen, duvet cover and pillowcase", keywords = "bed linen, bedsheet set, duvet, duvet cover, pillowcase, bed cover, razai cover", gstRate = 18.0, category = "Textiles"),
        HsnEntry(hsnCode = "6304", description = "Curtain rod pocket, valance, blind and drapery", keywords = "valance, blind fabric, drapery, curtain accessories, pelmet, tieback", gstRate = 18.0, category = "Textiles"),

        // ================= 18% — building materials (additional) =================
        HsnEntry(hsnCode = "3918", description = "PVC wall panel, ceiling panel and false ceiling", keywords = "pvc panel, wall panel, pvc ceiling, false ceiling panel, wpc board, wall cladding", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "4418", description = "Laminate flooring, engineered wood flooring", keywords = "laminate flooring, wooden flooring, engineered wood floor, hardwood floor, parquet", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "3925", description = "UPVC door and window profiles", keywords = "upvc door profile, upvc window frame, sliding window upvc, casement window upvc", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "6801", description = "Kerbstone, paving block and natural stone sett", keywords = "kerbstone, kerb stone, paving block, stone sett, cobblestone", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "6810", description = "RCC / cement pipe, manhole cover and precast concrete", keywords = "rcc pipe, cement pipe, manhole cover, precast slab, concrete ring, hume pipe", gstRate = 18.0, category = "Mineral Products"),
        HsnEntry(hsnCode = "7308", description = "Steel door, rolling shutter and metal gate", keywords = "steel door, rolling shutter, iron gate, metal door, collapsible gate, sliding gate", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7308", description = "Prefab steel shed, roof truss and metal structure", keywords = "prefab shed, steel structure, roof truss, metal shed, factory shed, warehouse shed, tin shed", gstRate = 18.0, category = "Metals"),
        HsnEntry(hsnCode = "7308", description = "Scaffolding, shuttering plate and centering material", keywords = "scaffolding, shuttering, centering, steel prop, cuplock, ms pipe scaffolding, acrow span", gstRate = 18.0, category = "Metals"),

        // ================= 18% — industrial machinery & equipment =================
        HsnEntry(hsnCode = "8425", description = "Chain pulley block and hoist", keywords = "chain pulley block, hoist, electric hoist, manual hoist, lifting chain block", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8427", description = "Forklift, pallet truck and stacker", keywords = "forklift, pallet truck, hand pallet, stacker, hydraulic pallet truck, reach truck", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8431", description = "Lifting equipment parts, hook and sling", keywords = "lifting hook, sling, chain sling, web sling, shackle, turnbuckle, lifting accessory", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8413", description = "Submersible pump, borewell pump and motor set", keywords = "submersible pump, borewell motor, openwell pump, sewage pump, dewatering pump", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8414", description = "Industrial exhaust fan and blower", keywords = "exhaust fan, industrial fan, blower, ventilation fan, roof extractor, air circulator", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8415", description = "Split AC, window AC and ductable AC unit", keywords = "split ac, window ac, ductable ac, cassette ac, vrf, hvac, air conditioning", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8422", description = "Dishwasher (domestic/commercial)", keywords = "dishwasher, dish washer, ware washer, glass washer commercial", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8450", description = "Washing machine and laundry dryer", keywords = "washing machine, laundry dryer, tumble dryer, semi automatic, fully automatic washer", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8479", description = "Water cooler and water dispenser", keywords = "water cooler, water dispenser, hot cold dispenser, bottled water dispenser", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8509", description = "Wet grinder, dry grinder and flour mill (domestic)", keywords = "wet grinder, dry grinder, instant grinder, atta chakki, table top grinder", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8423", description = "Electronic weighing scale and balance", keywords = "weighing scale, electronic balance, digital scale, platform scale, table top scale, counting scale", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8423", description = "Mechanical / spring weighing scale", keywords = "mechanical scale, spring scale, analog weigh, taraju, kanta, weighing machine manual", gstRate = 18.0, category = "Hardware"),

        // ================= 18% — packaging materials =================
        HsnEntry(hsnCode = "3923", description = "Plastic cap, closure and lid", keywords = "plastic cap, bottle cap, jar lid, screw cap, flip cap, dispensing cap", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3923", description = "Plastic pouch and laminated pouch for packing", keywords = "plastic pouch, laminated pouch, standy pouch, zipper pouch, packing pouch, milk pouch, oil pouch", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3923", description = "Plastic bottle (PET / HDPE) and jar", keywords = "plastic bottle, pet bottle, hdpe bottle, plastic jar, empty bottle, shampoo bottle", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "3920", description = "BOPP film, shrink wrap and packing film", keywords = "bopp film, shrink wrap, packing film, stretch film, cling wrap industrial, lamination film", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "4819", description = "Corrugated box, partition and fitting", keywords = "corrugated box, carton box, die cut box, 3 ply box, 5 ply box, shipping box", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "4821", description = "Paper label, price tag and swing tag", keywords = "paper label, price tag, swing tag, garment tag, barcode label, paper sticker", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "7210", description = "Tinplate sheet and printed tin sheet", keywords = "tinplate, tin sheet, printed tin, ms tin sheet, can making sheet", gstRate = 18.0, category = "Metals"),

        // ================= 18% — healthcare & wellness =================
        HsnEntry(hsnCode = "9402", description = "Hospital bed, examination table and medical furniture", keywords = "hospital bed, examination table, patient trolley, icu bed, dental chair, medical couch", gstRate = 18.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9018", description = "Blood pressure apparatus, stethoscope and diagnostic kit", keywords = "bp apparatus, sphygmomanometer, stethoscope, diagnostic set, opthalmoscope, otoscope", gstRate = 18.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9018", description = "Infusion pump, syringe pump and IV stand", keywords = "infusion pump, syringe pump, iv stand, drip stand, volumetric pump", gstRate = 18.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "9403", description = "Physiotherapy table and rehabilitation equipment", keywords = "physio table, traction table, rehab equipment, exercise therapy", gstRate = 18.0, category = "Medical Devices"),

        // ================= 18% — optics, photography & surveying =================
        HsnEntry(hsnCode = "9001", description = "Contact lens and lens care solution", keywords = "contact lens, contact lens solution, lens care, bausch lomb, soft lens", gstRate = 18.0, category = "Optical"),
        HsnEntry(hsnCode = "9003", description = "Spectacle frame and sunglass frame", keywords = "spectacle frame, sunglass frame, chasma frame, eyeglass frame, reading glass frame", gstRate = 18.0, category = "Optical"),
        HsnEntry(hsnCode = "9005", description = "Binoculars, telescope and monocular", keywords = "binoculars, telescope, monocular, bird watching, zoom lens optical", gstRate = 18.0, category = "Optical"),
        HsnEntry(hsnCode = "9015", description = "Measuring tape (survey), theodolite and total station", keywords = "total station, theodolite, survey instrument, auto level, measuring wheel, survey tape", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8529", description = "CCTV camera and DVR / NVR (security surveillance)", keywords = "cctv, hikvision, cp plus, dvr, nvr, ip camera, security camera system, dome camera, bullet camera", gstRate = 18.0, category = "Electronics"),

        // ================= 18% — more furniture & interiors =================
        HsnEntry(hsnCode = "9403", description = "Dining table and dining set", keywords = "dining table, dining set, dining chair, 6 seater dining, 4 seater table", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "Sofa set, couch and centre table", keywords = "sofa, sofa set, couch, diwan, sofa cum bed, centre table, coffee table, corner sofa", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "Bed (wooden / metal), cot and bed frame", keywords = "bed, bed frame, cot, khatiya, double bed, single bed, king size bed, queen bed, palang", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9404", description = "Pillow, cushion, bolster and neck pillow", keywords = "pillow, takiya, cushion, bolster, neck pillow, microfibre pillow, memory foam pillow", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "9403", description = "TV unit, entertainment centre and wall mount bracket", keywords = "tv unit, tv stand, entertainment unit, wall mount, tv bracket, led stand", gstRate = 18.0, category = "Furniture"),
        HsnEntry(hsnCode = "4421", description = "Wooden curtain rod, pelmet and drapery hardware", keywords = "curtain rod, curtain bracket, wooden rod, drapery rod, curtain track, finial", gstRate = 18.0, category = "Wood Products"),
        HsnEntry(hsnCode = "8302", description = "Curtain rail, track and aluminium channel", keywords = "curtain rail, curtain track, aluminium channel, sliding track", gstRate = 18.0, category = "Hardware"),

        // ================= 18% — DIY, home improvement & power backup =================
        HsnEntry(hsnCode = "8507", description = "Inverter battery and tubular battery", keywords = "inverter battery, tubular battery, luminous battery, exide inverter, amaron inverter, tall tubular", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8502", description = "Generator (DG set), portable gen-set and alternator", keywords = "generator, dg set, genset, diesel generator, portable generator, kirloskar generator, honda generator", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8501", description = "Electric motor (single phase / three phase)", keywords = "electric motor, motor single phase, three phase motor, induction motor, crompton motor, havells motor", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8301", description = "Door closer, floor spring and hydraulic hinge", keywords = "door closer, floor spring, hydraulic hinge, automatic door closer", gstRate = 18.0, category = "Hardware"),
        HsnEntry(hsnCode = "8301", description = "Digital lock, biometric lock and smart door lock", keywords = "digital lock, biometric lock, smart lock, fingerprint lock, godrej digital, electronic lock", gstRate = 18.0, category = "Hardware"),

        // ================= 18% — e-commerce & gadget accessories =================
        HsnEntry(hsnCode = "8504", description = "Mobile charger, USB adapter and fast charger", keywords = "mobile charger, fast charger, usb adapter, type c charger, iphone charger, samsung charger, oppo charger", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8518", description = "Bluetooth neckband, wireless earphone and TWS case", keywords = "bluetooth neckband, tws case, wireless earphone cover, boat earphone, oneplus buds, realme buds", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8523", description = "Screen guard, tempered glass and mobile skin", keywords = "screen guard, tempered glass, mobile skin, back cover, screen protector, matte guard", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "3926", description = "Mobile stand, holder and ring holder", keywords = "mobile stand, phone holder, ring holder, pop socket, car phone mount, desk stand", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "4202", description = "Mobile pouch and phone case (leather/fabric)", keywords = "mobile pouch, phone pouch, leather phone case, flip cover, wallet case", gstRate = 18.0, category = "Bags & Leather"),
        HsnEntry(hsnCode = "9006", description = "Digital camera, DSLR and action camera", keywords = "dslr, digital camera, action camera, gopro, mirrorless camera, slr, nikon, canon, sony alpha", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8523", description = "Gaming CD / DVD, game card and software media", keywords = "game cd, ps5 game, xbox game, nintendo card, game disc", gstRate = 18.0, category = "Toys"),
        HsnEntry(hsnCode = "8471", description = "Gaming keyboard, mousepad and gaming accessories", keywords = "gaming keyboard, gaming mouse, rgb mouse, mechanical keyboard, gaming headset", gstRate = 18.0, category = "Electronics"),

        // ================= 18% — kitchenware & tableware =================
        HsnEntry(hsnCode = "6911", description = "Porcelain tea set, coffee mug set and cup saucer", keywords = "tea set, coffee mug, cup saucer, porcelain cup, bone china mug", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "7013", description = "Glass jug, glass bowl and borosilicate cookware", keywords = "glass jug, glass bowl, borosil, borosilicate, pyrex, glass baking dish", gstRate = 18.0, category = "Household"),
        HsnEntry(hsnCode = "7323", description = "Stainless steel glass, jug and serving dish", keywords = "steel glass, steel jug, serving dish, serving bowl ss, steel tray, thali set", gstRate = 18.0, category = "Utensils"),
        HsnEntry(hsnCode = "3924", description = "Plastic lunch box, casserole and tiffin box", keywords = "lunch box, tiffin box, tiffin, dabba, plastic container lunch, meal prep box", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "4419", description = "Wooden chopping board and kitchen spatula", keywords = "chopping board, cutting board wood, wooden spatula, wooden ladle, kitchen wooden tool", gstRate = 18.0, category = "Utensils"),

        // ================= 18% — events, party & seasonal =================
        HsnEntry(hsnCode = "9505", description = "Balloon (plain/decorative), banner and party supplies", keywords = "balloon, party banner, birthday banner, party hat, confetti, streamer, party supply", gstRate = 18.0, category = "Festive"),
        HsnEntry(hsnCode = "6702", description = "Artificial flower and decorative plant (plastic/fabric)", keywords = "artificial flower, fake flower, decorative plant, plastic flower, silk flower, artificial garland", gstRate = 18.0, category = "Festive"),
        HsnEntry(hsnCode = "9505", description = "Christmas tree, decoration and festive articles", keywords = "christmas tree, christmas decoration, santa, ornament, bauble, wreath", gstRate = 18.0, category = "Festive"),
        HsnEntry(hsnCode = "9505", description = "Diwali toran, bandhanwar and door hanging", keywords = "toran, bandhanwar, door hanging, diwali decoration, mango leaf toran", gstRate = 18.0, category = "Festive"),

        // ================= 5% — bicycles & eco transport =================
        HsnEntry(hsnCode = "8712", description = "Children bicycle and tricycle", keywords = "children bicycle, kids cycle, tricycle, baby cycle, training cycle", gstRate = 5.0, category = "Automotive"),
        HsnEntry(hsnCode = "8715", description = "Baby pram, stroller and pushchair", keywords = "baby pram, stroller, pushchair, baby buggy, ghodagadi, perambulator", gstRate = 5.0, category = "Baby Care"),

        // ================= 18% — laptops, tablets & office equipment =================
        HsnEntry(hsnCode = "8471", description = "All-in-one desktop and mini PC", keywords = "all in one, aio computer, mini pc, imac, desktop computer, assembled pc", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8471", description = "Server, NAS and network storage device", keywords = "server, nas, network storage, rack server, blade server", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8473", description = "Laptop battery, adapter and spare parts", keywords = "laptop battery, laptop adapter, laptop charger, laptop keyboard, laptop screen", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8443", description = "Projector, projection screen and interactive whiteboard", keywords = "projector, epson, benq, projection screen, interactive board, smart board", gstRate = 18.0, category = "Electronics"),
        HsnEntry(hsnCode = "8472", description = "Shredder, laminator and binding machine", keywords = "paper shredder, laminator, binding machine, spiral binder, comb binder", gstRate = 18.0, category = "Books & Stationery"),

        // ================= 18% — automotive 2-wheeler & electric =================
        HsnEntry(hsnCode = "8714", description = "Motorcycle chain, sprocket and chain kit", keywords = "chain sprocket, bike chain kit, motorcycle chain, sprocket set, roller chain", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8714", description = "Motorcycle handle bar, lever and footrest", keywords = "handle bar, clutch lever, brake lever, footrest, gear lever, side stand", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "4011", description = "Motorcycle tyre and tube", keywords = "bike tyre, motorcycle tube, two wheeler tyre, scooter tyre, mrf tyre bike, ceat bike tyre", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "8512", description = "Motorcycle / car LED light and fog lamp", keywords = "led headlight bike, fog lamp car, auxiliary light, drl, daytime running light, light bar", gstRate = 18.0, category = "Automotive"),
        HsnEntry(hsnCode = "7009", description = "Rear view mirror (motorcycle/auto)", keywords = "rear view mirror, side mirror bike, rvm, bar end mirror, auto mirror", gstRate = 18.0, category = "Automotive"),

        // ================= 18% — electrical home appliances =================
        HsnEntry(hsnCode = "9105", description = "Wall clock, table clock and digital clock", keywords = "wall clock, table clock, digital clock, alarm clock, pendulum clock, time piece", gstRate = 18.0, category = "Watches"),
        HsnEntry(hsnCode = "8414", description = "Table fan, pedestal fan and wall fan", keywords = "table fan, pedestal fan, wall fan, usha fan, orient fan, havells fan, crompton fan, standing fan, ceiling fan", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "7321", description = "Gas stove (2 burner/3 burner/4 burner)", keywords = "gas stove, gas chulha, 2 burner, 3 burner, 4 burner, sun flame, prestige gas stove, glass top stove", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "8516", description = "Roti maker, sandwich maker and pop-up toaster", keywords = "roti maker, sandwich maker, pop up toaster, waffle maker, donut maker", gstRate = 18.0, category = "Appliances"),
        HsnEntry(hsnCode = "9102", description = "Stopwatch and sports timer watch", keywords = "stopwatch, timer watch, sports watch digital, chronograph, running watch", gstRate = 18.0, category = "Watches"),

        // ================= 18% — stationery, art & craft =================
        HsnEntry(hsnCode = "3213", description = "Artist colour, painting colour and acrylic colour", keywords = "artist colour, painting colour, acrylic paint, water colour, oil colour, poster colour, fabric colour, camel colour", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "9608", description = "Sketch pen, brush pen and calligraphy pen", keywords = "sketch pen, brush pen, calligraphy pen, felt tip, drawing pen, art pen", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "9609", description = "Eraser, sharpener and correction pen", keywords = "eraser, sharpener, correction pen, whitener, correction tape", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "8214", description = "Paper cutter, craft knife and rotary cutter", keywords = "paper cutter, craft knife, rotary cutter, x-acto, blade cutter paper, trimmer", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "3926", description = "Self-inking stamp, date stamp and stamp pad", keywords = "stamp, stamp pad, self inking stamp, date stamp, number stamp, office stamp", gstRate = 18.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "3506", description = "Glue stick, fevistick and school glue", keywords = "glue stick, fevistick, fevicol, school glue, paper glue, craft glue, hot glue stick", gstRate = 18.0, category = "Books & Stationery"),

        // ================= 18% — cleaning & janitorial (additional) =================
        HsnEntry(hsnCode = "9603", description = "Toilet brush, plunger and bathroom cleaning tools", keywords = "toilet brush, toilet plunger, wc brush, bathroom brush, scrubber bathroom", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "9603", description = "Floor wiper, squeegee and cleaning mop set", keywords = "floor wiper, squeegee, mop set, pochha, spin mop, flat mop, microfiber mop", gstRate = 18.0, category = "Cleaning"),
        HsnEntry(hsnCode = "3923", description = "Spray bottle, trigger sprayer and dispenser bottle", keywords = "spray bottle, trigger spray, mister bottle, dispenser pump, lotion pump", gstRate = 18.0, category = "Plastics"),
        HsnEntry(hsnCode = "4818", description = "Kitchen towel roll and napkin paper", keywords = "kitchen towel, paper napkin, napkin paper, tissue roll, kitchen roll", gstRate = 18.0, category = "Paper Products"),
        HsnEntry(hsnCode = "7323", description = "Steel dustbin and metal waste bin", keywords = "dustbin, steel dustbin, metal dustbin, waste bin, garbage bin steel, pedal bin", gstRate = 18.0, category = "Utensils"),
        HsnEntry(hsnCode = "3924", description = "Plastic dustbin and swing bin", keywords = "plastic dustbin, plastic swing bin, kitchen dustbin, bathroom dustbin", gstRate = 18.0, category = "Plastics"),

        // ================= 5% — fresh / chilled meat & fish (pre-packaged) =================
        HsnEntry(hsnCode = "0207", description = "Frozen chicken (pre-packaged and labelled)", keywords = "frozen chicken, packaged chicken, chicken packet, venkys, suguna chicken, frozen poultry", gstRate = 5.0, category = "Meat & Poultry"),
        HsnEntry(hsnCode = "0304", description = "Frozen fish fillets and fish portions (packaged)", keywords = "frozen fish fillet, fish packet, basa fillet, packaged seafood", gstRate = 5.0, category = "Meat & Poultry"),

        // ================= 0% — special exemptions =================
        HsnEntry(hsnCode = "1905", description = "Prasad / religious offering (food prepared in temple/gurudwara)", keywords = "prasad, prasadam, religious food, langar food, temple offering", gstRate = 0.0, category = "Religious Items"),
        HsnEntry(hsnCode = "3002", description = "Human blood, animal blood and antisera", keywords = "blood, antisera, plasma, vaccine raw", gstRate = 0.0, category = "Pharmaceuticals"),
        HsnEntry(hsnCode = "4907", description = "Currency notes, stamp paper and judicial documents", keywords = "stamp paper, currency, judicial stamp", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "9021", description = "Walking stick, white cane and mobility aid (basic)", keywords = "walking stick, chhadi, white cane, mobility aid", gstRate = 0.0, category = "Medical Devices"),
        HsnEntry(hsnCode = "8442", description = "Braille typewriter and braille paper", keywords = "braille, braille paper, braille typewriter, blind aid", gstRate = 0.0, category = "Books & Stationery"),
        HsnEntry(hsnCode = "8703", description = "Vehicle for physically handicapped persons", keywords = "handicap vehicle, wheelchair accessible vehicle, disabled car", gstRate = 0.0, category = "Medical Devices")
    )
}
