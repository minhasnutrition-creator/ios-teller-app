package com.minhas.teller.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.components.ValidatedNumberTextField
import java.util.Calendar
import android.app.Activity
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.utils.taxIdName
import com.minhas.teller.utils.LanguageType
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.TellerTopAppBar

import com.minhas.teller.ui.components.ModernTextField
import com.minhas.teller.ui.components.ModernDropdownField
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun saveImageToInternalStorage(context: android.content.Context, uri: Uri, prefix: String): String? {
    return try {
        // Decode then re-encode to PNG so HEIC/WEBP phone photos become readable by
        // the PDF library on invoices (raw-copying kept them in an undecodable format).
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: return null
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
fun CreateCompanyScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = AppDatabase.getDatabase(context)
    val repository = CompanyRepository(database.companyDao())
    val ledgerRepository = LedgerRepository(database.ledgerDao())
    val accountGroupRepository = AccountGroupRepository(database.accountGroupDao())
    val viewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(repository, ledgerRepository, accountGroupRepository))

    var name by remember { mutableStateOf("") }
    var mailingName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var state by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("₹") }
    var finYearFrom by remember { mutableStateOf("01-04-2025") } // Default
    var selectedLanguage by remember { mutableStateOf(LanguageType.SIMPLE) }
    var isSimpleVoucherMode by remember { mutableStateOf(true) }
    var useTax by remember { mutableStateOf(true) }
    var smartTax by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    var logoUri by remember { mutableStateOf<String?>(null) }
    var signatureUri by remember { mutableStateOf<String?>(null) }
    var stampType by remember { mutableIntStateOf(0) }
    var stampColor by remember { mutableIntStateOf(-16764007) }
    var stampDesignName by remember { mutableStateOf("Classic") }

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

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Create Company"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ModernTextField(
                value = name, 
                onValueChange = { name = it; if(mailingName.isEmpty()) mailingName = it }, 
                placeholder = TerminologyManager.getTerm("Company Name"),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModernTextField(
                value = mailingName, 
                onValueChange = { mailingName = it }, 
                placeholder = TerminologyManager.getTerm("Mailing Name"),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModernTextField(
                value = address, 
                onValueChange = { address = it }, 
                placeholder = TerminologyManager.getTerm("Address"),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                singleLine = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            var expandedCountryDropdown by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                ModernDropdownField(
                    text = country,
                    onClick = { expandedCountryDropdown = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    placeholder = TerminologyManager.getTerm("Country")
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
            Spacer(modifier = Modifier.height(8.dp))
            ModernTextField(
                value = state, 
                onValueChange = { state = it }, 
                placeholder = TerminologyManager.getTerm("State"),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            val countryConfig = remember(country) { TaxConfigManager.getConfigForCountry(country) }
            val taxIdLabel = countryConfig.taxIdName
            ValidatedNumberTextField(value = pincode, onValueChange = { pincode = it }, label = TerminologyManager.getTerm("Pincode"), isDecimal = false, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            ModernTextField(
                value = gstin, 
                onValueChange = { gstin = it }, 
                placeholder = taxIdLabel, 
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ValidatedNumberTextField(value = phone, onValueChange = { phone = it }, label = TerminologyManager.getTerm("Phone"), isDecimal = false, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            ModernTextField(
                value = email, 
                onValueChange = { email = it }, 
                placeholder = TerminologyManager.getTerm("Email (for Backup)"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), 
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Logo and Signature Upload
            Text(TerminologyManager.getTerm("Logo & Signature"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(8.dp))

            // Stamp Type Selection
            Text(TerminologyManager.getTerm("Stamp Type"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(16.dp))
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
                ModernDropdownField(
                    text = currencyOptions.firstOrNull { it.first == currencySymbol }?.second ?: currencySymbol,
                    onClick = { expandedCurrencyDropdown = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    placeholder = TerminologyManager.getTerm("Select Currency")
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

            var businessType by remember { mutableStateOf(TerminologyManager.getTerm("General")) }
            var expandedBusinessDropdown by remember { mutableStateOf(false) }
            val businessTypes = listOf(TerminologyManager.getTerm("General"), TerminologyManager.getTerm("Kirana"), TerminologyManager.getTerm("Electronics"), TerminologyManager.getTerm("Vehicle Dealer"))

            Spacer(modifier = Modifier.height(16.dp))
            Text(TerminologyManager.getTerm("Business Type (Auto Preloads Stock)"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                ModernDropdownField(
                    text = businessType,
                    onClick = { expandedBusinessDropdown = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    placeholder = TerminologyManager.getTerm("Select Business Type")
                )
                DropdownMenu(
                    expanded = expandedBusinessDropdown,
                    onDismissRequest = { expandedBusinessDropdown = false }
                ) {
                    businessTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                businessType = type
                                expandedBusinessDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            var expandedLangDropdown by remember { mutableStateOf(false) }
            Text(TerminologyManager.getTerm("Language & Terminology Mode"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                ModernDropdownField(
                    text = selectedLanguage.displayName,
                    onClick = { expandedLangDropdown = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    placeholder = TerminologyManager.getTerm("Select Language")
                )
                DropdownMenu(
                    expanded = expandedLangDropdown,
                    onDismissRequest = { expandedLangDropdown = false }
                ) {
                    LanguageType.values().forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.displayName) },
                            onClick = {
                                selectedLanguage = lang
                                expandedLangDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            var expandedVoucherDropdown by remember { mutableStateOf(false) }
            Text(TerminologyManager.getTerm("Voucher Creation Mode"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                ModernDropdownField(
                    text = if (isSimpleVoucherMode) TerminologyManager.getTerm("Simple Mode") else TerminologyManager.getTerm("Advanced Mode"),
                    onClick = { expandedVoucherDropdown = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    placeholder = TerminologyManager.getTerm("Select Voucher Mode")
                )
                DropdownMenu(
                    expanded = expandedVoucherDropdown,
                    onDismissRequest = { expandedVoucherDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Simple Mode")) },
                        onClick = {
                            isSimpleVoucherMode = true
                            expandedVoucherDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Advanced Mode")) },
                        onClick = {
                            isSimpleVoucherMode = false
                            expandedVoucherDropdown = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Tax Settings (enabled by default)
            Text(TerminologyManager.getTerm("Tax Settings"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(TerminologyManager.getTerm("Use GST / Taxes"), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(TerminologyManager.getTerm("Apply GST and tax codes on sales and purchases"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = useTax, onCheckedChange = { useTax = it })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(TerminologyManager.getTerm("Smart Tax Estimation"), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(TerminologyManager.getTerm("Auto-detect HSN codes and tax rates for items"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = smartTax, onCheckedChange = { smartTax = it })
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                enabled = !isCreating,
                onClick = {
                    if (isCreating) return@Button
                    if (name.isBlank()) {
                        Toast.makeText(context, TerminologyManager.getTerm("Company Name is required"), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isCreating = true
                    TerminologyManager.setLanguage(context, selectedLanguage)
                    DashboardPrefs.setSimpleVoucherMode(context, isSimpleVoucherMode)
                    val company = Company(
                        name = name,
                        mailingName = mailingName,
                        address = address,
                        state = state,
                        country = country,
                        pincode = pincode,
                        gstin = gstin.ifBlank { null },
                        phoneNumber = phone,
                        email = email,
                        currencySymbol = currencySymbol,
                        logoUri = logoUri,
                        signatureUri = signatureUri,
                        stampType = stampType,
                        stampColor = stampColor,
                        stampDesignName = stampDesignName,
                        financialYearFrom = System.currentTimeMillis()
                    )
                    viewModel.insert(company, businessType = businessType) { id ->
                        val companyId = id.toInt()
                        DashboardPrefs.setTaxCountry(context, country, companyId)
                        DashboardPrefs.setTaxSystemEnabled(context, useTax, companyId)
                        DashboardPrefs.setSmartTaxEstimationEnabled(context, smartTax, companyId)
                        Toast.makeText(context, TerminologyManager.getTerm("Company Created"), Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(TerminologyManager.getTerm("Create Company"))
            }
        }
    }
}
