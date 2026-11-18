package org.apache.poi.ss.usermodel

import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.util.Date

enum class CellType {
    _NONE,
    NUMERIC,
    STRING,
    FORMULA,
    BLANK,
    BOOLEAN,
    ERROR
}

enum class HorizontalAlignment {
    GENERAL,
    LEFT,
    CENTER,
    RIGHT,
    FILL,
    JUSTIFY,
    CENTER_SELECTION,
    DISTRIBUTED
}

enum class VerticalAlignment {
    TOP,
    CENTER,
    BOTTOM,
    JUSTIFY,
    DISTRIBUTED
}

enum class FillPatternType {
    NO_FILL,
    SOLID_FOREGROUND
}

object IndexedColors {
    class ColorIndex(val index: Short) {
        fun getIndex(): Short = index
    }
    val GREY_25_PERCENT = ColorIndex(0x16)
    val BLACK = ColorIndex(0x08)
    val WHITE = ColorIndex(0x09)
    val BLUE = ColorIndex(0x0C)
    val RED = ColorIndex(0x0A)
}

open class Font {
    var bold: Boolean = false
    var fontHeightInPoints: Short = 11
    var fontName: String = "Calibri"
    var color: Short = 0

    fun setBold(b: Boolean) { bold = b }
    fun setFontHeightInPoints(h: Short) { fontHeightInPoints = h }
    fun setFontName(name: String) { fontName = name }
    fun setColor(c: Short) { color = c }
}

open class DataFormat {
    fun getFormat(format: String): Short = 0
}

open class CellStyle {
    var alignment: HorizontalAlignment = HorizontalAlignment.GENERAL
    var verticalAlignment: VerticalAlignment = VerticalAlignment.BOTTOM
    var fillForegroundColor: Short = 0
    var fillPattern: FillPatternType = FillPatternType.NO_FILL
    var borderTop: Short = 0
    var borderBottom: Short = 0
    var borderLeft: Short = 0
    var borderRight: Short = 0
    var dataFormat: Short = 0

    fun setFont(font: Font) {}
    fun setAlignment(align: HorizontalAlignment) { alignment = align }
    fun setVerticalAlignment(align: VerticalAlignment) { verticalAlignment = align }
    fun setFillForegroundColor(color: Short) { fillForegroundColor = color }
    fun setFillPattern(pattern: FillPatternType) { fillPattern = pattern }
    fun setBorderTop(b: Short) { borderTop = b }
    fun setBorderBottom(b: Short) { borderBottom = b }
    fun setBorderLeft(b: Short) { borderLeft = b }
    fun setBorderRight(b: Short) { borderRight = b }
    fun setDataFormat(df: Short) { dataFormat = df }
}

open class Cell(val rowIndex: Int = 0, val columnIndex: Int = 0) {
    var cellType: CellType = CellType.BLANK
    var stringCellValue: String = ""
    var numericCellValue: Double = 0.0
    var booleanCellValue: Boolean = false
    var dateCellValue: Date? = null
    var cellStyle: CellStyle = CellStyle()

    fun setCellStyle(style: CellStyle) { cellStyle = style }

    fun setCellValue(value: String) {
        stringCellValue = value
        cellType = CellType.STRING
    }

    fun setCellValue(value: Double) {
        numericCellValue = value
        cellType = CellType.NUMERIC
    }

    fun setCellValue(value: Boolean) {
        booleanCellValue = value
        cellType = CellType.BOOLEAN
    }

    fun setCellValue(date: Date) {
        dateCellValue = date
        cellType = CellType.NUMERIC
    }
}

open class Row(val rowNum: Int = 0) : Iterable<Cell> {
    private val cells = mutableMapOf<Int, Cell>()

    val lastCellNum: Short get() = (cells.keys.maxOrNull()?.plus(1) ?: 0).toShort()
    val firstCellNum: Short get() = (cells.keys.minOrNull() ?: 0).toShort()

    fun getCell(index: Int): Cell? = cells[index]
    fun createCell(index: Int): Cell = cells.getOrPut(index) { Cell(rowNum, index) }

    override fun iterator(): Iterator<Cell> = cells.values.iterator()
}

open class Sheet(val sheetName: String = "") : Iterable<Row> {
    private val rows = mutableMapOf<Int, Row>()

    val lastRowNum: Int get() = rows.keys.maxOrNull() ?: 0
    val firstRowNum: Int get() = rows.keys.minOrNull() ?: 0

    fun getRow(index: Int): Row? = rows[index]
    fun createRow(index: Int): Row = rows.getOrPut(index) { Row(index) }

    fun setColumnWidth(col: Int, width: Int) {}
    fun autoSizeColumn(col: Int) {}

    override fun iterator(): Iterator<Row> = rows.values.iterator()
}

open class Workbook : Iterable<Sheet> {
    private val sheets = mutableListOf<Sheet>()

    val numberOfSheets: Int get() = sheets.size

    fun getSheetAt(index: Int): Sheet = sheets.getOrElse(index) { Sheet("Sheet$index") }
    fun getSheetName(index: Int): String = getSheetAt(index).sheetName
    fun getSheet(name: String): Sheet? = sheets.find { it.sheetName.equals(name, ignoreCase = true) }
    fun createSheet(name: String = "Sheet${sheets.size + 1}"): Sheet {
        val s = Sheet(name)
        sheets.add(s)
        return s
    }

    fun createCellStyle(): CellStyle = CellStyle()
    fun createFont(): Font = Font()
    fun createDataFormat(): DataFormat = DataFormat()

    fun write(out: OutputStream) {}
    fun close() {}

    override fun iterator(): Iterator<Sheet> = sheets.iterator()
}

object WorkbookFactory {
    fun create(stream: InputStream): Workbook = Workbook()
    fun create(file: File): Workbook = Workbook()
}

object DateUtil {
    fun isCellDateFormatted(cell: Cell): Boolean = false
    fun getJavaDate(date: Double): Date = Date()
}
