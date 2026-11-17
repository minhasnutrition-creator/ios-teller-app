package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

private fun printPdf(context: Context, file: File, title: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(title, object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle
        ) {
            if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder(title)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<android.print.PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }, null)
}

private fun printExcelDataAsPdf(context: Context, title: String, data: List<List<String>>) {
    val tempFile = File(context.cacheDir, "temp_excel_print_${System.currentTimeMillis()}.pdf")
    val document = Document(PageSize.A4.rotate()) // Landscape is ideal for sheets
    try {
        val writer = PdfWriter.getInstance(document, FileOutputStream(tempFile))
        document.open()
        
        val pTitle = Paragraph(title.uppercase(Locale.getDefault()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f))
        pTitle.spacingAfter = 12f
        document.add(pTitle)
        
        if (data.isNotEmpty()) {
            val colCount = data.maxOf { it.size }
            val table = PdfPTable(colCount)
            table.widthPercentage = 100f
            
            data.forEachIndexed { rowIndex, rowData ->
                for (colIdx in 0 until colCount) {
                    val cellText = rowData.getOrNull(colIdx) ?: ""
                    val isHeader = rowIndex == 0
                    val font = if (isHeader) {
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f)
                    } else {
                        FontFactory.getFont(FontFactory.HELVETICA, 8f)
                    }
                    val cell = PdfPCell(Phrase(cellText, font))
                    cell.setPadding(5f)
                    if (isHeader) {
                        cell.backgroundColor = com.minhas.teller.utils.BaseColor.LIGHT_GRAY
                    }
                    // Align numeric right, others left
                    if (cellText.toDoubleOrNull() != null) {
                        cell.horizontalAlignment = Element.ALIGN_RIGHT
                    } else {
                        cell.horizontalAlignment = Element.ALIGN_LEFT
                    }
                    table.addCell(cell)
                }
            }
            document.add(table)
        }
        document.close()
        
        printPdf(context, tempFile, title)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Print Generation Failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun loadExcelData(file: File): List<List<String>> {
    val list = mutableListOf<List<String>>()
    if (!file.exists()) return list
    try {
        com.minhas.teller.TellerApp.configureGlobalProperties()
        FileInputStream(file).use { fis ->
            val workbook = XSSFWorkbook(fis)
            val sheet = workbook.getSheetAt(0)
            val rowCount = sheet.lastRowNum + 1
            for (i in 0 until rowCount) {
                val row = sheet.getRow(i)
                val rowData = mutableListOf<String>()
                if (row != null) {
                    val colCount = row.lastCellNum.toInt()
                    for (j in 0 until colCount) {
                        val cell = row.getCell(j)
                        rowData.add(cell?.toString() ?: "")
                    }
                }
                list.add(rowData)
            }
            workbook.close()
        }
    } catch (e: Throwable) {
        android.util.Log.e("ExcelViewerScreen", "Failed to load Excel data: ${e.message}", e)
    }
    return list
}

private fun saveExcelData(context: Context, file: File, data: List<List<String>>) {
    try {
        com.minhas.teller.TellerApp.configureGlobalProperties()
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Report")
        
        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
            }
            setFont(font)
        }
        
        data.forEachIndexed { rowIndex, rowData ->
            val row = sheet.createRow(rowIndex)
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = row.createCell(colIndex)
                val doubleVal = cellValue.toDoubleOrNull()
                if (doubleVal != null) {
                    cell.setCellValue(doubleVal)
                } else {
                    cell.setCellValue(cellValue)
                }
                if (rowIndex == 0) {
                    cell.cellStyle = headerStyle
                }
            }
        }
        
        FileOutputStream(file).use { fos ->
            workbook.write(fos)
        }
        workbook.close()
        Toast.makeText(context, "✓ Spreadsheet Saved Successfully", Toast.LENGTH_SHORT).show()
    } catch (e: Throwable) {
        android.util.Log.e("ExcelViewerScreen", "Failed to save Excel data: ${e.message}", e)
        Toast.makeText(context, "Save Failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
    }
}

