package com.minhas.teller.utils

import com.minhas.teller.TellerApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UniversalBackupImporterTest {

    @Before
    fun setUp() {
        TellerApp.configureGlobalProperties()
    }

    @Test
    fun testUniversalRouterAutoDetection() {
        // 1. Tally TBK
        val tbkBytes = byteArrayOf(0xF4.toByte(), 0xDD.toByte(), 0x56.toByte(), 0x36.toByte(), 0x00, 0x00)
        assertEquals(UniversalBackupImporter.DetectedFormat.TALLY_TBK, UniversalBackupImporter.detectFormat(tbkBytes, "backup.001"))

        // 2. Busy BSH / Zip
        val bshZipOut = ByteArrayOutputStream()
        val zos = ZipOutputStream(bshZipOut)
        zos.putNextEntry(ZipEntry("COMP0001.MDB"))
        zos.write("Standard Jet DB Sample Content".toByteArray(Charsets.US_ASCII))
        zos.closeEntry()
        zos.close()
        val busyBytes = bshZipOut.toByteArray()
        assertEquals(UniversalBackupImporter.DetectedFormat.BUSY_BACKUP, UniversalBackupImporter.detectFormat(busyBytes, "Company_Backup.bsh"))

        // 3. Tally XML
        val xmlBytes = "<ENVELOPE><HEADER><TALLYREQUEST>Export</TALLYREQUEST></HEADER></ENVELOPE>".toByteArray(Charsets.UTF_8)
        assertEquals(UniversalBackupImporter.DetectedFormat.TALLY_XML, UniversalBackupImporter.detectFormat(xmlBytes, "tally_export.xml"))

        // 4. CSV
        val csvBytes = "Invoice No,Date,Customer Name,Amount\nINV-01,2024-01-01,Sharma,1000".toByteArray(Charsets.UTF_8)
        assertEquals(UniversalBackupImporter.DetectedFormat.CSV_FILE, UniversalBackupImporter.detectFormat(csvBytes, "register.csv"))
    }

    @Test
    fun testSyntheticTallyBtkStream() {
        // Build synthetic Tally binary buffer with TLV string fields
        val out = ByteArrayOutputStream()
        out.write(TallyBackupExtractor.TBK_MAGIC)
        out.write(ByteArray(60)) // filler

        fun writeTlv(tag: Int, str: String) {
            out.write(0x02)
            out.write(0x10)
            out.write(tag and 0xFF)
            out.write((tag shr 8) and 0xFF)
            out.write(0x0F) // Type 0x000F String
            out.write(0x00)
            val strBytes = str.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0x00, 0x00)
            val len = strBytes.size
            out.write(len and 0xFF)
            out.write((len shr 8) and 0xFF)
            out.write(strBytes)
        }

        // Ledger 1
        writeTlv(0x01f7, "Apex Trading Corp")
        writeTlv(0x0002, "Sundry Debtors")
        writeTlv(0x01fc, "07AAAAA1111A1Z5")
        writeTlv(0x05fa, "Delhi")
        writeTlv(0x05fb, "110001")

        // Ledger 2 (Bank)
        writeTlv(0x01f7, "HDFC Bank Ltd")
        writeTlv(0x0002, "Bank Accounts")

        val result = TallyBackupExtractor.extract(ByteArrayInputStream(out.toByteArray()))
        assertTrue("Should extract at least 2 ledgers", result.ledgers.size >= 2)

        val apex = result.ledgers.find { it.name == "Apex Trading Corp" }
        assertNotNull(apex)
        assertEquals("07AAAAA1111A1Z5", apex!!.gstin)
        assertEquals("Sundry Debtors", apex.parentGroup)

        val hdfc = result.ledgers.find { it.name == "HDFC Bank Ltd" }
        assertNotNull(hdfc)
        assertEquals("Bank Accounts", hdfc!!.parentGroup)
    }

    @Test
    fun testBusyBackupExtraction_syntheticZipStream() {
        val bshZipOut = ByteArrayOutputStream()
        val zos = ZipOutputStream(bshZipOut)
        zos.putNextEntry(ZipEntry("COMP0001.MDB"))

        val content = """
            Master1 Table
            Hindustan Enterprises Ltd  06BBBBB2222B1Z6
            State Bank of India
            Basmati Rice 5kg
            Refined Oil 15Ltr
        """.trimIndent()
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val busyBytes = bshZipOut.toByteArray()
        val result = BusyBackupExtractor.extract(ByteArrayInputStream(busyBytes), "BusyBackup.bsh")

        assertTrue("Should extract ledgers from Busy backup", result.ledgers.isNotEmpty())
        val sbi = result.ledgers.find { it.name.contains("State Bank of India") }
        assertNotNull("Should detect State Bank of India", sbi)
        assertEquals("Bank Accounts", sbi!!.parentGroup)

        assertTrue("Should extract stock items from Busy backup", result.stockItems.isNotEmpty())
        val rice = result.stockItems.find { it.name.contains("Basmati Rice") }
        assertNotNull("Should detect Basmati Rice", rice)
        assertEquals("Kg", rice!!.unitName)
    }

    @Test
    fun testXmlSanitizationWithUnescapedAmpersandsAndControlChars() {
        val rawXml = "<ENVELOPE><BODY><DATA><TALLYMESSAGE><LEDGER NAME=\"M&S Enterprises & Co.\"><PARENT>Sundry Debtors\u0000</PARENT></LEDGER></TALLYMESSAGE></DATA></BODY></ENVELOPE>"
        val cleanBytes = UniversalParser.sanitizeXml(rawXml.toByteArray(Charsets.UTF_8))
        val cleanStr = String(cleanBytes, Charsets.UTF_8)

        assertTrue("Unescaped & should be sanitized to &amp;", cleanStr.contains("M&amp;S Enterprises &amp; Co."))
        assertFalse("Illegal null control character must be stripped", cleanStr.contains("\u0000"))

        // Must parse via DocumentBuilder without SAXParseException
        val result = UniversalParser.parseTallyXml(ByteArrayInputStream(cleanBytes))
        assertEquals(1, result.ledgers.size)
        assertEquals("M&S Enterprises & Co.", result.ledgers[0].name)
    }

    @Test
    fun testFormatDetection_Ole2XlsAndUtf16Xml() {
        // OLE2 Excel .xls magic
        val xlsBytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte())
        assertEquals(UniversalBackupImporter.DetectedFormat.EXCEL_WORKBOOK, UniversalBackupImporter.detectFormat(xlsBytes, "legacy_accounts.xls"))

        // UTF-16LE XML with BOM
        val utf16XmlStr = "<ENVELOPE><BODY><DATA></DATA></BODY></ENVELOPE>"
        val utf16Bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + utf16XmlStr.toByteArray(Charsets.UTF_16LE)
        assertEquals(UniversalBackupImporter.DetectedFormat.TALLY_XML, UniversalBackupImporter.detectFormat(utf16Bytes, "export.xml"))
    }

    @Test
    fun testFallbackCascade_UnknownFormatAttemptsExcelAndCsv() {
        // Plain CSV with non-standard extension
        val csvData = "Invoice No,Customer Name,Date,Amount\nINV-001,Raj Traders,01-04-2024,5000\n"
        val inStream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))

        val result = UniversalBackupImporter.parseAnyFile(inStream, "accounts.dat")
        assertNull("Fallback cascade should succeed without error", result.errorMessage)
        assertNotNull("Should parse excel/csv result via fallback cascade", result.excelResult)
    }

    @Test
    fun testDirectExtractionFromRealTallyBackupFileIfPresent() {
        val realFile = File("""C:\Users\SUNI\Downloads\TBK900_10001(1).001""")
        if (!realFile.exists()) {
            println("Skipping real file test: ${realFile.absolutePath} not found on this system")
            return
        }

        val result = TallyBackupExtractor.extract(realFile.inputStream())
        println("=== TALLY EXTRACT RESULTS ===")
        println("Extracted ${result.ledgers.size} ledgers:")
        result.ledgers.forEach { println("  Ledger: '${it.name}' | Parent: '${it.parentGroup}' | GSTIN: '${it.gstin}'") }
        println("Extracted ${result.stockItems.size} stock items:")
        result.stockItems.forEach { println("  StockItem: '${it.name}'") }
        println("Extracted ${result.vouchers.size} vouchers:")
        result.vouchers.forEach { v ->
            println("  Voucher: ${v.voucherType} #${v.voucherNumber} Party: '${v.partyName}' Date: ${v.date} Entries: ${v.entries.size}")
            v.entries.forEach { e -> println("    Entry: ${if (e.isDebit) "DR" else "CR"} ${e.amount} -> '${e.ledgerName}'") }
        }

        // Verify extraction quality
        assertTrue("Should extract 1000+ real vouchers from TBK", result.vouchers.size >= 1000)
        assertTrue("All extracted vouchers must have non-zero amounts", result.vouchers.all { v -> v.entries.isNotEmpty() && v.entries.all { it.amount > 0.0 } })
        assertFalse("Must not contain system tokens like Ctra, Pymt, INR as ledgers", result.ledgers.any { it.name in listOf("Ctra", "Pymt", "Rcpt", "Jrnl", "Sale", "Purc", "INR", "Default") })
        assertFalse("Must not contain banking terms as stock items", result.stockItems.any { it.name.contains("DEPOSIT", true) || it.name.contains("FINANCE", true) || it.name.contains("NEFT", true) })

        // Assert ledgers are clean: no blank names, no unused template dummy banks (Axis, SBI, etc.)
        assertTrue("All extracted ledgers must have non-blank names", result.ledgers.all { it.name.isNotBlank() })
        assertFalse("Must not contain unused dummy template banks", result.ledgers.any { it.name in listOf("AXIS BANK", "CENTRAL BANK OF INDIA", "SBI BANK", "STATE BANK OF INDIA", "PUNJAB NATIONAL BANK") })
        assertTrue("Extracted ledgers must only contain the active company accounts (<= 5)", result.ledgers.size <= 5)
        assertTrue("Must include active debtor VPS MOTORS", result.ledgers.any { it.name == "VPS MOTORS" })
        assertTrue("Must include Sales Accounts", result.ledgers.any { it.name == "Sales Accounts" })

        val cal = java.util.Calendar.getInstance()
        val currentYear = cal.get(java.util.Calendar.YEAR)
        val vouchersInCurrentYear = result.vouchers.filter {
            cal.timeInMillis = it.date
            cal.get(java.util.Calendar.YEAR) == currentYear
        }
        assertEquals("Historical backup must have 0 vouchers assigned to current year ($currentYear)", 0, vouchersInCurrentYear.size)
        assertTrue("All vouchers should be in historical range (2018..2022)", result.vouchers.all {
            cal.timeInMillis = it.date
            val yr = cal.get(java.util.Calendar.YEAR)
            yr in 2018..2022
        })

        // Verify conversion to Teller Room entities
        val converted = TallyEntityConverter.convert(result, companyId = 1)
        println("=== CONVERTED ROOM ENTITIES ===")
        println("Converted ledgers: ${converted.ledgers.size}")
        println("Converted voucher bundles: ${converted.voucherBundles.size}")
        assertTrue("Converted vouchers must match extracted count", converted.voucherBundles.size >= 1000)
        val allBalanced = converted.voucherBundles.all { b ->
            val d = b.entries.filter { it.isDebit }.sumOf { it.amount }
            val c = b.entries.filter { !it.isDebit }.sumOf { it.amount }
            d == c && d > 0L
        }
        assertTrue("All converted voucher bundles must have strictly balanced Debits and Credits", allBalanced)
    }
}
