package com.minhas.teller.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.BarcodeLabelGenerator
import com.minhas.teller.utils.BarcodeLabelItem
import com.minhas.teller.utils.TerminologyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val GradientColors = listOf(
    Color(0xFF3B1C78),
    Color(0xFF2B1660),
    Color(0xFF1F1B5E),
    Color(0xFF131B4D),
    Color(0xFF0B1238)
)
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassFillDeep = Color.White.copy(alpha = 0.04f)
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val Cyan = Color(0xFF45E0E6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeGeneratorScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var company by remember { mutableStateOf<Company?>(null) }
    var stockItems by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<StockItem?>(null) }
    var copiesCount by remember { mutableIntStateOf(24) }
    var includePrice by remember { mutableStateOf(true) }
    var includeCompanyName by remember { mutableStateOf(true) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showItemPicker by remember { mutableStateOf(false) }

    LaunchedEffect(currentCompanyId) {
        val cid = currentCompanyId ?: return@LaunchedEffect
        company = db.companyDao().getCompanyById(cid).firstOrNull()
        val items = db.stockItemDao().getAllStockItems(cid)
        stockItems = items
        if (selectedItem == null && items.isNotEmpty()) {
            selectedItem = items.first()
        }
    }

    // Update preview barcode whenever selectedItem changes
    LaunchedEffect(selectedItem) {
        val item = selectedItem
        if (item != null) {
            val code = item.alias?.takeIf { it.isNotBlank() } ?: item.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${item.id.toString().padStart(6, '0')}"
            withContext(Dispatchers.Default) {
                try {
                    previewBitmap = BarcodeLabelGenerator.generateBarcodeBitmap(code, 480, 160)
                } catch (e: Exception) {
                    previewBitmap = null
                }
            }
        } else {
            previewBitmap = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(GradientColors))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TellerTopAppBar(
                    title = TerminologyManager.getTerm("Barcode & Label Generator"),
                    canNavigateBack = true,
                    navigateUp = { navController.navigateUp() }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Item Selection Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassFill)
                            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Cyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null,
                                        tint = Cyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Select Inventory Item",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Generate standard Code-128 barcode sticker",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Selected Item Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GlassFillDeep)
                                .border(1.dp, GlassStroke.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable { showItemPicker = true }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoShrinkText(
                                        text = selectedItem?.name ?: "Tap to choose stock item",
                                        color = if (selectedItem != null) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxFontSize = 15.sp
                                    )
                                    if (selectedItem != null) {
                                        val code = selectedItem?.alias?.takeIf { it.isNotBlank() } ?: selectedItem?.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${selectedItem?.id.toString().padStart(6, '0')}"
                                        AutoShrinkText(
                                            text = "Code: $code | Rate: ₹${"%,.2f".format(selectedItem?.rate ?: 0.0)}",
                                            color = Cyan,
                                            maxFontSize = 12.sp,
                                            style = TextStyle(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Cyan
                                )
                            }
                        }
                    }
                }

                // Live Barcode Sticker Preview Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassFill)
                            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Live Sticker Preview",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Physical sticker mockup
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (includeCompanyName) {
                                    Text(
                                        text = company?.name?.uppercase() ?: "TELLER ENTERPRISES",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = selectedItem?.name ?: "SAMPLE PRODUCT NAME",
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "Barcode",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(55.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(55.dp)
                                            .background(Color.LightGray.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(TerminologyManager.getTerm("Barcode preview"), color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                val codeDisplay = selectedItem?.alias?.takeIf { it.isNotBlank() } ?: selectedItem?.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${selectedItem?.id?.toString()?.padStart(6, '0') ?: "000001"}"
                                AutoShrinkText(
                                    text = codeDisplay,
                                    color = Color.Black,
                                    maxFontSize = 11.sp,
                                    minFontSize = 8.sp,
                                    style = TextStyle(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Medium
                                )

                                if (includePrice) {
                                    AutoShrinkText(
                                        text = "Price: ₹${"%,.2f".format(selectedItem?.rate ?: 0.0)} (Incl. taxes)",
                                        color = Color.Black,
                                        maxFontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Customization Options Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassFill)
                            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Sticker Configuration",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Number of copies on A4 Sheet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Labels on Sheet",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Standard A4 layout (3 × 8 = 24 labels)",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (copiesCount > 1) copiesCount-- },
                                    enabled = copiesCount > 1
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = Cyan)
                                }
                                AutoShrinkText(
                                    text = "$copiesCount",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { if (copiesCount < 48) copiesCount++ },
                                    enabled = copiesCount < 48
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = Cyan)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = GlassStroke.copy(alpha = 0.3f)
                        )

                        // Include Price Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Show Selling Price",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Switch(
                                checked = includePrice,
                                onCheckedChange = { includePrice = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Cyan,
                                    checkedTrackColor = Cyan.copy(alpha = 0.3f)
                                )
                            )
                        }

                        // Include Company Name Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Show Company Header",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Switch(
                                checked = includeCompanyName,
                                onCheckedChange = { includeCompanyName = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Cyan,
                                    checkedTrackColor = Cyan.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                // Action Buttons
                item {
                    Button(
                        onClick = {
                            val item = selectedItem
                            if (item == null) {
                                Toast.makeText(context, "Please select an item first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGeneratingPdf = true
                            scope.launch {
                                try {
                                    val rawCode = item.alias?.takeIf { it.isNotBlank() } ?: item.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${item.id.toString().padStart(6, '0')}"
                                    val code = rawCode.filter { it.code in 32..126 }.trim().ifBlank { "ITM${item.id.toString().padStart(6, '0')}" }
                                    val labelItem = BarcodeLabelItem(
                                        itemName = item.name,
                                        barcodeCode = code,
                                        price = if (includePrice) item.rate else 0.0,
                                        companyName = if (includeCompanyName) company?.name ?: "TELLER" else null
                                    )
                                    val labelList = List(copiesCount) { labelItem }

                                    val pdfFile = withContext(Dispatchers.IO) {
                                        BarcodeLabelGenerator.generateA4LabelSheetPdf(context, labelList, 3)
                                    }

                                    isGeneratingPdf = false

                                    // Straight to the system print dialog — one tap, works with
                                    // whatever printer (or "Save as PDF") the OS already knows about.
                                    com.minhas.teller.utils.ExportUtils.printPdf(context, pdfFile, "Barcode Sticker Sheet")
                                } catch (t: Throwable) {
                                    isGeneratingPdf = false
                                    android.util.Log.e("BarcodeScreen", "Print failed", t)
                                    Toast.makeText(context, "Failed: ${t.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isGeneratingPdf && selectedItem != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan,
                            disabledContainerColor = Cyan.copy(alpha = 0.3f)
                        )
                    ) {
                        if (isGeneratingPdf) {
                            CircularProgressIndicator(
                                color = Color(0xFF0F1A12),
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(TerminologyManager.getTerm("Generating Printable Sheet..."), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = Color(0xFF0F1A12)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate & Print A4 Sticker Sheet",
                                color = Color(0xFF0F1A12),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary action: Share PDF (WhatsApp / Email / Drive)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val item = selectedItem ?: return@OutlinedButton
                                isGeneratingPdf = true
                                scope.launch {
                                    try {
                                        val rawCode = item.alias?.takeIf { it.isNotBlank() } ?: item.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${item.id.toString().padStart(6, '0')}"
                                        val code = rawCode.filter { it.code in 32..126 }.trim().ifBlank { "ITM${item.id.toString().padStart(6, '0')}" }
                                        val labelItem = BarcodeLabelItem(
                                            itemName = item.name,
                                            barcodeCode = code,
                                            price = if (includePrice) item.rate else 0.0,
                                            companyName = if (includeCompanyName) company?.name ?: "TELLER" else null
                                        )
                                        val labelList = List(copiesCount) { labelItem }
                                        val pdfFile = withContext(Dispatchers.IO) {
                                            BarcodeLabelGenerator.generateA4LabelSheetPdf(context, labelList, 3)
                                        }
                                        isGeneratingPdf = false
                                        com.minhas.teller.utils.ExportUtils.shareFile(context, pdfFile, "application/pdf", "Share Barcode Sheet")
                                    } catch (t: Throwable) {
                                        isGeneratingPdf = false
                                        Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isGeneratingPdf && selectedItem != null,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PDF", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val item = selectedItem ?: return@OutlinedButton
                                isGeneratingPdf = true
                                scope.launch {
                                    try {
                                        val rawCode = item.alias?.takeIf { it.isNotBlank() } ?: item.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${item.id.toString().padStart(6, '0')}"
                                        val code = rawCode.filter { it.code in 32..126 }.trim().ifBlank { "ITM${item.id.toString().padStart(6, '0')}" }
                                        val labelItem = BarcodeLabelItem(
                                            itemName = item.name,
                                            barcodeCode = code,
                                            price = if (includePrice) item.rate else 0.0,
                                            companyName = if (includeCompanyName) company?.name ?: "TELLER" else null
                                        )
                                        val labelList = List(copiesCount) { labelItem }
                                        val pdfFile = withContext(Dispatchers.IO) {
                                            BarcodeLabelGenerator.generateA4LabelSheetPdf(context, labelList, 3)
                                        }
                                        isGeneratingPdf = false
                                        val saved = com.minhas.teller.utils.ExportUtils.saveFileToDownloads(context, pdfFile, pdfFile.name, "application/pdf")
                                        if (saved) {
                                            Toast.makeText(context, "Saved to Downloads: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (t: Throwable) {
                                        isGeneratingPdf = false
                                        Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isGeneratingPdf && selectedItem != null,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Searchable Item Picker Dialog
        if (showItemPicker) {
            AlertDialog(
                onDismissRequest = { showItemPicker = false },
                containerColor = Color(0xFF1E1744),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "Choose Inventory Item",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(TerminologyManager.getTerm("Search by name or code..."), color = Color.White.copy(alpha = 0.4f)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Cyan,
                                unfocusedBorderColor = GlassStroke
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val filteredItems = stockItems.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                (it.alias?.contains(searchQuery, ignoreCase = true) == true) ||
                                (it.hsnCode?.contains(searchQuery, ignoreCase = true) == true)
                        }

                        if (filteredItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(TerminologyManager.getTerm("No items found"), color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredItems.size) { idx ->
                                    val item = filteredItems[idx]
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selectedItem?.id == item.id) Cyan.copy(alpha = 0.2f) else GlassFill)
                                            .border(1.dp, if (selectedItem?.id == item.id) Cyan else GlassStroke, RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedItem = item
                                                showItemPicker = false
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                AutoShrinkText(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxFontSize = 14.sp)
                                                val code = item.alias?.takeIf { it.isNotBlank() } ?: item.hsnCode?.takeIf { it.isNotBlank() } ?: "ITM${item.id}"
                                                AutoShrinkText(
                                                    "Code: $code",
                                                    color = Cyan,
                                                    maxFontSize = 11.sp,
                                                    minFontSize = 8.sp,
                                                    style = TextStyle(fontFamily = FontFamily.Monospace)
                                                )
                                            }
                                            AutoShrinkText(
                                                "₹${"%,.2f".format(item.rate)}",
                                                color = Color(0xFF69F0AE),
                                                fontWeight = FontWeight.Bold,
                                                maxFontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showItemPicker = false }) {
                        Text(TerminologyManager.getTerm("Done"), color = Cyan)
                    }
                }
            )
        }
    }
}
