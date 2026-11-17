package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.OcrHelper
import com.minhas.teller.utils.ScannedInventoryItem
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val itemTintPalette = listOf(
    Color(0xFFFFF3CD), Color(0xFFFFD9D2), Color(0xFFD6E9FF), Color(0xFFD3F3DA),
    Color(0xFFEBD9FF), Color(0xFFFFE3C2), Color(0xFFCFF3EC), Color(0xFFFFD9EC)
)

private fun itemTint(name: String): Color =
    itemTintPalette[Math.abs(name.hashCode()) % itemTintPalette.size]

private fun formatQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toInt().toString() else "%.2f".format(q)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StockItemListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val stockRepository = remember { StockItemRepository(database.stockItemDao(), database.inventoryEntryDao()) }
    val viewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockRepository))
    val stockItems by viewModel.allStockItems.collectAsState()

    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val units by unitViewModel.allUnits.collectAsState()

    var showExportMenu by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<com.minhas.teller.data.entities.StockItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showItemOptionsDialog by remember { mutableStateOf(false) }
    var selectedStockItem by remember { mutableStateOf<com.minhas.teller.data.entities.StockItem?>(null) }

    // Item Long-Press Options Dialog
    if (showItemOptionsDialog && selectedStockItem != null) {
        val item = selectedStockItem!!
        AlertDialog(
            onDismissRequest = {
                showItemOptionsDialog = false
                selectedStockItem = null
            },
            title = {
                Text(item.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text(
                    TerminologyManager.getTerm("What would you like to do with this item?"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showItemOptionsDialog = false
                            itemToDelete = item
                            selectedStockItem = null
                            showDeleteConfirmDialog = true
                        }
                    ) {
                        Text(
                            TerminologyManager.getTerm("Remove"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = {
                            showItemOptionsDialog = false
                            selectedStockItem = null
                            navController.navigate(
                                Screen.AddEditStockItem.route.replace("{stockItemId}", item.id.toString())
                            )
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Edit"), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showItemOptionsDialog = false
                        selectedStockItem = null
                    }
                ) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    if (showDeleteConfirmDialog && itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                itemToDelete = null
            },
            title = {
                Text(
                    TerminologyManager.getTerm("Permanently Delete Item?"),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    TerminologyManager.getTerm("Are you sure you want to permanently delete '${item.name}'? This will remove it from active stock and future billing. This action cannot be undone.")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.delete(item)
                        Toast.makeText(context, TerminologyManager.getTerm("Stock item permanently deleted"), Toast.LENGTH_SHORT).show()
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(TerminologyManager.getTerm("Permanently Delete"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    // OCR scan state
    val coroutineScope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scannedItem by remember { mutableStateOf<ScannedInventoryItem?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun createImageUri(): Uri {
        val file = File(context.cacheDir, "inv_scan_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            Toast.makeText(context, "Camera did not capture a photo. Please try again.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val uri = photoUri ?: run {
            Toast.makeText(context, "Could not prepare photo storage. Please try again.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        isScanning = true
        coroutineScope.launch {
            val result = OcrHelper.scanInventoryItem(context, uri)
            isScanning = false
            when {
                result.name.isNotBlank() -> {
                    scannedItem = result
                }
                !result.barcode.isNullOrBlank() -> {
                    Toast.makeText(context, "Barcode found: ${result.barcode} — but no readable text. Try better lighting.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(context, "No text found. Try better lighting, a closer angle, or ensure the label is in English or Hindi.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val calendar = Calendar.getInstance()
    var toDate by remember { mutableStateOf(calendar.time) }
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    var fromDate by remember { mutableStateOf(calendar.time) }

    val dateFormatter = SimpleDateFormat("dd MMM yy", Locale.getDefault())

    val fromDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        fromDate = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.time
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    val toDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        toDate = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }.time
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var movements by remember { mutableStateOf<Map<Int, com.minhas.teller.data.dao.StockMovement>>(emptyMap()) }
    var cumulativeMovements by remember { mutableStateOf<Map<Int, com.minhas.teller.data.dao.StockMovement>>(emptyMap()) }

    LaunchedEffect(fromDate, toDate, stockItems, currentCompanyId) {
        val cid = currentCompanyId ?: return@LaunchedEffect
        val moveList = stockRepository.getStockMovements(fromDate, toDate, cid)
        movements = moveList.associateBy { it.itemId }
        // Cumulative movements from the beginning up to `toDate`, so "Closing" reflects the true
        // on-hand balance independent of which month the user has selected.
        val cumulativeList = stockRepository.getStockMovements(java.util.Date(0L), toDate, cid)
        cumulativeMovements = cumulativeList.associateBy { it.itemId }
    }

    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(stockItems, searchQuery) {
        stockItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val totalRecordedStock = remember(stockItems, cumulativeMovements) {
        stockItems.sumOf {
            it.openingStock + (cumulativeMovements[it.id]?.inwardQty ?: 0.0) - (cumulativeMovements[it.id]?.outwardQty ?: 0.0)
        }
    }

    val totalInventoryValue = remember(stockItems, cumulativeMovements) {
        stockItems.sumOf {
            val qty = it.openingStock + (cumulativeMovements[it.id]?.inwardQty ?: 0.0) - (cumulativeMovements[it.id]?.outwardQty ?: 0.0)
            qty * it.rate
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0F1A12), Color(0xFF060D40))))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(TerminologyManager.getTerm("Stock Item List"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { navController.navigate(Screen.MonthlyInventory.route) }) {
                        Icon(Icons.Default.CalendarMonth, "Monthly Inventory", tint = Color.White)
                    }
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.PictureAsPdf, "Export", tint = Color.White)
                    }
                }
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            val uri = createImageUri()
                            photoUri = uri
                            cameraLauncher.launch(uri)
                        },
                        shape = CircleShape,
                        containerColor = Color(0xFF29B6F6)
                    ) {
                        Icon(Icons.Filled.CameraAlt, "Scan Item", tint = Color.Black, modifier = Modifier.size(26.dp))
                    }
                    FloatingActionButton(
                        onClick = { navController.navigate(Screen.AddEditStockItem.route.replace("{stockItemId}", "-1")) },
                        shape = CircleShape,
                        containerColor = Color(0xFF81C784)
                    ) {
                        Icon(Icons.Filled.Add, "Add Item", tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Color(0xFF81C784).copy(alpha = 0.22f), Color(0xFF40C4FF).copy(alpha = 0.22f))))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(TerminologyManager.getTerm("ACTIVE STOCK ITEMS"), color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            AutoShrinkText(
                                text = "${stockItems.size} Items",
                                color = Color.White,
                                maxFontSize = 36.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF81C784).copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    AutoShrinkText("${formatQty(totalRecordedStock)} units", color = Color(0xFF81C784), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFB74D).copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    AutoShrinkText("₹ ${"%,.0f".format(totalInventoryValue)} value", color = Color(0xFFFFB74D), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                                .background(Color(0xFF1B2E1E).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(TerminologyManager.getTerm("Search"), color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                                .clickable { fromDatePickerDialog.show() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AutoShrinkText(dateFormatter.format(fromDate), color = Color.Black, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                                .clickable { toDatePickerDialog.show() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AutoShrinkText(dateFormatter.format(toDate), color = Color.Black, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(TerminologyManager.getTerm("Item"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f), fontSize = 13.sp)
                        Text(TerminologyManager.getTerm("In / Out"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontSize = 13.sp)
                        Text(TerminologyManager.getTerm("Closing"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, fontSize = 13.sp)
                    }
                }

                itemsIndexed(filteredItems) { _, stockItem ->
                    val unitName = units.find { it.id == stockItem.unitId }?.name ?: ""
                    val mov = movements[stockItem.id]
                    val inQty = mov?.inwardQty ?: 0.0
                    val outQty = mov?.outwardQty ?: 0.0
                    val cum = cumulativeMovements[stockItem.id]
                    val clsQty = stockItem.openingStock + (cum?.inwardQty ?: 0.0) - (cum?.outwardQty ?: 0.0)

                    val stockStatusText = when {
                        clsQty <= 0 -> "OUT OF STOCK"
                        clsQty <= 3 -> "LOW STOCK"
                        else -> "IN STOCK"
                    }
                    val stockStatusColor = when {
                        clsQty <= 0 -> Color(0xFFFF5252)
                        clsQty <= 3 -> Color(0xFFFFD54F)
                        else -> Color(0xFF00E676)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(0.5.dp, Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    navController.navigate(
                                        Screen.AddEditStockItem.route.replace("{stockItemId}", stockItem.id.toString())
                                    )
                                },
                                onLongClick = {
                                    selectedStockItem = stockItem
                                    showItemOptionsDialog = true
                                }
                            ),
                        colors = CardDefaults.cardColors(containerColor = itemTint(stockItem.name).copy(alpha = 0.22f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.8f)) {
                                    AutoShrinkText(
                                        stockItem.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AutoShrinkText("Unit: $unitName", color = Color.Gray, maxFontSize = 11.sp, minFontSize = 8.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = stockStatusColor.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, stockStatusColor.copy(alpha = 0.3f))
                                        ) {
                                            AutoShrinkText(
                                                text = stockStatusText,
                                                color = stockStatusColor.copy(alpha = 0.8f),
                                                maxFontSize = 7.5.sp,
                                                minFontSize = 5.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AutoShrinkText(
                                        "${formatQty(inQty)} In",
                                        color = Color(0xFF00E676),
                                        maxFontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    AutoShrinkText(
                                        "${formatQty(outQty)} Out",
                                        color = Color(0xFFFF5252),
                                        maxFontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                AutoShrinkText(
                                    text = "${formatQty(clsQty)} $unitName",
                                    color = Color.White,
                                    maxFontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scanning overlay
        if (isScanning) {
            Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1B2A1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF81C784), modifier = Modifier.size(48.dp))
                        Text(TerminologyManager.getTerm("Scanning item..."), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Scanned item confirmation dialog
        val scanned = scannedItem
        if (scanned != null) {
            var editName by remember(scanned) { mutableStateOf(scanned.name) }
            var editRate by remember(scanned) { mutableStateOf(scanned.rate?.toString() ?: "") }
            var editOpeningStock by remember(scanned) { mutableStateOf("0") }
            var editUnit by remember(scanned) { mutableStateOf(scanned.unitGuess ?: "") }
            var editBarcode by remember(scanned) { mutableStateOf(scanned.barcode ?: "") }
            var showUnitDropdown by remember { mutableStateOf(false) }
            var isSaving by remember { mutableStateOf(false) }
            var showRawText by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { scannedItem = null },
                containerColor = Color(0xFF1A2A1E),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                        Text(TerminologyManager.getTerm("Confirm Scanned Item"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(TerminologyManager.getTerm("Review and edit the details below before saving."),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )

                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text(TerminologyManager.getTerm("Item Name"), color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF81C784),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color(0xFF81C784)
                            )
                        )

                        OutlinedTextField(
                            value = editRate,
                            onValueChange = { editRate = it },
                            label = { Text(TerminologyManager.getTerm("Rate / Price"), color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF81C784),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color(0xFF81C784)
                            )
                        )

                        OutlinedTextField(
                            value = editOpeningStock,
                            onValueChange = { editOpeningStock = it },
                            label = { Text(TerminologyManager.getTerm("Opening Stock"), color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF81C784),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color(0xFF81C784)
                            )
                        )

                        ExposedDropdownMenuBox(
                            expanded = showUnitDropdown,
                            onExpandedChange = { showUnitDropdown = !showUnitDropdown }
                        ) {
                            OutlinedTextField(
                                value = editUnit.ifBlank { "Select unit" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(TerminologyManager.getTerm("Unit"), color = Color.White.copy(alpha = 0.7f)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showUnitDropdown) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF81C784),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = showUnitDropdown,
                                onDismissRequest = { showUnitDropdown = false }
                            ) {
                                units.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u.name) },
                                        onClick = {
                                            editUnit = u.name
                                            showUnitDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editBarcode,
                            onValueChange = { editBarcode = it },
                            label = { Text(TerminologyManager.getTerm("Barcode (optional)"), color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF81C784),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color(0xFF81C784)
                            )
                        )

                        // ── Raw OCR text section ──
                        if (scanned.rawOcrText.isNotBlank()) {
                            TextButton(
                                onClick = { showRawText = !showRawText },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    if (showRawText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (showRawText) "Hide raw OCR text" else "Show raw OCR text",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                            if (showRawText) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF0D1A0F),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = scanned.rawOcrText,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editName.isBlank()) {
                                Toast.makeText(context, "Item name cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val companyId = currentCompanyId ?: 0
                            val matchedUnit = units.firstOrNull { it.name.equals(editUnit, ignoreCase = true) }
                            isSaving = true
                            coroutineScope.launch {
                                val newItem = StockItem(
                                    name = editName.trim(),
                                    alias = editBarcode.trim().ifBlank { null },
                                    unitId = matchedUnit?.id ?: 0,
                                    rate = editRate.toDoubleOrNull() ?: 0.0,
                                    openingStock = editOpeningStock.toDoubleOrNull() ?: 0.0,
                                    companyId = companyId
                                )
                                stockRepository.insert(newItem)
                                isSaving = false
                                scannedItem = null
                                Toast.makeText(context, "Item added to inventory", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text(TerminologyManager.getTerm("Save Item"), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { scannedItem = null }) {
                            Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            scannedItem = null
                            val uri = createImageUri()
                            photoUri = uri
                            cameraLauncher.launch(uri)
                        }) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(TerminologyManager.getTerm("Retake"), color = Color(0xFF4FC3F7))
                        }
                    }
                }
            )
        }
    }
}
