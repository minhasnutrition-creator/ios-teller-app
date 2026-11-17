package com.minhas.teller.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.utils.TerminologyManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

/** An employee the manager has created, stored locally on the manager's device. */
data class TaskEmployee(val code: String, val name: String, val dept: String)

/** A single task the manager assigns (fully generic - any shop, any task). */
data class TaskDef(val id: String, val label: String, val type: String, val order: Int)

/** One entry an employee filled in for a task, stored self-contained inside a report. */
data class ReportEntry(val id: String, val label: String, val type: String, val done: Boolean, val value: String, val remarks: String)

/** A whole day's submitted report for one employee. */
data class DailyReport(
    val date: String,
    val status: String,
    val arrival: String,
    val departure: String,
    val managerComments: String,
    val submittedAt: Long,
    val entries: List<ReportEntry>
)

// Task types the manager can pick when building a checklist.
const val TASK_TYPE_CHECK = "CHECK"    // Done / Not done tick
const val TASK_TYPE_NUMBER = "NUMBER"  // A count the employee enters
const val TASK_TYPE_AMOUNT = "AMOUNT"  // A money amount the employee enters
const val TASK_TYPE_TEXT = "TEXT"      // A short note the employee writes

val TASK_TYPE_LABELS = mapOf(
    TASK_TYPE_CHECK to "Tick (Done / Not done)",
    TASK_TYPE_NUMBER to "Number (count)",
    TASK_TYPE_AMOUNT to "Amount (money)",
    TASK_TYPE_TEXT to "Short text"
)

private val DailyTasksBackground = Brush.verticalGradient(
    colors = listOf(Color(0xFF06363D), Color(0xFF041D22))
)

// ---------------------------------------------------------------------------
// Local storage for the manager's employee list
// ---------------------------------------------------------------------------

private fun loadTaskEmployees(context: Context): MutableList<TaskEmployee> {
    val prefs = context.getSharedPreferences("DailyTasksPrefs", Context.MODE_PRIVATE)
    val raw = prefs.getString("employees", null) ?: return mutableListOf()
    val list = mutableListOf<TaskEmployee>()
    try {
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                TaskEmployee(
                    code = o.optString("code"),
                    name = o.optString("name"),
                    dept = o.optString("dept")
                )
            )
        }
    } catch (_: Exception) {
    }
    return list
}

private fun saveTaskEmployees(context: Context, list: List<TaskEmployee>) {
    val prefs = context.getSharedPreferences("DailyTasksPrefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    list.forEach {
        val o = JSONObject()
        o.put("code", it.code)
        o.put("name", it.name)
        o.put("dept", it.dept)
        arr.put(o)
    }
    prefs.edit().putString("employees", arr.toString()).apply()
}

// 10-character unguessable code (see TeamCodes) - it is the only key to this employee's cloud data.
private fun generateEmployeeCode(existing: List<TaskEmployee>): String =
    com.minhas.teller.utils.TeamCodes.generate(existing.map { it.code })

// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------
// Firebase helpers
// ---------------------------------------------------------------------------

private fun writeEmployeeConfig(code: String, name: String, dept: String) {
    val db = FirebaseDatabase.getInstance()
    val ref = db.getReference("task_config").child(code)
    ref.child("name").setValue(name)
    ref.child("dept").setValue(dept)
    ref.child("updatedAt").setValue(System.currentTimeMillis())
}

private fun parseTasks(snapshot: DataSnapshot): List<TaskDef> {
    val list = mutableListOf<TaskDef>()
    val tasksNode = snapshot.child("tasks")
    tasksNode.children.forEach { child ->
        val id = child.key ?: return@forEach
        val label = child.child("label").getValue(String::class.java) ?: ""
        val type = child.child("type").getValue(String::class.java) ?: TASK_TYPE_CHECK
        val order = (child.child("order").getValue(Long::class.java) ?: 0L).toInt()
        if (label.isNotBlank()) list.add(TaskDef(id, label, type, order))
    }
    return list.sortedBy { it.order }
}

