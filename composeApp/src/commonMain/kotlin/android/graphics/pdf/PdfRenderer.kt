package android.graphics.pdf

import android.graphics.Bitmap

class PdfRenderer(val input: Any?) : AutoCloseable {
    val pageCount: Int = 0

    fun openPage(index: Int): Page = Page()
    override fun close() {}

    class Page : AutoCloseable {
        val width: Int = 100
        val height: Int = 100

        fun render(destination: Bitmap, destClip: Any?, transform: Any?, renderMode: Int) {}
        override fun close() {}

        companion object {
            const val RENDER_MODE_FOR_DISPLAY = 1
            const val RENDER_MODE_FOR_PRINT = 2
        }
    }
}
