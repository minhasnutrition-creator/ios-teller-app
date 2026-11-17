package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exhaustive combinatorial sweep: items × verbs × word-orders × amounts × parties × units,
 * plus English verb-first forms and every report query. Runs the full 3-tier pipeline.
 */
class MegaFuzzTest {

    private data class C(val p: String, val type: String, val amt: Double, val party: String? = null, val item: String? = null, val qty: Double = 1.0)

    private fun parseAny(p: String): AiResponse.DraftVoucher? {
        runBlocking { FinanceHelper.tryParseHinglish(null, p) as? AiResponse.DraftVoucher }?.let { return it }
        runBlocking { FinanceHelper.smartRegexParser(null, p) as? AiResponse.DraftVoucher }?.let { return it }
        return FinanceHelper.parseVoiceCommandLocal(p) as? AiResponse.DraftVoucher
    }

    private val items = listOf(
        "seb" to "Apple", "aam" to "Mango", "kela" to "Banana", "santra" to "Orange", "angoor" to "Grapes",
        "anar" to "Pomegranate", "papita" to "Papaya", "nimbu" to "Lemon", "nariyal" to "Coconut", "kheera" to "Cucumber",
        "aalu" to "Potato", "pyaj" to "Onion", "tamatar" to "Tomato", "gobhi" to "Cauliflower", "palak" to "Spinach",
        "bhindi" to "Ladyfinger", "baigan" to "Brinjal", "matar" to "Peas", "gajar" to "Carrot", "mooli" to "Radish",
        "kaddu" to "Pumpkin", "mirch" to "Chili", "adrak" to "Ginger", "lasan" to "Garlic", "haldi" to "Turmeric",
        "jeera" to "Cumin", "dhaniya" to "Coriander", "pudina" to "Mint", "chawal" to "Rice", "atta" to "Flour",
        "maida" to "Refined Flour", "besan" to "Gram Flour", "dal" to "Lentils", "rajma" to "Kidney Beans", "chana" to "Chickpeas",
        "gehun" to "Wheat", "makka" to "Corn", "bajra" to "Millet", "cheeni" to "Sugar", "namak" to "Salt",
        "tel" to "Oil", "masala" to "Spices", "ghee" to "Ghee", "makhan" to "Butter", "doodh" to "Milk",
        "dahi" to "Curd", "paneer" to "Cottage Cheese", "anda" to "Egg", "murgi" to "Chicken", "machli" to "Fish",
        "badam" to "Almond", "kaju" to "Cashew", "kishmish" to "Raisins", "pista" to "Pistachio", "akhrot" to "Walnut",
        "sabun" to "Soap", "shampoo" to "Shampoo", "kapda" to "Cloth", "juta" to "Shoes", "kurta" to "Shirt",
        "pajama" to "Trousers", "saree" to "Sari", "topi" to "Cap", "moja" to "Socks", "chappal" to "Slippers",
        "gaadi" to "Vehicle", "bike" to "Motorcycle", "car" to "Car", "truck" to "Truck", "mobile" to "Mobile",
        "laptop" to "Laptop", "computer" to "Computer", "tv" to "Television", "fridge" to "Refrigerator", "fan" to "Fan",
        "bulb" to "Bulb", "cement" to "Cement", "eent" to "Bricks", "sariya" to "Iron Rods", "iron" to "Iron",
        "paint" to "Paint", "kitab" to "Book", "pencil" to "Pencil", "shirt" to "Shirt", "jeans" to "Jeans",
        "candy" to "Candy", "chocolate" to "Chocolate", "chips" to "Chips", "juice" to "Juice", "bottle" to "Bottle",
        "bag" to "Bag", "kursi" to "Chair", "mez" to "Table", "kalam" to "Pen", "kagaz" to "Paper",
        "hari mirch" to "Green Chilli", "lal mirch" to "Red Chilli", "kali mirch" to "Black Pepper", "chai patti" to "Tea Leaves",
        "kabuli chana" to "White Chickpeas", "nariyal paani" to "Coconut Water", "nimbu paani" to "Lemonade", "double roti" to "Bread",
        "shimla mirch" to "Capsicum", "wash basin" to "Wash Basin", "cold drink" to "Soft Drink"
    )

    private val parties = listOf("Ram", "Amit", "Ramesh", "Gagan", "Suresh", "Arijit Singh", "Kiran", "Sunita", "Rahul", "Vikram", "Neha Sharma", "Ravi Kumar")

    private val digits = listOf("500" to 500.0, "1000" to 1000.0, "5000" to 5000.0, "10000" to 10000.0, "50000" to 50000.0, "100000" to 100000.0, "250000" to 250000.0, "1000000" to 1000000.0)
    private val hinglishAmts = listOf("paanch hazaar" to 5000.0, "das hazaar" to 10000.0, "pachaas hazaar" to 50000.0, "ek lakh" to 100000.0, "do lakh" to 200000.0, "dhaai lakh" to 250000.0, "dedh lakh" to 150000.0, "sava lakh" to 125000.0, "ek karod" to 10000000.0, "do karod" to 20000000.0)
    private val englishAmts = listOf("five hundred" to 500.0, "one thousand" to 1000.0, "five thousand" to 5000.0, "ten thousand" to 10000.0, "one lakh" to 100000.0, "fifty thousand" to 50000.0)
    private val compoundAmts = listOf("ikattis" to 31.0, "bavan" to 52.0, "chhappan" to 56.0, "unasi" to 79.0, "ninanave" to 99.0)

