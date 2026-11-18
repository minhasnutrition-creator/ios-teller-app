package org.apache.poi.xssf.usermodel

import org.apache.poi.ss.usermodel.Workbook
import java.io.InputStream

open class XSSFWorkbook(stream: InputStream? = null) : Workbook()
