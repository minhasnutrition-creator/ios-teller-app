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
    constructor(table: PdfPTable) : this()

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

    fun setPaddingTop(p: Float) { paddingTop = p }
    fun setPaddingBottom(p: Float) { paddingBottom = p }
    fun setPaddingLeft(p: Float) { paddingLeft = p }
    fun setPaddingRight(p: Float) { paddingRight = p }
    fun setBorder(b: Int) { border = b }
    fun setBorderWidth(w: Float) { borderWidth = w }
    fun setBorderColor(c: Any?) { borderColor = c }
    fun setBackgroundColor(c: Any?) { backgroundColor = c }
    fun setHorizontalAlignment(a: Int) { horizontalAlignment = a }
    fun setVerticalAlignment(a: Int) { verticalAlignment = a }
    fun setColspan(c: Int) { colspan = c }
    fun setRowspan(r: Int) { rowspan = r }
    fun setFixedHeight(h: Float) { fixedHeight = h }
    fun setMinimumHeight(h: Float) { minimumHeight = h }

    fun addElement(element: Any) {}
}

open class PdfPTable(val numColumns: Int) {
    constructor(relativeWidths: FloatArray) : this(relativeWidths.size)
    var widthPercentage: Float = 100f
    var spacingBefore: Float = 0f
    var spacingAfter: Float = 0f
    var horizontalAlignment: Int = Element.ALIGN_CENTER
    var headerRows: Int = 0
    val defaultCell: PdfPCell = PdfPCell()

    fun setSpacingBefore(s: Float) { spacingBefore = s }
    fun setSpacingAfter(s: Float) { spacingAfter = s }
    fun setWidthPercentage(w: Float) { widthPercentage = w }
    fun setHorizontalAlignment(a: Int) { horizontalAlignment = a }
    fun setHeaderRows(rows: Int) { headerRows = rows }

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
    fun setColorFill(color: Any?) {}
    fun setColorStroke(color: Any?) {}
    fun moveTo(x: Float, y: Float) {}
    fun lineTo(x: Float, y: Float) {}
    fun closePath() {}
    fun rectangle(x: Float, y: Float, w: Float, h: Float) {}
    fun fill() {}
    fun stroke() {}
    fun setShadingFill(pattern: Any?) {}
    fun paintShading(shading: Any?) {}
    fun setFontAndSize(font: Any?, size: Float) {}
    fun showText(text: String) {}
    fun beginText() {}
    fun endText() {}
    fun setTextMatrix(x: Float, y: Float) {}
}

open class PdfWriter {
    companion object {
        fun getInstance(document: Document, os: Any?): PdfWriter = PdfWriter()
    }

    var pageEvent: Any? = null
    val directContent: PdfContentByte = PdfContentByte()
    val directContentUnder: PdfContentByte = PdfContentByte()
    fun close() {}
}

open class PdfPageEventHelper {
    open fun onStartPage(writer: PdfWriter, document: Document) {}
    open fun onEndPage(writer: PdfWriter, document: Document) {}
    open fun onCloseDocument(writer: PdfWriter, document: Document) {}
    open fun onOpenDocument(writer: PdfWriter, document: Document) {}
}

open class PdfShading {
    companion object {
        fun simpleAxial(writer: PdfWriter, x0: Float, y0: Float, x1: Float, y1: Float, startColor: Any?, endColor: Any?): PdfShading = PdfShading()
    }
}

class PdfShadingPattern(val shading: PdfShading? = null)
