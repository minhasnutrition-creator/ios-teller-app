package com.lowagie.text.pdf

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Image
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle

open class RGBColor(
    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
    val alpha: Int = 255
)

open class PdfPCell(val phrase: Phrase = Phrase()) {
    constructor(paragraph: Paragraph) : this(Phrase(paragraph.text, paragraph.font))
    constructor(image: Image) : this()

    var border: Int = Rectangle.BOX
    var borderWidth: Float = 1f
    var borderColor: Any? = null
    var backgroundColor: Any? = null
    var horizontalAlignment: Int = Element.ALIGN_LEFT
    var verticalAlignment: Int = Element.ALIGN_TOP
    var colspan: Int = 1
    var rowspan: Int = 1
    var fixedHeight: Float = 0f
    var minimumHeight: Float = 0f
    var paddingTop: Float = 0f
    var paddingBottom: Float = 0f
    var paddingLeft: Float = 0f
    var paddingRight: Float = 0f

    fun setPadding(p: Float) {
        paddingTop = p
        paddingBottom = p
        paddingLeft = p
        paddingRight = p
    }

    fun addElement(element: Any) {}
}

open class PdfPTable(val numColumns: Int) {
    var widthPercentage: Float = 100f
    var spacingBefore: Float = 0f
    var spacingAfter: Float = 0f
    var horizontalAlignment: Int = Element.ALIGN_CENTER
    val defaultCell: PdfPCell = PdfPCell()

    fun setWidths(widths: FloatArray) {}
    fun setWidths(widths: IntArray) {}
    fun setTotalWidth(width: Float) {}
    fun addCell(cell: PdfPCell) {}
    fun addCell(phrase: Phrase) {}
    fun addCell(text: String) {}
    fun addCell(image: Image) {}
}

open class PdfContentByte {
    fun saveState() {}
    fun restoreState() {}
    fun setRGBColorFill(r: Int, g: Int, b: Int) {}
    fun setRGBColorStroke(r: Int, g: Int, b: Int) {}
    fun rectangle(x: Float, y: Float, w: Float, h: Float) {}
    fun fill() {}
    fun stroke() {}
}

open class PdfWriter {
    companion object {
        fun getInstance(document: Document, os: Any?): PdfWriter = PdfWriter()
    }

    var pageEvent: Any? = null
    val directContent: PdfContentByte = PdfContentByte()
    fun close() {}
}

open class PdfPageEventHelper
class PdfShading
class PdfShadingPattern(val shading: PdfShading? = null)