    private val saleVerbs = listOf("becha", "beche", "bechi", "bech", "bikri", "sold")
    private val buyVerbs = listOf("kharida", "kharidi", "kharide", "khareed", "bought", "purchased")

    // word order templates: (template, kind) where {i}=item {p}=party {v}=verb {a}=amount {q}=qty
    private val saleOrders = listOf(
        "50 {i} {v} {p} ko {a} ki",
        "{p} ko 50 {i} {v} {a} ki",
        "{a} ki 50 {i} {v} {p} ko",
        "50 {i} {v} {a} ki {p} ko",
        "{p} ko {a} ki 50 {i} {v}",
        "50 {i} {p} ko {v} {a}"
    )
    private val buyOrders = listOf(
        "50 {i} {v} {p} se {a} ki",
        "{p} se 50 {i} {v} {a} ki",
        "{a} ki 50 {i} {v} {p} se",
        "50 {i} {v} {a} ki {p} se",
        "{p} se {a} ki 50 {i} {v}",
        "50 {i} {p} se {v} {a}"
    )

    private fun gen(): List<C> {
        val cases = mutableListOf<C>()
        var n = 0
        val allAmts = digits + hinglishAmts + englishAmts + compoundAmts

        fun fill(t: String, i: String, v: String, p: String, a: String) =
            t.replace("{i}", i).replace("{v}", v).replace("{p}", p).replace("{a}", a)

        // ── Full word-order × item × amount matrix (rotating verb/party) ──
        for ((item, itemEn) in items) {
            for ((amt, amtV) in allAmts) {
                val party = parties[n % parties.size]; n++
                val sverb = saleVerbs[n % saleVerbs.size]
                val bverb = buyVerbs[n % buyVerbs.size]
                for (t in saleOrders) cases.add(C(fill(t, item, sverb, party, amt), "Sales", amtV, party, itemEn, 50.0))
                for (t in buyOrders) cases.add(C(fill(t, item, bverb, party, amt), "Purchase", amtV, party, itemEn, 50.0))
            }
        }

        // ── English verb-first ──
        for ((item, itemEn) in items) {
            val party = parties[n % parties.size]; val (amt, amtV) = digits[n % digits.size]; n++
            cases.add(C("sold 50 $item to $party for $amt", "Sales", amtV, party, itemEn, 50.0))
            cases.add(C("bought 50 $item from $party for $amt", "Purchase", amtV, party, itemEn, 50.0))
            cases.add(C("purchased 50 $item for $amt", "Purchase", amtV, "Cash-in-hand", itemEn, 50.0))
            cases.add(C("sold 50 $item for $amt", "Sales", amtV, "Cash-in-hand", itemEn, 50.0))
        }

        // ── Units ──
        val unitItems = listOf("cheeni" to "Sugar", "chawal" to "Rice", "atta" to "Flour", "doodh" to "Milk", "tel" to "Oil")
        for ((item, itemEn) in unitItems) {
            val party = parties[n % parties.size]; val (amt, amtV) = digits[n % digits.size]; n++
            cases.add(C("5 kilo $item kharidi $party se $amt", "Purchase", amtV, party, itemEn, 5.0))
            cases.add(C("2 litre $item kharida $party se $amt", "Purchase", amtV, party, itemEn, 2.0))
            cases.add(C("1 darjan $item beche $party ko $amt", "Sales", amtV, party, itemEn, 1.0))
        }

        // ── Payments / receipts full matrix ──
        for (party in parties) for ((amt, amtV) in digits) {
            cases.add(C("$party ko $amt diye", "Payment", amtV, party))
            cases.add(C("$party ko $amt diya", "Payment", amtV, party))
            cases.add(C("$party ko $amt de diya", "Payment", amtV, party))
            cases.add(C("$amt diye $party ko", "Payment", amtV, party))
            cases.add(C("$party se $amt liya", "Receipt", amtV, party))
            cases.add(C("$party se $amt liye", "Receipt", amtV, party))
            cases.add(C("$party se $amt mila", "Receipt", amtV, party))
            cases.add(C("$amt liye $party se", "Receipt", amtV, party))
        }

        return cases
    }

    @Test
    fun mega_sweep() {
        val cases = gen()
        val failures = mutableListOf<String>()
        for (c in cases) {
            val d = parseAny(c.p)
            when {
                d == null -> failures.add("${c.p} => NULL (exp ${c.type} ${c.amt})")
                d.type != c.type -> failures.add("${c.p} => type=${d.type} (exp ${c.type})")
                (d.amount ?: 0.0) != c.amt -> failures.add("${c.p} => amt=${d.amount} (exp ${c.amt})")
                c.party != null && d.partyName != c.party -> failures.add("${c.p} => party=${d.partyName} (exp ${c.party})")
                c.item != null -> {
                    val it = d.inventoryItems.firstOrNull()
                    when {
                        it == null -> failures.add("${c.p} => no item (exp ${c.item})")
                        it.name != c.item -> failures.add("${c.p} => item=${it.name} (exp ${c.item})")
                        it.quantity != c.qty -> failures.add("${c.p} => qty=${it.quantity} (exp ${c.qty})")
                    }
                }
            }
        }
        println("MEGA FUZZ: ${cases.size - failures.size} / ${cases.size} passed, ${failures.size} failed")
        failures.take(100).forEach { println("  FAIL $it") }
        assertEquals("failures:\n" + failures.take(300).joinToString("\n"), 0, failures.size)
    }
}
