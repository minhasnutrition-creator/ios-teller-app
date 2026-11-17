package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.minhas.teller.data.repositories.OutstandingVoucher
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun OutstandingBillsDialog(
    outstandingVouchers: List<OutstandingVoucher>,
    onDismissRequest: () -> Unit,
    onBillSelected: (OutstandingVoucher, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedVoucher by remember { mutableStateOf<OutstandingVoucher?>(null) }
    
    // Filtered list
    val filteredVouchers = outstandingVouchers.filter { 
        it.voucherNumber.contains(searchQuery, ignoreCase = true) 
    }

    if (selectedVoucher != null) {
        AmountInputDialog(
            voucher = selectedVoucher!!,
            onDismiss = { selectedVoucher = null },
            onConfirm = { amount ->
                onBillSelected(selectedVoucher!!, amount)
                selectedVoucher = null
            }
        )
    } else {
        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(TerminologyManager.getTerm("Outstanding Bills"), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(TerminologyManager.getTerm("Search Voucher No")) },
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredVouchers) { voucher ->
                            OutstandingVoucherItem(voucher) {
                                selectedVoucher = voucher
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutstandingVoucherItem(voucher: OutstandingVoucher, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AutoShrinkText(voucher.voucherNumber, fontWeight = FontWeight.Bold)
                AutoShrinkText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(voucher.date))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AutoShrinkText(voucher.type, style = MaterialTheme.typography.bodyMedium)

                Column(horizontalAlignment = Alignment.End) {
                    AutoShrinkText("Pending: ${voucher.pendingAmount}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)

                    if (voucher.interestAmount > 0.01) {
                        AutoShrinkText(
                            text = "Interest: ₹${String.format(Locale.getDefault(), "%.2f", voucher.interestAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB00020)
                        )
                    }

                    if (voucher.totalReceived > 0) {
                        AutoShrinkText(
                            text = "Rec: ${voucher.totalReceived}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Green
                        )
                        if (voucher.lastPaymentDate != null) {
                            AutoShrinkText(
                                text = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(voucher.lastPaymentDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                maxFontSize = 10.sp,
                                minFontSize = 7.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AmountInputDialog(
    voucher: OutstandingVoucher,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountStr by remember { mutableStateOf(voucher.pendingAmount.toString()) }
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val diff = amount - voucher.pendingAmount
    
    val statusColor = if (diff < -0.01) Color.Red else if (diff > 0.01) Color.Green else MaterialTheme.colorScheme.onSurface
    val statusText = if (diff < -0.01) "Short" else if (diff > 0.01) "Over" else "Full"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(TerminologyManager.getTerm("Receive/Pay Amount"), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                AutoShrinkText("Bill Amount: ${voucher.pendingAmount}")
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(TerminologyManager.getTerm("Amount")) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                AutoShrinkText(
                    text = "$statusText (${kotlin.math.abs(diff)})",
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(TerminologyManager.getTerm("Cancel")) }
                    Button(onClick = { onConfirm(amount) }) { Text(TerminologyManager.getTerm("Confirm")) }
                }
            }
        }
    }
}