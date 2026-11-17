package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.components.ValidatedNumberTextField
import com.minhas.teller.utils.AppTheme
import java.text.SimpleDateFormat
import java.util.*

// Available colors for selection
private val STOCK_COLORS = listOf(
    Color(0xFF9C27B0), // Purple
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFFFFFFF), // White
    Color(0xFFE91E63), // Pink
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFF00BCD4), // Cyan
    Color(0xFF607D8B), // Blue Grey
    Color(0xFF000000), // Z Black
    Color(0xFF333333)  // Dark Grey
)

fun shareTeamApp(context: Context) {
    try {
        val outFile = File(context.cacheDir, "TellerTeam.apk")
        context.assets.open("teamapp.apk").use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Install this Teller Team app on your phone. When it asks for a code, enter the code your manager gave you. The same code works for Daily Tasks and Live Stock.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Teller Team App"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing app: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun colorToHex(color: Color): String {
    // Extract 32-bit ARGB from Compose Color (which stores it in the upper 32 bits of the Long value)
    val argb = (color.value shr 32).toInt()
    return String.format("#%08X", argb)
}

private fun hexToColor(hex: String?): Color? {
    return try {
        if (hex.isNullOrEmpty()) null
        else Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}

// Convert a comma‑separated hex string to a list of Colors (supports up to 3)
private fun hexStringToColors(hex: String?): List<Color> {
    if (hex.isNullOrBlank()) return emptyList()
    return hex.split(",")
        .mapNotNull { h ->
            try { Color(android.graphics.Color.parseColor(h.trim())) } catch (e: Exception) { null }
        }
        .ifEmpty { emptyList() }
}

// Convert a list of Colors to a comma‑separated hex string for storage
private fun colorsToHexString(colors: List<Color>): String {
    return colors.joinToString(",") { colorToHex(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStockManagementScreen(navController: NavController, currentAppTheme: AppTheme) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("LiveStockPrefs", Context.MODE_PRIVATE) }

    val database = AppDatabase.getDatabase(context)

    // Helper to load employees from DailyTasksPrefs (shared with DailyTasksScreen)
    fun loadEmployeesFromPrefs(): List<TaskEmployee> {
        val taskPrefs = context.getSharedPreferences("DailyTasksPrefs", Context.MODE_PRIVATE)
        val raw = taskPrefs.getString("employees", null) ?: return emptyList()
        val list = mutableListOf<TaskEmployee>()
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(TaskEmployee(
                    code = o.optString("code"),
                    name = o.optString("name"),
                    dept = o.optString("dept")
                ))
            }
        } catch (_: Exception) {}
        return list.toList()
    }

    // Helper to save employees to DailyTasksPrefs
    fun saveEmployeesToPrefs(list: List<TaskEmployee>) {
        val taskPrefs = context.getSharedPreferences("DailyTasksPrefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        list.forEach {
            val o = org.json.JSONObject()
            o.put("code", it.code)
            o.put("name", it.name)
            o.put("dept", it.dept)
            arr.put(o)
        }
        taskPrefs.edit().putString("employees", arr.toString()).apply()
    }

    // Generate a unique, unguessable 10-character code (see TeamCodes)
    fun generateEmployeeCode(existing: List<TaskEmployee>): String =
        com.minhas.teller.utils.TeamCodes.generate(existing.map { it.code })

    // Reactive employee list — reloaded from prefs when changes are made
    var taskEmployees by remember { mutableStateOf(loadEmployeesFromPrefs()) }

    fun reloadEmployees() { taskEmployees = loadEmployeesFromPrefs() }

    // One-time upgrade of legacy 6-digit codes to unguessable ones (shared with Daily Tasks).
    var upgradedCodes by remember { mutableStateOf<List<com.minhas.teller.utils.TeamCodes.MovedCode>>(emptyList()) }
    var codesMigrated by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val moved = com.minhas.teller.utils.TeamCodes.migrateLegacyCodes(context)
        if (moved.isNotEmpty()) {
            upgradedCodes = moved
            codesMigrated++
        }
    }
    if (upgradedCodes.isNotEmpty()) {
        com.minhas.teller.ui.components.CodesUpgradedDialog(upgradedCodes) { upgradedCodes = emptyList() }
    }

    // Use employee codes from Daily Tasks; fall back to saved/random code if no employees exist
    var showroomCode by remember {
        val saved = sharedPrefs.getString("showroom_code", null)
        // If employees exist, prefer the first employee's code (sync with task section)
        if (taskEmployees.isNotEmpty()) {
            // Persist the employee code as showroom_code so it stays consistent
            val empCode = taskEmployees.first().code
            if (saved != empCode) {
                sharedPrefs.edit().putString("showroom_code", empCode).apply()
            }
            mutableStateOf(empCode)
        } else if (saved != null) {
            mutableStateOf(saved)
        } else {
            val newCode = com.minhas.teller.utils.TeamCodes.generate()
            sharedPrefs.edit().putString("showroom_code", newCode).apply()
            mutableStateOf(newCode)
        }
    }

    // Track which employee is selected (index into taskEmployees)
    var selectedEmployeeIndex by remember {
        val idx = taskEmployees.indexOfFirst { it.code == showroomCode }
        mutableStateOf(if (idx >= 0) idx else -1)
    }

    // When employee selection changes, update showroomCode
    fun selectEmployee(index: Int) {
        if (index in taskEmployees.indices) {
            val code = taskEmployees[index].code
            selectedEmployeeIndex = index
            showroomCode = code
            sharedPrefs.edit().putString("showroom_code", code).apply()
        }
    }

    // After a legacy-code upgrade, point this screen at the employee's new code.
    LaunchedEffect(codesMigrated) {
        if (codesMigrated > 0) {
            reloadEmployees()
            showroomCode = sharedPrefs.getString("showroom_code", null) ?: showroomCode
            selectedEmployeeIndex = taskEmployees.indexOfFirst { it.code == showroomCode }
        }
    }

    val calendar = Calendar.getInstance()
    var fromDate by remember { mutableStateOf(calendar.time) }
    var toDate by remember { mutableStateOf(calendar.time) }
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    var liveStockList by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    var stockMetadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var pendingTransactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var isEditingCode by remember { mutableStateOf(false) }
    var shareCardExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var newCodeInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf("") }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    
    var selectedVehicleName by remember { mutableStateOf("") }
    var editInQty by remember { mutableStateOf("") }
    var editOutQty by remember { mutableStateOf("") }
    // Selected colors for a stock item (supports up to 3 colors), empty by default
    var selectedColors by remember { mutableStateOf(listOf<Color>()) }

    val fromDatePicker = DatePickerDialog(
        context,
        { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d)
            fromDate = cal.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val toDatePicker = DatePickerDialog(
        context,
        { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d)
            toDate = cal.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Firebase Listeners - use DisposableEffect for proper cleanup when switching codes
    val currentCode = showroomCode
    DisposableEffect(currentCode) {
        isLoading = true
        val db = FirebaseDatabase.getInstance()

            val stockListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newStock = mutableMapOf<String, Pair<Int, Int>>()
                    val newMeta = mutableMapOf<String, String>()
                    snapshot.children.forEach { child ->
                        val dbName = child.child("name").getValue(String::class.java)
                        val name = (dbName ?: child.key?.replace("_", " ") ?: "").trim()
                        var inQty = 0; var outQty = 0
                        if (child.hasChild("in")) {
                             inQty = child.child("in").getValue(Int::class.java) ?: 0
                             outQty = child.child("out").getValue(Int::class.java) ?: 0
                             newMeta[name] = child.child("color").getValue(String::class.java) ?: ""
                        } else {
                             inQty = child.getValue(Int::class.java) ?: 0
                        }
                        newStock[name] = Pair(inQty, outQty)
                    }
                    liveStockList = newStock
                    stockMetadata = newMeta
                }
                override fun onCancelled(dbError: DatabaseError) { error = dbError.message }
            }
            db.getReference("stock").child(currentCode).addValueEventListener(stockListener)

            val txListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Map<String, Any>>()
                    snapshot.children.forEach { child ->
                        @Suppress("UNCHECKED_CAST")
                            val map = child.value as? Map<String, Any>
                        if (map != null) {
                            val mutableMap = map.toMutableMap()
                            mutableMap["vehicle"] = (mutableMap["vehicle"] as? String ?: "").trim()
                            list.add(mutableMap)
                        }
                    }
                    transactions = list
                    isLoading = false
                }
                override fun onCancelled(dbError: DatabaseError) {
                    error = dbError.message; isLoading = false
                }
            }
            db.getReference("transactions").child(currentCode).addValueEventListener(txListener)

            val pendingListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Map<String, Any>>()
                    snapshot.children.forEach { child ->
                        @Suppress("UNCHECKED_CAST")
                            val map = child.value as? Map<String, Any>
                        if (map != null) {
                            val mutableMap = map.toMutableMap()
                            mutableMap["key"] = child.key ?: ""
                            mutableMap["vehicle"] = (mutableMap["vehicle"] as? String ?: "").trim()
                            list.add(mutableMap)
                        }
                    }
                    pendingTransactions = list
                }
                override fun onCancelled(dbError: DatabaseError) {
                    error = dbError.message
                }
            }
            db.getReference("pending_transactions").child(currentCode).addValueEventListener(pendingListener)

            onDispose {
                db.getReference("stock").child(currentCode).removeEventListener(stockListener)
                db.getReference("transactions").child(currentCode).removeEventListener(txListener)
                db.getReference("pending_transactions").child(currentCode).removeEventListener(pendingListener)
            }
    }

    val processedData = remember(transactions, fromDate, toDate, liveStockList) {
        val startTs = Calendar.getInstance().apply { time = fromDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val endTs = Calendar.getInstance().apply { time = toDate; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

        val periodTransactions = transactions.filter { (it["timestamp"] as? Long ?: 0L) in startTs..endTs }
        
        val vehicleSummary = mutableMapOf<String, Triple<Int, Int, Int>>()
        val allVehicleNames = (transactions.mapNotNull { (it["vehicle"] as? String)?.trim() } + liveStockList.keys.map { it.trim() }).filter { it.isNotEmpty() }.distinct().sorted()

        allVehicleNames.forEach { vehicle ->
            val pIn = periodTransactions.filter { (it["vehicle"] as? String)?.trim() == vehicle && it["type"] == "IN" }.sumOf { (it["quantity"] as? Long)?.toInt() ?: 0 }
            val pOut = periodTransactions.filter { (it["vehicle"] as? String)?.trim() == vehicle && it["type"] == "OUT" }.sumOf { (it["quantity"] as? Long)?.toInt() ?: 0 }
            
            // Current stock is based on liveStockList (which is the source of truth for balances)
            val liveData = liveStockList[vehicle] ?: liveStockList[vehicle.replace(" ", "_")] ?: liveStockList[vehicle.replace("_", " ")]
            val currentStock = if (liveData != null) liveData.first - liveData.second else 0
            
            vehicleSummary[vehicle] = Triple(pIn, pOut, currentStock)
        }

        val dayWise = periodTransactions.groupBy { val ts = (it["timestamp"] as? Long) ?: 0L; dateFormatter.format(Date(ts)) }
        Pair(vehicleSummary, dayWise)
    }
    
    val (summary, dayWiseData) = processedData

    if (showEditDialog) {
        val isAddNew = selectedVehicleName.isEmpty()
        var itemName by remember { mutableStateOf(selectedVehicleName) }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(text = if (isAddNew) "Add New Stock Item" else "Edit Stock Item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text(text = "Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(TerminologyManager.getTerm("Select Item Colors (max 3)"), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(STOCK_COLORS) { color ->
                            val isSelected = selectedColors.contains(color)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color, RoundedCornerShape(4.dp))
                                    .border(width = if (isSelected) 3.dp else 0.dp, color = if (isSelected) Color.White else Color.Transparent, shape = RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (isSelected) {
                                            selectedColors = selectedColors.filterNot { it == color }
                                        } else if (selectedColors.size < 3) {
                                            selectedColors = selectedColors + color
                                        }
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(TerminologyManager.getTerm("Total Received (IN)"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    ValidatedNumberTextField(value = editInQty, onValueChange = { editInQty = it }, label = "Total Received (IN)", modifier = Modifier.fillMaxWidth(), isDecimal = false)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(TerminologyManager.getTerm("Total Sold (OUT)"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    ValidatedNumberTextField(value = editOutQty, onValueChange = { editOutQty = it }, label = "Total Sold (OUT)", modifier = Modifier.fillMaxWidth(), isDecimal = false)
                    
                    val calculatedTotal = (editInQty.toIntOrNull() ?: 0) - (editOutQty.toIntOrNull() ?: 0)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Stock Preview:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = calculatedTotal.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = if (calculatedTotal >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalName = itemName.trim()
                    if (finalName.isEmpty()) return@Button
                    
                    // Firebase Sanitization: Keys cannot contain . $ # [ ] /
                    val safeKey = finalName.replace("[.\\$#\\[\\]/]".toRegex(), "_")
                    
                    val db = FirebaseDatabase.getInstance()
                    val newIn = editInQty.toIntOrNull() ?: 0
                    val newOut = editOutQty.toIntOrNull() ?: 0
                    
                    // Get current values to calculate difference for transactions
                    val oldName = if (isAddNew) finalName else selectedVehicleName
                    val liveData = liveStockList[oldName] ?: liveStockList[oldName.replace(" ", "_")] ?: liveStockList[oldName.replace("_", " ")]
                    val oldIn = liveData?.first ?: 0
                    val oldOut = liveData?.second ?: 0
                    
                    val diffIn = newIn - oldIn
                    val diffOut = newOut - oldOut
                    
                    val updates = mapOf(
                        "in" to newIn,
                        "out" to newOut,
                        "color" to colorsToHexString(selectedColors),
                        "name" to finalName,
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    // If name has changed, delete the old stock key and update transaction records
                    if (!isAddNew && finalName != selectedVehicleName) {
                        val oldSafeKey = selectedVehicleName.replace("[.\\$#\\[\\]/]".toRegex(), "_")
                        db.getReference("stock").child(showroomCode!!).child(oldSafeKey).removeValue()
                        
                        // Migrate transaction history
                        db.getReference("transactions").child(showroomCode!!).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val txUpdates = mutableMapOf<String, Any?>()
                                snapshot.children.forEach { child ->
                                    val vehicle = child.child("vehicle").getValue(String::class.java)
                                    if (vehicle?.trim() == selectedVehicleName.trim()) {
                                        txUpdates["/transactions/$showroomCode/${child.key}/vehicle"] = finalName
                                    }
                                }
                                if (txUpdates.isNotEmpty()) {
                                    db.getReference().updateChildren(txUpdates)
                                }
                            }
                            override fun onCancelled(dbError: DatabaseError) {}
                        })
                        
                        // Migrate pending transaction approvals
                        db.getReference("pending_transactions").child(showroomCode!!).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val pendingUpdates = mutableMapOf<String, Any?>()
                                snapshot.children.forEach { child ->
                                    val vehicle = child.child("vehicle").getValue(String::class.java)
                                    if (vehicle?.trim() == selectedVehicleName.trim()) {
                                        pendingUpdates["/pending_transactions/$showroomCode/${child.key}/vehicle"] = finalName
                                    }
                                }
                                if (pendingUpdates.isNotEmpty()) {
                                    db.getReference().updateChildren(pendingUpdates)
                                }
                            }
                            override fun onCancelled(dbError: DatabaseError) {}
                        })
                    }
                    
                    db.getReference("stock").child(showroomCode!!).child(safeKey).setValue(updates)
                        .addOnSuccessListener {
                            // Create transactions for the differences
                            if (diffIn != 0) {
                                val txId = db.getReference("transactions").child(showroomCode!!).push().key
                                if (txId != null) {
                                    db.getReference("transactions").child(showroomCode!!).child(txId).setValue(mapOf(
                                        "vehicle" to finalName,
                                        "type" to if (diffIn > 0) "IN" else "OUT",
                                        "quantity" to Math.abs(diffIn),
                                        "timestamp" to System.currentTimeMillis()
                                    ))
                                }
                            }
                            if (diffOut != 0) {
                                val txId = db.getReference("transactions").child(showroomCode!!).push().key
                                if (txId != null) {
                                    db.getReference("transactions").child(showroomCode!!).child(txId).setValue(mapOf(
                                        "vehicle" to finalName,
                                        "type" to if (diffOut > 0) "OUT" else "IN",
                                        "quantity" to Math.abs(diffOut),
                                        "timestamp" to System.currentTimeMillis()
                                    ))
                                }
                            }
                            
                            Toast.makeText(context, "Stock updated successfully", Toast.LENGTH_SHORT).show()
                            showEditDialog = false
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Firebase Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }) { Text(TerminologyManager.getTerm("Save")) }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isAddNew) {
                        TextButton(
                            onClick = {
                                itemToDelete = selectedVehicleName
                                showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(TerminologyManager.getTerm("Delete"))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = { showEditDialog = false }) {
                        Text(TerminologyManager.getTerm("Cancel"))
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(TerminologyManager.getTerm("Delete Stock Item")) },
            text = { Text("Are you sure you want to delete '$itemToDelete'? This will permanently remove it from live stock.") },
            confirmButton = {
                Button(
                    onClick = {
                        val safeKey = itemToDelete.trim().replace("[.\\$#\\[\\]/]".toRegex(), "_")
                        val db = FirebaseDatabase.getInstance()
                        db.getReference("stock").child(showroomCode!!).child(safeKey).removeValue()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Stock item removed successfully", Toast.LENGTH_SHORT).show()
                                showDeleteConfirm = false
                                showEditDialog = false
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                showDeleteConfirm = false
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(TerminologyManager.getTerm("Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    // Add Employee Dialog — shared between Live Stock and Daily Tasks screens
    if (showAddEmployeeDialog) {
        var newEmpName by remember { mutableStateOf("") }
        var newEmpDept by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddEmployeeDialog = false },
            title = { Text(TerminologyManager.getTerm("Add Employee"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newEmpName, onValueChange = { newEmpName = it },
                        label = { Text(TerminologyManager.getTerm("Employee name")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newEmpDept, onValueChange = { newEmpDept = it },
                        label = { Text(TerminologyManager.getTerm("Department (optional)")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(TerminologyManager.getTerm("A unique 10-character code will be generated. The same code is used for Daily Tasks and Live Stock."),
                        fontSize = 11.sp, color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newEmpName.trim().isEmpty()) return@Button
                    val code = generateEmployeeCode(taskEmployees)
                    val updated = taskEmployees + TaskEmployee(code, newEmpName.trim(), newEmpDept.trim())
                    saveEmployeesToPrefs(updated)
                    // Write employee config to Firebase (same as DailyTasksScreen)
                    val db = FirebaseDatabase.getInstance()
                    db.getReference("task_config").child(code).child("name").setValue(newEmpName.trim())
                    db.getReference("task_config").child(code).child("dept").setValue(newEmpDept.trim())
                    db.getReference("task_config").child(code).child("updatedAt").setValue(System.currentTimeMillis())
                    reloadEmployees()
                    // Auto-select the new employee
                    showroomCode = code
                    sharedPrefs.edit().putString("showroom_code", code).apply()
                    showAddEmployeeDialog = false
                    Toast.makeText(context, "Employee added. Code: ${com.minhas.teller.utils.TeamCodes.format(code)}", Toast.LENGTH_LONG).show()
                }) { Text(TerminologyManager.getTerm("Create")) }
            },
            dismissButton = {
                TextButton(onClick = { showAddEmployeeDialog = false }) { Text(TerminologyManager.getTerm("Cancel")) }
            }
        )
    }

    if (showCodeDialog) {
        val selectedEmp = taskEmployees.getOrNull(selectedEmployeeIndex)
        AlertDialog(
            onDismissRequest = {
                showCodeDialog = false
                isEditingCode = false
            },
            title = {
                Text(
                    text = if (isEditingCode) "Edit Sync Code"
                           else if (selectedEmp != null) "Employee Sync Code"
                           else "Showroom Sync Code",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isEditingCode) {
                        Text(
                            text = "Enter new sync code (alphanumeric, no spaces):",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newCodeInput,
                            onValueChange = { input ->
                                newCodeInput = input.filter { it.isLetterOrDigit() }.uppercase().take(com.minhas.teller.utils.TeamCodes.LENGTH)
                            },
                            label = { Text(TerminologyManager.getTerm("Sync Code")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: Changing this code will load different sync data.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        if (selectedEmp != null) {
                            // Employee info banner
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF00838F).copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        null,
                                        tint = Color(0xFF4DD0E1),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            selectedEmp.name.ifBlank { "Employee" },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (selectedEmp.dept.isNotBlank()) {
                                            Text(
                                                selectedEmp.dept,
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = if (selectedEmp != null)
                                "Give this code to ${selectedEmp.name.ifBlank { "your employee" }} to enter in the Teller Team app for both tasks AND live stock:"
                            else
                                "Use this code to sync stock with your employee app:",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = showroomCode ?: "------",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "The employee enters this one code in their app — it works for both Daily Tasks and Live Stock.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                newCodeInput = showroomCode ?: ""
                                isEditingCode = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(TerminologyManager.getTerm("Change Sync Code"))
                        }
                    }
                }
            },
            confirmButton = {
                if (isEditingCode) {
                    Button(
                        onClick = {
                            val cleanInput = newCodeInput.trim()
                            if (!com.minhas.teller.utils.TeamCodes.isValid(cleanInput)) {
                                Toast.makeText(context, TerminologyManager.getTerm("Enter the full 10-character code (letters and numbers)"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            sharedPrefs.edit().putString("showroom_code", cleanInput).apply()
                            showroomCode = cleanInput
                            isEditingCode = false
                            Toast.makeText(context, "Sync code updated", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Save"))
                    }
                } else {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Showroom Code", showroomCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            showCodeDialog = false
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Copy Code"))
                    }
                }
            },
            dismissButton = {
                if (isEditingCode) {
                    TextButton(
                        onClick = {
                            isEditingCode = false
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Cancel"))
                    }
                } else {
                    TextButton(
                        onClick = {
                            showCodeDialog = false
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Close"))
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A4A), Color(0xFF0F0F2D))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 40.dp, end = 16.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(TerminologyManager.getTerm("Live Stock"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

                    // Employee selector dropdown (when employees exist from Daily Tasks)
                    if (taskEmployees.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { expanded = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    "Employee",
                                    tint = Color(0xFF4DD0E1),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    taskEmployees.getOrNull(selectedEmployeeIndex)?.name?.ifBlank { "Employee" }
                                        ?: "Select",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    "Dropdown",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                taskEmployees.forEachIndexed { index, emp ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    emp.name.ifBlank { "Employee $index" },
                                                    fontWeight = if (index == selectedEmployeeIndex) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    "Code: ${emp.code}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectEmployee(index)
                                            expanded = false
                                        },
                                        leadingIcon = {
                                            if (index == selectedEmployeeIndex) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    null,
                                                    tint = Color(0xFF4CAF50),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                                DropdownMenuItem(
                                    text = { Text(TerminologyManager.getTerm("+ Add Employee"), color = Color(0xFF4DD0E1), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                    onClick = {
                                        expanded = false
                                        showAddEmployeeDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF4DD0E1), modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    } else {
                        // No employees yet — show Add Employee button
                        IconButton(
                            onClick = { showAddEmployeeDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                "Add Employee",
                                tint = Color(0xFF4DD0E1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    IconButton(onClick = { navController.navigate("help_detail/live_stock") }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, "How to use Live Stock", tint = Color(0xFFFFD54F))
                    }
                    IconButton(onClick = { shareTeamApp(context) }) {
                        Icon(Icons.Default.Share, "Share Team App", tint = Color.White)
                    }

                    IconButton(onClick = {
                        val headers = listOf("Item", "In", "Sold", "Current Stock")
                        val data = summary.map { (name, counts) ->
                            listOf(name, counts.first.toString(), counts.second.toString(), counts.third.toString())
                        }
                        com.minhas.teller.utils.ExportUtils.exportLiveStockToPdf(context, data)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = Color.White)
                    }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(TerminologyManager.getTerm("Show Code")) }, onClick = { showCodeDialog = true; showMenu = false })
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Share Team App")) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { showMenu = false; shareTeamApp(context) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                // ── Share prompt banner ──
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { shareCardExpanded = !shareCardExpanded },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00838F).copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (shareCardExpanded) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, null, tint = Color(0xFF4DD0E1), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(TerminologyManager.getTerm("Share Team App"),
                                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { shareCardExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Collapse", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(TerminologyManager.getTerm("Send the app to your employees. They can update stock and complete daily tasks. Use the 3-dot menu → Show Code to give them the activation code."),
                                color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    shareTeamApp(context)
                                    shareCardExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(TerminologyManager.getTerm("Share with employee"), fontSize = 13.sp)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, null, tint = Color(0xFF4DD0E1), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(TerminologyManager.getTerm("Share Team App"),
                                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Control Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Search Bar
                    Row(
                        modifier = Modifier.weight(1f).height(36.dp).background(Color(0xFF1E1E3F), RoundedCornerShape(8.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true
                        )
                    }
                    
                    Button(onClick = { selectedVehicleName = ""; editInQty = "0"; editOutQty = "0"; showEditDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp)) {
                        Text(TerminologyManager.getTerm("ADD NEW"), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }

                    Button(onClick = { navController.navigate("monthly_sales_report/$showroomCode") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5B0E0), contentColor = Color(0xFF311B92)), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp)) {
                        Text(TerminologyManager.getTerm("MONTHLY SALES"), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Date Pills
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { fromDatePicker.show() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("From: ${dateFormatter.format(fromDate)}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { toDatePicker.show() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("To: ${dateFormatter.format(toDate)}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                } else {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(TerminologyManager.getTerm("Item"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), fontSize = 14.sp)
                        Text(TerminologyManager.getTerm("In"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontSize = 14.sp)
                        Text(TerminologyManager.getTerm("Sold"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontSize = 14.sp)
                        Text(TerminologyManager.getTerm("Stock"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, fontSize = 14.sp)
                    }

                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
                        if (pendingTransactions.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Pending Employee Entries (${pendingTransactions.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB74D),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(pendingTransactions) { pending ->
                                val txKey = pending["key"] as? String ?: ""
                                val vehicleName = pending["vehicle"] as? String ?: ""
                                val type = pending["type"] as? String ?: "IN"
                                val quantity = (pending["quantity"] as? Long)?.toInt() ?: 0
                                val timestamp = pending["timestamp"] as? Long ?: System.currentTimeMillis()
                                val dateStr = dateFormatter.format(Date(timestamp))
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFB74D)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFB74D))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (type == "IN") Color(0xFF4CAF50) else Color(0xFFF44336))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(type, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(vehicleName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Qty: $quantity • $dateStr", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                        }
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val db = FirebaseDatabase.getInstance()
                                                    db.getReference("pending_transactions")
                                                        .child(showroomCode!!)
                                                        .child(txKey)
                                                        .removeValue()
                                                        .addOnSuccessListener {
                                                            Toast.makeText(context, "Entry rejected", Toast.LENGTH_SHORT).show()
                                                        }
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                                            ) {
                                                Text(TerminologyManager.getTerm("Reject"))
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    val db = FirebaseDatabase.getInstance()
                                                    val safeVehicleKey = vehicleName.replace("[.\\$#\\[\\]/]".toRegex(), "_")
                                                    val typeLower = if (type == "IN") "in" else "out"
                                                    
                                                    val updates = mapOf(
                                                        "/stock/$showroomCode/$safeVehicleKey/$typeLower" to com.google.firebase.database.ServerValue.increment(quantity.toLong()),
                                                        "/stock/$showroomCode/$safeVehicleKey/name" to vehicleName,
                                                        "/transactions/$showroomCode/$txKey" to mapOf(
                                                            "type" to type,
                                                            "vehicle" to vehicleName,
                                                            "quantity" to quantity,
                                                            "timestamp" to timestamp
                                                        ),
                                                        "/pending_transactions/$showroomCode/$txKey" to null
                                                    )
                                                    
                                                    db.getReference().updateChildren(updates)
                                                        .addOnSuccessListener {
                                                            Toast.makeText(context, "Entry approved", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .addOnFailureListener { e ->
                                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(TerminologyManager.getTerm("Accept"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Text(TerminologyManager.getTerm("Summary (Tap to Edit Live Stock)"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 12.dp)) }
                        
                        items(summary.filter { it.key.contains(searchQuery, true) }.toList()) { (name, counts) ->
                            val (pIn, pOut, stock) = counts
                            val itemColors = hexStringToColors(stockMetadata[name] ?: stockMetadata[name.replace(" ", "_")] ?: stockMetadata[name.replace("_", " ")])
                            // Use the first color as the base for card tint
                            val baseColor = itemColors.firstOrNull() ?: STOCK_COLORS[0]
                            
                            val stockStatusText = when {
                                stock <= 0 -> "OUT OF STOCK"
                                stock <= 3 -> "LOW STOCK"
                                else -> "IN STOCK"
                            }
                            val stockStatusColor = when {
                                stock <= 0 -> Color(0xFFFF5252) // Red
                                stock <= 3 -> Color(0xFFFFD54F) // Yellow
                                else -> Color(0xFF00E676) // Green
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = baseColor.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { 
                                            selectedVehicleName = name
                                            val liveData = liveStockList[name] ?: liveStockList[name.replace(" ", "_")] ?: liveStockList[name.replace("_", " ")]
                                            editInQty = (liveData?.first ?: 0).toString()
                                            editOutQty = (liveData?.second ?: 0).toString()
                                            selectedColors = itemColors
                                            showEditDialog = true 
                                        }.padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Color indicator box showing up to three colors equally proportioned
                                        Row(modifier = Modifier.size(16.dp)) {
                                            val weight = 1f / itemColors.size.coerceAtLeast(1)
                                            itemColors.forEach { col ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(weight)
                                                        .fillMaxHeight()
                                                        .background(col, RoundedCornerShape(4.dp))
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1.5f).padding(start = 12.dp)
                                        ) {
                                            Text(name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Surface(
                                                color = stockStatusColor.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, stockStatusColor.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = stockStatusText,
                                                    color = stockStatusColor.copy(alpha = 0.8f),
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                        Text(if(pIn > 0) pIn.toString() else "-", color = if(pIn > 0) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                                        Text(if(pOut > 0) pOut.toString() else "-", color = if(pOut > 0) Color(0xFFF44336) else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                                        Row(modifier = Modifier.weight(0.6f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                            Text(stock.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                            Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp).padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item { 
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(TerminologyManager.getTerm("Day-wise Details (All)"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        dayWiseData.entries.sortedByDescending { it.key }.forEach { entry ->
                            item {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                            items(entry.value) { t ->
                                val type = t["type"] as? String ?: "IN"
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x22FFFFFF))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if(type == "IN") Icons.Default.Add else Icons.Default.Remove, null, tint = if(type == "IN") Color(0xFF4CAF50) else Color(0xFFF44336), modifier = Modifier.size(16.dp))
                                        Text("Item: ${t["vehicle"]}", color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f).padding(start = 8.dp))
                                        Text("${t["quantity"] ?: 0}", color = Color.White, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Pin Total to Bottom
        val totalIn = summary.values.sumOf { it.first }
        val totalOut = summary.values.sumOf { it.second }
        val totalStock = summary.values.sumOf { it.third }
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC1A1A3F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(TerminologyManager.getTerm("TOTAL"), fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1.5f))
                    Text(totalIn.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                    Text(totalOut.toString(), fontWeight = FontWeight.Bold, color = Color(0xFFF44336), modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                    Text(totalStock.toString(), fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, fontSize = 20.sp)
                }
            }
        }
    }
}
