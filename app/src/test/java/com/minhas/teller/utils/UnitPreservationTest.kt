package com.minhas.teller.utils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The spoken unit ("20 litre milk", "10 kg sugar") must be preserved on the draft item. */
class UnitPreservationTest {
    private fun t1(p: String) = runBlocking { FinanceHelper.tryParseHinglish(null, p) as? AiResponse.DraftVoucher }

    private fun check(p: String, item: String, qty: Double, unit: String?) {
        val it = t1(p)?.inventoryItems?.firstOrNull()
        assertEquals("[$p] item", item, it?.name)
        assertEquals("[$p] qty", qty, it?.quantity ?: 0.0, 0.001)
        assertEquals("[$p] unit", unit, it?.unit)
    }

    @Test fun units_kept() {
        check("20 litre doodh kharida 500", "Milk", 20.0, "Liter")
        check("10 kg sugar kharida 500", "Sugar", 10.0, "Kg")
        check("5 kilo cheeni kharidi 500", "Sugar", 5.0, "Kg")
        check("5 killo aaloo 100 me becha", "Potato", 5.0, "Kg")
        check("5 killo aaloo 100", "Potato", 5.0, "Kg")
        check("20 liter milk becha 1000 me", "Milk", 20.0, "Liter")
        check("20 liter milk 1000 me", "Milk", 20.0, "Liter")
        check("sell 20 liter milk for 1000", "Milk", 20.0, "Liter")
        check("20 liter milk 1000", "Milk", 20.0, "Liter")
    }

    @Test fun pieces_explicit_and_no_unit() {
        // Explicitly-spoken "pieces" must be kept, not overridden by the banana→Dozen default.
        check("sold 50 pieces banana to ankit for 500", "Banana", 50.0, "Pcs")
        // No unit spoken → the draft carries no unit; the save path then uses the item's saved unit.
        check("sold 50 banana to ankit for 500", "Banana", 50.0, null)
    }

    @Test fun canonicalItemDetails_respects_explicit_unit() {
        val (n1, q1, u1) = LanguageTranslationUtils.canonicalItemDetails("Banana", 50.0, "Pcs")
        assertEquals("Banana", n1)
        assertEquals(50.0, q1, 0.001)
        assertEquals("Pcs", u1)

        val (n2, q2, u2) = LanguageTranslationUtils.canonicalItemDetails("Banana", 50.0, null)
        assertEquals("Banana", n2)
        assertEquals(50.0, q2, 0.001)
        assertEquals(null, u2)
    }
}
