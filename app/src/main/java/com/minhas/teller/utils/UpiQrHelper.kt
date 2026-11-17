package com.minhas.teller.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.EnumMap
import java.util.Locale

/**
 * Universal UPI QR Code generator for NPCI-compliant payment requests.
 * Generates:
 * 1. Standard NPCI UPI URI strings.
 * 2. High-contrast monochrome Android Bitmaps for UI & thermal printing.
 * 3. PNG byte streams for OpenPDF A4 & 80mm PDF invoices.
 * 4. ESC/POS raster bitmap commands (GS v 0) compatible with 100% of thermal printers.
 */
object UpiQrHelper {

    /**
     * Builds an NPCI-compliant UPI payment URI.
     * Example: upi://pay?pa=store@upi&pn=Store%20Name&am=450.00&cu=INR&tn=Invoice%20INV-101&tr=INV-101
     */
    fun buildUpiUri(
        upiId: String,
        payeeName: String,
        amount: Double,
        invoiceNumber: String? = null
    ): String {
        val encodedName = URLEncoder.encode(payeeName.trim(), "UTF-8").replace("+", "%20")
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val sb = StringBuilder("upi://pay?pa=").append(upiId.trim())
            .append("&pn=").append(encodedName)
            .append("&am=").append(formattedAmount)
            .append("&cu=INR")

        if (!invoiceNumber.isNullOrBlank()) {
            val safeInv = invoiceNumber.trim()
            val encNote = URLEncoder.encode("Invoice #$safeInv", "UTF-8").replace("+", "%20")
            val encRef = URLEncoder.encode(safeInv, "UTF-8").replace("+", "%20")
            sb.append("&tn=").append(encNote)
            sb.append("&tr=").append(encRef)
        }
        return sb.toString()
    }

    /**
     * Generates a monochrome Android Bitmap QR code from text.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 240): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 1)
            }
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates PNG bytes for embedding in PDF documents (OpenPDF).
     */
    fun generateQrPngBytes(content: String, sizePx: Int = 240): ByteArray? {
        val bmp = generateQrBitmap(content, sizePx) ?: return null
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Converts a monochrome Bitmap to ESC/POS raster image bytes (GS v 0).
     * Works universally across all thermal receipt printers (Bluetooth, USB, WiFi).
     */
    fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val out = ByteArrayOutputStream()

        // GS v 0 m xL xH yL yH d1...dk
        out.write(byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(),
            ((height shr 8) and 0xFF).toByte()
        ))

        for (y in 0 until height) {
            for (byteX in 0 until widthBytes) {
                var slice: Byte = 0
                for (b in 0 until 8) {
                    val px = (byteX * 8) + b
                    if (px < width) {
                        val pixel = bitmap.getPixel(px, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val bl = pixel and 0xFF
                        val lum = (0.299 * r + 0.587 * g + 0.114 * bl).toInt()
                        if (lum < 128) {
                            slice = (slice.toInt() or (0x80 shr b)).toByte()
                        }
                    }
                }
                out.write(slice.toInt())
            }
        }
        return out.toByteArray()
    }
}
