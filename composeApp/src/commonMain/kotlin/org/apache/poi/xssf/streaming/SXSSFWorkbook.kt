package org.apache.poi.xssf.streaming

import org.apache.poi.ss.usermodel.Workbook

open class SXSSFWorkbook(val rowAccessWindowSize: Int = 100) : Workbook() {
    fun dispose(): Boolean = true
}
