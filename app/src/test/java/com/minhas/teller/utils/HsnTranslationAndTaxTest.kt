package com.minhas.teller.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test suite for the item translation → category → GST rate → HSN pipeline.
 *
 * Covers:
 *  • translateItem: Hindi transliteration, ASR misspellings, English plurals, brand names
 *  • suggestCategory: correct category assignment for translated items
 *  • getGstRate: correct GST 2.0 rate assignment
 *  • canonicalItemName: quantity extraction from voice strings
 *  • HsnPresetData.sanitizeHsnEntry: 28% → 18%/40% migration, motorcycle sub-slab logic
 *  • HsnCommonItems: brand keyword coverage
 *
 * Every test documents the EXPECTED output for a real-world voice/text input so regressions
 * are caught immediately.
 */
class HsnTranslationAndTaxTest {

    // ══════════════════════════════════════════════════════════════════════════
    // ═══════════════  SECTION 1: translateItem — Hindi inputs  ═════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `translate aam → Mango`() = assertTranslation("aam", "Mango")
    @Test fun `translate aamo → Mango`() = assertTranslation("aamo", "Mango")
    @Test fun `translate seb → Apple`() = assertTranslation("seb", "Apple")
    @Test fun `translate sebo → Apple`() = assertTranslation("sebo", "Apple")
    @Test fun `translate doodh → Milk`() = assertTranslation("doodh", "Milk")
    @Test fun `translate dudh → Milk`() = assertTranslation("dudh", "Milk")
    @Test fun `translate paneer → Cottage Cheese`() = assertTranslation("paneer", "Cottage Cheese")
    @Test fun `translate chawal → Rice`() = assertTranslation("chawal", "Rice")
    @Test fun `translate atta → Flour`() = assertTranslation("atta", "Flour")
    @Test fun `translate dal → Lentils`() = assertTranslation("dal", "Lentils")
    @Test fun `translate cheeni → Sugar`() = assertTranslation("cheeni", "Sugar")
    @Test fun `translate shakkar → Sugar`() = assertTranslation("shakkar", "Sugar")
    @Test fun `translate gud → Jaggery`() = assertTranslation("gud", "Jaggery")
    @Test fun `translate namak → Salt`() = assertTranslation("namak", "Salt")
    @Test fun `translate tel → Oil`() = assertTranslation("tel", "Oil")
    @Test fun `translate masala → Spices`() = assertTranslation("masala", "Spices")
    @Test fun `translate masale → Spices`() = assertTranslation("masale", "Spices")
    @Test fun `translate sabzi → Vegetable`() = assertTranslation("sabzi", "Vegetable")
    @Test fun `translate sabji → Vegetable`() = assertTranslation("sabji", "Vegetable")
    @Test fun `translate adrak → Ginger`() = assertTranslation("adrak", "Ginger")
    @Test fun `translate lahsun → Garlic`() = assertTranslation("lahsun", "Garlic")
    @Test fun `translate lasan → Garlic`() = assertTranslation("lasan", "Garlic")
    @Test fun `translate haldi → Turmeric`() = assertTranslation("haldi", "Turmeric")
    @Test fun `translate jeera → Cumin`() = assertTranslation("jeera", "Cumin")
    @Test fun `translate badam → Almond`() = assertTranslation("badam", "Almond")
    @Test fun `translate kaju → Cashew`() = assertTranslation("kaju", "Cashew")
    @Test fun `translate ghee → Ghee`() = assertTranslation("ghee", "Ghee")
    @Test fun `translate sabun → Soap`() = assertTranslation("sabun", "Soap")
    @Test fun `translate chai → Tea`() = assertTranslation("chai", "Tea")
    @Test fun `translate nimbu → Lemon`() = assertTranslation("nimbu", "Lemon")

    // ── Vegetables ──
    @Test fun `translate tamatar → Tomato`() = assertTranslation("tamatar", "Tomato")
    @Test fun `translate tamater → Tomato`() = assertTranslation("tamater", "Tomato")
    @Test fun `translate tamator → Tomato`() = assertTranslation("tamator", "Tomato")
    @Test fun `translate tamatr → Tomato`() = assertTranslation("tamatr", "Tomato")
    @Test fun `translate tamataro → Tomato`() = assertTranslation("tamataro", "Tomato")

    @Test fun `translate aloo → Potato`() = assertTranslation("aloo", "Potato")
    @Test fun `translate aalu → Potato`() = assertTranslation("aalu", "Potato")
    @Test fun `translate aaloo → Potato`() = assertTranslation("aaloo", "Potato")
    @Test fun `translate aalloo → Potato`() = assertTranslation("aalloo", "Potato")
    @Test fun `translate alloo → Potato`() = assertTranslation("alloo", "Potato")
    @Test fun `translate allu → Potato`() = assertTranslation("allu", "Potato")
    @Test fun `translate aalo → Potato`() = assertTranslation("aalo", "Potato")
    @Test fun `translate allo → Potato`() = assertTranslation("allo", "Potato")
    @Test fun `translate alu → Potato`() = assertTranslation("alu", "Potato")
    @Test fun `translate alo → Potato`() = assertTranslation("alo", "Potato")
    @Test fun `translate batata → Potato`() = assertTranslation("batata", "Potato")
    @Test fun `translate batate → Potato`() = assertTranslation("batate", "Potato")

