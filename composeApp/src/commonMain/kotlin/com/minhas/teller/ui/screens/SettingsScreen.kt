package com.minhas.teller.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.launch
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.viewmodels.HSNSyncViewModel
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.AiModelPrefs
import com.minhas.teller.utils.PrinterPrefs
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.GemmaLLM
import com.minhas.teller.utils.LanguageType
import com.minhas.teller.utils.UiLanguage
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.ThemeManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.OpenInNew

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTheme by remember { mutableStateOf(ThemeManager.getCurrentTheme(context)) }
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()
    val currentUiLanguage by TerminologyManager.currentUiLanguage.collectAsState()
    var isSimpleVoucherMode by remember { mutableStateOf(DashboardPrefs.isSimpleVoucherMode(context)) }
    var showRestartDialog by remember { mutableStateOf<AppTheme?>(null) }
    var isTaxSystemEnabled by remember { mutableStateOf(DashboardPrefs.isTaxSystemEnabled(context)) }
    var isSmartTaxEstimation by remember { mutableStateOf(DashboardPrefs.isSmartTaxEstimationEnabled(context)) }
    var currentTemplate by remember { mutableStateOf(DashboardPrefs.getInvoiceTemplate(context)) }
    var selectedCountry by remember { mutableStateOf(DashboardPrefs.getTaxCountry(context)) }
    var showCountryMenu by remember { mutableStateOf(false) }

    // AI Model Settings
    var isAiLoaded by remember { mutableStateOf(GemmaLLM.isLoaded()) }
    var aiError by remember { mutableStateOf(GemmaLLM.getLoadError()) }
    val workInfos by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(com.minhas.teller.utils.FinanceHelper.OFFLINE_MLC_DOWNLOAD_WORK_NAME)
    }.observeAsState(initial = emptyList())
    val downloadWorkInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val isDownloading = downloadWorkInfo != null
    val downloadProgress = downloadWorkInfo?.progress?.getInt("progress", 0) ?: 0
    val downloadedBytes = downloadWorkInfo?.progress?.getLong("downloadedBytes", 0L) ?: 0L
    val totalBytes = downloadWorkInfo?.progress?.getLong("totalBytes", 0L) ?: 0L

    if (showRestartDialog != null) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = null },
            title = { Text(TerminologyManager.getTerm("Restart Required")) },
            text = { Text(TerminologyManager.getTerm("To apply the new theme, the application needs to restart. Do you want to continue?")) },
            confirmButton = {
                TextButton(onClick = {
                    val newTheme = showRestartDialog!!
                    ThemeManager.setTheme(context, newTheme)
                    ThemeManager.restartApp(context)
                    showRestartDialog = null
                }) {
                    Text(TerminologyManager.getTerm("Restart Now"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = null }) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(TerminologyManager.getTerm("Settings")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = TerminologyManager.getTerm("Cancel"))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GlowingCard(
                title = TerminologyManager.getTerm("Layout & Theme"),
                glowColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(Modifier.selectableGroup()) {
                    AppTheme.values().forEach { theme ->
                        NeonRadioOption(
                            text = theme.themeName,
                            selected = currentTheme == theme,
                            onClick = { if (currentTheme != theme) showRestartDialog = theme },
                            selectedColor = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            GlowingCard(
                title = TerminologyManager.getTerm("App Language"),
                glowColor = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(Modifier.selectableGroup()) {
                    UiLanguage.values().forEach { uiLang ->
                        NeonRadioOption(
                            text = uiLang.displayName,
                            selected = currentUiLanguage == uiLang,
                            onClick = {
                                TerminologyManager.setUiLanguage(context, uiLang)
                            },
                            selectedColor = MaterialTheme.colorScheme.tertiary,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            GlowingCard(
                title = TerminologyManager.getTerm("Language & Terminology"),
                glowColor = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(Modifier.selectableGroup()) {
                    LanguageType.values().forEach { language ->
                        NeonRadioOption(
                            text = language.displayName,
                            selected = currentLanguage == language,
                            onClick = {
                                TerminologyManager.setLanguage(context, language)
                            },
                            selectedColor = MaterialTheme.colorScheme.secondary,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = TerminologyManager.getTerm("Changes whether technical accounting terms (e.g., Sundry Debtors) or simplified words (e.g., Customers) are displayed across the app."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            GlowingCard(
                title = TerminologyManager.getTerm("Voucher Creation Mode"),
                glowColor = Color(0xFF9C27B0),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(Modifier.selectableGroup()) {
                    NeonRadioOption(
                        text = TerminologyManager.getTerm("Simple Mode (Auto-assigns ledger & hides pickers)"),
                        selected = isSimpleVoucherMode,
                        onClick = {
                            isSimpleVoucherMode = true
                            DashboardPrefs.setSimpleVoucherMode(context, true)
                        },
                        selectedColor = Color(0xFF9C27B0),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                    NeonRadioOption(
                        text = TerminologyManager.getTerm("Advanced Mode (Full manual ledger controls)"),
                        selected = !isSimpleVoucherMode,
                        onClick = {
                            isSimpleVoucherMode = false
                            DashboardPrefs.setSimpleVoucherMode(context, false)
                        },
                        selectedColor = Color(0xFF9C27B0),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = TerminologyManager.getTerm("Simple mode auto-assigns sales/purchase ledgers and hides technical transaction type pickers. Advanced mode provides full manual voucher control."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            GlowingCard(
                title = TerminologyManager.getTerm("Invoice Templates"),
                glowColor = Color(0xFF00BCD4),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Description,
                    iconTint = Color(0xFF00BCD4),
                    text = "${TerminologyManager.getTerm("Active Template")}: ${currentTemplate.displayName}",
                    subtitle = TerminologyManager.getTerm("Tap to change the invoice print layout"),
                    trailing = {
                        TextButton(onClick = {
                            navController.navigate(Screen.InvoiceTemplates.route)
                        }) { Text(TerminologyManager.getTerm("Change")) }
                    }
                )
                InvoiceTemplatePreview(
                    template = currentTemplate,
                    isSelected = true,
                    onClick = { navController.navigate(Screen.InvoiceTemplates.route) }
                )
            }

            GlowingCard(
                title = "PC Desktop Mode",
                glowColor = Color(0xFF10B981),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Laptop,
                    iconTint = Color(0xFF10B981),
                    text = "PC Desktop Companion",
                    subtitle = "Open Teller in Chrome or Edge on PC/Mac for fast keyboard accounting",
                    trailing = {
                        IconButton(onClick = { navController.navigate(Screen.DesktopCompanion.route) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            GlowingCard(
                title = "Cloud Sync & Backup",
                glowColor = Color(0xFF2979FF),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Sync,
                    iconTint = Color(0xFF2979FF),
                    text = "Cloud Sync",
                    subtitle = "Sign in to back up & sync across phones",
                    trailing = {
                        IconButton(onClick = { navController.navigate(Screen.CloudSync.route) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            GlowingCard(
                title = "Hardware & Printers",
                glowColor = Color(0xFF45E0E6),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Print,
                    iconTint = Color(0xFF45E0E6),
                    text = "Invoice Printer Hub (A4 & POS)",
                    subtitle = "Default: ${PrinterPrefs.getFriendlyPrinterSummary(context)}",
                    trailing = {
                        IconButton(onClick = { navController.navigate(Screen.ThermalPrinter.route) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            GlowingCard(
                title = TerminologyManager.getTerm("Subscription"),
                glowColor = Color(0xFFFFC107),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                // Previously the only way into the purchase/plans screen was a banner that only
                // appeared once the trial had already expired — added here (and in About, and the
                // Dashboard's 3-dot menu) so it's always findable.
                SettingsRow(
                    icon = Icons.Default.WorkspacePremium,
                    iconTint = Color(0xFFFFC107),
                    text = TerminologyManager.getTerm("Manage Subscription"),
                    subtitle = TerminologyManager.getTerm("View plans, upgrade, or manage your license"),
                    trailing = {
                        IconButton(onClick = { navController.navigate(Screen.License.route) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            GlowingCard(
                title = TerminologyManager.getTerm("Live Stock"),
                glowColor = Color(0xFFE91E63),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Share,
                    iconTint = Color(0xFFE91E63),
                    text = TerminologyManager.getTerm("Share Team App with Employee"),
                    subtitle = TerminologyManager.getTerm("Send the Teller Team companion app via WhatsApp, Email or any app"),
                    trailing = {
                        TextButton(onClick = { shareTeamApp(context) }) {
                            Text(TerminologyManager.getTerm("Share Team App"))
                        }
                    }
                )
                // Link to install guide
                TextButton(
                    onClick = { navController.navigate(Screen.InstallGuide.route) },
                    modifier = Modifier.padding(start = 56.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(TerminologyManager.getTerm("📱 Show employee how to install it"),
                        color = Color(0xFF4CAF50),
                        fontSize = 13.sp
                    )
                }
            }

            // HSN Sync ViewModel
            val hsnViewModel: HSNSyncViewModel = viewModel()
            val hsnUiState by hsnViewModel.uiState.collectAsState()
            
            LaunchedEffect(Unit) {
                hsnViewModel.refreshStats()
            }

            GlowingCard(
                title = TerminologyManager.getTerm("Advanced Features"),
                glowColor = Color(0xFFFF9800),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                // Master switch — enables the whole GST/tax system for THIS company.
                // Saved per company, so tax can be ON for one company and OFF for another.
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    iconTint = Color(0xFFFF9800),
                    text = TerminologyManager.getTerm("Use GST / Taxes"),
                    subtitle = TerminologyManager.getTerm("Calculate GST, HSN codes & tax on invoices. Saved separately for each company — turn it off to use the app just for personal tracking."),
                    trailing = {
                        Switch(
                            checked = isTaxSystemEnabled,
                            onCheckedChange = {
                                isTaxSystemEnabled = it
                                DashboardPrefs.setTaxSystemEnabled(context, it)
                            }
                        )
                    }
                )

                if (isTaxSystemEnabled) {

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                SettingsRow(
                    icon = Icons.Default.Calculate,
                    iconTint = Color(0xFFFF9800),
                    text = TerminologyManager.getTerm("Smart Tax Estimation"),
                    subtitle = TerminologyManager.getTerm("Auto-detect the HSN code & GST rate for each item you add."),
                    trailing = {
                        Switch(
                            checked = isSmartTaxEstimation,
                            onCheckedChange = {
                                isSmartTaxEstimation = it
                                DashboardPrefs.setSmartTaxEstimationEnabled(context, it)
                            }
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                SettingsRow(
                    icon = Icons.Default.Language,
                    iconTint = Color(0xFFFF9800),
                    text = TerminologyManager.getTerm("Tax Country"),
                    subtitle = "${TerminologyManager.getTerm("Current Country")}: $selectedCountry",
                    trailing = {
                        Box {
                            TextButton(onClick = { showCountryMenu = true }) {
                                Text(TerminologyManager.getTerm("Change"))
                            }
                            DropdownMenu(
                                expanded = showCountryMenu,
                                onDismissRequest = { showCountryMenu = false },
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                com.minhas.teller.utils.TaxConfigManager.getAllCountries().forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text(country) },
                                        onClick = {
                                            selectedCountry = country
                                            DashboardPrefs.setTaxCountry(context, country)
                                            showCountryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                
                // HSN/GST Data Update Section
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                TerminologyManager.getTerm("HSN & Tax Rates Database"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${hsnUiState.totalHSNCount} ${TerminologyManager.getTerm("codes stored")} • ${TerminologyManager.getTerm("Last updated")}: ${hsnUiState.lastSyncDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hsnViewModel.isSyncRecommended()) {
                                Text(
                                    TerminologyManager.getTerm("Update recommended"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }
                        
                        Button(
                            onClick = { hsnViewModel.syncHSNData() },
                            enabled = !hsnUiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            if (hsnUiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(TerminologyManager.getTerm("Update"))
                            }
                        }
                    }
                    
                    // Auto-update toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            TerminologyManager.getTerm("Auto-update tax rates"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = hsnUiState.isAutoUpdateEnabled,
                            onCheckedChange = { hsnViewModel.toggleAutoUpdate(it) }
                        )
                    }
                    
                    // Sync status message
                    hsnUiState.syncMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hsnUiState.syncSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    // Custom HSN Sync URL
                    var customHsnUrl by remember {
                        val prefs = context.getSharedPreferences("HSNDataManager", android.content.Context.MODE_PRIVATE)
                        mutableStateOf(prefs.getString("custom_hsn_sync_url", "") ?: "")
                    }
                    var urlSaved by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            TerminologyManager.getTerm("Custom Tax Data URL"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            TerminologyManager.getTerm("Optional: enter a URL to fetch the latest HSN/tax rate JSON from your own source"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = customHsnUrl,
                            onValueChange = { customHsnUrl = it; urlSaved = false },
                            label = { Text(TerminologyManager.getTerm("Sync URL (optional)")) },
                            placeholder = { Text(TerminologyManager.getTerm("https://example.com/hsn_data.json")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (customHsnUrl.isNotBlank()) {
                                    IconButton(onClick = { customHsnUrl = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = TerminologyManager.getTerm("Clear"))
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                val prefs = context.getSharedPreferences("HSNDataManager", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putString("custom_hsn_sync_url", customHsnUrl.trim()).apply()
                                urlSaved = true
                            },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text(if (urlSaved) TerminologyManager.getTerm("Saved") else TerminologyManager.getTerm("Save URL"))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    // Import from File option
                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            scope.launch {
                                val hsnManager = com.minhas.teller.utils.HSNDataManager.getInstance(context)
                                val result = hsnManager.importFromFile(it)
                                // Show result as a toast or snackbar
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        result.message,
                                        if (result.success) android.widget.Toast.LENGTH_SHORT else android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                if (result.success) {
                                    hsnViewModel.refreshStats()
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importLauncher.launch("*/*") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            TerminologyManager.getTerm("Import HSN from File (CSV/JSON)"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                } // end if (isTaxSystemEnabled)
                  GlowingCard(
                title = "\uD83E\uDDE0 ${TerminologyManager.getTerm("Helafin AI Engine")}",
                glowColor = Color(0xFF9C27B0),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Psychology,
                    iconTint = Color(0xFF9C27B0),
                    text = TerminologyManager.getTerm("Helafin Cloud Intelligence"),
                    subtitle = TerminologyManager.getTerm("Powered by DeepSeek AI - Active & Ready"),
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Text(TerminologyManager.getTerm("ACTIVE"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }          }

            GlowingCard(
                title = TerminologyManager.getTerm("Stamp & Signature"),
                glowColor = Color(0xFFE91E63),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.Brush,
                    iconTint = Color(0xFFE91E63),
                    text = TerminologyManager.getTerm("Draw Signature"),
                    subtitle = TerminologyManager.getTerm("Create custom signature for invoices"),
                    trailing = {
                        IconButton(onClick = { navController.navigate(Screen.SignatureDrawing.route) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            GlowingCard(
                title = TerminologyManager.getTerm("About & Privacy"),
                glowColor = Color(0xFF607D8B),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                SettingsRow(
                    icon = Icons.Default.PrivacyTip,
                    iconTint = Color(0xFF607D8B),
                    text = TerminologyManager.getTerm("Privacy Policy"),
                    subtitle = TerminologyManager.getTerm("Read our developer program compliant privacy policy"),
                    trailing = {
                        val browserIntent = remember { Intent(Intent.ACTION_VIEW, Uri.parse("https://minhas-teller.web.app/privacy-policy.html")) }
                        IconButton(onClick = { context.startActivity(browserIntent) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun GlowingCard(
    title: String,
    glowColor: Color,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (() -> Unit)? = null,
    innerBrush: Brush? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = glowColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, glowColor.copy(alpha = 0.3f))
        ) {
            if (innerBrush != null) {
                Box(Modifier.background(innerBrush)) { content() }
            } else {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    subtitle: String = "",
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun NeonRadioOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = selectedColor)
        )
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (selected) selectedColor else textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun InvoiceTemplatePreview(
    template: DashboardPrefs.InvoiceTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        TemplateContentMockup(template, scale = 0.65f)
    }
}

@Composable
fun ThemeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null recommended for accessibility with selectable
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}