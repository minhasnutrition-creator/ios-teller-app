package com.minhas.teller.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import com.lowagie.text.Image
import java.io.File

/**
 * Turns Android bitmaps and image files into OpenPDF [Image]s **without** going through
 * `Image.getInstance(bytes)`.
 *
 * That innocent-looking call is a crash: for anything PNG, OpenPDF decodes via
 * `javax.imageio.ImageIO`, which is a Java SE class that does not exist on Android, so it throws
 * `NoClassDefFoundError`. And because that is an `Error` rather than an `Exception`, every
 * `catch (e: Exception)` guarding image code waves it straight through and the app dies — which is
 * exactly what happened when an invoice template drew a UPI QR code.
 *
 * So we decode with Android's own BitmapFactory and hand OpenPDF **raw** pixels, which it stores
 * as-is with no decoder involved.
 */
object PdfImages {

    /** Bitmaps bigger than this are downscaled first — raw pixels are uncompressed in the PDF. */
    private const val MAX_DIMENSION = 1200

    /**
     * Raw-RGB [Image] from [bitmap]. Transparency is flattened onto white, since an invoice is
     * printed on white paper anyway and raw RGB has no alpha channel.
     */
    fun fromBitmap(bitmap: Bitmap): Image {
        val scaled = downscaleIfNeeded(bitmap)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val rgb = ByteArray(width * height * 3)
        var out = 0
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (alpha == 255) {
                rgb[out++] = r.toByte()
                rgb[out++] = g.toByte()
                rgb[out++] = b.toByte()
            } else {
                // Composite onto white: result = colour*a + 255*(1-a)
                rgb[out++] = blendOnWhite(r, alpha)
                rgb[out++] = blendOnWhite(g, alpha)
                rgb[out++] = blendOnWhite(b, alpha)
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        // components = 3 (RGB), bits per component = 8.
        return Image.getInstance(width, height, 3, 8, rgb)
    }

    /**
     * [Image] from encoded image bytes (PNG/JPEG/WebP), or null if they can't be decoded.
     * Use this instead of `Image.getInstance(bytes)` anywhere the bytes might be PNG.
     */
    fun fromBytes(bytes: ByteArray?): Image? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val image = fromBitmap(bitmap)
            bitmap.recycle()
            image
        } catch (t: Throwable) {
            android.util.Log.w("PdfImages", "Could not decode image bytes", t)
            null
        }
    }

    /** [Image] from a file on disk, or null if it is missing or not a readable image. */
    fun fromFile(path: String?): Image? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val image = fromBitmap(bitmap)
            bitmap.recycle()
            image
        } catch (t: Throwable) {
            android.util.Log.w("PdfImages", "Could not load image $path", t)
            null
        }
    }

    @VisibleForTesting
    internal fun blendOnWhite(channel: Int, alpha: Int): Byte =
        ((channel * alpha + 255 * (255 - alpha)) / 255).coerceIn(0, 255).toByte()

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