private fun parseReports(snapshot: DataSnapshot): List<DailyReport> {
    val list = mutableListOf<DailyReport>()
    snapshot.children.forEach { dateNode ->
        val date = dateNode.key ?: return@forEach
        val status = dateNode.child("status").getValue(String::class.java) ?: "PENDING"
        val arrival = dateNode.child("arrival").getValue(String::class.java) ?: ""
        val departure = dateNode.child("departure").getValue(String::class.java) ?: ""
        val comments = dateNode.child("managerComments").getValue(String::class.java) ?: ""
        val submittedAt = dateNode.child("submittedAt").getValue(Long::class.java) ?: 0L
        val entries = mutableListOf<ReportEntry>()
        dateNode.child("entries").children.forEach { e ->
            entries.add(
                ReportEntry(
                    id = e.key ?: "",
                    label = e.child("label").getValue(String::class.java) ?: "",
                    type = e.child("type").getValue(String::class.java) ?: TASK_TYPE_CHECK,
                    done = e.child("done").getValue(Boolean::class.java) ?: false,
                    value = e.child("value").getValue(String::class.java) ?: "",
                    remarks = e.child("remarks").getValue(String::class.java) ?: ""
                )
            )
        }
        list.add(DailyReport(date, status, arrival, departure, comments, submittedAt, entries))
    }
    return list.sortedByDescending { it.date }
}

private fun todayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

