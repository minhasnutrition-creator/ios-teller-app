package android.graphics

import androidx.compose.ui.graphics.ImageBitmap

open class Bitmap {
    var width: Int = 100
    var height: Int = 100

    enum class Config {
        ARGB_8888,
        RGB_565
    }

    enum class CompressFormat {
        JPEG,
        PNG,
        WEBP
    }

    fun compress(format: CompressFormat, quality: Int, stream: java.io.OutputStream): Boolean {
        return true
    }

    fun setPixel(x: Int, y: Int, color: Int) {}
    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {}
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {}
    fun getPixel(x: Int, y: Int): Int = 0
    fun eraseColor(color: Int) {}
    fun recycle() {}
    val isRecycled: Boolean get() = false

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            val b = Bitmap()
            b.width = width
            b.height = height
            return b
        }
        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap = src
    }
}

fun Bitmap.asImageBitmap(): ImageBitmap =
    ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))

class Path {
    fun moveTo(x: Float, y: Float) {}
    fun lineTo(x: Float, y: Float) {}
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {}
    fun reset() {}
    fun close() {}
}

class RectF(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

class Typeface {
    companion object {
        val DEFAULT = Typeface()
        val DEFAULT_BOLD = Typeface()
        const val BOLD = 1
        const val NORMAL = 0
        fun create(family: String?, style: Int): Typeface = Typeface()
        fun create(family: Typeface?, style: Int): Typeface = Typeface()
    }
}

class BlurMaskFilter(val radius: Float, val style: Blur) {
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

open class ColorMatrix {
    fun setSaturation(sat: Float) {}
}

open class ColorMatrixColorFilter(val matrix: Any? = null)

class Paint {
    var color: Int = 0
    var alpha: Int = 255
    var strokeWidth: Float = 1f
    var isAntiAlias: Boolean = true
    var style: Style = Style.FILL
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var textSize: Float = 12f
    var typeface: Typeface? = null
    var textAlign: Align = Align.LEFT
    var isFakeBoldText: Boolean = false
    var maskFilter: Any? = null
    var colorFilter: Any? = null

    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    fun measureText(text: String): Float = text.length * 8f
    fun setAlpha(a: Int) { alpha = a }
    fun descent(): Float = 3f
    fun ascent(): Float = -10f
}

fun androidx.compose.ui.graphics.NativeCanvas.drawText(text: String, x: Float, y: Float, paint: Any? = null) {}

class Canvas(val bitmap: Bitmap? = null) {
    fun drawColor(color: Int) {}
    fun drawPath(path: Path, paint: Paint) {}
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {}
    fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {}
    fun drawRect(rect: RectF, paint: Paint) {}
    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {}
    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {}
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {}
    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint? = null) {}
    fun save(): Int = 0
    fun restore() {}
    fun translate(dx: Float, dy: Float) {}
    fun scale(sx: Float, sy: Float) {}
    fun rotate(degrees: Float) {}
}

object Color {
    const val BLACK = -16777216
    const val WHITE = -1
    const val RED = -65536
    const val GREEN = -16711936
    const val BLUE = -16776961
    const val TRANSPARENT = 0
    const val LTGRAY = -3355444
    const val GRAY = -7829368
    const val DKGRAY = -12303292
    const val YELLOW = -256
    const val CYAN = -16711681
    const val MAGENTA = -65281

    fun parseColor(colorString: String): Int = 0
}
