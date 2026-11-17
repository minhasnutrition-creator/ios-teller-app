package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.minhas.teller.ui.components.AutoShrinkText

/**
 * In-app WebView for the GST portal so the owner files without leaving the app. Keeps GST hosts
 * in the WebView and opens every other host (banks, payment/3DS gateways) in the external browser,
 * since those frequently fail inside an embedded WebView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GstPortalWebViewScreen(
    navController: NavHostController,
    url: String,
    title: String
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    fun handleBack() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else navController.popBackStack()
    }

    fun buildClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val target = request?.url?.toString() ?: return false
            return if (isAllowedHost(target)) false
            else {
                try {
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                } catch (_: Exception) {
                }
                true
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            view?.let { canGoBack = it.canGoBack() }
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            view?.let { canGoBack = it.canGoBack() }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) loadFailed = true
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            loadFailed = true
            return true
        }
    }

    BackHandler(enabled = true) { handleBack() }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(title, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1A12))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F1A12), Color(0xFF060D40))))
                .padding(padding)
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF69F0AE)
                )
            }

            Box(Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    // JS window.open(): give it a child WebView with the SAME
                                    // allow-list client so non-GST popups still get externalized.
                                    val newWebView = WebView(view!!.context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webViewClient = buildClient()
                                    }
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = newWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }
                            }

                            webViewClient = buildClient()
                            loadUrl(url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (loadFailed) {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(TerminologyManager.getTerm("Couldn't load the GST portal"), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(TerminologyManager.getTerm("Check your internet connection and try again."), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    loadFailed = false
                                    webView?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
                            ) { Text(TerminologyManager.getTerm("Retry"), color = Color(0xFF0F1A12), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

/** Stay in-app for GST hosts, externalize everything else. */
private fun isAllowedHost(url: String): Boolean {
    val host = Uri.parse(url).host?.lowercase() ?: return false
    return host == "gst.gov.in" || host.endsWith(".gst.gov.in") ||
        host == "gstn.org.in" || host.endsWith(".gstn.org.in") ||
        host == "gstsystem.in" || host.endsWith(".gstsystem.in")
}
