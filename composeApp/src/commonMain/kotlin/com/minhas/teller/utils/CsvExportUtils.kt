package com.minhas.teller.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.minhas.teller.data.entities.VoucherWithAllEntries
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

object CsvExportUtils {

    fun exportDaybookToCsv(context: Context, vouchers: List<VoucherWithAllEntries>) {
        val fileName = "Daybook_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        try {
            FileWriter(file).use { writer ->
                // Header
                writer.append("Date,Voucher No,Type,Narration,Ledger ID,Debit/Credit,Amount\n")

                // Data
                for (voucherEntry in vouchers) {
                    val voucher = voucherEntry.voucher
                    val entries = voucherEntry.voucherEntries

                    for (entry in entries) {
                        writer.append(dateFormatter.format(voucher.date))
                        writer.append(",")
                        writer.append(voucher.voucherNumber)
                        writer.append(",")
                        writer.append(voucher.type)
                        writer.append(",")
                        writer.append(escapeCsv(voucher.narration ?: ""))
                        writer.append(",")
                        writer.append(entry.ledgerId.toString())
                        writer.append(",")
                        writer.append(if (entry.isDebit) "Debit" else "Credit")
                        writer.append(",")
                        writer.append(entry.amount.paiseToRupees().toString())
                        writer.append("\n")
                    }
                }
            }

            shareFile(context, file)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun escapeCsv(text: String): String {
        var escaped = text.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\n")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }

    private fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Daybook CSV"))
    }
}
