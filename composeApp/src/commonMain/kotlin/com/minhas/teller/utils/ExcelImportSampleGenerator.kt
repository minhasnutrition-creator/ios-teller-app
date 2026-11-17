package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.PlatformUtils
import java.io.File

/**
 * Generates sample import templates for each voucher type (Sale, Purchase, Payment, Receipt).
 * iOS Multiplatform compatible.
 */
object ExcelImportSampleGenerator {

    enum class VoucherTemplate(val displayName: String, val fileName: String) {
        SALE("Sale Voucher", "teller_sample_sale_import.csv"),
        PURCHASE("Purchase Voucher", "teller_sample_purchase_import.csv"),
        PAYMENT("Payment Voucher", "teller_sample_payment_import.csv"),
        RECEIPT("Receipt Voucher", "teller_sample_receipt_import.csv")
    }

    /**
     * Saves raw template bytes directly to the application directory.
     */
    fun saveBytesToDownloads(context: Context, fileName: String, bytes: ByteArray): File? {
        return try {
            val dir = File(PlatformUtils.getAppDataDirectory(), "Templates")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun saveToDownloads(context: Context, template: VoucherTemplate): File? {
        val bytes = buildWorkbookBytes(template)
        return saveBytesToDownloads(context, template.fileName, bytes)
    }

    fun downloadAndShare(context: Context, template: VoucherTemplate) {
        val file = saveToDownloads(context, template)
        if (file != null) {
            PlatformUtils.shareText(file.readText(), "Sample ${template.displayName} Template")
        }
    }

    fun buildWorkbookBytes(template: VoucherTemplate): ByteArray {
        val csvHeader = when (template) {
            VoucherTemplate.SALE -> "Date,Invoice No,Customer Name,Item Name,Quantity,Rate,Amount,GST Rate\n01/04/2026,INV-001,Cash Sale,Sample Item,1,100.0,100.0,18.0"
            VoucherTemplate.PURCHASE -> "Date,Bill No,Supplier Name,Item Name,Quantity,Rate,Amount,GST Rate\n01/04/2026,BILL-001,Main Supplier,Sample Material,10,50.0,500.0,18.0"
            VoucherTemplate.PAYMENT -> "Date,Voucher No,Paid To,Paid From,Amount,Narration\n01/04/2026,PAY-001,Office Rent,Bank Account,15000.0,April Rent"
            VoucherTemplate.RECEIPT -> "Date,Voucher No,Received From,Deposited In,Amount,Narration\n01/04/2026,REC-001,Customer Name,Cash,5000.0,Advance Payment"
        }
        return csvHeader.encodeToByteArray()
    }
}
