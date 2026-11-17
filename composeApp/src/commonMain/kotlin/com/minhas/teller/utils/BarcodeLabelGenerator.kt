package com.minhas.teller.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class BarcodeLabelItem(
    val itemName: String,
    val barcodeCode: String,
    val price: Double,
    val currencySymbol: String = "₹",
    val companyName: String? = null
)

object BarcodeLabelGenerator {

    /**
     * Generates a 1D (Code 128) barcode bitmap from a code string using ZXing.
     */
    fun generateBarcodeBitmap(
        code: String,
        width: Int = 300,
        height: Int = 100,
        format: BarcodeFormat = BarcodeFormat.CODE_128
    ): Bitmap {
        // ZXing Code 128 accepts ASCII 32 to 126. Strip non-ASCII or control characters to avoid crashes.
        val cleanCode = code.filter { it.code in 32..126 }.trim().ifBlank { "000000" }
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(cleanCode, format, width, height)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)

        for (x in 0 until matrixWidth) {
            for (y in 0 until matrixHeight) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Generates an A4 PDF sheet formatted for standard 24-up label stickers (3 columns x 8 rows).
     */
    fun generateA4LabelSheetPdf(
        context: Context,
        labels: List<BarcodeLabelItem>,
        columns: Int = 3
    ): File {
        val document = Document(PageSize.A4, 20f, 20f, 20f, 20f)
        val outFile = File(context.cacheDir, "BarcodeLabels_${System.currentTimeMillis()}.pdf")
        val writer = PdfWriter.getInstance(document, FileOutputStream(outFile))
        document.open()

        val table = PdfPTable(columns)
        table.widthPercentage = 100f
        table.defaultCell.border = com.lowagie.text.Rectangle.NO_BORDER

        val fontSmall = Font(Font.HELVETICA, 7f, Font.NORMAL)
        val fontBold = Font(Font.HELVETICA, 8f, Font.BOLD)
        val fontPrice = Font(Font.HELVETICA, 9f, Font.BOLD)

        labels.forEach { item ->
            val cell = PdfPCell()
            cell.setPadding(6f)
            cell.border = com.lowagie.text.Rectangle.BOX
            cell.borderWidth = 0.5f
            cell.horizontalAlignment = Element.ALIGN_CENTER

            // Company Name
            item.companyName?.takeIf { it.isNotBlank() }?.let {
                val compP = Paragraph(it.take(24), fontSmall)
                compP.alignment = Element.ALIGN_CENTER
                cell.addElement(compP)
            }

            // Item Name
            val nameP = Paragraph(item.itemName.take(28), fontBold)
            nameP.alignment = Element.ALIGN_CENTER
            cell.addElement(nameP)

            // Barcode Image
            try {
                val bitmap = generateBarcodeBitmap(item.barcodeCode, 240, 70)
                // PdfImages, not Image.getInstance(png): OpenPDF's PNG decoder needs
                // javax.imageio, which Android lacks (NoClassDefFoundError, not an Exception).
                val img = PdfImages.fromBitmap(bitmap)
                img.alignment = Element.ALIGN_CENTER
                img.scalePercent(65f)
                cell.addElement(img)
            } catch (t: Throwable) {
                val codeP = Paragraph(item.barcodeCode, fontSmall)
                codeP.alignment = Element.ALIGN_CENTER
                cell.addElement(codeP)
            }

            // Human readable code & Price
            val priceP = Paragraph("MRP: ${item.currencySymbol}%.2f".format(item.price), fontPrice)
            priceP.alignment = Element.ALIGN_CENTER
            cell.addElement(priceP)

            table.addCell(cell)
        }

        // Fill remaining cells in the last row if needed
        val remaining = columns - (labels.size % columns)
        if (remaining < columns) {
            for (i in 0 until remaining) {
                val emptyCell = PdfPCell()
                emptyCell.border = com.lowagie.text.Rectangle.NO_BORDER
                table.addCell(emptyCell)
            }
        }

        document.add(table)
        document.close()
        writer.close()
        return outFile
    }
}
