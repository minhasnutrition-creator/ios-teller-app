package org.apache.poi.hssf.usermodel

import org.apache.poi.ss.usermodel.Workbook
import java.io.InputStream

open class HSSFWorkbook(stream: InputStream? = null) : Workbook()
