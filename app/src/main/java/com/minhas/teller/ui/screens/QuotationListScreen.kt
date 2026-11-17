package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Quotation
import com.minhas.teller.data.repositories.QuotationRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.viewmodels.QuotationViewModel
import com.minhas.teller.ui.viewmodels.QuotationViewModelFactory
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.paiseToRupees
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotationListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = QuotationRepository(database.quotationDao())
    val viewModel: QuotationViewModel = viewModel(factory = QuotationViewModelFactory(repository))
    
    val companyId = SessionManager.currentCompanyId.collectAsState().value ?: 1
    
    val quotations by viewModel.quotations.collectAsState()
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<Quotation?>(null) }
    
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val displayFormatter = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    
    // Load quotations on first launch
    LaunchedEffect(companyId) {
        viewModel.loadQuotations(companyId)
    }
    
    // Date pickers
    val fromDatePicker = DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
            viewModel.setDateRange(cal.time, toDate, companyId)
        },
        Calendar.getInstance().apply { time = fromDate }.get(Calendar.YEAR),
        Calendar.getInstance().apply { time = fromDate }.get(Calendar.MONTH),
        Calendar.getInstance().apply { time = fromDate }.get(Calendar.DAY_OF_MONTH)
    )
    
    val toDatePicker = DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance().apply { set(year, month, day, 23, 59, 59) }
            viewModel.setDateRange(fromDate, cal.time, companyId)
        },
        Calendar.getInstance().apply { time = toDate }.get(Calendar.YEAR),
        Calendar.getInstance().apply { time = toDate }.get(Calendar.MONTH),
        Calendar.getInstance().apply { time = toDate }.get(Calendar.DAY_OF_MONTH)
    )
    
    // Delete confirmation dialog
    showDeleteDialog?.let { quotation ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(TerminologyManager.getTerm("Delete Quotation")) },
            text = { Text("Delete quotation ${quotation.quotationNumber} for ${quotation.customerName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteQuotation(quotation) {
                            showDeleteDialog = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(TerminologyManager.getTerm("Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }
    
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(Color(0xFF1A1A4A), Color(0xFF0F0F2D)))
        )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(TerminologyManager.getTerm("Quotations"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Screen.CreateQuotation.route) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(TerminologyManager.getTerm("Create New")) },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.Black
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(TerminologyManager.getTerm("Search by customer name..."), color = Color.White.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.7f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Date Range Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { fromDatePicker.show() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoShrinkText(
                            "From: ${displayFormatter.format(fromDate)}",
                            color = Color.Black,
                            maxFontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { toDatePicker.show() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoShrinkText(
                            "To: ${displayFormatter.format(toDate)}",
                            color = Color.Black,
                            maxFontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quotations List
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    val filteredQuotations = quotations.filter {
                        it.customerName.contains(searchQuery, ignoreCase = true)
                    }
                    
                    if (filteredQuotations.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Description,
                                    null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(TerminologyManager.getTerm("No quotations found"),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 16.sp
                                )
                                Text(TerminologyManager.getTerm("Tap + to create your first quotation"),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(filteredQuotations) { quotation ->
                                QuotationCard(
                                    quotation = quotation,
                                    dateFormatter = dateFormatter,
                                    onClick = {
                                        // View quotation PDF
                                        ExportUtils.generateQuotationPdf(context, quotation)
                                    },
                                    onEdit = {
                                        navController.navigate(Screen.EditQuotation.route.replace("{quotationId}", quotation.id.toString()))
                                    },
                                    onDelete = {
                                        showDeleteDialog = quotation
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuotationCard(
    quotation: Quotation,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quote Number Circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                AutoShrinkText(
                    text = quotation.quotationNumber.replace("QT-", ""),
                    color = Color(0xFF4CAF50),
                    maxFontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                AutoShrinkText(
                    text = quotation.customerName,
                    color = Color.White,
                    maxFontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                AutoShrinkText(
                    text = "${quotation.items.size} items • ${dateFormatter.format(quotation.quotationDate)}",
                    color = Color.White.copy(alpha = 0.6f),
                    maxFontSize = 12.sp,
                    minFontSize = 9.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                AutoShrinkText(
                    text = "₹ %.2f".format(quotation.grandTotal.paiseToRupees()),
                    color = Color(0xFF81C784),
                    maxFontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252).copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