// ---------------------------------------------------------------------------
// Screen 1 : Employee list (manager home for Daily Tasks)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTasksScreen(navController: NavController) {
    val context = LocalContext.current
    var employees by remember { mutableStateOf(loadTaskEmployees(context)) }
    var showAddDialog by remember { mutableStateOf(false) }

    // One-time upgrade of legacy 6-digit codes to unguessable ones, then show the new codes.
    var upgradedCodes by remember { mutableStateOf<List<com.minhas.teller.utils.TeamCodes.MovedCode>>(emptyList()) }
    LaunchedEffect(Unit) {
        val moved = com.minhas.teller.utils.TeamCodes.migrateLegacyCodes(context)
        if (moved.isNotEmpty()) {
            employees = loadTaskEmployees(context)
            upgradedCodes = moved
        }
    }
    if (upgradedCodes.isNotEmpty()) {
        com.minhas.teller.ui.components.CodesUpgradedDialog(upgradedCodes) { upgradedCodes = emptyList() }
    }
    var employeeToDelete by remember { mutableStateOf<TaskEmployee?>(null) }
    var shareCardExpanded by remember { mutableStateOf(false) }
    // code -> true if that employee has a PENDING report for today
    val pendingToday = remember { mutableStateMapOf<String, Boolean>() }

    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    // Attach a listener per employee for today's report status so the manager sees a badge.
    DisposableEffect(employees) {
        val db = FirebaseDatabase.getInstance()
        val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()
        employees.forEach { emp ->
            val ref = db.getReference("task_reports").child(emp.code).child(today)
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.child("status").getValue(String::class.java)
                    pendingToday[emp.code] = (status == "PENDING")
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(l)
            listeners.add(ref to l)
        }
        onDispose { listeners.forEach { (ref, l) -> ref.removeEventListener(l) } }
    }

    if (showAddDialog) {
        AddEmployeeDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, dept ->
                val code = generateEmployeeCode(employees)
                val updated = (employees + TaskEmployee(code, name, dept)).toMutableList()
                saveTaskEmployees(context, updated)
                employees = updated
                writeEmployeeConfig(code, name, dept)
                showAddDialog = false
                Toast.makeText(context, "Employee added. Code: ${com.minhas.teller.utils.TeamCodes.format(code)}", Toast.LENGTH_LONG).show()
            }
        )
    }

    employeeToDelete?.let { emp ->
        AlertDialog(
            onDismissRequest = { employeeToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFD32F2F)) },
            title = { Text(TerminologyManager.getTerm("Remove") + " ${emp.name.ifBlank { TerminologyManager.getTerm("employee") }}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(TerminologyManager.getTerm("This permanently deletes their checklist and all saved reports — from this app and from the cloud. This can't be undone."))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = employees.filter { it.code != emp.code }.toMutableList()
                        saveTaskEmployees(context, updated)
                        employees = updated
                        FirebaseDatabase.getInstance().getReference("task_config").child(emp.code).removeValue()
                        FirebaseDatabase.getInstance().getReference("task_reports").child(emp.code).removeValue()
                        DailyTasksArchive.deleteAll(context, emp.code)
                        employeeToDelete = null
                        Toast.makeText(context, TerminologyManager.getTerm("Employee removed"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text(TerminologyManager.getTerm("Delete")) }
            },
            dismissButton = { TextButton(onClick = { employeeToDelete = null }) { Text(TerminologyManager.getTerm("Cancel")) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DailyTasksBackground)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 40.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, TerminologyManager.getTerm("Back"), tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(TerminologyManager.getTerm("Daily Tasks"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date()) },
                            fontSize = 12.sp, color = Color(0xFF4DD0E1)
                        )
                    }
                    IconButton(onClick = { navController.navigate("help_detail/daily_tasks_setup") }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, TerminologyManager.getTerm("How to use Daily Tasks"), tint = Color(0xFFFFD54F))
                    }
                    IconButton(onClick = { shareTeamApp(context) }) {
                        Icon(Icons.Default.Share, TerminologyManager.getTerm("Share Employee App"), tint = Color.White)
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF00BFA5),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(TerminologyManager.getTerm("Add Employee"))
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
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
                                Text("Connect Employee App",
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
                            Text("Ask employees to install 'Task and Stock for Teller' from the App Store (free download). Send them their connection code to get connected.",
                                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    shareTeamApp(context)
                                    shareCardExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share App Name & Instructions", fontSize = 13.sp)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, null, tint = Color(0xFF4DD0E1), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Connect Employee App (Task and Stock for Teller)",
                                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                        }
                    }
                }

                if (employees.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Groups, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(TerminologyManager.getTerm("No employees yet"), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                            Text(TerminologyManager.getTerm("Tap 'Add Employee' to start"), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                        items(employees) { emp ->
                            EmployeeCard(
                                employee = emp,
                                hasPending = pendingToday[emp.code] == true,
                                onClick = { navController.navigate("daily_task_detail/${emp.code}") },
                                onLongClick = { employeeToDelete = emp }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmployeeCard(employee: TaskEmployee, hasPending: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(Color(0xFF00897B), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    employee.name.take(1).uppercase().ifEmpty { "?" },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(employee.name.ifEmpty { "(no name)" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (employee.dept.isNotBlank()) {
                    Text(employee.dept, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Text("Code: ${com.minhas.teller.utils.TeamCodes.format(employee.code)}", color = Color(0xFF4DD0E1), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (hasPending) {
                Box(
                    modifier = Modifier.background(Color(0xFFFFB74D), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(TerminologyManager.getTerm("REVIEW"), color = Color(0xFF3E2723), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun AddEmployeeDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TerminologyManager.getTerm("Add Employee"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(TerminologyManager.getTerm("Employee name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = dept, onValueChange = { dept = it },
                    label = { Text(TerminologyManager.getTerm("Department (optional)")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    TerminologyManager.getTerm("A unique 10-character code will be generated for this employee."),
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isEmpty()) return@Button
                onCreate(name.trim(), dept.trim())
            }) { Text(TerminologyManager.getTerm("Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(TerminologyManager.getTerm("Cancel")) } }
    )
}

// ---------------------------------------------------------------------------
// Screen 2 : Employee detail (task builder + review & approve)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTaskEmployeeDetailScreen(navController: NavController, employeeCode: String) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var employeeName by remember { mutableStateOf("") }
    var employeeDept by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf<List<TaskDef>>(emptyList()) }
    var reports by remember { mutableStateOf<List<DailyReport>>(emptyList()) }
    var activeUntil by remember { mutableStateOf("") }; var stockEnabled by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }

    var showAddTask by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Tasks, 1 = Reports

    // Selected report dates (for manager delete-by-date).
    val selectedDates = remember { mutableStateListOf<String>() }

    // Show any locally-archived (past) reports immediately, before Firebase responds.
    LaunchedEffect(employeeCode) {
        val local = DailyTasksArchive.load(context, employeeCode)
        if (local.isNotEmpty()) reports = local
    }

    DisposableEffect(employeeCode) {
        val db = FirebaseDatabase.getInstance()
        val configRef = db.getReference("task_config").child(employeeCode)
        val reportsRef = db.getReference("task_reports").child(employeeCode)

        val configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                employeeName = snapshot.child("name").getValue(String::class.java) ?: ""
                employeeDept = snapshot.child("dept").getValue(String::class.java) ?: ""
                activeUntil = snapshot.child("activeUntil").getValue(String::class.java) ?: ""; stockEnabled = snapshot.child("stockEnabled").getValue(Boolean::class.java) ?: true
                tasks = parseTasks(snapshot)
                isLoading = false
            }
            override fun onCancelled(error: DatabaseError) { isLoading = false }
        }
        val reportsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fb = parseReports(snapshot)
                val today = todayKey()

                // Any past day: archive it locally, then delete it from Firebase so the
                // Realtime Database only ever holds today's live report (free-tier friendly).
                val past = fb.filter { it.date < today }
                if (past.isNotEmpty()) {
                    DailyTasksArchive.archive(context, employeeCode, past)
                    val removals = HashMap<String, Any?>()
                    past.forEach { removals[it.date] = null }
                    reportsRef.updateChildren(removals)
                }

                // Display = today's live report (from Firebase) + everything archived locally.
                val todays = fb.filter { it.date >= today }
                val local = DailyTasksArchive.load(context, employeeCode)
                reports = (todays + local).distinctBy { it.date }.sortedByDescending { it.date }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        configRef.addValueEventListener(configListener)
        reportsRef.addValueEventListener(reportsListener)
        onDispose {
            configRef.removeEventListener(configListener)
            reportsRef.removeEventListener(reportsListener)
        }
    }

    if (showAddTask) {
        AddTaskDialog(
            onDismiss = { showAddTask = false },
            onCreate = { label, type ->
                val db = FirebaseDatabase.getInstance()
                val ref = db.getReference("task_config").child(employeeCode).child("tasks").push()
                val order = (tasks.maxOfOrNull { it.order } ?: 0) + 1
                ref.setValue(mapOf("label" to label, "type" to type, "order" to order))
                db.getReference("task_config").child(employeeCode).child("updatedAt").setValue(System.currentTimeMillis())
                showAddTask = false
            }
        )
    }

    if (showScheduleDialog) {
        AssignScheduleDialog(
            currentActiveUntil = activeUntil,
            onDismiss = { showScheduleDialog = false },
            onPick = { days ->
                val ref = FirebaseDatabase.getInstance().getReference("task_config").child(employeeCode)
                if (days == null) {
                    ref.child("activeUntil").removeValue()
                    Toast.makeText(context, TerminologyManager.getTerm("Tasks now show every day"), Toast.LENGTH_SHORT).show()
                } else {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, days - 1)
                    val until = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                    ref.child("activeUntil").setValue(until)
                    Toast.makeText(context, "${TerminologyManager.getTerm("Assigned for")} $days ${TerminologyManager.getTerm("days")}", Toast.LENGTH_SHORT).show()
                }
                ref.child("updatedAt").setValue(System.currentTimeMillis())
                showScheduleDialog = false
            }
        )
    }

    if (showCodeDialog) {
        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            title = { Text(TerminologyManager.getTerm("Employee Sync Code"), fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("${TerminologyManager.getTerm("Give this code to")} ${employeeName.ifEmpty { TerminologyManager.getTerm("your employee") }} ${TerminologyManager.getTerm("to enter in the Teller Team app. This one code works for both Tasks AND Live Stock.")}:", textAlign = TextAlign.Center, fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(
                            employeeCode, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Employee Code", employeeCode))
                    Toast.makeText(context, TerminologyManager.getTerm("Code copied"), Toast.LENGTH_SHORT).show()
                    showCodeDialog = false
                }) { Text(TerminologyManager.getTerm("Copy Code")) }
            },
            dismissButton = { TextButton(onClick = { showCodeDialog = false }) { Text(TerminologyManager.getTerm("Close")) } }
        )
    }

    // Today's progress for the hero card.
    val heroToday = todayKey()
    val heroReport = reports.firstOrNull { it.date == heroToday }
    val heroEntries = heroReport?.entries?.associateBy { it.id } ?: emptyMap()
    val heroTotal = tasks.size
    val heroDone = tasks.count { t ->
        val e = heroEntries[t.id]
        e != null && (if (t.type == TASK_TYPE_CHECK) e.done else e.value.isNotBlank())
    }
    val heroDateText = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date()) }
    val (heroStatusText, heroStatusColor) = when {
        heroReport == null -> TerminologyManager.getTerm("Not started") to Color(0xFF90A4AE)
        heroReport.status == "APPROVED" -> TerminologyManager.getTerm("Approved") to Color(0xFF69F0AE)
        else -> TerminologyManager.getTerm("In progress") to Color(0xFFFFB74D)
    }

    Box(modifier = Modifier.fillMaxSize().background(DailyTasksBackground)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 40.dp, end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, TerminologyManager.getTerm("Back"), tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(employeeName.ifEmpty { TerminologyManager.getTerm("Employee") }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Code ${employeeCode}${if (employeeDept.isNotBlank()) " • $employeeDept" else ""}", fontSize = 12.sp, color = Color(0xFF4DD0E1))
                        }
                        IconButton(onClick = { showCodeDialog = true }) {
                            Icon(Icons.Default.Pin, TerminologyManager.getTerm("Show Code"), tint = Color.White)
                        }
                        IconButton(onClick = { shareTeamApp(context) }) {
                            Icon(Icons.Default.Share, TerminologyManager.getTerm("Share App"), tint = Color.White)
                        }
                        IconButton(onClick = {
                            val newVal = !stockEnabled
                            stockEnabled = newVal
                            FirebaseDatabase.getInstance().getReference("task_config").child(employeeCode).child("stockEnabled").setValue(newVal)
                            Toast.makeText(context, if (newVal) "Stock: ON" else "Stock: OFF", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.Inventory2,
                                TerminologyManager.getTerm("Live Stock"),
                                tint = if (stockEnabled) Color(0xFF69F0AE) else Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    EmployeeHeroCard(
                        dateText = heroDateText,
                        done = heroDone,
                        total = heroTotal,
                        arrival = heroReport?.arrival ?: "",
                        departure = heroReport?.departure ?: "",
                        statusText = heroStatusText,
                        statusColor = heroStatusColor
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tasks (${tasks.size})") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reports (${reports.size})") })
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (selectedTab == 0) {
                    val todayEntries = reports.firstOrNull { it.date == todayKey() }
                        ?.entries?.associateBy { it.id } ?: emptyMap()
                    TaskBuilderList(
                        tasks = tasks,
                        todayEntries = todayEntries,
                        activeUntil = activeUntil,
                        onAssignSchedule = { showScheduleDialog = true },
                        onAdd = { showAddTask = true },
                        onDelete = { taskId ->
                            FirebaseDatabase.getInstance().getReference("task_config")
                                .child(employeeCode).child("tasks").child(taskId).removeValue()
                        }
                    )
                } else {
                    ReportsList(
                        reports = reports,
                        dateFormatter = dateFormatter,
                        selectedDates = selectedDates,
                        onToggleSelect = { date ->
                            if (selectedDates.contains(date)) selectedDates.remove(date) else selectedDates.add(date)
                        },
                        onDeleteSelected = {
                            val dates = selectedDates.toSet()
                            // Remove from the local archive (past reports live only here)...
                            DailyTasksArchive.delete(context, employeeCode, dates)
                            // ...and from Firebase too, in case today's report was selected.
                            val removals = HashMap<String, Any?>()
                            dates.forEach { removals[it] = null }
                            FirebaseDatabase.getInstance().getReference("task_reports")
                                .child(employeeCode).updateChildren(removals)
                            reports = reports.filter { it.date !in dates }
                            selectedDates.clear()
                            Toast.makeText(context, "${TerminologyManager.getTerm("Deleted")} ${dates.size} ${TerminologyManager.getTerm("reports")}", Toast.LENGTH_SHORT).show()
                        },
                        onToggleEntry = { date, entryId, newDone ->
                            if (date == todayKey()) {
                                FirebaseDatabase.getInstance().getReference("task_reports")
                                    .child(employeeCode).child(date).child("entries").child(entryId)
                                    .child("done").setValue(newDone)
                            } else {
                                val updated = reports.map { r ->
                                    if (r.date == date)
                                        r.copy(entries = r.entries.map { if (it.id == entryId) it.copy(done = newDone) else it })
                                    else r
                                }
                                reports = updated
                                DailyTasksArchive.archive(context, employeeCode, updated.filter { it.date == date })
                            }
                        },
                        onApprove = { date, comments ->
                            if (date == todayKey()) {
                                val updates = mapOf(
                                    "/task_reports/$employeeCode/$date/status" to "APPROVED",
                                    "/task_reports/$employeeCode/$date/managerComments" to comments,
                                    "/task_reports/$employeeCode/$date/approvedAt" to System.currentTimeMillis()
                                )
                                FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                            } else {
                                // Past report — lives in the local archive only.
                                reports = reports.map {
                                    if (it.date == date) it.copy(status = "APPROVED", managerComments = comments) else it
                                }
                                DailyTasksArchive.archive(context, employeeCode, reports.filter { it.date == date })
                            }
                            Toast.makeText(context, TerminologyManager.getTerm("Day approved & saved"), Toast.LENGTH_SHORT).show()
                        },
                        onReopen = { date ->
                            if (date == todayKey()) {
                                FirebaseDatabase.getInstance().getReference("task_reports")
                                    .child(employeeCode).child(date).child("status").setValue("PENDING")
                            } else {
                                reports = reports.map {
                                    if (it.date == date) it.copy(status = "PENDING") else it
                                }
                                DailyTasksArchive.archive(context, employeeCode, reports.filter { it.date == date })
                            }
                            Toast.makeText(context, TerminologyManager.getTerm("Reopened"), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmployeeHeroCard(
    dateText: String,
    done: Int,
    total: Int,
    arrival: String,
    departure: String,
    statusText: String,
    statusColor: Color
) {
    val pct = if (total > 0) done.toFloat() / total else 0f
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateText, color = Color(0xFF4DD0E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (total == 0) TerminologyManager.getTerm("No tasks yet") else "$done ${TerminologyManager.getTerm("of")} $total ${TerminologyManager.getTerm("tasks done today")}",
                        color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (pct >= 1f) Color(0xFF69F0AE) else Color(0xFF00BFA5),
                trackColor = Color(0x33FFFFFF)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "In: ${arrival.ifBlank { "—" }}       Out: ${departure.ifBlank { "—" }}",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TaskBuilderList(
    tasks: List<TaskDef>,
    todayEntries: Map<String, ReportEntry>,
    activeUntil: String,
    onAssignSchedule: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)) {
        if (tasks.isNotEmpty()) {
            item { ScheduleCard(activeUntil = activeUntil, onAssignSchedule = onAssignSchedule) }
        }
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(TerminologyManager.getTerm("Add Task"))
            }
        }
        if (tasks.isNotEmpty()) {
            item {
                Text(TerminologyManager.getTerm("Today's status is shown on each task."),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        if (tasks.isEmpty()) {
            item {
                Text(TerminologyManager.getTerm("No tasks yet. Add the daily tasks this employee must complete — e.g. 'Clean the shop', 'Today's total sales', 'New policies count'."),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp), textAlign = TextAlign.Center
                )
            }
        }
        items(tasks) { task ->
            val entry = todayEntries[task.id]
            val (statusText, statusColor) = when {
                entry == null -> TerminologyManager.getTerm("Not filled") to Color(0xFF78909C)
                task.type == TASK_TYPE_CHECK && entry.done -> TerminologyManager.getTerm("Done") to Color(0xFF69F0AE)
                task.type == TASK_TYPE_CHECK && !entry.done -> TerminologyManager.getTerm("Not done") to Color(0xFFFF8A80)
                else -> (entry.value.ifBlank { "—" }) to Color(0xFF4DD0E1)
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (task.type) {
                            TASK_TYPE_CHECK -> Icons.Default.CheckBox
                            TASK_TYPE_NUMBER -> Icons.Default.Tag
                            TASK_TYPE_AMOUNT -> Icons.Default.CurrencyRupee
                            else -> Icons.Default.Description
                        },
                        null, tint = Color(0xFF4DD0E1), modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(task.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { onDelete(task.id) }) {
                        Icon(Icons.Default.DeleteOutline, TerminologyManager.getTerm("Delete"), tint = Color(0xFFFF8A80))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(activeUntil: String, onAssignSchedule: () -> Unit) {
    val today = todayKey()
    val pretty = remember(activeUntil) {
        if (activeUntil.isBlank()) "" else try {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(activeUntil)!!)
        } catch (_: Exception) { activeUntil }
    }
    val (line, color) = when {
        activeUntil.isBlank() -> TerminologyManager.getTerm("Tasks show every day (no end date)") to Color(0xFF4DD0E1)
        today > activeUntil -> "Schedule ended $pretty — tasks are hidden" to Color(0xFFFF8A80)
        else -> "Assigned through $pretty" to Color(0xFF69F0AE)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, null, tint = color, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(TerminologyManager.getTerm("Schedule"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(line, color = color, fontSize = 12.sp)
            }
            TextButton(onClick = onAssignSchedule) { Text(TerminologyManager.getTerm("Set")) }
        }
    }
}

@Composable
private fun AssignScheduleDialog(currentActiveUntil: String, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TerminologyManager.getTerm("Assign these tasks for"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    TerminologyManager.getTerm("The employee sees today's tasks each morning for the chosen period, then they stop automatically. You can change or extend this anytime."),
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                listOf(10, 15, 20, 30).forEach { d ->
                    Button(
                        onClick = { onPick(d) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F))
                    ) { Text("Next $d days") }
                }
                OutlinedButton(
                    onClick = { onPick(null) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text(TerminologyManager.getTerm("Always (no end date)")) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(TerminologyManager.getTerm("Cancel")) } }
    )
}

@Composable
private fun ReportsList(
    reports: List<DailyReport>,
    dateFormatter: SimpleDateFormat,
    selectedDates: List<String>,
    onToggleSelect: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onToggleEntry: (String, String, Boolean) -> Unit,
    onApprove: (String, String) -> Unit,
    onReopen: (String) -> Unit
) {
    if (reports.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(TerminologyManager.getTerm("No submitted reports yet."), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedDates.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0x33FF5252))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${selectedDates.size} selected", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(TerminologyManager.getTerm("Delete"))
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)) {
            items(reports) { report ->
                ReportCard(
                    report, dateFormatter,
                    isSelected = selectedDates.contains(report.date),
                    onToggleSelect = { onToggleSelect(report.date) },
                    onToggleEntry = onToggleEntry,
                    onApprove = onApprove, onReopen = onReopen
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: DailyReport,
    dateFormatter: SimpleDateFormat,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onToggleEntry: (String, String, Boolean) -> Unit,
    onApprove: (String, String) -> Unit,
    onReopen: (String) -> Unit
) {
    val approved = report.status == "APPROVED"
    var comment by remember(report.date, report.status) { mutableStateOf(report.managerComments) }
    val prettyDate = remember(report.date) {
        try { dateFormatter.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(report.date)!!) }
        catch (_: Exception) { report.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFFFF5252) else if (approved) Color(0x4400E676) else Color(0x44FFB74D)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF5252), uncheckedColor = Color.White.copy(alpha = 0.5f))
                )
                Text(prettyDate, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(if (approved) Color(0x2200E676) else Color(0x22FFB74D), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (approved) TerminologyManager.getTerm("APPROVED") else TerminologyManager.getTerm("PENDING REVIEW"),
                        color = if (approved) Color(0xFF69F0AE) else Color(0xFFFFB74D),
                        fontSize = 10.sp, fontWeight = FontWeight.Black
                    )
                }
            }
            if (report.arrival.isNotBlank() || report.departure.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Arrival: ${report.arrival.ifBlank { "-" }}   •   Leaving: ${report.departure.ifBlank { "-" }}",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 10.dp))

            if (report.entries.any { it.type == TASK_TYPE_CHECK }) {
                Text(
                    TerminologyManager.getTerm("Tap a Done / Not done to override it."),
                    color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            report.entries.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.label, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    when (entry.type) {
                        TASK_TYPE_CHECK -> Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleEntry(report.date, entry.id, !entry.done) }
                                .background(if (entry.done) Color(0x2200E676) else Color(0x22FF5252))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (entry.done) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null,
                                tint = if (entry.done) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (entry.done) TerminologyManager.getTerm("Done") else TerminologyManager.getTerm("Not done"),
                                color = if (entry.done) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        else -> Text(
                            entry.value.ifBlank { "-" },
                            color = Color(0xFF4DD0E1), fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
                if (entry.remarks.isNotBlank()) {
                    Text(
                        "↳ ${entry.remarks}",
                        color = Color(0xFFFFB74D), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            if (approved) {
                if (report.managerComments.isNotBlank()) {
                    Text("Your note: ${report.managerComments}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { onReopen(report.date) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(TerminologyManager.getTerm("Reopen for editing")) }
            } else {
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text(TerminologyManager.getTerm("Manager comments (optional)")) },
                    modifier = Modifier.fillMaxWidth(), minLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onReopen(report.date) }, modifier = Modifier.weight(1f)) {
                        Text(TerminologyManager.getTerm("Send back"))
                    }
                    Button(
                        onClick = { onApprove(report.date, comment) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(TerminologyManager.getTerm("Approve"))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TASK_TYPE_CHECK) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TerminologyManager.getTerm("Add Task"), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(TerminologyManager.getTerm("Task label")) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(14.dp))
                Text(TerminologyManager.getTerm("What does the employee enter?"), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                listOf(TASK_TYPE_CHECK, TASK_TYPE_NUMBER, TASK_TYPE_AMOUNT, TASK_TYPE_TEXT).forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { type = t }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(TerminologyManager.getTerm(TASK_TYPE_LABELS[t] ?: t), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (label.trim().isEmpty()) return@Button
                onCreate(label.trim(), type)
            }) { Text(TerminologyManager.getTerm("Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(TerminologyManager.getTerm("Cancel")) } }
    )
}