    @Test fun `translate pyaj → Onion`() = assertTranslation("pyaj", "Onion")
    @Test fun `translate pyaaz → Onion`() = assertTranslation("pyaaz", "Onion")
    @Test fun `translate pyaz → Onion`() = assertTranslation("pyaz", "Onion")
    @Test fun `translate pyaaj → Onion`() = assertTranslation("pyaaj", "Onion")
    @Test fun `translate kanda → Onion`() = assertTranslation("kanda", "Onion")
    @Test fun `translate kande → Onion`() = assertTranslation("kande", "Onion")

    // ── Meat & Eggs ──
    @Test fun `translate anda → Egg`() = assertTranslation("anda", "Egg")
    @Test fun `translate ande → Egg`() = assertTranslation("ande", "Egg")
    @Test fun `translate andey → Egg`() = assertTranslation("andey", "Egg")
    @Test fun `translate murgi → Chicken`() = assertTranslation("murgi", "Chicken")
    @Test fun `translate machli → Fish`() = assertTranslation("machli", "Fish")

    // ── Fruits ──
    @Test fun `translate kela → Banana`() = assertTranslation("kela", "Banana")
    @Test fun `translate kele → Banana`() = assertTranslation("kele", "Banana")
    @Test fun `translate santra → Orange`() = assertTranslation("santra", "Orange")
    @Test fun `translate angoor → Grapes`() = assertTranslation("angoor", "Grapes")
    @Test fun `translate anar → Pomegranate`() = assertTranslation("anar", "Pomegranate")
    @Test fun `translate amrood → Guava`() = assertTranslation("amrood", "Guava")

    // ── Spices ──
    @Test fun `translate kali mirch → Black Pepper`() = assertTranslation("kali mirch", "Black Pepper")
    @Test fun `translate dalchini → Cinnamon`() = assertTranslation("dalchini", "Cinnamon")
    @Test fun `translate elaichi → Cardamom`() = assertTranslation("elaichi", "Cardamom")
    @Test fun `translate hing → Asafoetida`() = assertTranslation("hing", "Asafoetida")
    @Test fun `translate methi → Fenugreek`() = assertTranslation("methi", "Fenugreek")
    @Test fun `translate ajwain → Carom Seeds`() = assertTranslation("ajwain", "Carom Seeds")

    // ── Construction ──
    @Test fun `translate cement → Cement`() = assertTranslation("cement", "Cement")
    @Test fun `translate eent → Bricks`() = assertTranslation("eent", "Bricks")
    @Test fun `translate rodi → Gravel`() = assertTranslation("rodi", "Gravel")
    @Test fun `translate sariya → Iron Rods`() = assertTranslation("sariya", "Iron Rods")
    @Test fun `translate choona → Lime`() = assertTranslation("choona", "Lime")

    // ── Electronics & Appliances ──
    @Test fun `translate pankha → Fan`() = assertTranslation("pankha", "Fan")
    @Test fun `translate pankhe → Fan`() = assertTranslation("pankhe", "Fan")
    @Test fun `translate pankho → Fan`() = assertTranslation("pankho", "Fan")
    @Test fun `translate gaadi → Vehicle`() = assertTranslation("gaadi", "Vehicle")
    @Test fun `translate gadi → Vehicle`() = assertTranslation("gadi", "Vehicle")

    // ── Furniture ──
    @Test fun `translate kursi → Chair`() = assertTranslation("kursi", "Chair")
    @Test fun `translate kursiyan → Chair`() = assertTranslation("kursiyan", "Chair")
    @Test fun `translate kursiya → Chair`() = assertTranslation("kursiya", "Chair")
    @Test fun `translate mez → Table`() = assertTranslation("mez", "Table")
    @Test fun `translate mej → Table`() = assertTranslation("mej", "Table")
    @Test fun `translate tebal → Table`() = assertTranslation("tebal", "Table")
    @Test fun `translate teble → Table`() = assertTranslation("teble", "Table")

    // ── Stationery ──
    @Test fun `translate kitab → Book`() = assertTranslation("kitab", "Book")
    @Test fun `translate kitaab → Book`() = assertTranslation("kitaab", "Book")
    @Test fun `translate kitaben → Book`() = assertTranslation("kitaben", "Book")
    @Test fun `translate kapi → Notebook`() = assertTranslation("kapi", "Notebook")
    @Test fun `translate pencil → Pencil`() = assertTranslation("pencil", "Pencil")
    @Test fun `translate kalam → Pen`() = assertTranslation("kalam", "Pen")
    @Test fun `translate rabar → Eraser`() = assertTranslation("rabar", "Eraser")

    // ── Clothing & Footwear ──
    @Test fun `translate kapda → Cloth`() = assertTranslation("kapda", "Cloth")
    @Test fun `translate kapde → Cloth`() = assertTranslation("kapde", "Cloth")
    @Test fun `translate juta → Shoes`() = assertTranslation("juta", "Shoes")
    @Test fun `translate joote → Shoes`() = assertTranslation("joote", "Shoes")
    @Test fun `translate chappal → Slippers`() = assertTranslation("chappal", "Slippers")

    // ── Business terms ──
    @Test fun `translate kiraya → Rent`() = assertTranslation("kiraya", "Rent")
    @Test fun `translate udhar → Outstanding`() = assertTranslation("udhar", "Outstanding")
    @Test fun `translate bakaya → Outstanding`() = assertTranslation("bakaya", "Outstanding")

