package com.minhas.teller.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.utils.taxIdName
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.utils.DashboardPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private fun saveImageToInternalStorage(context: Context, uri: Uri, prefix: String): String? {
    return try {
        // Decode then RE-ENCODE to PNG. Modern phones deliver HEIC/WEBP, which the
        // iText PDF library cannot read — copying raw bytes into a ".jpg"-named file
        // left logos/signatures silently blank on invoices. Re-encoding to PNG makes
        // the stored image readable regardless of the source format.
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: return null

        // Downscale very large photos so the PDF stays small and we avoid OOM.
        val maxDim = 1200
        val longest = maxOf(decoded.width, decoded.height)
        val bitmap = if (longest > maxDim) {
            val scale = maxDim.toFloat() / longest
            android.graphics.Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else decoded

        val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCompanyScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val viewModel: CompanyViewModel = viewModel(
        factory = CompanyViewModelFactory(CompanyRepository(db.companyDao()))
    )
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by viewModel.getCompany(currentCompanyId ?: 1).collectAsState(initial = null)

    var initialized by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mailingName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var state by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("₹") }
    var logoUri by remember { mutableStateOf<String?>(null) }
    var signatureUri by remember { mutableStateOf<String?>(null) }
    var stampType by remember { mutableIntStateOf(0) }
    var stampColor by remember { mutableIntStateOf(-16764007) }
    var stampDesignName by remember { mutableStateOf("Classic") }
    var financialYearFrom by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.Default) {
                    val saved = saveImageToInternalStorage(context, uri, "company_logo")
                    withContext(Dispatchers.Main) { logoUri = saved }
                }
            }
        }
    )

    val signaturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.Default) {
                    val saved = saveImageToInternalStorage(context, uri, "company_sig")
                    withContext(Dispatchers.Main) { signatureUri = saved }
                }
            }
        }
    )

    LaunchedEffect(company) {
        company?.let {
            if (!initialized) {
                name = it.name
                mailingName = it.mailingName ?: ""
                address = it.address ?: ""
                country = it.country ?: "India"
                state = it.state ?: ""
                pincode = it.pincode ?: ""
                gstin = it.gstin ?: ""
                phoneNumber = it.phoneNumber ?: ""
                email = it.email ?: ""
                upiId = it.upiId ?: ""
                currencySymbol = it.currencySymbol
                logoUri = it.logoUri
                signatureUri = it.signatureUri
                stampType = it.stampType
                stampColor = it.stampColor
                stampDesignName = it.stampDesignName ?: "Classic"
                financialYearFrom = it.financialYearFrom
                initialized = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(TerminologyManager.getTerm("Edit Company")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = TerminologyManager.getTerm("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (mailingName.isBlank()) mailingName = it
                },
                label = { Text(TerminologyManager.getTerm("Company Name")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mailingName,
                onValueChange = { mailingName = it },
                label = { Text(TerminologyManager.getTerm("Mailing Name")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(TerminologyManager.getTerm("Address")) },
                modifier = Modifier.fillMaxWidth()
            )
            var expandedCountryDropdown by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text(TerminologyManager.getTerm("Country")) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { expandedCountryDropdown = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = TerminologyManager.getTerm("Country"))
                        }
                    }
                )
                DropdownMenu(
                    expanded = expandedCountryDropdown,
                    onDismissRequest = { expandedCountryDropdown = false }
                ) {
                    TaxConfigManager.getMainCountries().forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c) },
                            onClick = {
                                country = c
                                currencySymbol = TaxConfigManager.currencyForCountry(c)
                                expandedCountryDropdown = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state,
                onValueChange = { state = it },
                label = { Text(TerminologyManager.getTerm("State")) },
                modifier = Modifier.fillMaxWidth()
            )
            val countryConfig = remember(country) { TaxConfigManager.getConfigForCountry(country) }
            val taxIdLabel = countryConfig.taxIdName
            OutlinedTextField(
                value = pincode,
                onValueChange = { pincode = it },
                label = { Text(TerminologyManager.getTerm("Pincode")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = gstin,
                onValueChange = { gstin = it },
                label = { Text(taxIdLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text(TerminologyManager.getTerm("Phone")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(TerminologyManager.getTerm("Email")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text(TerminologyManager.getTerm("UPI ID (for payments)")) },
                placeholder = { Text(TerminologyManager.getTerm("e.g. yourbusiness@upi")) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(TerminologyManager.getTerm("Currency Symbol"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            var expandedCurrencyDropdown by remember { mutableStateOf(false) }
            val currencyOptions = listOf(
                "₹" to "Rupee (₹)", "$" to "Dollar ($)", "£" to "Pound (£)", "€" to "Euro (€)",
                "¥" to "Yen (¥)", "R$" to "Real (R$)", "₽" to "Ruble (₽)", "₺" to "Lira (₺)",
                "RM" to "Ringgit (RM)", "Rp" to "Rupiah (Rp)", "฿" to "Baht (฿)", "₱" to "Peso (₱)",
                "₫" to "Dong (₫)", "kr" to "Krona (kr)", "CHF" to "Franc (CHF)", "R" to "Rand (R)",
                "د.إ" to "Dirham (د.إ)", "﷼" to "Riyal (﷼)", "৳" to "Taka (৳)", "₨" to "Rupee (₨)",
                "रू" to "Nepali Rupee (रू)", "Rs" to "Rupee (Rs)"
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = currencyOptions.firstOrNull { it.first == currencySymbol }?.second ?: currencySymbol,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(TerminologyManager.getTerm("Currency Symbol")) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { expandedCurrencyDropdown = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = TerminologyManager.getTerm("Currency Symbol"))
                        }
                    }
                )
                DropdownMenu(
                    expanded = expandedCurrencyDropdown,
                    onDismissRequest = { expandedCurrencyDropdown = false }
                ) {
                    currencyOptions.forEach { (sym, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                currencySymbol = sym
                                expandedCurrencyDropdown = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(TerminologyManager.getTerm("Upload Logo"), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { signaturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(TerminologyManager.getTerm("Upload Sig"), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { navController.navigate(Screen.SignatureDrawing.route) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(TerminologyManager.getTerm("Draw Sig"), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(TerminologyManager.getTerm("Stamp Type"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(TerminologyManager.getTerm("None"), TerminologyManager.getTerm("Classic"), TerminologyManager.getTerm("Modern"), TerminologyManager.getTerm("Elegant")).forEachIndexed { idx, design ->
                    Button(
                        onClick = {
                            stampType = idx
                            if (idx > 0) stampDesignName = design
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = if (stampType == idx) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(design, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Button(
                onClick = {
                    if (isSaving) return@Button
                    if (name.isBlank()) {
                        Toast.makeText(context, TerminologyManager.getTerm("Company Name is required"), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    company?.let { original ->
                        viewModel.update(original.copy(
                            name = name,
                            mailingName = mailingName,
                            address = address,
                            country = country,
                            state = state,
                            pincode = pincode,
                            gstin = gstin,
                            phoneNumber = phoneNumber,
                            email = email,
                            upiId = upiId,
                            currencySymbol = currencySymbol,
                            stampType = stampType,
                            stampColor = stampColor,
                            stampDesignName = stampDesignName,
                            logoUri = logoUri,
                            signatureUri = signatureUri,
                            financialYearFrom = financialYearFrom
                        ))
                        DashboardPrefs.setTaxCountry(context, country, original.id)
                    }
                    Toast.makeText(context, TerminologyManager.getTerm("Company Profile Updated"), Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(if (isSaving) TerminologyManager.getTerm("Saving...") else TerminologyManager.getTerm("Save Changes"))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = TerminologyManager.getTerm("Delete Company"))
                Spacer(modifier = Modifier.width(8.dp))
                Text(TerminologyManager.getTerm("Delete Company"))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(TerminologyManager.getTerm("Delete Company")) },
            text = { Text(TerminologyManager.getTerm("Are you sure you want to delete this company? All accounting vouchers, inventory data, and transactions will be permanently lost. This action cannot be undone.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        company?.let { original ->
                            scope.launch {
                                viewModel.delete(original)
                                SessionManager.clearSession()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, TerminologyManager.getTerm("Company Deleted"), Toast.LENGTH_SHORT).show()
                                    navController.navigate(Screen.CompanyList.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(TerminologyManager.getTerm("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }
}
