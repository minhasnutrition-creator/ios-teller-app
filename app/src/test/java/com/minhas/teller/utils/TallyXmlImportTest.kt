package com.minhas.teller.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class TallyXmlImportTest {

    @Test
    fun testParseTallyXml_mastersAndVouchers() {
        val sampleTallyXml = """
            <ENVELOPE>
                <HEADER>
                    <TALLYREQUEST>Export Data</TALLYREQUEST>
                </HEADER>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <TALLYMESSAGE xmlns:UDF="TallyUDF">
                                <LEDGER NAME="Sharma Trading Co" ACTION="Create">
                                    <NAME>Sharma Trading Co</NAME>
                                    <PARENT>Sundry Debtors</PARENT>
                                    <OPENINGBALANCE>-5000.00</OPENINGBALANCE>
                                </LEDGER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE xmlns:UDF="TallyUDF">
                                <LEDGER NAME="Sales Account" ACTION="Create">
                                    <NAME>Sales Account</NAME>
                                    <PARENT>Sales Accounts</PARENT>
                                    <OPENINGBALANCE>0</OPENINGBALANCE>
                                </LEDGER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE xmlns:UDF="TallyUDF">
                                <STOCKITEM NAME="Basmati Rice Premium" ACTION="Create">
                                    <NAME>Basmati Rice Premium</NAME>
                                    <BASEUNITS>Kg</BASEUNITS>
                                    <OPENINGBALANCE> 50.00 Kg</OPENINGBALANCE>
                                    <OPENINGVALUE>-4000.00</OPENINGVALUE>
                                    <OPENINGRATE>80.00/Kg</OPENINGRATE>
                                    <HSNCODE>10063020</HSNCODE>
                                    <GSTRATE>5.0</GSTRATE>
                                </STOCKITEM>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE xmlns:UDF="TallyUDF">
                                <VOUCHER VCHTYPE="Sales" ACTION="Create">
                                    <DATE>20260401</DATE>
                                    <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
                                    <VOUCHERNUMBER>INV-1001</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Sharma Trading Co</PARTYLEDGERNAME>
                                    <NARRATION>Sale of rice on credit</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sharma Trading Co</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-10500.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sales Account</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>10000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>CGST Output</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>250.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>SGST Output</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>250.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLINVENTORYENTRIES.LIST>
                                        <STOCKITEMNAME>Basmati Rice Premium</STOCKITEMNAME>
                                        <ACTUALQTY> 100.00 Kg</ACTUALQTY>
                                        <RATE>100.00/Kg</RATE>
                                        <AMOUNT>-10000.00</AMOUNT>
                                    </ALLINVENTORYENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(sampleTallyXml.toByteArray(Charsets.UTF_8))
        val result = UniversalParser.parseTallyXml(inputStream)

        // Verify Masters
        assertEquals(2, result.ledgers.size)
        val sharma = result.ledgers.find { it.name == "Sharma Trading Co" }
        assertNotNull(sharma)
        assertEquals("Sundry Debtors", sharma!!.parentGroup)
        assertEquals(5000.0, sharma.openingBalance, 0.01)

        val sales = result.ledgers.find { it.name == "Sales Account" }
        assertNotNull(sales)
        assertEquals("Sales Accounts", sales!!.parentGroup)

        // Verify Stock Item
        assertEquals(1, result.stockItems.size)
        val rice = result.stockItems[0]
        assertEquals("Basmati Rice Premium", rice.name)
        assertEquals("Kg", rice.unitName)
        assertEquals(50.0, rice.openingQty, 0.01)
        assertEquals(4000.0, rice.openingVal, 0.01)
        assertEquals("10063020", rice.hsnCode)
        assertEquals(5.0, rice.gstRate ?: 0.0, 0.01)

        // Verify Vouchers
        assertEquals(1, result.vouchers.size)
        val voucher = result.vouchers[0]
        assertEquals("Sales", voucher.voucherType)
        assertEquals("INV-1001", voucher.voucherNumber)
        assertEquals("Sharma Trading Co", voucher.partyName)
        assertEquals("Sale of rice on credit", voucher.narration)

        // Verify Voucher Entries
        assertEquals(4, voucher.entries.size)
        val debitEntry = voucher.entries.find { it.ledgerName == "Sharma Trading Co" }
        assertNotNull(debitEntry)
        assertTrue("Party should be debit", debitEntry!!.isDebit)
        assertEquals(10500.0, debitEntry.amount, 0.01)

        val salesEntry = voucher.entries.find { it.ledgerName == "Sales Account" }
        assertNotNull(salesEntry)
        assertFalse("Sales account should be credit", salesEntry!!.isDebit)
        assertEquals(10000.0, salesEntry.amount, 0.01)

        // Verify Inventory Entries
        assertEquals(1, voucher.inventoryEntries.size)
        val inv = voucher.inventoryEntries[0]
        assertEquals("Basmati Rice Premium", inv.stockItemName)
        assertEquals(100.0, inv.quantity, 0.01)
        assertEquals(100.0, inv.rate, 0.01)
        assertEquals(10000.0, inv.totalAmount, 0.01)
    }

    @Test
    fun testParseTallyXml_paymentVoucher_dateFormats() {
        val paymentXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Payment">
                                    <DATE>12-Oct-2026</DATE>
                                    <VOUCHERNUMBER>PAY-55</VOUCHERNUMBER>
                                    <PARTYNAME>Gupta Traders</PARTYNAME>
                                    <NARRATION>Paid vendor via HDFC</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Gupta Traders</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-25000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>HDFC Bank</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>25000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(paymentXml.toByteArray(Charsets.UTF_8))
        val result = UniversalParser.parseTallyXml(inputStream)

        assertEquals(1, result.vouchers.size)
        val v = result.vouchers[0]
        assertEquals("Payment", v.voucherType)
        assertEquals("PAY-55", v.voucherNumber)
        assertEquals("Gupta Traders", v.partyName)
        assertEquals(2, v.entries.size)

        val debit = v.entries.find { it.ledgerName == "Gupta Traders" }
        assertNotNull(debit)
        assertTrue(debit!!.isDebit)
        assertEquals(25000.0, debit.amount, 0.01)

        val credit = v.entries.find { it.ledgerName == "HDFC Bank" }
        assertNotNull(credit)
        assertFalse(credit!!.isDebit)
        assertEquals(25000.0, credit.amount, 0.01)
    }

    @Test
    fun testParseTallyXml_allVoucherTypesAndDoubleEntryBalance() {
        val comprehensiveXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <!-- 1. Purchase Voucher with Inventory and Taxes -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Purchase">
                                    <DATE>2026-05-15</DATE>
                                    <VOUCHERNUMBER>PUR-202</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Mahajan Wholesalers</PARTYLEDGERNAME>
                                    <NARRATION>Purchase of cement &amp; steel</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Purchase Account</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-50000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>IGST Input</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-9000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Mahajan Wholesalers</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>59000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLINVENTORYENTRIES.LIST>
                                        <STOCKITEMNAME>Ultratech Cement</STOCKITEMNAME>
                                        <ACTUALQTY>100 Bags</ACTUALQTY>
                                        <RATE>500.00/Bag</RATE>
                                        <AMOUNT>-50000.00</AMOUNT>
                                    </ALLINVENTORYENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>

                            <!-- 2. Receipt Voucher (Cash received from Customer) -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Receipt">
                                    <DATE>16/05/2026</DATE>
                                    <VOUCHERNUMBER>REC-801</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Sharma Trading Co</PARTYLEDGERNAME>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Cash</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-10000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sharma Trading Co</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>10000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>

                            <!-- 3. Contra Voucher (Cash deposited into Bank) -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Contra">
                                    <DATE>17-May-26</DATE>
                                    <VOUCHERNUMBER>CNT-101</VOUCHERNUMBER>
                                    <NARRATION>Cash deposit to State Bank of India</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>State Bank of India</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-15000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Cash</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>15000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>

                            <!-- 4. Journal Voucher (Depreciation adjustment) -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Journal">
                                    <DATE>2026/05/18</DATE>
                                    <VOUCHERNUMBER>JRN-501</VOUCHERNUMBER>
                                    <NARRATION>Monthly office equipment depreciation</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Depreciation Expense</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-3500.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Office Equipment</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>3500.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>

                            <!-- 5. Credit Note Voucher (Sales Return) -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Credit Note">
                                    <DATE>19-05-2026</DATE>
                                    <VOUCHERNUMBER>CRN-01</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Sharma Trading Co</PARTYLEDGERNAME>
                                    <NARRATION>Damaged goods returned</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sales Return</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-2000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sharma Trading Co</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>2000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>

                            <!-- 6. Debit Note Voucher (Purchase Return) -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Debit Note">
                                    <DATE>20260520</DATE>
                                    <VOUCHERNUMBER>DBN-01</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Mahajan Wholesalers</PARTYLEDGERNAME>
                                    <NARRATION>Defective cement returned</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Mahajan Wholesalers</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-5000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Purchase Return</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>5000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(comprehensiveXml.toByteArray(Charsets.UTF_8))
        val result = UniversalParser.parseTallyXml(inputStream)

        assertEquals(6, result.vouchers.size)

        // Verify each voucher and mathematical balance of debits vs credits
        for (v in result.vouchers) {
            val totalDebits = v.entries.filter { it.isDebit }.sumOf { it.amount }
            val totalCredits = v.entries.filter { !it.isDebit }.sumOf { it.amount }
            assertEquals("Voucher ${v.voucherNumber} (${v.voucherType}) debits must equal credits",
                totalDebits, totalCredits, 0.001)
            assertTrue("Total amount must be greater than zero", totalDebits > 0.0)
        }

        // Check Purchase Voucher
        val pur = result.vouchers.find { it.voucherType == "Purchase" }
        assertNotNull(pur)
        assertEquals("PUR-202", pur!!.voucherNumber)
        assertEquals("Mahajan Wholesalers", pur.partyName)
        assertEquals(1, pur.inventoryEntries.size)
        assertEquals("Ultratech Cement", pur.inventoryEntries[0].stockItemName)
        assertEquals(100.0, pur.inventoryEntries[0].quantity, 0.01)
        assertEquals(500.0, pur.inventoryEntries[0].rate, 0.01)

        // Check Contra Voucher
        val contra = result.vouchers.find { it.voucherType == "Contra" }
        assertNotNull(contra)
        assertEquals("CNT-101", contra!!.voucherNumber)
        val sbi = contra.entries.find { it.ledgerName == "State Bank of India" }
        assertTrue("Bank should be debited in cash deposit", sbi!!.isDebit)
        assertEquals(15000.0, sbi.amount, 0.01)

        // Check Credit Note
        val crn = result.vouchers.find { it.voucherType == "Credit Note" }
        assertNotNull(crn)
        assertEquals("Sharma Trading Co", crn!!.partyName)

        // Check Debit Note
        val dbn = result.vouchers.find { it.voucherType == "Debit Note" }
        assertNotNull(dbn)
        assertEquals("Mahajan Wholesalers", dbn!!.partyName)
    }

    @Test
    fun testParseTallyXml_masterMetadataExtraction() {
        val masterXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <TALLYMESSAGE>
                                <LEDGER NAME="Apex Infotech Pvt Ltd">
                                    <PARENT>Sundry Creditors</PARENT>
                                    <OPENINGBALANCE>125000.00</OPENINGBALANCE>
                                    <ADDRESS.LIST>
                                        <ADDRESS>Plot 42, Okhla Phase 3</ADDRESS>
                                        <ADDRESS>Industrial Area</ADDRESS>
                                        <ADDRESS>New Delhi</ADDRESS>
                                    </ADDRESS.LIST>
                                    <PINCODE>110020</PINCODE>
                                    <PARTYGSTIN>07AABCA1234F1Z5</PARTYGSTIN>
                                    <LEDGERPHONE>+919876543210</LEDGERPHONE>
                                    <EMAIL>accounts@apexinfotech.com</EMAIL>
                                </LEDGER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(masterXml.toByteArray(Charsets.UTF_8))
        val result = UniversalParser.parseTallyXml(inputStream)

        assertEquals(1, result.ledgers.size)
        val ledger = result.ledgers[0]
        assertEquals("Apex Infotech Pvt Ltd", ledger.name)
        assertEquals("Sundry Creditors", ledger.parentGroup)
        assertEquals(125000.0, ledger.openingBalance, 0.01)
        assertTrue(ledger.address!!.contains("Okhla Phase 3"))
        assertEquals("110020", ledger.pincode)
        assertEquals("07AABCA1234F1Z5", ledger.gstin)
        assertEquals("+919876543210", ledger.phoneNumber)
        assertEquals("accounts@apexinfotech.com", ledger.email)
    }

    @Test
    fun testConversionToAppEntities_mastersAndVouchers() {
        val sampleXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <!-- Master Ledgers -->
                            <TALLYMESSAGE>
                                <LEDGER NAME="Apex Infotech Pvt Ltd">
                                    <PARENT>Sundry Debtors</PARENT>
                                    <OPENINGBALANCE>-50000.00</OPENINGBALANCE>
                                    <PARTYGSTIN>07AABCA1234F1Z5</PARTYGSTIN>
                                    <LEDGERPHONE>+919876543210</LEDGERPHONE>
                                </LEDGER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE>
                                <LEDGER NAME="Sales Account">
                                    <PARENT>Sales Accounts</PARENT>
                                    <OPENINGBALANCE>0</OPENINGBALANCE>
                                </LEDGER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE>
                                <LEDGER NAME="HDFC Bank Current A/c">
                                    <PARENT>Bank Accounts</PARENT>
                                    <OPENINGBALANCE>-150000.00</OPENINGBALANCE>
                                </LEDGER>
                            </TALLYMESSAGE>

                            <!-- Master Stock Items -->
                            <TALLYMESSAGE>
                                <STOCKITEM NAME="Basmati Rice 1006">
                                    <BASEUNITS>Kg</BASEUNITS>
                                    <OPENINGBALANCE>500 Kg</OPENINGBALANCE>
                                    <OPENINGVALUE>-40000.00</OPENINGVALUE>
                                    <HSNCODE>10063020</HSNCODE>
                                    <GSTRATE>5.0</GSTRATE>
                                </STOCKITEM>
                            </TALLYMESSAGE>

                            <!-- Sales Voucher -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Sales">
                                    <DATE>2026-06-01</DATE>
                                    <VOUCHERNUMBER>INV-2026-001</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Apex Infotech Pvt Ltd</PARTYLEDGERNAME>
                                    <NARRATION>Credit sale of Basmati Rice</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Apex Infotech Pvt Ltd</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-21000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sales Account</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>20000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>CGST Output</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>500.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>SGST Output</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>500.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLINVENTORYENTRIES.LIST>
                                        <STOCKITEMNAME>Basmati Rice 1006</STOCKITEMNAME>
                                        <ACTUALQTY>200 Kg</ACTUALQTY>
                                        <RATE>100.00/Kg</RATE>
                                        <AMOUNT>-20000.00</AMOUNT>
                                    </ALLINVENTORYENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>

                            <!-- Bank Receipt Voucher -->
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Receipt">
                                    <DATE>2026-06-05</DATE>
                                    <VOUCHERNUMBER>REC-501</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Apex Infotech Pvt Ltd</PARTYLEDGERNAME>
                                    <NARRATION>NEFT payment received</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>HDFC Bank Current A/c</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-21000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Apex Infotech Pvt Ltd</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>21000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(sampleXml.toByteArray(Charsets.UTF_8))
        val parsedResult = UniversalParser.parseTallyXml(inputStream)

        // Convert into Teller App entities
        val companyId = 42
        val conversion = TallyEntityConverter.convert(
            result = parsedResult,
            companyId = companyId
        )

        // 1. Verify Units
        assertTrue("Kg unit must be created", conversion.units.any { it.name == "Kg" && it.companyId == companyId })

        // 2. Verify Ledgers and Paise Conversion
        val apex = conversion.ledgers.find { it.name == "Apex Infotech Pvt Ltd" }
        assertNotNull(apex)
        assertEquals("Sundry Debtors", apex!!.group)
        assertEquals("07AABCA1234F1Z5", apex.gstin)
        assertEquals("+919876543210", apex.phoneNumber)
        assertEquals(5000000L, apex.openingBalance) // 50,000.00 Rs in paise

        val hdfc = conversion.ledgers.find { it.name == "HDFC Bank Current A/c" }
        assertNotNull(hdfc)
        assertEquals("Bank Accounts", hdfc!!.group)
        assertEquals(15000000L, hdfc.openingBalance) // 150,000.00 Rs in paise

        // Auto-created tax ledgers
        val cgst = conversion.ledgers.find { it.name == "CGST Output" }
        assertNotNull("CGST Output ledger should be auto-created", cgst)
        assertEquals("Duties & Taxes", cgst!!.group)

        val sgst = conversion.ledgers.find { it.name == "SGST Output" }
        assertNotNull("SGST Output ledger should be auto-created", sgst)
        assertEquals("Duties & Taxes", sgst!!.group)

        // 3. Verify Stock Items
        val rice = conversion.stockItems.find { it.name == "Basmati Rice 1006" }
        assertNotNull(rice)
        assertEquals(companyId, rice!!.companyId)
        assertEquals("10063020", rice.hsnCode)
        assertEquals(5.0, rice.gstRate, 0.01)
        assertEquals(2.5, rice.cgstRate, 0.01)
        assertEquals(2.5, rice.sgstRate, 0.01)
        assertEquals(500.0, rice.openingStock, 0.01)
        assertEquals(80.0, rice.rate, 0.01) // 40,000 / 500 = 80 Rs/Kg

        // 4. Verify Vouchers & Strict Double-Entry Balance
        assertEquals(2, conversion.voucherBundles.size)

        for (bundle in conversion.voucherBundles) {
            val v = bundle.voucher
            assertEquals(companyId, v.companyId)
            assertTrue(v.voucherNumber.isNotEmpty())
            assertTrue(v.type in listOf("Sales", "Receipt"))

            // Verify strict Double-Entry Balance: sum of debits == sum of credits
            val debitsPaise = bundle.entries.filter { it.isDebit }.sumOf { it.amount }
            val creditsPaise = bundle.entries.filter { !it.isDebit }.sumOf { it.amount }
            assertEquals("Voucher ${v.voucherNumber} (${v.type}) debits must exactly equal credits in paise",
                debitsPaise, creditsPaise)
            assertTrue("Voucher total amount must be > 0", debitsPaise > 0L)

            // Verify foreign key linking of VoucherEntries
            for (entry in bundle.entries) {
                assertEquals(v.id, entry.voucherId)
                assertTrue("Entry ledgerId must be valid", entry.ledgerId > 0)
                assertTrue("Entry amount must be positive", entry.amount > 0L)
            }

            // Verify InventoryEntries if present
            for (inv in bundle.inventoryEntries) {
                assertEquals(v.id, inv.voucherId)
                assertEquals(rice.id, inv.stockItemId)
                assertEquals(200.0, inv.quantity, 0.01)
                assertEquals(100.0, inv.rate, 0.01)
                assertEquals(2000000L, inv.amount) // 20,000 Rs in paise
                assertEquals(2000000L, inv.totalAmount)
            }
        }
    }

    @Test
    fun testTallyVoucherTypeNormalization() {
        val mixedTypesXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="SALES">
                                    <DATE>20260401</DATE>
                                    <VOUCHERNUMBER>V1</VOUCHERNUMBER>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Customer A</LEDGERNAME>
                                        <AMOUNT>-100.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sales</LEDGERNAME>
                                        <AMOUNT>100.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="credit_note">
                                    <DATE>20260402</DATE>
                                    <VOUCHERNUMBER>V2</VOUCHERNUMBER>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sales Return</LEDGERNAME>
                                        <AMOUNT>-50.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Customer A</LEDGERNAME>
                                        <AMOUNT>50.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="debitnote">
                                    <DATE>20260403</DATE>
                                    <VOUCHERNUMBER>V3</VOUCHERNUMBER>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Supplier B</LEDGERNAME>
                                        <AMOUNT>-30.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Purchase Return</LEDGERNAME>
                                        <AMOUNT>30.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val parsed = UniversalParser.parseTallyXml(ByteArrayInputStream(mixedTypesXml.toByteArray(Charsets.UTF_8)))
        val converted = TallyEntityConverter.convert(parsed, companyId = 1)

        assertEquals("Sales", converted.voucherBundles[0].voucher.type)
        assertEquals("Credit Note", converted.voucherBundles[1].voucher.type)
        assertEquals("Debit Note", converted.voucherBundles[2].voucher.type)
    }

    @Test
    fun testConversion_undeclaredMastersHandledSafelyAndBalanced() {
        // XML with zero <LEDGER> or <STOCKITEM> tags, only vouchers referencing various accounts
        val rawVoucherXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Payment">
                                    <DATE>2026-07-01</DATE>
                                    <VOUCHERNUMBER>PAY-99</VOUCHERNUMBER>
                                    <NARRATION>Payment for office broadband</NARRATION>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Internet &amp; Telephone Expense</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-1499.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>ICICI Bank Current</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>1499.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Purchase">
                                    <DATE>2026-07-02</DATE>
                                    <VOUCHERNUMBER>PUR-77</VOUCHERNUMBER>
                                    <PARTYLEDGERNAME>Global Steel Suppliers</PARTYLEDGERNAME>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Purchase Account</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-10000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>IGST Input</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-1800.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Global Steel Suppliers</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>11800.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLINVENTORYENTRIES.LIST>
                                        <STOCKITEMNAME>TMT Steel Bars 12mm</STOCKITEMNAME>
                                        <ACTUALQTY>200 Nos</ACTUALQTY>
                                        <RATE>50.00/Nos</RATE>
                                        <AMOUNT>-10000.00</AMOUNT>
                                    </ALLINVENTORYENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val parsed = UniversalParser.parseTallyXml(ByteArrayInputStream(rawVoucherXml.toByteArray(Charsets.UTF_8)))
        val converted = TallyEntityConverter.convert(parsed, companyId = 5)

        // 1. Verify ledgers were created and grouped correctly
        val exp = converted.ledgers.find { it.name.contains("Internet") }
        assertNotNull(exp)
        assertEquals("Indirect Expenses", exp!!.group)

        val bank = converted.ledgers.find { it.name.contains("ICICI") }
        assertNotNull(bank)
        assertEquals("Bank Accounts", bank!!.group)

        val igst = converted.ledgers.find { it.name.contains("IGST") }
        assertNotNull(igst)
        assertEquals("Duties & Taxes", igst!!.group)

        val vendor = converted.ledgers.find { it.name.contains("Global Steel") }
        assertNotNull(vendor)
        assertEquals("Sundry Creditors", vendor!!.group)

        // 2. Verify stock item was created with Nos unit
        val steel = converted.stockItems.find { it.name.contains("TMT Steel") }
        assertNotNull(steel)
        val nosUnit = converted.units.find { it.id == steel!!.unitId }
        assertNotNull(nosUnit)
        assertEquals("Nos", nosUnit!!.name)

        // 3. Verify double-entry balance for both vouchers
        for (bundle in converted.voucherBundles) {
            val debitsPaise = bundle.entries.filter { it.isDebit }.sumOf { it.amount }
            val creditsPaise = bundle.entries.filter { !it.isDebit }.sumOf { it.amount }
            assertEquals("Voucher ${bundle.voucher.voucherNumber} debits must equal credits",
                debitsPaise, creditsPaise)
        }
    }

    @Test
    fun testParseTallyXml_tbkBackupDetection() {
        val out = java.io.ByteArrayOutputStream()
        out.write(TallyBackupExtractor.TBK_MAGIC)
        out.write(ByteArray(60))

        // Write a synthetic TLV ledger
        out.write(0x02); out.write(0x10)
        out.write(0xF7); out.write(0x01) // tag 0x01f7 (Ledger Name)
        out.write(0x0F); out.write(0x00) // type UTF-16LE
        val strBytes = "Generic Trade Corp".toByteArray(Charsets.UTF_16LE)
        val len = strBytes.size
        out.write(len and 0xFF); out.write((len shr 8) and 0xFF)
        out.write(strBytes)

        val result = UniversalParser.parseTallyXml(ByteArrayInputStream(out.toByteArray()))
        assertNotNull(result)
        assertTrue("TBK backup stream directly extracted ledgers", result.ledgers.isNotEmpty())
        assertEquals("Generic Trade Corp", result.ledgers.first().name)
    }
}