    // ══════════════════════════════════════════════════════════════════════════
    // ═══════════  SECTION 2: translateItem — English plurals/variants  ═════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `translate mangoes → Mango`() = assertTranslation("mangoes", "Mango")
    @Test fun `translate mangos → Mango`() = assertTranslation("mangos", "Mango")
    @Test fun `translate apples → Apple`() = assertTranslation("apples", "Apple")
    @Test fun `translate oranges → Orange`() = assertTranslation("oranges", "Orange")
    @Test fun `translate bananas → Banana`() = assertTranslation("bananas", "Banana")
    @Test fun `translate tomatoes → Tomato`() = assertTranslation("tomatoes", "Tomato")
    @Test fun `translate potatoes → Potato`() = assertTranslation("potatoes", "Potato")
    @Test fun `translate onions → Onion`() = assertTranslation("onions", "Onion")
    @Test fun `translate chairs → Chair`() = assertTranslation("chairs", "Chair")
    @Test fun `translate tables → Table`() = assertTranslation("tables", "Table")
    @Test fun `translate pens → Pen`() = assertTranslation("pens", "Pen")
    @Test fun `translate books → Book`() = assertTranslation("books", "Book")
    @Test fun `translate fans → Fan`() = assertTranslation("fans", "Fan")
    @Test fun `translate tiles → Tiles`() = assertTranslation("tiles", "Tiles")
    @Test fun `translate bottles → Bottle`() = assertTranslation("bottles", "Bottle")

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 3: translateItem — ASR misspellings & normalization  ════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `translate wash basin → Wash Basin`() = assertTranslation("wash basin", "Wash Basin")
    @Test fun `translate wash baisen → Wash Basin`() = assertTranslation("wash baisen", "Wash Basin")
    @Test fun `translate bash baisen → Wash Basin`() = assertTranslation("bash baisen", "Wash Basin")
    @Test fun `translate bashbaisen → Wash Basin`() = assertTranslation("bashbaisen", "Wash Basin")
    @Test fun `translate basin → Wash Basin`() = assertTranslation("basin", "Wash Basin")
    @Test fun `translate cooler → Air Cooler`() = assertTranslation("cooler", "Air Cooler")
    @Test fun `translate dawai → Medicine`() = assertTranslation("dawai", "Medicine")
    @Test fun `translate dawa → Medicine`() = assertTranslation("dawa", "Medicine")
    @Test fun `translate coffe → Coffee`() = assertTranslation("coffe", "Coffee")
    @Test fun `translate coffee → Coffee`() = assertTranslation("coffee", "Coffee")
    @Test fun `translate biskut → Biscuits`() = assertTranslation("biskut", "Biscuits")
    @Test fun `translate choclet → Chocolate`() = assertTranslation("choclet", "Chocolate")

    // ── Repeated-letter collapse ──
    @Test fun `translate aaluo → Potato via normalization`() = assertTranslation("aaluo", "Potato")

    // ── Filler words → Goods ──
    @Test fun `translate filler 'maal' → Goods`() = assertTranslation("maal", "Goods")
    @Test fun `translate filler 'items' → Goods`() = assertTranslation("items", "Goods")
    @Test fun `translate filler 'rupees' → Goods`() = assertTranslation("rupees", "Goods")
    @Test fun `translate filler 'for' → Goods`() = assertTranslation("for", "Goods")

