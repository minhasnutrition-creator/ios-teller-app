package com.minhas.teller.utils.export

import android.content.Context
import android.util.Log
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.minhas.teller.utils.ExcelNavigator
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

typealias BaseColor = java.awt.Color

object PdfExportEngine {
    private const val TAG = "PdfExportEngine"

    /**
     * Generates a styled PDF report for various ledger, daybook, and financial summaries.
     */
    fun exportReportToPdf(
        context: Context,
        fileName: String,
        subtitle: String,
        title: String,
        headers: List<String>,
        widths: FloatArray,
        data: List<List<String>>
    ) {
        val file = File(context.getExternalFilesDir(null), fileName)
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)

        try {
            val writer = PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // Header Title
            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, BaseColor(30, 45, 74))
            val pTitle = Paragraph(title.uppercase(Locale.getDefault()), titleFont)
            pTitle.alignment = Element.ALIGN_LEFT
            document.add(pTitle)

            if (subtitle.isNotBlank()) {
                val pSub = Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor(100, 100, 100)))
                pSub.alignment = Element.ALIGN_LEFT
                document.add(pSub)
            }

            document.add(Paragraph(" ")) // Spacing

            // Table setup
            val table = PdfPTable(headers.size)
            table.widthPercentage = 100f
            try {
                table.setWidths(widths)
            } catch (e: Exception) {
                // fallback
            }

            // Headers
            val thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.WHITE)
            headers.forEach { header ->
                val cell = PdfPCell(Phrase(header, thFont)).apply {
                    backgroundColor = BaseColor(30, 45, 74)
                    setPadding(6f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                table.addCell(cell)
            }

            // Rows
            val dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, BaseColor.DARK_GRAY)
            data.forEachIndexed { rowIndex, row ->
                val bgColor = if (rowIndex % 2 == 0) BaseColor(245, 247, 250) else BaseColor.WHITE
                row.forEach { text ->
                    val cell = PdfPCell(Phrase(text, dataFont)).apply {
                        backgroundColor = bgColor
                        setPadding(5f)
                        verticalAlignment = Element.ALIGN_MIDDLE
                    }
                    table.addCell(cell)
                }
            }

            document.add(table)
            document.close()
            ExcelNavigator.open(file.absolutePath, title)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF: ${e.message}", e)
        } finally {
            if (document.isOpen) {
                try { document.close() } catch (_: Exception) {}
            }
        }
    }
}
