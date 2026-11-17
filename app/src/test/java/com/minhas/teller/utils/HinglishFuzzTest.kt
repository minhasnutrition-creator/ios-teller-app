package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Combinatorial fuzz sweep of the offline Hinglish/English transaction parser. Generates a large
 * matrix of phrases (items × verbs × word-orders × amounts × parties) and asserts type + amount
 * (+ party/item/qty where deterministic) against ground truth.
 */
class HinglishFuzzTest {

    private data class Case(
        val phrase: String, val type: String, val amount: Double,
        val party: String? = null, val item: String? = null, val qty: Double = 1.0
    )

    private fun parseAny(phrase: String): AiResponse.DraftVoucher? {
        runBlocking { FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher }?.let { return it }
        runBlocking { FinanceHelper.smartRegexParser(null, phrase) as? AiResponse.DraftVoucher }?.let { return it }
        return FinanceHelper.parseVoiceCommandLocal(phrase) as? AiResponse.DraftVoucher
    }

    private val items = listOf(
        // Fruits & veg & groceries
        "seb" to "Apple", "aam" to "Mango", "kela" to "Banana", "santra" to "Orange",
        "angoor" to "Grapes", "anar" to "Pomegranate", "papita" to "Papaya", "nimbu" to "Lemon",
        "nariyal" to "Coconut", "kheera" to "Cucumber", "aalu" to "Potato", "pyaj" to "Onion",
        "tamatar" to "Tomato", "gobhi" to "Cauliflower", "palak" to "Spinach", "bhindi" to "Ladyfinger",
        "baigan" to "Brinjal", "matar" to "Peas", "gajar" to "Carrot", "mooli" to "Radish",
        "kaddu" to "Pumpkin", "mirch" to "Chili", "adrak" to "Ginger", "lasan" to "Garlic",
        "haldi" to "Turmeric", "jeera" to "Cumin", "dhaniya" to "Coriander", "pudina" to "Mint",
        "chawal" to "Rice", "atta" to "Flour", "maida" to "Refined Flour", "besan" to "Gram Flour",
        "dal" to "Lentils", "rajma" to "Kidney Beans", "chana" to "Chickpeas", "gehun" to "Wheat",
        "makka" to "Corn", "bajra" to "Millet", "cheeni" to "Sugar", "namak" to "Salt",
        "tel" to "Oil", "masala" to "Spices", "ghee" to "Ghee", "makhan" to "Butter",
        "doodh" to "Milk", "dahi" to "Yogurt", "paneer" to "Cottage Cheese", "anda" to "Egg",
        "murgi" to "Chicken", "machli" to "Fish", "badam" to "Almond", "kaju" to "Cashew",
        "kishmish" to "Raisins", "pista" to "Pistachio", "akhrot" to "Walnut",
        // Non-food
        "sabun" to "Soap", "shampoo" to "Shampoo", "kapda" to "Cloth", "juta" to "Shoes",
        "kurta" to "Shirt", "pajama" to "Trousers", "saree" to "Sari", "topi" to "Cap",
        "moja" to "Socks", "chappal" to "Slippers", "gaadi" to "Vehicle", "bike" to "Motorcycle",
        "car" to "Car", "truck" to "Truck", "mobile" to "Mobile", "laptop" to "Laptop",
        "computer" to "Computer", "tv" to "Television", "fridge" to "Refrigerator", "fan" to "Fan",
        "bulb" to "Bulb", "cement" to "Cement", "eent" to "Bricks", "sariya" to "Iron Rods",
        "iron" to "Iron", "paint" to "Paint", "kitab" to "Book", "pencil" to "Pencil",
        "shirt" to "Shirt", "jeans" to "Jeans", "candy" to "Candy", "chocolate" to "Chocolate",
        "chips" to "Chips", "juice" to "Juice", "bottle" to "Bottle", "bag" to "Bag",
        "kursi" to "Chair", "mez" to "Table", "kalam" to "Pen", "kagaz" to "Paper",
        // Multi-word items
        "hari mirch" to "Green Chilli", "lal mirch" to "Red Chilli", "kali mirch" to "Black Pepper",
        "chai patti" to "Tea Leaves", "kabuli chana" to "White Chickpeas", "nariyal paani" to "Coconut Water",
        "nimbu paani" to "Lemonade", "double roti" to "Bread", "kasuri methi" to "Dry Fenugreek Leaves",
        "shimla mirch" to "Capsicum", "wash basin" to "Wash Basin", "cold drink" to "Soft Drink"
    )

    private val parties = listOf("Ram", "Amit", "Ramesh", "Gagan", "Suresh", "Arijit Singh", "Kiran")

    private val digitAmounts = listOf(
        "500" to 500.0, "1000" to 1000.0, "5000" to 5000.0,
        "10000" to 10000.0, "50000" to 50000.0, "100000" to 100000.0, "250000" to 250000.0
    )

