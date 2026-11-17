 package com.minhas.teller

import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.minhas.teller.navigation.Screen
import com.minhas.teller.navigation.AppNavigation
import com.minhas.teller.ui.screens.*
import com.minhas.teller.ui.theme.TellerAppTheme
import com.minhas.teller.ui.theme.getBackgroundBrush
import com.minhas.teller.utils.LicenseUtils
import com.minhas.teller.utils.ThemeUtils
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.ui.components.GlassBottomNavBar
import com.minhas.teller.ui.theme.MainBackgroundGradient
import com.minhas.teller.ui.theme.BackgroundPurpleGradient
import com.minhas.teller.ui.theme.BackgroundYellowGradient
import com.minhas.teller.ui.theme.BackgroundTealGradient
import com.minhas.teller.ui.theme.BackgroundInventoryGradient
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.ThemeManager
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.SecurityUtils
import com.minhas.teller.ui.screens.dashboard.DashboardBento
import com.minhas.teller.ui.screens.dashboard.DashboardCrystal
import com.minhas.teller.ui.screens.dashboard.DashboardCyberFinance
import androidx.compose.ui.graphics.Brush
import com.minhas.teller.SessionManager

import androidx.activity.SystemBarStyle

import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.minhas.teller.utils.PdfNavigator
import com.minhas.teller.utils.ExcelNavigator
import com.minhas.teller.ui.screens.ExcelViewerScreen
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.minhas.teller.utils.HSNDataManager
import android.net.Uri
import java.util.concurrent.TimeUnit
import android.content.SharedPreferences

@Composable
private fun DowngradeBlockedScreen() {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = TerminologyManager.getTerm("Please update Teller"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = TerminologyManager.getTerm("This copy of Teller is older than the one that last saved your books. To keep your data safe, Teller won't open it with this version. Install the latest Teller and all your companies and vouchers will be there."),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val pkg = context.packageName
                val market = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(market)
                } catch (_: android.content.ActivityNotFoundException) {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) {
                Text(TerminologyManager.getTerm("Get the latest version"))
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission granted or denied. We continue either way.
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle multiple permission results
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // All permissions granted, proceed with setup
            proceedWithAppSetup()
        } else {
            // Some permissions denied, but we continue anyway
            proceedWithAppSetup()
        }
    }

    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirst = prefs.getBoolean("is_first_launch", true)
        if (isFirst) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
        }
        return isFirst
    }

    private fun proceedWithAppSetup() {
        // Continue with app initialization after permissions
        setupBackupAndDownload()
        triggerAutoSyncIfSignedIn()
    }

    private fun triggerAutoSyncIfSignedIn() {
        // "Login and forget": if the user is already signed in, kick off a cloud sync on app open.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.minhas.teller.utils.AuthManager.refresh()
                if (SessionManager.currentUserId.value != null) {
                    com.minhas.teller.utils.CloudSyncEngine(applicationContext).sync()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        

        
        // Add internet/data permissions (usually granted automatically, but let's be thorough)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.INTERNET)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_NETWORK_STATE)
        }
        
        // Request all permissions at once if any are needed
        if (permissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            // All permissions already granted, proceed with setup
            proceedWithAppSetup()
        }
    }

    private fun setupBackupAndDownload() {
        // Schedule Daily Summary Worker at 8:00 PM daily
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 20)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= now) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = calendar.timeInMillis - now

        val summaryRequest = PeriodicWorkRequestBuilder<com.minhas.teller.workers.DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailySummary",
            ExistingPeriodicWorkPolicy.UPDATE,
            summaryRequest
        )

        // Schedule GST return due-date reminder (daily). Only India companies are notified.
        val gstRequest = PeriodicWorkRequestBuilder<com.minhas.teller.workers.GstReturnReminderWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.minhas.teller.workers.GstReturnReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            gstRequest
        )

        // Schedule background cloud sync (every 6 hours). No-ops when signed out.
        val cloudSyncRequest = PeriodicWorkRequestBuilder<com.minhas.teller.workers.CloudSyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CloudSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            cloudSyncRequest
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // An older APK was installed over a newer one: the on-disk database uses a schema this build
        // doesn't know. Opening it would fail (and used to wipe all data), so stop here and ask the
        // user to update. Nothing below runs, so nothing touches the database.
        if (com.minhas.teller.data.DatabaseVersionGuard.isDowngrade(this)) {
            TerminologyManager.initialize(this)
            setContent { MaterialTheme { DowngradeBlockedScreen() } }
            return
        }

        // Pre-warm the offline HSN directory (~21k entries) in the background so the first
        // finance-helper voucher creation doesn't stall on the one-time DB load.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                HSNDataManager.getInstance(applicationContext).ensureDefaultHSNLoaded()
            } catch (_: Exception) {
            }
        }

        // Security Check: non-blocking, runs on background thread
        Thread {
            try {
                val securityStatus = SecurityUtils.performSecurityChecks(this@MainActivity)
                if (securityStatus.isRooted || securityStatus.isTampered) {
                    android.util.Log.w("SECURITY", "Compromised device: $securityStatus")
                    SecurityUtils.flagCompromisedDevice(
                        rooted = securityStatus.isRooted,
                        tampered = securityStatus.isTampered
                    )
                }
                if (!securityStatus.isSecure) {
                    android.util.Log.w("SECURITY", "Security check failed: $securityStatus")
                }
            } catch (e: Exception) {
                android.util.Log.e("SECURITY", "Error performing security checks", e)
            }
        }.start()

        // Manual Firebase initialization (Google Services plugin incompatible with AGP 9.0)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                android.util.Log.d("FIREBASE_INIT", "Initializing Firebase manually...")
                val options = FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build()
                val app = FirebaseApp.initializeApp(this, options)
                android.util.Log.d("FIREBASE_INIT", "Firebase initialized successfully: ${app?.name}")
            } else {
                android.util.Log.d("FIREBASE_INIT", "Firebase already initialized")
            }
        } catch (e: Exception) {
            android.util.Log.e("FIREBASE_INIT", "Failed to initialize Firebase: ${e.message}", e)
        }

        // Enable edge-to-edge for Android 15+ compatibility
        // Deprecated APIs (setStatusBarColor, setNavigationBarColor, setDecorFitsSystemWindows) removed
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        // Handle first launch permissions
        if (isFirstLaunch()) {
            requestAllPermissions()
        } else {
            setupBackupAndDownload()
        }
        
        setContent {
            val context = LocalContext.current
            // Initialize TerminologyManager
            remember { TerminologyManager.initialize(context); true }
            
            val currentAppTheme = remember { ThemeManager.getCurrentTheme(context) }

            TellerAppTheme(appTheme = currentAppTheme) {
                val navController = rememberNavController()

                val backgroundBrush = when (currentAppTheme) {
                    AppTheme.CYBER_FINANCE -> Brush.linearGradient(listOf(Color.Black, Color.Black))
                    AppTheme.BENTO -> Brush.linearGradient(listOf(Color(0xFF2C3E50), Color(0xFF2C3E50)))
                    AppTheme.CRYSTAL -> MainBackgroundGradient
                    else -> MainBackgroundGradient
                }

                AppNavigation(
                    navController = navController,
                    onThemeChanged = { /* This is now handled by ThemeManager restart */ },
                    currentAppTheme = currentAppTheme,
                    backgroundBrush = backgroundBrush
                )
            }
        }
    }
}