private fun getColLetter(colIndex: Int): String {
    var temp = colIndex
    var letter = ""
    while (temp >= 0) {
        letter = ('A'.code + (temp % 26)).toChar() + letter
        temp = (temp / 26) - 1
    }
    return letter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(navController: NavController, filePath: String, title: String) {
    val context = LocalContext.current
    val file = remember { File(filePath) }
    var gridData by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val hScrollState = rememberScrollState()

    LaunchedEffect(filePath) {
        isLoading = true
        gridData = loadExcelData(file)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(title.ifBlank { "Excel Viewer" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E3F),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1A3A), Color(0xFF121224))))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save
                    Button(
                        onClick = { saveExcelData(context, file, gridData) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TerminologyManager.getTerm("Save"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Print
                    Button(
                        onClick = { printExcelDataAsPdf(context, title, gridData) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TerminologyManager.getTerm("Print"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Share
                    Button(
                        onClick = { ExportUtils.shareFile(context, file, "application/vnd.ms-excel", "Share $title") },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TerminologyManager.getTerm("Share"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Export (to downloads)
                    Button(
                        onClick = {
                            val saved = ExportUtils.saveFileToDownloads(context, file, file.name, "application/vnd.ms-excel")
                            Toast.makeText(context, if (saved) "✓ Exported to Downloads" else "Could not save", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1.5f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TerminologyManager.getTerm("Export Excel"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF13132B), Color(0xFF1E1E3F))))
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (gridData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(TerminologyManager.getTerm("No spreadsheet data loaded"), color = Color.Gray)
                }
            } else {
                val maxCols = remember(gridData) { gridData.maxOfOrNull { it.size } ?: 0 }
                
                Column(modifier = Modifier.fillMaxSize()) {
                    // Formula / Active Cell Editor Bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3F)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2196F3).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                val cellCoord = selectedCell?.let { "${getColLetter(it.second)}${it.first + 1}" } ?: "fx"
                                Text(cellCoord, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            
                            val editValue = remember(selectedCell, gridData) {
                                selectedCell?.let { gridData.getOrNull(it.first)?.getOrNull(it.second) } ?: ""
                            }
                            
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = { newValue ->
                                    selectedCell?.let { (r, c) ->
                                        gridData = gridData.mapIndexed { rowIndex, rowData ->
                                            if (rowIndex == r) {
                                                rowData.mapIndexed { colIndex, cellVal ->
                                                    if (colIndex == c) newValue else cellVal
                                                }
                                            } else {
                                                rowData
                                            }
                                        }
                                    }
                                },
                                placeholder = { Text(TerminologyManager.getTerm("Select a cell to edit..."), color = Color.Gray, fontSize = 13.sp) },
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF9C27B0),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    cursorColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(48.dp)
                            )
                        }
                    }

                    // Grid Control buttons (Insert/Delete Row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val newRow = List(maxCols) { "" }
                                gridData = if (selectedCell != null) {
                                    val idx = selectedCell!!.first + 1
                                    gridData.take(idx) + listOf(newRow) + gridData.drop(idx)
                                } else {
                                    gridData + listOf(newRow)
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF81C784))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(TerminologyManager.getTerm("Insert Row"), fontSize = 12.sp)
                        }
                        
                        if (selectedCell != null && selectedCell!!.first > 0) {
                            TextButton(
                                onClick = {
                                    val r = selectedCell!!.first
                                    gridData = gridData.filterIndexed { index, _ -> index != r }
                                    selectedCell = null
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE57373))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(TerminologyManager.getTerm("Delete Row"), fontSize = 12.sp)
                            }
                        }
                    }

                    // Column Letter Labels Headers Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .horizontalScroll(hScrollState)
                            .background(Color.White.copy(alpha = 0.02f))
                    ) {
                        for (colIndex in 0 until maxCols) {
                            val cellWidth = if (colIndex == 0) 160.dp else 120.dp
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .height(24.dp)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getColLetter(colIndex),
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Excel Grid Rows
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        itemsIndexed(gridData) { rowIndex, rowData ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(hScrollState)
                            ) {
                                for (colIndex in 0 until maxCols) {
                                    val cellValue = rowData.getOrNull(colIndex) ?: ""
                                    val isHeader = rowIndex == 0
                                    val isSelected = selectedCell == Pair(rowIndex, colIndex)
                                    val cellWidth = if (colIndex == 0) 160.dp else 120.dp
                                    
                                    val cellBg = when {
                                        isHeader -> Color(0xFF2196F3).copy(alpha = 0.15f)
                                        isSelected -> Color(0xFF9C27B0).copy(alpha = 0.25f)
                                        rowIndex % 2 == 0 -> Color.White.copy(alpha = 0.04f)
                                        else -> Color.Transparent
                                    }
                                    val cellBorder = if (isSelected) {
                                        Color(0xFF9C27B0)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .height(44.dp)
                                            .background(cellBg)
                                            .border(0.5.dp, cellBorder)
                                            .clickable {
                                                selectedCell = Pair(rowIndex, colIndex)
                                            },
                                        contentAlignment = if (cellValue.toDoubleOrNull() != null) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellValue,
                                            color = if (isHeader) Color(0xFFC5B0E0) else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
