package com.lowagie.text

import java.io.OutputStream

open class Rectangle(val width: Float = 595f, val height: Float = 842f) {
    companion object {
        const val NO_BORDER = 0
        const val TOP = 1
        const val BOTTOM = 2
        const val LEFT = 4
        const val RIGHT = 8
        const val BOX = 15
    }
}

object PageSize {
    val A4 = Rectangle(595f, 842f)
    val LETTER = Rectangle(612f, 792f)
    val A5 = Rectangle(420f, 595f)
}

object Element {
    const val ALIGN_LEFT = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_RIGHT = 2
    const val ALIGN_JUSTIFIED = 3
    const val ALIGN_TOP = 4
    const val ALIGN_MIDDLE = 5
    const val ALIGN_BOTTOM = 6
}

open class Font(
    val family: Any? = null,
    val size: Float = 12f,
    val style: Int = 0,
    val color: Any? = null
) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val UNDERLINE = 4
        const val BOLDITALIC = 3
        const val HELVETICA = "Helvetica"
        const val TIMES_ROMAN = "Times-Roman"
        const val COURIER = "Courier"
    }
}

object FontFactory {
    const val HELVETICA = "Helvetica"
    const val HELVETICA_BOLD = "Helvetica-Bold"
    const val HELVETICA_OBLIQUE = "Helvetica-Oblique"
    const val HELVETICA_BOLDOBLIQUE = "Helvetica-BoldOblique"
    const val COURIER = "Courier"
    const val TIMES = "Times"
    const val TIMES_ROMAN = "Times-Roman"
    const val TIMES_BOLD = "Times-Bold"

    fun getFont(name: String, size: Float = 12f, style: Int = 0, color: Any? = null): Font = Font(name, size, style, color)
    fun getFont(name: String, size: Float, color: Any?): Font = Font(name, size, 0, color)
}

open class Chunk(val content: String = "", val font: Font = Font()) {
    companion object {
        val NEWLINE = Chunk("\n")
    }
    fun setBackground(color: Any?): Chunk = this
}

open class Phrase(val content: String = "", val font: Font = Font()) {
    constructor(chunk: Chunk) : this(chunk.content, chunk.font)
    fun add(element: Any): Boolean = true
}

open class Paragraph(val text: String = "", val font: Font = Font()) {
    constructor(phrase: Phrase) : this(phrase.content, phrase.font)
    var alignment: Int = Element.ALIGN_LEFT
    var spacingBefore: Float = 0f
    var spacingAfter: Float = 0f
    var leading: Float = 0f
    var multiAnalyzed: Boolean = false
    fun add(element: Any): Boolean = true
    fun setAlignment(align: Int) { alignment = align }
    fun setSpacingBefore(spacing: Float) { spacingBefore = spacing }
    fun setSpacingAfter(spacing: Float) { spacingAfter = spacing }
    fun setLeading(lead: Float) { leading = lead }
}

open class Image {
    companion object {
        fun getInstance(bytes: ByteArray): Image = Image()
        fun getInstance(path: String): Image = Image()
        fun getInstance(url: Any?): Image = Image()
    }
    var alignment: Int = 0
    fun scaleToFit(w: Float, h: Float) {}
    fun scalePercent(percent: Float) {}
    fun scaleAbsolute(w: Float, h: Float) {}
    fun setAbsolutePosition(x: Float, y: Float) {}
}

open class Document(
    val pageSize: Rectangle = PageSize.A4,
    val marginLeft: Float = 36f,
    val marginRight: Float = 36f,
    val marginTop: Float = 36f,
    val marginBottom: Float = 36f
) {
    val isOpen: Boolean get() = false
    fun open() {}
    fun close() {}
    fun add(element: Any): Boolean = true
    fun setMargins(l: Float, r: Float, t: Float, b: Float): Boolean = true
    fun newPage(): Boolean = true
}
