package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OrderPermutationsFuzzTest {

    private fun parse(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        (FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher)
            ?: (FinanceHelper.smartRegexParser(null, phrase) as? AiResponse.DraftVoucher)
            ?: (FinanceHelper.parseVoiceCommandLocal(phrase) as? AiResponse.DraftVoucher)
    }

    private fun check(phrase: String, expectedType: String, expectedAmount: Double, expectedParty: String?, expectedItem: String?, expectedQty: Double?) {
        val result = parse(phrase)
        println("TEST [$phrase] => Type=${result?.type}, Amt=${result?.amount}, Party=${result?.partyName}, Items=${result?.inventoryItems}")
        assertNotNull("Failed to parse: '$phrase'", result)
        assertEquals("[$phrase] Voucher Type mismatch", expectedType, result!!.type)
        assertEquals("[$phrase] Amount mismatch", expectedAmount, result.amount ?: 0.0, 0.01)
        if (expectedParty != null) {
            assertEquals("[$phrase] Party mismatch", expectedParty.lowercase(), result.partyName?.lowercase())
        }
        if (expectedItem != null) {
            val item = result.inventoryItems.firstOrNull()
            assertNotNull("[$phrase] Expected item '$expectedItem' but none found", item)
            assertEquals("[$phrase] Item mismatch", expectedItem.lowercase(), item?.name?.lowercase())
            if (expectedQty != null) {
                assertEquals("[$phrase] Quantity mismatch", expectedQty, item?.quantity ?: 0.0, 0.01)
            }
        }
    }

    @Test
    fun testSalesAllOrderPermutations() {
        // Permutation 1: Amount First, Item Second, Party Third (A-I-P)
        check("5000 me 10 kg chawal Ramesh ko beche", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("5000 ke 10 packet doodh Ramesh ko beche", "Sales", 5000.0, "Ramesh", "Milk", 10.0)
        check("for 5000, 10 kg rice sold to Ramesh", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("५००० में १० किलो चावल रमेश को बेचा", "Sales", 5000.0, "Ramesh", "Rice", 10.0)

        // Permutation 2: Amount First, Party Second, Item Third (A-P-I)
        check("5000 me Ramesh ko 10 kg chawal beche", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("5000 ke Ramesh ko 10 packet doodh beche", "Sales", 5000.0, "Ramesh", "Milk", 10.0)
        check("for 5000 to Ramesh sold 10 kg rice", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("५००० में रमेश को १० किलो चावल बेचा", "Sales", 5000.0, "Ramesh", "Rice", 10.0)

        // Permutation 3: Item First, Amount Second, Party Third (I-A-P)
        check("10 kg chawal 5000 me Ramesh ko beche", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("10 packet doodh 500 me Ramesh ko beche", "Sales", 500.0, "Ramesh", "Milk", 10.0)
        check("10 kg rice for 5000 sold to Ramesh", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("१० किलो चावल ५००० में रमेश को बेचा", "Sales", 5000.0, "Ramesh", "Rice", 10.0)

        // Permutation 4: Item First, Party Second, Amount Third (I-P-A)
        check("10 kg chawal Ramesh ko 5000 me beche", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("10 packet doodh Ramesh ko beche 500 me", "Sales", 500.0, "Ramesh", "Milk", 10.0)
        check("10 kg rice to Ramesh sold for 5000", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("10 kg rice sold to Ramesh for 5000", "Sales", 5000.0, "Ramesh", "Rice", 10.0)

        // Permutation 5: Party First, Amount Second, Item Third (P-A-I)
        check("Ramesh ko 5000 me 10 kg chawal beche", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("Ramesh ko 5000 ke 10 packet doodh beche", "Sales", 5000.0, "Ramesh", "Milk", 10.0)
        check("to Ramesh for 5000 sold 10 kg rice", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("रमेश को ५००० में १० किलो चावल बेचा", "Sales", 5000.0, "Ramesh", "Rice", 10.0)

        // Permutation 6: Party First, Item Second, Amount Third (P-I-A)
        check("Ramesh ko 10 kg chawal 5000 me beche", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
        check("Ramesh ko 10 packet doodh beche 500 me", "Sales", 500.0, "Ramesh", "Milk", 10.0)
        check("to Ramesh 10 kg rice sold for 5000", "Sales", 5000.0, "Ramesh", "Rice", 10.0)
    }

    @Test
    fun testPurchasesAllOrderPermutations() {
        // Permutation 1: Amount First, Item Second, Party Third (A-I-P)
        check("3000 me 5 bori cement Suresh se kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("3000 ka 5 bori cement Suresh se kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("for 3000, 5 bags cement bought from Suresh", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("३००० में ५ बोरी सीमेंट सुरेश से खरीदा", "Purchase", 3000.0, "Suresh", "Cement", 5.0)

        // Permutation 2: Amount First, Party Second, Item Third (A-P-I)
        check("3000 me Suresh se 5 bori cement kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("3000 ka Suresh se 5 bori cement kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("for 3000 from Suresh bought 5 bags cement", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("३००० में सुरेश से ५ बोरी सीमेंट खरीदा", "Purchase", 3000.0, "Suresh", "Cement", 5.0)

        // Permutation 3: Item First, Amount Second, Party Third (I-A-P)
        check("5 bori cement 3000 me Suresh se kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("5 bori cement 3000 ka Suresh se kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("5 bags cement for 3000 bought from Suresh", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("५ बोरी सीमेंट ३००० में सुरेश से खरीदा", "Purchase", 3000.0, "Suresh", "Cement", 5.0)

        // Permutation 4: Item First, Party Second, Amount Third (I-P-A)
        check("5 bori cement Suresh se 3000 me kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("5 bori cement Suresh se kharida 3000 me", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("5 bags cement from Suresh bought for 3000", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("5 bags cement bought from Suresh for 3000", "Purchase", 3000.0, "Suresh", "Cement", 5.0)

        // Permutation 5: Party First, Amount Second, Item Third (P-A-I)
        check("Suresh se 3000 me 5 bori cement kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("Suresh se 3000 ka 5 bori cement kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("from Suresh for 3000 bought 5 bags cement", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("सुरेश से ३००० में ५ बोरी सीमेंट खरीदा", "Purchase", 3000.0, "Suresh", "Cement", 5.0)

        // Permutation 6: Party First, Item Second, Amount Third (P-I-A)
        check("Suresh se 5 bori cement 3000 me kharida", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("Suresh se 5 bori cement kharida 3000 me", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
        check("from Suresh 5 bags cement bought for 3000", "Purchase", 3000.0, "Suresh", "Cement", 5.0)
    }
}
