package com.minhas.teller.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.minhas.teller.utils.EInvoiceStore
import org.json.JSONTokener

/**
 * Hosts the government portal inside the app with full desktop overview mode, pinch-to-zoom,
 * and direct routing to the official Login and Bulk Upload pages.
 *
 * What this automates:
 * - Direct landing on the Login page (https://einvoice1.gst.gov.in/Account/Login) instead of
 *   an unscaled, cut-off informational homepage.
 * - Overview mode + Wide viewport so desktop-designed government pages scale to fit mobile screens.
 * - Auto-attaching the generated JSON to the portal's "Choose File" picker during Bulk Upload.
 * - Reading the portal's reply/error directly when submitted.
 * - One-tap fallback to Google Chrome if the taxpayer wants saved passwords or full browser UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalAutopilotScreen(navController: NavHostController) {
    val context = LocalContext.current
    val inbox = navController.previousBackStackEntry?.savedStateHandle

    val targetUrl = remember { inbox?.get<String>("portal_url") ?: "https://einvoice1.gst.gov.in/Account/Login" }
    val attachUriStr = remember { inbox?.get<String>("attach_uri") }
    val attachFileName = remember { inbox?.get<String>("attach_file_name") }
    val wantedDocNo = remember { inbox?.get<String>("wanted_doc_no") }
    val companyId = remember { inbox?.get<Int>("portal_company_id") }
    val voucherId = remember { inbox?.get<Int>("portal_voucher_id") }

    val allowedHosts = remember {
        setOf(
            "einvoice1.gst.gov.in", "einvoice2.gst.gov.in", "einvoice3.gst.gov.in",
            "einvoice4.gst.gov.in", "einvoice6.gst.gov.in", "ewaybillgst.gov.in",
            "gst.gov.in", "services.gst.gov.in", "nic.in", "einvoice.nic.in"
        )
    }
    fun isAllowedHost(host: String?): Boolean {
        if (host == null) return false
        return host.endsWith(".gov.in") || host == "gov.in" ||
                host.endsWith(".nic.in") || host == "nic.in" ||
                allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    fun openInBrowser(urlToOpen: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply {
                setPackage("com.android.chrome")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)))
            } catch (_: Exception) {
                // Ignore if browser not found
            }
        }
    }

    var currentUrl by remember { mutableStateOf(targetUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var capturedRecord by remember { mutableStateOf<EInvoiceStore.Record?>(null) }
    var capturedError by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingFileChooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        pendingFileChooser?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        pendingFileChooser = null
    }

    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            navController.popBackStack()
        }
    }

    fun handleCapturedText(text: String) {
        if (text.isBlank()) return
        val rec = EInvoiceStore.parseReply(text, wantedDocNo)
        if (rec != null) {
            capturedRecord = rec
            capturedError = null
            statusMsg = "Captured IRN from government portal."
            return
        }
        val err = EInvoiceStore.findError(text, wantedDocNo)
        if (err != null) {
            capturedError = err
            capturedRecord = null
            statusMsg = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "e-Invoice Portal (Govt)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl.replace("https://", "").take(32) + if (currentUrl.length > 40) "..." else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webViewRef
                        if (wv != null && wv.canGoBack()) wv.goBack() else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { openInBrowser(webViewRef?.url ?: currentUrl) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Chrome")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Quick Navigation Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { webViewRef?.loadUrl("https://einvoice1.gst.gov.in/Account/Login") },
                    label = { Text("🔑 Login Page", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = { webViewRef?.loadUrl("https://einvoice1.gst.gov.in/eInvoice/BulkUpload") },
                    label = { Text("📤 Bulk Upload", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = { webViewRef?.loadUrl("https://einvoice1.gst.gov.in/") },
                    label = { Text("🏠 Portal Home", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = { webViewRef?.loadUrl("https://einvoice2.gst.gov.in/Account/Login") },
                    label = { Text("🔄 Server 2 (Backup)", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = { openInBrowser(webViewRef?.url ?: currentUrl) },
                    label = { Text("🌐 Open in Chrome", fontSize = 12.sp) }
                )
            }

            if (isLoading && progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color(0xFF2E7D32)
                )
            }

            if (attachFileName != null && capturedRecord == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1B5E20).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ready: \"$attachFileName\" will attach automatically when the portal asks you to choose a file.",
                            fontSize = 12.sp,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            capturedRecord?.let { rec ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(Modifier.width(8.dp))
                            Text("IRN Captured Successfully", fontWeight = FontWeight.Bold)
                        }
                        Text("IRN: ${rec.irn}", fontSize = 12.sp)
                        if (saved) {
                            Text("✓ Saved to this invoice.", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else if (companyId != null && voucherId != null) {
                            Button(onClick = {
                                EInvoiceStore.save(companyId, voucherId, rec, context)
                                EInvoiceStore.setEnabled(companyId, true, context)
                                saved = true
                                navController.previousBackStackEntry?.savedStateHandle?.set("portal_capture_done", true)
                            }) { Text("Save to Invoice") }
                        } else {
                            Text("Copy this IRN - no invoice was attached to this session.", fontSize = 12.sp)
                        }
                    }
                }
            }

            capturedError?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.12f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFC62828))
                            Spacer(Modifier.width(8.dp))
                            Text("Government Portal Rejection", fontWeight = FontWeight.Bold)
                        }
                        Text(err, fontSize = 12.sp)
                    }
                }
            }

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // Remove "; wv" so government servers treat it like standard desktop/mobile Chrome
                            userAgentString = userAgentString.replace("; wv", "")
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                isLoading = newProgress < 100
                                view?.url?.let { currentUrl = it }
                            }

                            override fun onShowFileChooser(
                                view: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                params: FileChooserParams?
                            ): Boolean {
                                val uriStr = attachUriStr
                                if (uriStr != null && filePathCallback != null) {
                                    try {
                                        val uri = Uri.parse(uriStr)
                                        ctx.grantUriPermission(ctx.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        filePathCallback.onReceiveValue(arrayOf(uri))
                                        statusMsg = "Auto-attached $attachFileName"
                                        return true
                                    } catch (_: Exception) {
                                        // Fall through to the system picker
                                    }
                                }
                                return try {
                                    val intent = params?.createIntent()
                                    if (intent != null && filePathCallback != null) {
                                        pendingFileChooser = filePathCallback
                                        fileChooserLauncher.launch(intent)
                                        true
                                    } else {
                                        filePathCallback?.onReceiveValue(null)
                                        false
                                    }
                                } catch (_: Exception) {
                                    filePathCallback?.onReceiveValue(null)
                                    false
                                }
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val newWebView = WebView(view!!.context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            webViewRef?.loadUrl(url)
                                            return true
                                        }
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = newWebView
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url ?: return false
                                if (!isAllowedHost(url.host)) {
                                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) { }
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let { currentUrl = it }
                                CookieManager.getInstance().flush()
                            }
                        }

                        setDownloadListener(DownloadListener { url, _, _, _, _ ->
                            Thread {
                                try {
                                    val cookie = CookieManager.getInstance().getCookie(url)
                                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                    if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
                                    connection.connectTimeout = 15000
                                    connection.readTimeout = 15000
                                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                                    (ctx as? android.app.Activity)?.runOnUiThread { handleCapturedText(text) }
                                } catch (_: Exception) {
                                    (ctx as? android.app.Activity)?.runOnUiThread {
                                        statusMsg = "Couldn't read the portal's reply file automatically."
                                    }
                                }
                            }.start()
                        })

                        loadUrl(targetUrl)
                        webViewRef = this
                    }
                }
            )

            statusMsg?.let { msg ->
                Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        webViewRef?.evaluateJavascript("document.body.innerText") { jsResult ->
                            val text = try {
                                (JSONTokener(jsResult).nextValue() as? String) ?: jsResult
                            } catch (_: Exception) { jsResult }
                            handleCapturedText(text)
                            if (capturedRecord == null && capturedError == null) {
                                statusMsg = "Nothing that looked like an IRN or an error was found on this page."
                            }
                        }
                    }
                ) { Text("Capture this page") }

                OutlinedButton(modifier = Modifier.weight(1f), onClick = { webViewRef?.reload() }) {
                    Text("Reload")
                }
            }
        }
    }
}
