package com.minhas.teller.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lowagie.text.Image
import java.util.Hashtable

/**
 * Compatibility shim for iText's BarcodeQRCode → ZXing migration.
 * OpenPDF removed BarcodeQRCode; this class uses ZXing to generate
 * QR code images and returns them as OpenPDF Image objects.
 */
class BarcodeQRCode(
    private val content: String,
    private val width: Int,
    private val height: Int,
    @Suppress("unused") hints: Hashtable<EncodeHintType, Any>?
) {
    /**
     * Generates the QR code and returns it as an OpenPDF Image.
     */
    val image: Image
        get() = generateImage()

    private fun generateImage(): Image {
        val writer = QRCodeWriter()
        val hintMap = Hashtable<EncodeHintType, Any>()
        hintMap[EncodeHintType.MARGIN] = 1

        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hintMap)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }

        // NOT Image.getInstance(pngBytes): OpenPDF decodes PNG through javax.imageio.ImageIO,
        // which does not exist on Android and kills the app with NoClassDefFoundError.
        val image = PdfImages.fromBitmap(bitmap)
        bitmap.recycle()
        return image
    }
}