    private val wordAmounts = listOf(
        "paanch hazaar" to 5000.0, "das hazaar" to 10000.0, "pachaas hazaar" to 50000.0,
        "ek lakh" to 100000.0, "do lakh" to 200000.0, "dhaai lakh" to 250000.0,
        "ek karod" to 10000000.0
    )

    private val purchaseVerbs = listOf("kharida", "kharidi", "kharide", "khariden", "kharidte", "khareed", "khareeda", "bought", "purchased")
    private val saleVerbs = listOf("becha", "beche", "bechi", "bech", "becho", "bika", "bikri", "sold")
    private val payVerbs = listOf("diya", "diye", "de diya", "chukaya", "pay kiya")
    private val recvVerbs = listOf("liya", "liye", "mila", "mile", "lena")

    private fun generate(): List<Case> {
        val cases = mutableListOf<Case>()
        fun add(p: String, type: String, amt: Double, party: String? = null, item: String? = null, qty: Double = 1.0) {
            cases.add(Case(p, type, amt, party, item, qty))
        }
        val allAmounts = digitAmounts + wordAmounts
        var n = 0

        // ── Core sweep: every item × amount × party on the two canonical orders ──
        for ((item, _) in items) {
            val itemEn = LanguageTranslationUtils.translateItem(item)
            for ((amt, amtV) in allAmounts) {
                val party = parties[n % parties.size]; n++
                add("50 $item kharida $party se $amt ki", "Purchase", amtV, party, itemEn, 50.0)
                add("50 $item becha $party ko $amt ki", "Sales", amtV, party, itemEn, 50.0)
            }
        }

        // ── Verb-variant sweep (purchase + sale word orders) ──
        for ((item, _) in items) {
            val itemEn = LanguageTranslationUtils.translateItem(item)
            val party = parties[n % parties.size]; val (amt, amtV) = allAmounts[n % allAmounts.size]; n++
            for (verb in purchaseVerbs) {
                add("50 $item $verb $party se $amt ki", "Purchase", amtV, party, itemEn, 50.0)
                add("$party se 50 $item $verb $amt ki", "Purchase", amtV, party, itemEn, 50.0)
            }
            for (verb in saleVerbs) {
                add("50 $item $verb $party ko $amt ki", "Sales", amtV, party, itemEn, 50.0)
                add("$party ko 50 $item $verb $amt ki", "Sales", amtV, party, itemEn, 50.0)
            }
        }

        // ── Payment / receipt verb-variant sweep ──
        for (party in parties) {
            for ((amt, amtV) in digitAmounts) {
                for (verb in payVerbs) add("$party ko $amt $verb", "Payment", amtV, party)
                for (verb in recvVerbs) add("$party se $amt $verb", "Receipt", amtV, party)
            }
        }

        // ── Expenses ──
        for ((amt, amtV) in digitAmounts) {
            add("kiraya diya $amt", "Payment", amtV)
            add("salary diya $amt", "Payment", amtV)
            add("bijli diya $amt", "Payment", amtV)
            add("petrol diya $amt", "Payment", amtV)
            add("rent diya $amt", "Payment", amtV)
            add("pagar diya $amt", "Payment", amtV)
            add("diesel diya $amt", "Payment", amtV)
            add("electricity diya $amt", "Payment", amtV)
        }

        return cases
    }

    @Test
    fun fuzz_extended() {
        val cases = generate()
        val failures = mutableListOf<String>()
        for (c in cases) {
            val d = parseAny(c.phrase)
            if (d == null) { failures.add("${c.phrase}  => NULL (exp ${c.type} ${c.amount})"); continue }
            if (d.type != c.type) { failures.add("${c.phrase}  => type=${d.type} (exp ${c.type})"); continue }
            if ((d.amount ?: 0.0) != c.amount) { failures.add("${c.phrase}  => amt=${d.amount} (exp ${c.amount})"); continue }
            if (c.party != null && d.partyName != c.party) { failures.add("${c.phrase}  => party=${d.partyName} (exp ${c.party})"); continue }
            if (c.item != null) {
                val it = d.inventoryItems.firstOrNull()
                if (it == null) { failures.add("${c.phrase}  => no item (exp ${c.item})"); continue }
                if (it.name != c.item) { failures.add("${c.phrase}  => item=${it.name} (exp ${c.item})"); continue }
                if (it.quantity != c.qty) failures.add("${c.phrase}  => qty=${it.quantity} (exp ${c.qty})")
            }
        }
        println("FUZZ: ${cases.size - failures.size} / ${cases.size} passed, ${failures.size} failed")
        failures.take(80).forEach { println("  FAIL $it") }
        assertEquals("fuzz failures:\n" + failures.take(200).joinToString("\n"), 0, failures.size)
    }
}
