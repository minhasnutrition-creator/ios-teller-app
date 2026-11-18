package com.minhas.teller.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.LicenseUtils
import com.minhas.teller.utils.PlayBillingManager
import com.minhas.teller.utils.TerminologyManager
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(navController: NavController, reason: String? = null) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val machineId = remember { LicenseUtils.getMachineId(context) }
    // Billing Manager
    val billingManager = remember { PlayBillingManager(context, scope) }
    val productDetails by billingManager.productDetails.collectAsState()
    val purchaseStatus by billingManager.purchaseStatus.collectAsState()
    val connectionState by billingManager.connectionState.collectAsState()

    var licenseKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successKey by remember { mutableStateOf<String?>(null) }
    var purchaseErrorMessage by remember { mutableStateOf<String?>(null) }
    var productsLoadTimedOut by remember { mutableStateOf(false) }

    val isTrialExpired = reason == "expired"

    // Intercept back gesture/button when trial or license is expired to lock app down completely
    BackHandler(enabled = isTrialExpired) {
        Toast.makeText(context, TerminologyManager.getTerm("License required to proceed."), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(productDetails) {
        if (productDetails.isNotEmpty()) productsLoadTimedOut = false
    }

    LaunchedEffect(Unit) {
        delay(12_000)
        if (productDetails.isEmpty()) productsLoadTimedOut = true
    }

    // Monitor Purchase Status
    LaunchedEffect(purchaseStatus) {
        when (val status = purchaseStatus) {
            is PlayBillingManager.PurchaseStatus.Success -> {
                isLoading = false
                purchaseErrorMessage = null
                successKey = status.licenseKey
                Toast.makeText(context, TerminologyManager.getTerm("Purchase Successful! Activated."), Toast.LENGTH_LONG).show()
            }
            is PlayBillingManager.PurchaseStatus.Error -> {
                isLoading = false
                purchaseErrorMessage = status.message
            }
            is PlayBillingManager.PurchaseStatus.Processing -> {
                isLoading = true
                purchaseErrorMessage = null
            }
            else -> {}
        }
    }
    
    // Auto-navigate if successful
    LaunchedEffect(successKey) {
        if (successKey != null) {
            // Give user a moment to see the key if they want, or show a dialog
            // We'll show a dialog below instead of navigating immediately if you prefer,
            // but for smooth UX, we might just navigate. 
            // However, user needs to SEE the key to save it. 
        }
    }

    if (successKey != null) {
        AlertDialog(
            onDismissRequest = { 
                navController.navigate(Screen.CompanyList.route) { popUpTo(0) } 
            },
            title = { Text(TerminologyManager.getTerm("Activation Successful!")) },
            text = {
                Column {
                    Text(TerminologyManager.getTerm("Your app is now activated. Please SAVE this License Key securely. You will need it if you move to another device."))
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(successKey!!, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(successKey!!))
                                Toast.makeText(context, TerminologyManager.getTerm("Copied!"), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = TerminologyManager.getTerm("Copy"))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    navController.navigate(Screen.CompanyList.route) { popUpTo(0) } 
                }) {
                    Text(TerminologyManager.getTerm("Continue to App"))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { AutoShrinkText(if (isTrialExpired) TerminologyManager.getTerm("Trial Expired") else TerminologyManager.getTerm("Product Activation")) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (isTrialExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (isTrialExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                imageVector = if (isTrialExpired) Icons.Default.Lock else Icons.Default.Lock,
                contentDescription = TerminologyManager.getTerm("Locked"),
                modifier = Modifier.size(60.dp),
                tint = if (isTrialExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isTrialExpired) TerminologyManager.getTerm("Trial Period Ended") else TerminologyManager.getTerm("License Required"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = TerminologyManager.getTerm("Purchase a license to unlock full features."),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Purchase Options
            Text(TerminologyManager.getTerm("Select a Plan"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            // Show what step the billing client is on so we can diagnose silent hangs
            if (productDetails.isEmpty() && !productsLoadTimedOut) {
                val statusText = when (connectionState) {
                    PlayBillingManager.ConnectionState.Disconnected -> TerminologyManager.getTerm("Waiting for Google Play…")
                    PlayBillingManager.ConnectionState.Connecting -> TerminologyManager.getTerm("Connecting to Google Play…")
                    PlayBillingManager.ConnectionState.Connected -> TerminologyManager.getTerm("Connected. Fetching plans…")
                    PlayBillingManager.ConnectionState.QueryingProducts -> TerminologyManager.getTerm("Loading plans from Google Play…")
                    PlayBillingManager.ConnectionState.Ready -> null
                    PlayBillingManager.ConnectionState.Error -> null  // error card below handles this
                }
                if (statusText != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (purchaseErrorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        purchaseErrorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (productDetails.isEmpty() && productsLoadTimedOut) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            TerminologyManager.getTerm("Couldn't load subscription plans from Google Play."),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            TerminologyManager.getTerm("Common causes: 1) Play Store app is outdated — update it. 2) Play Store cache is stale — clear it in Settings → Apps → Play Store → Storage → Clear Cache. 3) Running on an emulator/LDPlayer without Play Services setup. 4) Product IDs in Play Console don't match the app."),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                productsLoadTimedOut = false
                                billingManager.queryProducts()
                            }) {
                                Text(TerminologyManager.getTerm("Retry Loading Plans"))
                            }
                        }
                    }
                }
            }

            val plans = listOf(
                Triple(PlayBillingManager.SUBS_MONTHLY, TerminologyManager.getTerm("Monthly"), TerminologyManager.getTerm("Low barrier to entry")),
                Triple(PlayBillingManager.SUBS_YEARLY, TerminologyManager.getTerm("Yearly"), TerminologyManager.getTerm("Recommended. Best Value.")),
                Triple(PlayBillingManager.INAPP_LIFETIME, TerminologyManager.getTerm("Lifetime"), TerminologyManager.getTerm("One-time payment. No recurring fees."))
            )

            plans.forEach { (id, name, desc) ->
                val details = productDetails[id]
                var price = if (productsLoadTimedOut) TerminologyManager.getTerm("Unavailable") else TerminologyManager.getTerm("Loading...")

                if (details != null) {
                    price = details.toString()
                } else {
                    price = when (id) {
                        PlayBillingManager.SUBS_MONTHLY -> "₹499 / mo"
                        PlayBillingManager.SUBS_YEARLY -> "₹3,999 / yr"
                        PlayBillingManager.INAPP_LIFETIME -> "₹9,999 once"
                        else -> TerminologyManager.getTerm("Available on App Store")
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = !isLoading) {
                            if (activity != null && details != null) {
                                purchaseErrorMessage = null
                                billingManager.launchPurchaseFlow(activity, id)
                            }
                        },
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        if (details == null) {
                            if (productsLoadTimedOut) {
                                Text(price, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            Text(price, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(TerminologyManager.getTerm("Verifying purchase…"), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Existing Key Input Section
            Text(TerminologyManager.getTerm("Already have a key?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = licenseKey,
                onValueChange = { 
                    licenseKey = it.uppercase().trim()
                    errorMessage = null
                },
                label = { Text(TerminologyManager.getTerm("Enter License Key")) },
                placeholder = { Text(TerminologyManager.getTerm("XXXX-XXXX-XXXX-XXXX")) },
                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                singleLine = true,
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (licenseKey.isBlank()) {
                        errorMessage = TerminologyManager.getTerm("Please enter a key")
                        return@Button
                    }
                    
                    isLoading = true
                    scope.launch {
                        val result = LicenseUtils.activateLicense(context, licenseKey)
                        isLoading = false
                        if (result.first) {
                            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            navController.navigate(Screen.CompanyList.route) {
                                popUpTo(0)
                            }
                        } else {
                            errorMessage = result.second
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text(TerminologyManager.getTerm("ACTIVATE WITH KEY"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Machine ID at bottom for reference
            Text("${TerminologyManager.getTerm("Machine ID")}: $machineId", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
    }
}