    // ── Title-casing for unknown multi-word items ──
    @Test fun `translate unknown item title-cases all words`() {
        val result = LanguageTranslationUtils.translateItem("leather belt")
        assertEquals("Leather Belt", result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 4: canonicalItemName — quantity extraction  ═════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `canonicalItemName strips leading qty '20 milk'`() {
        val (name, qty) = LanguageTranslationUtils.canonicalItemName("20 milk", 1.0)
        assertEquals("Milk", name)
        assertEquals(20.0, qty, 0.01)
    }

    @Test fun `canonicalItemName strips leading qty '5 wash basin'`() {
        val (name, qty) = LanguageTranslationUtils.canonicalItemName("5 wash basin", 1.0)
        assertEquals("Wash Basin", name)
        assertEquals(5.0, qty, 0.01)
    }

    @Test fun `canonicalItemName preserves explicit qty`() {
        val (name, qty) = LanguageTranslationUtils.canonicalItemName("10 atta", 10.0)
        assertEquals("Flour", name)
        assertEquals(10.0, qty, 0.01)
    }

    @Test fun `canonicalItemDetails detects unit prefix 'liter milk'`() {
        val (name, qty, unit) = LanguageTranslationUtils.canonicalItemDetails("5 liter milk", 1.0)
        assertEquals("Milk", name)
        assertEquals(5.0, qty, 0.01)
        assertEquals("Liter", unit)
    }

    @Test fun `canonicalItemDetails detects kg prefix 'kg rice'`() {
        val (name, qty, unit) = LanguageTranslationUtils.canonicalItemDetails("10 kg rice", 1.0)
        assertEquals("Rice", name)
        assertEquals(10.0, qty, 0.01)
        assertEquals("Kg", unit)
    }

    @Test fun `canonicalItemDetails detects dozen prefix`() {
        val (name, qty, unit) = LanguageTranslationUtils.canonicalItemDetails("2 dozen banana", 1.0)
        assertEquals("Banana", name)
        assertEquals(2.0, qty, 0.01)
        assertEquals("Dozen", unit)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 5: suggestCategory — correct category assignment  ═══════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `category for Mango → Fruits`() = assertCategory("Mango", "Fruits")
    @Test fun `category for Apple → Fruits`() = assertCategory("Apple", "Fruits")
    @Test fun `category for Potato → Vegetables`() = assertCategory("Potato", "Vegetables")
    @Test fun `category for Tomato → Vegetables`() = assertCategory("Tomato", "Vegetables")
    @Test fun `category for Onion → Vegetables`() = assertCategory("Onion", "Vegetables")
    @Test fun `category for Rice → Grains and Pulses`() = assertCategory("Rice", "Grains & Pulses")
    @Test fun `category for Wheat → Grains and Pulses`() = assertCategory("Wheat", "Grains & Pulses")
    @Test fun `category for Flour → Grains and Pulses`() = assertCategory("Flour", "Grains & Pulses")
    @Test fun `category for Sugar → Spices and Pantry`() = assertCategory("Sugar", "Spices & Pantry")
    @Test fun `category for Turmeric → Spices and Pantry`() = assertCategory("Turmeric", "Spices & Pantry")
    @Test fun `category for Milk → Dairy`() = assertCategory("Milk", "Dairy")
    @Test fun `category for Ghee → Dairy`() = assertCategory("Ghee", "Dairy")
    @Test fun `category for Chicken → Meat and Fish`() = assertCategory("Chicken", "Meat & Fish")
    @Test fun `category for Fish → Meat and Fish`() = assertCategory("Fish", "Meat & Fish")
    @Test fun `category for Almond → Dry Fruits`() = assertCategory("Almond", "Dry Fruits")
    @Test fun `category for Soap → Personal Care`() = assertCategory("Soap", "Personal Care")
    @Test fun `category for Cement → Construction`() = assertCategory("Cement", "Construction")
    @Test fun `category for Bricks → Construction`() = assertCategory("Bricks", "Construction")
    @Test fun `category for Chair → Furniture`() = assertCategory("Chair", "Furniture")
    @Test fun `category for Table → Furniture`() = assertCategory("Table", "Furniture")
    @Test fun `category for Pen → Stationery`() = assertCategory("Pen", "Stationery")
    @Test fun `category for Book → Stationery`() = assertCategory("Book", "Stationery")
    @Test fun `category for Pencil → Stationery`() = assertCategory("Pencil", "Stationery")
    @Test fun `category for Medicine → Healthcare`() = assertCategory("Medicine", "Healthcare")
    @Test fun `category for Fan → Electronics and Appliances`() = assertCategory("Fan", "Electronics & Appliances")

    // ── suggestCategory via translateItem (end-to-end Hindi → category) ──
    @Test fun `Hindi aam → Fruits category`() = assertCategoryViaTranslation("aam", "Fruits")
    @Test fun `Hindi aloo → Vegetables category`() = assertCategoryViaTranslation("aloo", "Vegetables")
    @Test fun `Hindi cheeni → Spices and Pantry category`() = assertCategoryViaTranslation("cheeni", "Spices & Pantry")
    @Test fun `Hindi doodh → Dairy category`() = assertCategoryViaTranslation("doodh", "Dairy")
    @Test fun `Hindi cement → Construction category`() = assertCategoryViaTranslation("cement", "Construction")
    @Test fun `Hindi kursi → Furniture category`() = assertCategoryViaTranslation("kursi", "Furniture")

    // ── suggestCategory keyword fallback (brand/supplement names) ──
    @Test fun `category for whey protein → Health Supplements`() {
        assertEquals("Health Supplements", LanguageTranslationUtils.suggestCategory("whey protein"))
    }
    @Test fun `category for mutant mass gainer → Health Supplements`() {
        assertEquals("Health Supplements", LanguageTranslationUtils.suggestCategory("mutant mass gainer"))
    }
    @Test fun `category for muscleblaze creatine → Health Supplements`() {
        assertEquals("Health Supplements", LanguageTranslationUtils.suggestCategory("muscleblaze creatine"))
    }
    @Test fun `category for cadbury dairy milk → Confectionery`() {
        assertEquals("Confectionery & Chocolates", LanguageTranslationUtils.suggestCategory("cadbury dairy milk"))
    }
    @Test fun `category for thums up → Beverages`() {
        assertEquals("Beverages", LanguageTranslationUtils.suggestCategory("thums up"))
    }
    @Test fun `category for maggi noodles → Noodles`() {
        assertEquals("Noodles", LanguageTranslationUtils.suggestCategory("maggi noodles"))
    }
    @Test fun `category for engine oil → Vehicle Parts`() {
        assertEquals("Vehicle Parts & Accessories", LanguageTranslationUtils.suggestCategory("engine oil"))
    }
    @Test fun `category for mustard oil → Cooking Oils`() {
        assertEquals("Cooking Oils", LanguageTranslationUtils.suggestCategory("mustard oil"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 6: getGstRate — GST 2.0 rate correctness  ══════════════
    // ══════════════════════════════════════════════════════════════════════════

    // 0% — fresh produce, essentials
    @Test fun `GST 0 for milk`() = assertGstRate("doodh", 0.0)
    @Test fun `GST 0 for potato`() = assertGstRate("aloo", 0.0)
    @Test fun `GST 0 for tomato`() = assertGstRate("tamatar", 0.0)
    @Test fun `GST 0 for onion`() = assertGstRate("pyaj", 0.0)
    @Test fun `GST 0 for rice`() = assertGstRate("chawal", 0.0)
    @Test fun `GST 0 for wheat`() = assertGstRate("gehun", 0.0)
    @Test fun `GST 0 for dal`() = assertGstRate("dal", 0.0)
    @Test fun `GST 0 for atta`() = assertGstRate("atta", 0.0)
    @Test fun `GST 0 for egg`() = assertGstRate("anda", 0.0)
    @Test fun `GST 0 for chicken`() = assertGstRate("murgi", 0.0)
    @Test fun `GST 0 for fish`() = assertGstRate("machli", 0.0)
    @Test fun `GST 0 for mango`() = assertGstRate("aam", 0.0)
    @Test fun `GST 0 for apple`() = assertGstRate("seb", 0.0)
    @Test fun `GST 0 for banana`() = assertGstRate("kela", 0.0)
    @Test fun `GST 0 for sabzi`() = assertGstRate("sabzi", 0.0)

    // 5% — sugar, tea, coffee, edible oils, spices
    @Test fun `GST 5 for sugar`() = assertGstRate("cheeni", 0.05)
    @Test fun `GST 5 for tea`() = assertGstRate("chai", 0.05)
    @Test fun `GST 5 for coffee`() = assertGstRate("coffee", 0.05)
    @Test fun `GST 5 for turmeric`() = assertGstRate("haldi", 0.05)
    @Test fun `GST 5 for jeera`() = assertGstRate("jeera", 0.05)
    @Test fun `GST 5 for spices`() = assertGstRate("masala", 0.05)

    // 5% — edible oil (not personal care oil)
    @Test fun `isFivePercentOil for mustard oil`() {
        assertTrue(LanguageTranslationUtils.isFivePercentOil("mustard oil"))
    }
    @Test fun `isFivePercentOil for sarson tel`() {
        assertTrue(LanguageTranslationUtils.isFivePercentOil("sarson tel"))
    }
    @Test fun `NOT isFivePercentOil for hair oil`() {
        assertFalse(LanguageTranslationUtils.isFivePercentOil("hair oil"))
    }
    @Test fun `NOT isFivePercentOil for baby oil`() {
        assertFalse(LanguageTranslationUtils.isFivePercentOil("baby oil"))
    }
    @Test fun `NOT isFivePercentOil for beard oil`() {
        assertFalse(LanguageTranslationUtils.isFivePercentOil("beard oil"))
    }

    // 12% — butter, cheese, ghee, dry fruits
    @Test fun `GST 12 for butter`() = assertGstRate("makhan", 0.12)
    @Test fun `GST 12 for ghee`() = assertGstRate("ghee", 0.12)
    @Test fun `GST 12 for paneer`() = assertGstRate("paneer", 0.12)
    @Test fun `GST 12 for badam`() = assertGstRate("badam", 0.12)
    @Test fun `GST 12 for kaju`() = assertGstRate("kaju", 0.12)

    // 18% — soap, toothpaste, paint, cement, general goods
    @Test fun `GST 18 for soap`() = assertGstRate("sabun", 0.18)
    @Test fun `GST 18 for toothpaste`() = assertGstRate("toothpaste", 0.18)
    @Test fun `GST 18 for paint`() = assertGstRate("paint", 0.18)
    @Test fun `GST 18 for cement`() = assertGstRate("cement", 0.18)

    // 28% — AC, fridge, washing machine (now 18% post GST 2.0 actually, but getGstRate returns 0.28 for AC)
    @Test fun `GST 28 for fridge`() = assertGstRate("fridge", 0.28)

    // ── Motorcycle price-dependent GST ──
    @Test fun `GST 18 for motorcycle under 2L`() {
        assertEquals(0.18, LanguageTranslationUtils.getGstRate("motorcycle", 150000.0), 0.001)
    }
    @Test fun `GST 40 for motorcycle over 2L`() {
        assertEquals(0.40, LanguageTranslationUtils.getGstRate("motorcycle", 250000.0), 0.001)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 7: itemKey — deduplication key  ════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `itemKey normalizes 'Apples' to 'apple'`() {
        assertEquals("apple", LanguageTranslationUtils.itemKey("Apples"))
    }
    @Test fun `itemKey normalizes 'TOMATOES' to 'tomato'`() {
        assertEquals("tomato", LanguageTranslationUtils.itemKey("TOMATOES"))
    }
    @Test fun `itemKey normalizes 'Potatoes' to 'potato'`() {
        assertEquals("potato", LanguageTranslationUtils.itemKey("Potatoes"))
    }
    @Test fun `itemKey strips 'ies' ending`() {
        assertEquals("berry", LanguageTranslationUtils.itemKey("Berries"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 8: HsnPresetData.sanitizeHsnEntry  ═════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `sanitize migrates 28pct tobacco → 40pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "2403", description = "Cigarettes and tobacco",
            keywords = "cigarette, tobacco", gstRate = 28.0, category = "Tobacco Products"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(40.0, sanitized.gstRate, 0.001)
    }

    @Test fun `sanitize migrates 28pct aerated drinks → 40pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "2202", description = "Aerated soft drink cola",
            keywords = "cola, soft drink, aerated", gstRate = 28.0, category = "Beverages"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(40.0, sanitized.gstRate, 0.001)
    }

    @Test fun `sanitize migrates 28pct generic to 18pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "8450", description = "Washing machines",
            keywords = "washing machine", gstRate = 28.0, category = "Appliances"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(18.0, sanitized.gstRate, 0.001)
    }

    @Test fun `sanitize bidi → 18pct not 40pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "2403", description = "Bidi / biri",
            keywords = "bidi, biri, beedi", gstRate = 28.0, category = "Tobacco Products"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(18.0, sanitized.gstRate, 0.001)
    }

    @Test fun `sanitize EV two-wheeler 871160 → 5pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "871160", description = "Electric scooter",
            keywords = "electric scooter, ev", gstRate = 18.0, category = "Two Wheelers"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(5.0, sanitized.gstRate, 0.001)
    }

    @Test fun `sanitize motorcycle above 350cc → 40pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "87114", description = "Motorcycles exceeding 350cc",
            keywords = "motorcycle, superbike", gstRate = 18.0, category = "Two Wheelers"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(40.0, sanitized.gstRate, 0.001)
    }

    @Test fun `sanitize motorcycle up to 350cc → 18pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "8711", description = "Motorcycles and scooters",
            keywords = "motorcycle, scooter", gstRate = 5.0, category = "Two Wheelers"
        )
        val sanitized = HsnPresetData.sanitizeHsnEntry(entry)
        assertEquals(18.0, sanitized.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading books 4901 → 0pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "4901", description = "Printed books",
            keywords = "book, textbook", gstRate = 5.0, category = "Books"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(0.0, corrected.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading chocolate 1806 → 18pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "1806", description = "Chocolate products",
            keywords = "chocolate, cadbury", gstRate = 5.0, category = "Food"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(18.0, corrected.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading namkeen 2106 → 5pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "2106", description = "Namkeen bhujia mixture",
            keywords = "namkeen, bhujia, sev", gstRate = 18.0, category = "Snacks"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(5.0, corrected.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading supplements 2106 → 18pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "2106", description = "Protein powder supplement",
            keywords = "whey, protein, supplement", gstRate = 5.0, category = "Supplements"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(18.0, corrected.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading soap 3401 → 18pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "3401", description = "Soap",
            keywords = "soap, bathing bar", gstRate = 5.0, category = "Personal Care"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(18.0, corrected.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading notebook 4820 → 0pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "4820", description = "Notebook exercise book copy",
            keywords = "notebook, exercise book, copy", gstRate = 18.0, category = "Stationery"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(0.0, corrected.gstRate, 0.001)
    }

    @Test fun `correctMisPricedHeading diary 4820 → 18pct`() {
        val entry = com.minhas.teller.data.entities.HsnEntry(
            hsnCode = "4820", description = "Diary and planner",
            keywords = "diary, planner, journal", gstRate = 0.0, category = "Stationery"
        )
        val corrected = HsnPresetData.correctMisPricedHeading(entry)
        assertEquals(18.0, corrected.gstRate, 0.001)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 9: HsnCommonItems — brand keyword smoke test  ══════════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `HsnCommonItems contains motorcycle entry with Splendor keyword`() {
        val entries = HsnCommonItems.entries()
        val motorcycleEntry = entries.find { it.hsnCode == "8711" && it.keywords.contains("splendor") }
        assertNotNull("Should have a motorcycle entry matching 'splendor'", motorcycleEntry)
        assertEquals(18.0, motorcycleEntry!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains Mutant supplement entry`() {
        val entries = HsnCommonItems.entries()
        val mutant = entries.find { it.keywords.contains("mutant mass") }
        assertNotNull("Should have a supplement entry matching 'mutant mass'", mutant)
        assertEquals("2106", mutant!!.hsnCode)
        assertEquals(18.0, mutant.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains chocolate Cadbury entry`() {
        val entries = HsnCommonItems.entries()
        val choc = entries.find { it.hsnCode == "1806" && it.keywords.contains("cadbury") }
        assertNotNull("Should have a chocolate entry matching 'cadbury'", choc)
        assertEquals(18.0, choc!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains electric scooter at 5pct`() {
        val entries = HsnCommonItems.entries()
        val ev = entries.find { it.hsnCode == "871160" }
        assertNotNull("Should have an EV two-wheeler entry", ev)
        assertEquals(5.0, ev!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems aerated drinks at 40pct`() {
        val entries = HsnCommonItems.entries()
        val soft = entries.find { it.hsnCode == "2202" && it.keywords.contains("thums up") }
        assertNotNull("Should have a soft drink entry matching 'thums up'", soft)
        assertEquals(40.0, soft!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems medicine at 5pct`() {
        val entries = HsnCommonItems.entries()
        val med = entries.find { it.hsnCode == "3004" }
        assertNotNull("Should have a medicine entry", med)
        assertEquals(5.0, med!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems plain biscuit at 5pct`() {
        val entries = HsnCommonItems.entries()
        val biscuit = entries.find { it.hsnCode == "1905" && it.keywords.contains("parle g") }
        assertNotNull("Should have a plain biscuit entry matching 'parle g'", biscuit)
        assertEquals(5.0, biscuit!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems cream biscuit at 18pct`() {
        val entries = HsnCommonItems.entries()
        val cream = entries.find { it.keywords.contains("oreo") }
        assertNotNull("Should have a cream biscuit entry matching 'oreo'", cream)
        assertEquals(18.0, cream!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems fresh potato at 0pct`() {
        val entries = HsnCommonItems.entries()
        val potato = entries.find { it.hsnCode == "0701" && it.keywords.contains("potato") }
        assertNotNull("Should have a fresh potato entry", potato)
        assertEquals(0.0, potato!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains mobile phones`() {
        val entries = HsnCommonItems.entries()
        val phones = entries.filter { it.hsnCode == "8517" }
        assertTrue("Should have multiple phone entries", phones.size >= 10)
        val allKeywords = phones.joinToString(" ") { it.keywords }
        assertTrue("Should contain iphone", allKeywords.contains("iphone"))
        assertTrue("Should contain samsung galaxy", allKeywords.contains("samsung galaxy"))
        assertTrue("Should contain oneplus", allKeywords.contains("oneplus"))
        assertTrue("Should contain redmi", allKeywords.contains("redmi"))
        assertTrue(phones.all { it.gstRate == 18.0 })
    }

    @Test fun `HsnCommonItems contains watches and smartwatches`() {
        val entries = HsnCommonItems.entries()
        val watches = entries.filter { it.hsnCode == "9102" }
        assertTrue("Should have multiple watch entries", watches.size >= 8)
        val allKeywords = watches.joinToString(" ") { it.keywords }
        assertTrue("Should contain apple watch", allKeywords.contains("apple watch"))
        assertTrue("Should contain titan", allKeywords.contains("titan"))
        assertTrue("Should contain g-shock", allKeywords.contains("g-shock"))
        assertTrue("Should contain fastrack", allKeywords.contains("fastrack"))
        assertTrue(watches.all { it.gstRate == 18.0 })
    }

    @Test fun `HsnCommonItems contains sunglasses and eyewear`() {
        val entries = HsnCommonItems.entries()
        val eyewear = entries.filter { it.hsnCode == "9004" }
        assertTrue("Should have eyewear entries", eyewear.isNotEmpty())
        val allKeywords = eyewear.joinToString(" ") { it.keywords }
        assertTrue("Should contain ray ban", allKeywords.contains("ray ban") || allKeywords.contains("ray-ban"))
        assertTrue("Should contain oakley", allKeywords.contains("oakley"))
        val sunglasses = eyewear.filter { it.description.contains("sunglass", ignoreCase = true) }
        assertTrue("Sunglasses should be 18%", sunglasses.all { it.gstRate == 18.0 })
        val spectacles = eyewear.find { it.description.contains("Spectacles", ignoreCase = true) }
        assertNotNull(spectacles)
        assertEquals("Corrective spectacles are 12%", 12.0, spectacles!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains headphones and earphones`() {
        val entries = HsnCommonItems.entries()
        val audio = entries.filter { it.hsnCode == "8518" }
        assertTrue("Should have audio entries", audio.isNotEmpty())
        val allKeywords = audio.joinToString(" ") { it.keywords }
        assertTrue("Should contain airpods", allKeywords.contains("airpods"))
        assertTrue("Should contain sony wh-1000xm5", allKeywords.contains("sony wh-1000xm5"))
        assertTrue("Should contain boat airdopes", allKeywords.contains("boat airdopes"))
        assertTrue(audio.all { it.gstRate == 18.0 })
    }

    @Test fun `HsnCommonItems contains restaurant items at 5pct`() {
        val entries = HsnCommonItems.entries()
        val restaurant = entries.filter { it.hsnCode == "9963" }
        assertTrue("Should have restaurant entries", restaurant.isNotEmpty())
        val allKeywords = restaurant.joinToString(" ") { it.keywords }
        assertTrue("Should contain biryani", allKeywords.contains("biryani"))
        assertTrue("Should contain paneer butter masala", allKeywords.contains("paneer butter masala"))
        assertTrue("Should contain butter chicken", allKeywords.contains("butter chicken"))
        assertTrue(restaurant.all { it.gstRate == 5.0 })
    }

    @Test fun `HsnCommonItems contains diagnostic lab tests at 0pct exempt`() {
        val entries = HsnCommonItems.entries()
        val labTests = entries.filter { it.hsnCode == "9993" }
        assertTrue("Should have lab test entries", labTests.isNotEmpty())
        val allKeywords = labTests.joinToString(" ") { it.keywords }
        assertTrue("Should contain cbc", allKeywords.contains("cbc"))
        assertTrue("Should contain lft", allKeywords.contains("lft"))
        assertTrue("Should contain lipid profile", allKeywords.contains("lipid profile"))
        assertTrue("Should contain widal", allKeywords.contains("widal"))
        assertTrue(labTests.all { it.gstRate == 0.0 })
    }

    @Test fun `HsnCommonItems contains lab reagents and equipment at 18pct`() {
        val entries = HsnCommonItems.entries()
        val reagent = entries.find { it.hsnCode == "3822" && it.keywords.contains("lab reagents") }
        assertNotNull("Should have diagnostic reagents under 3822", reagent)
        assertEquals(18.0, reagent!!.gstRate, 0.001)

        val vacutainer = entries.find { it.hsnCode == "9018" && it.keywords.contains("vacutainer") }
        assertNotNull("Should have vacutainers under 9018", vacutainer)
        assertEquals(18.0, vacutainer!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains paints and allied materials`() {
        val entries = HsnCommonItems.entries()
        val asianPaints = entries.find { it.hsnCode == "3209" && it.keywords.contains("asian paints") }
        assertNotNull("Should have Asian Paints under 3209", asianPaints)
        assertEquals(18.0, asianPaints!!.gstRate, 0.001)

        val wallPutty = entries.find { it.hsnCode == "3214" && it.keywords.contains("wall putty") }
        assertNotNull("Should have Wall Putty under 3214", wallPutty)
        assertEquals(18.0, wallPutty!!.gstRate, 0.001)

        val fevicol = entries.find { it.hsnCode == "3506" && it.keywords.contains("fevicol") }
        assertNotNull("Should have Fevicol under 3506", fevicol)
        assertEquals(18.0, fevicol!!.gstRate, 0.001)

        val pop = entries.find { it.hsnCode == "2520" && it.keywords.contains("pop") }
        assertNotNull("Should have POP under 2520 at 5pct", pop)
        assertEquals(5.0, pop!!.gstRate, 0.001)
    }

    @Test fun `HsnCommonItems contains extensive supplement brands`() {
        val entries = HsnCommonItems.entries()
        val supplements = entries.filter { it.category == "Health Supplements" }
        val allKeywords = supplements.joinToString(" ") { it.keywords }
        assertTrue("Should contain nutrabay", allKeywords.contains("nutrabay"))
        assertTrue("Should contain nakpro", allKeywords.contains("nakpro"))
        assertTrue("Should contain avvatar", allKeywords.contains("avvatar"))
        assertTrue("Should contain as-it-is", allKeywords.contains("as-it-is") || allKeywords.contains("asitis"))
        assertTrue("Should contain doctor's choice", allKeywords.contains("doctor's choice"))
        assertTrue("Should contain scitron", allKeywords.contains("scitron"))
        assertTrue("Should contain ronnie coleman", allKeywords.contains("ronnie coleman"))
        assertTrue("Should contain redcon1", allKeywords.contains("redcon1"))
        assertTrue("Should contain gorilla mode", allKeywords.contains("gorilla mode"))
        assertTrue("Should contain pintola", allKeywords.contains("pintola"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 10: Bulk smoke test — all entries sanitized, no 28%  ════
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `all fallback entries have no 28pct GST after sanitization`() {
        val entries = HsnPresetData.getFallbackEntries()
        val bad = entries.filter { it.gstRate == 28.0 }
        assertTrue(
            "Found ${bad.size} entries still at 28% after sanitization: ${bad.take(5).map { "${it.hsnCode}: ${it.description}" }}",
            bad.isEmpty()
        )
    }

    @Test fun `all fallback entries have positive or zero GST`() {
        val entries = HsnPresetData.getFallbackEntries()
        val bad = entries.filter { it.gstRate < 0.0 }
        assertTrue("Found entries with negative GST rates", bad.isEmpty())
    }

    @Test fun `fallback entries cover at least 100 items`() {
        val entries = HsnPresetData.getFallbackEntries()
        assertTrue("Expected at least 100 fallback entries, got ${entries.size}", entries.size >= 100)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  SECTION 11: Bulk end-to-end — Hindi input → correct GST rate  ══
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Parameterized-style bulk test: for each (hindiInput, expectedRate), verify the
     * full pipeline translateItem → getGstRate returns the correct GST rate.
     */
    @Test fun `bulk Hindi-to-GST rate verification`() {
        val expectations = listOf(
            // 0% items
            "aam" to 0.0, "seb" to 0.0, "kela" to 0.0, "santra" to 0.0,
            "aloo" to 0.0, "tamatar" to 0.0, "pyaj" to 0.0, "doodh" to 0.0,
            "chawal" to 0.0, "gehun" to 0.0, "atta" to 0.0, "dal" to 0.0,
            "anda" to 0.0, "murgi" to 0.0, "machli" to 0.0, "sabzi" to 0.0,
            // English plurals → 0%
            "apples" to 0.0, "potatoes" to 0.0, "tomatoes" to 0.0, "onions" to 0.0, "bananas" to 0.0,
            // 5%
            "cheeni" to 0.05, "shakkar" to 0.05, "chai" to 0.05, "coffee" to 0.05,
            "haldi" to 0.05, "jeera" to 0.05, "masala" to 0.05,
            // 12%
            "ghee" to 0.12, "paneer" to 0.12, "badam" to 0.12, "kaju" to 0.12,
            // 18%
            "sabun" to 0.18, "cement" to 0.18, "paint" to 0.18, "toothpaste" to 0.18
        )
        val failures = mutableListOf<String>()
        for ((input, expectedRate) in expectations) {
            val translated = LanguageTranslationUtils.translateItem(input)
            val actual = LanguageTranslationUtils.getGstRate(input)
            if (Math.abs(actual - expectedRate) > 0.001) {
                failures.add("'$input' → '$translated' expected GST=$expectedRate but got GST=$actual")
            }
        }
        assertTrue("GST rate mismatches:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══  Helpers  ═══════════════════════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    private fun assertTranslation(input: String, expected: String) {
        assertEquals("translateItem('$input')", expected, LanguageTranslationUtils.translateItem(input))
    }

    private fun assertCategory(translated: String, expected: String) {
        assertEquals("suggestCategory('$translated')", expected, LanguageTranslationUtils.suggestCategory(translated))
    }

    private fun assertCategoryViaTranslation(hindiInput: String, expected: String) {
        val translated = LanguageTranslationUtils.translateItem(hindiInput)
        assertEquals("suggestCategory(translateItem('$hindiInput')='$translated')", expected, LanguageTranslationUtils.suggestCategory(translated))
    }

    private fun assertGstRate(input: String, expected: Double) {
        assertEquals("getGstRate('$input')", expected, LanguageTranslationUtils.getGstRate(input), 0.001)
    }
}
