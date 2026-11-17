package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.DashboardPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceTemplateSelectionScreen(navController: NavController) {
    val context = LocalContext.current
    var currentTemplate by remember { mutableStateOf(DashboardPrefs.getInvoiceTemplate(context)) }
    var selectedPreviewTemplate by remember { mutableStateOf<DashboardPrefs.InvoiceTemplate?>(null) }

    if (selectedPreviewTemplate != null) {
        Dialog(onDismissRequest = { selectedPreviewTemplate = null }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        selectedPreviewTemplate!!.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TemplateFullPreview(selectedPreviewTemplate!!)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedPreviewTemplate = null },
                            modifier = Modifier.weight(1f)
                        ) { Text(TerminologyManager.getTerm("Cancel")) }
                        Button(
                            onClick = {
                                currentTemplate = selectedPreviewTemplate!!
                                DashboardPrefs.setInvoiceTemplate(context, currentTemplate)
                                selectedPreviewTemplate = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(TerminologyManager.getTerm("Apply")) }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Invoice Templates") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(12.dp)
        ) {
            Text(TerminologyManager.getTerm("Tap a template to preview, then apply"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(DashboardPrefs.InvoiceTemplate.values().toList()) { template ->
                    TemplateThumbnailCard(
                        template = template,
                        isSelected = currentTemplate == template,
                        onClick = { selectedPreviewTemplate = template }
                    )
                }
            }
        }
    }
}

@Composable
fun TemplateContentMockup(template: DashboardPrefs.InvoiceTemplate, scale: Float = 1f) {
    val pad = (8 * scale).dp
    when (template) {
        DashboardPrefs.InvoiceTemplate.BLUE_YELLOW -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A39A3))
            ) {
                Column(
                    modifier = Modifier
                        .weight(3f)
                        .padding(pad),
                    verticalArrangement = Arrangement.spacedBy((3 * scale).dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height((10 * scale).dp)
                            .background(Color(0xFFDCEB3C), RoundedCornerShape((1 * scale).dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height((5 * scale).dp)
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.height((6 * scale).dp))
                    repeat(3) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy((3 * scale).dp)
                        ) {
                            Box(Modifier.weight(3f).height((3 * scale).dp).background(Color.White.copy(alpha = 0.5f)))
                            Box(Modifier.weight(1f).height((3 * scale).dp).background(Color.White.copy(alpha = 0.5f)))
                        }
                    }
                    Spacer(Modifier.height((6 * scale).dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier
                                .width((40 * scale).dp)
                                .height((8 * scale).dp)
                                .background(Color(0xFFDCEB3C), RoundedCornerShape((1 * scale).dp))
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((12 * scale).dp)
                            .background(Color(0xFFF5F5D7), RoundedCornerShape((2 * scale).dp))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(Color(0xFFDCEB3C))
                )
            }
        }
        DashboardPrefs.InvoiceTemplate.MAROON_SIDEBAR -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1.2f)
                        .background(Color(0xFF78141E))
                        .padding(pad)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().height((3 * scale).dp).background(Color(0xFFCDAAAD)))
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth().height((4 * scale).dp).background(Color.White))
                        Box(Modifier.fillMaxWidth().height((4 * scale).dp).background(Color.White))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(2.8f)
                        .padding(pad),
                    verticalArrangement = Arrangement.spacedBy((3 * scale).dp)
                ) {
                    Box(
                        Modifier
                            .size((16 * scale).dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF78141E))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height((3 * scale).dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((6 * scale).dp)
                            .background(Color(0xFFCD5A32))
                    )
                    repeat(3) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((2 * scale).dp)) {
                            Box(Modifier.weight(2f).height((4 * scale).dp).background(Color(0xFF78141E)))
                            Box(Modifier.weight(1f).height((4 * scale).dp).background(Color(0xFFE0E0E0)))
                            Box(Modifier.weight(1f).height((4 * scale).dp).background(Color(0xFFE0E0E0)))
                        }
                    }
                    Spacer(Modifier.height((6 * scale).dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height((12 * scale).dp)
                            .background(Color(0xFFCD5A32))
                            .align(Alignment.End)
                    )
                }
            }
        }
        DashboardPrefs.InvoiceTemplate.BLACK_ORANGE -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy((4 * scale).dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy((2 * scale).dp)) {
                        Box(Modifier.width((30 * scale).dp).height((6 * scale).dp).background(Color(0xFFCD6E3C)))
                        Box(Modifier.width((20 * scale).dp).height((4 * scale).dp).background(Color.LightGray))
                    }
                    Box(
                        Modifier
                            .width((45 * scale).dp)
                            .height((18 * scale).dp)
                            .background(Color(0xFF282828))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight()
                                .background(Color(0xFFCD6E3C).copy(alpha = 0.6f))
                        )
                    }
                }
                Spacer(Modifier.height((3 * scale).dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((6 * scale).dp)
                        .background(Color(0xFF282828))
                )
                repeat(4) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((4 * scale).dp)
                            .background(Color(0xFFF0F0F0))
                    )
                }
                Spacer(Modifier.height((4 * scale).dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(Modifier.width((40 * scale).dp).height((15 * scale).dp).background(Color(0xFFF0F0F0)))
                    Column(verticalArrangement = Arrangement.spacedBy((2 * scale).dp)) {
                        Box(Modifier.width((35 * scale).dp).height((5 * scale).dp).background(Color.LightGray))
                        Box(Modifier.width((35 * scale).dp).height((6 * scale).dp).background(Color(0xFF282828)))
                    }
                }
            }
        }
        DashboardPrefs.InvoiceTemplate.TEAL_CLEAN -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((18 * scale).dp)
                        .background(Color(0xFF284B55))
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(pad),
                    verticalArrangement = Arrangement.spacedBy((3 * scale).dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((12 * scale).dp)
                            .background(Color(0xFFECEFF1))
                    )
                    Spacer(Modifier.height((2 * scale).dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((6 * scale).dp)
                            .background(Color(0xFF284B55))
                    )
                    repeat(3) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.width((60 * scale).dp).height((1 * scale).dp).background(Color.LightGray))
                            Box(Modifier.width((20 * scale).dp).height((1 * scale).dp).background(Color.LightGray))
                        }
                        Spacer(Modifier.height((3 * scale).dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(Modifier.width((35 * scale).dp).height((10 * scale).dp).background(Color(0xFFECEFF1)))
                        Box(Modifier.width((40 * scale).dp).height((12 * scale).dp).background(Color(0xFF284B55)))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((4 * scale).dp)
                        .background(Color(0xFF284B55))
                )
            }
        }
        // ── Mockups for the business-oriented layouts ────────────────────────
        DashboardPrefs.InvoiceTemplate.GST_CLASSIC -> {
            // Bordered GST invoice book: boxed header, dense ruled grid, declaration + sign boxes.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .border((1 * scale).dp, Color(0xFF333333))
                    .padding(pad / 2),
                verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((7 * scale).dp)
                        .border((0.7 * scale).dp, Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.fillMaxWidth(0.35f).height((2 * scale).dp).background(Color(0xFF333333)))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border((0.7 * scale).dp, Color(0xFF777777))
                        .padding((2 * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((1.5 * scale).dp)
                ) {
                    Box(Modifier.fillMaxWidth(0.6f).height((2.5 * scale).dp).background(Color(0xFF444444)))
                    Box(Modifier.fillMaxWidth(0.45f).height((1.5 * scale).dp).background(Color(0xFFBBBBBB)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((2 * scale).dp)) {
                    Box(
                        Modifier
                            .weight(1.3f)
                            .height((14 * scale).dp)
                            .border((0.7 * scale).dp, Color(0xFF777777))
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height((14 * scale).dp)
                            .border((0.7 * scale).dp, Color(0xFF777777))
                    )
                }
                Box(Modifier.fillMaxWidth().height((4 * scale).dp).background(Color(0xFFE8E8E8)))
                repeat(5) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((1 * scale).dp)) {
                        repeat(6) { col ->
                            Box(
                                Modifier
                                    .weight(if (col == 1) 2.4f else 1f)
                                    .height((3 * scale).dp)
                                    .border((0.4 * scale).dp, Color(0xFFCCCCCC))
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height((10 * scale).dp)
                            .border((0.7 * scale).dp, Color(0xFF333333))
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((2 * scale).dp)) {
                    Box(
                        Modifier
                            .weight(1.4f)
                            .height((13 * scale).dp)
                            .border((0.7 * scale).dp, Color(0xFF777777))
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height((13 * scale).dp)
                            .border((0.7 * scale).dp, Color(0xFF777777))
                    )
                }
            }
        }
        DashboardPrefs.InvoiceTemplate.MINIMAL_MONO -> {
            // No fills anywhere — hairlines and whitespace only.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy((3 * scale).dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.width((34 * scale).dp).height((4 * scale).dp).background(Color(0xFF222222)))
                    Box(Modifier.width((26 * scale).dp).height((8 * scale).dp).background(Color(0xFFDDDDDD)))
                }
                Box(Modifier.fillMaxWidth().height((0.8 * scale).dp).background(Color(0xFF222222)))
                Spacer(Modifier.height((4 * scale).dp))
                Box(Modifier.width((28 * scale).dp).height((3 * scale).dp).background(Color(0xFF555555)))
                Spacer(Modifier.height((6 * scale).dp))
                repeat(5) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(3f).height((1.5 * scale).dp).background(Color(0xFFCCCCCC)))
                        Spacer(Modifier.width((6 * scale).dp))
                        Box(Modifier.weight(1f).height((1.5 * scale).dp).background(Color(0xFFCCCCCC)))
                    }
                    Spacer(Modifier.height((3 * scale).dp))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                    ) {
                        Box(Modifier.width((40 * scale).dp).height((1.5 * scale).dp).background(Color(0xFFCCCCCC)))
                        Box(Modifier.width((40 * scale).dp).height((0.8 * scale).dp).background(Color(0xFF222222)))
                        Box(Modifier.width((40 * scale).dp).height((3.5 * scale).dp).background(Color(0xFF222222)))
                    }
                }
            }
        }
        DashboardPrefs.InvoiceTemplate.ROYAL_INDIGO -> {
            // Indigo brand block + tinted title block, gold rule, zebra rows.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                Row(Modifier.fillMaxWidth().height((26 * scale).dp)) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF312E81))
                            .padding(pad / 2),
                        verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                    ) {
                        Box(Modifier.fillMaxWidth(0.75f).height((4 * scale).dp).background(Color.White))
                        repeat(2) {
                            Box(Modifier.fillMaxWidth(0.6f).height((1.5 * scale).dp).background(Color(0xFFBEBCE8)))
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F0FA))
                            .padding(pad / 2),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                    ) {
                        Box(Modifier.width((30 * scale).dp).height((7 * scale).dp).background(Color(0xFF312E81)))
                        Box(Modifier.width((22 * scale).dp).height((1.5 * scale).dp).background(Color(0xFF9A98C8)))
                        Box(Modifier.width((26 * scale).dp).height((3 * scale).dp).background(Color(0xFF312E81)))
                    }
                }
                Box(Modifier.fillMaxWidth().height((2.5 * scale).dp).background(Color(0xFFBF9B30)))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(pad),
                    verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                ) {
                    Box(Modifier.fillMaxWidth().height((5 * scale).dp).background(Color(0xFF312E81)))
                    repeat(4) { idx ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((4 * scale).dp)
                                .background(if (idx % 2 == 0) Color.White else Color(0xFFF0F0FA))
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((3 * scale).dp)) {
                        Box(Modifier.weight(1f).height((14 * scale).dp).background(Color(0xFFF7F7FC)))
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                        ) {
                            Box(Modifier.fillMaxWidth().height((3 * scale).dp).background(Color(0xFFE5E5F2)))
                            Box(Modifier.fillMaxWidth().height((3 * scale).dp).background(Color(0xFFE5E5F2)))
                            Box(Modifier.fillMaxWidth().height((7 * scale).dp).background(Color(0xFF312E81)))
                        }
                    }
                }
            }
        }
        DashboardPrefs.InvoiceTemplate.GREEN_RETAIL -> {
            // Counter bill: green header, zebra rows, QR block, big TO PAY chip, thank-you strip.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                Row(Modifier.fillMaxWidth().height((22 * scale).dp)) {
                    Column(
                        Modifier
                            .weight(1.6f)
                            .fillMaxHeight()
                            .background(Color(0xFF168055))
                            .padding(pad / 2),
                        verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                    ) {
                        Box(Modifier.fillMaxWidth(0.8f).height((5 * scale).dp).background(Color.White))
                        Box(Modifier.fillMaxWidth(0.55f).height((1.5 * scale).dp).background(Color(0xFFBFE4D2)))
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFD6F0E1))
                            .padding(pad / 2),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                    ) {
                        Box(Modifier.width((22 * scale).dp).height((8 * scale).dp).background(Color(0xFF168055)))
                        Box(Modifier.width((26 * scale).dp).height((1.5 * scale).dp).background(Color(0xFF6FA98D)))
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(pad),
                    verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                ) {
                    Box(Modifier.fillMaxWidth().height((5 * scale).dp).background(Color(0xFF168055)))
                    repeat(4) { idx ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((4 * scale).dp)
                                .background(if (idx % 2 == 0) Color.White else Color(0xFFD6F0E1))
                        )
                    }
                    Spacer(Modifier.height((3 * scale).dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((3 * scale).dp)) {
                        Box(
                            Modifier
                                .size((16 * scale).dp)
                                .background(Color(0xFFEFEFEF))
                                .border((1 * scale).dp, Color(0xFF168055))
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
                        ) {
                            Box(Modifier.fillMaxWidth().height((3 * scale).dp).background(Color(0xFFE0E0E0)))
                            Box(Modifier.fillMaxWidth().height((9 * scale).dp).background(Color(0xFF168055)))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((7 * scale).dp)
                            .background(Color(0xFF168055))
                    )
                }
            }
        }
        DashboardPrefs.InvoiceTemplate.LETTERHEAD -> {
            // The top third stays empty: that space belongs to the shop's printed letterhead.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy((2 * scale).dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((34 * scale).dp)
                        .border((0.7 * scale).dp, Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.fillMaxWidth(0.4f).height((1.2 * scale).dp).background(Color(0xFFE8E8E8)))
                }
                Box(Modifier.fillMaxWidth(0.3f).height((3 * scale).dp).background(Color(0xFF333333)).align(Alignment.CenterHorizontally))
                Box(Modifier.fillMaxWidth().height((0.8 * scale).dp).background(Color(0xFF333333)))
                Spacer(Modifier.height((2 * scale).dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.width((30 * scale).dp).height((3 * scale).dp).background(Color(0xFF555555)))
                    Box(Modifier.width((24 * scale).dp).height((3 * scale).dp).background(Color(0xFF999999)))
                }
                Spacer(Modifier.height((2 * scale).dp))
                repeat(5) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((1 * scale).dp)) {
                        repeat(4) { col ->
                            Box(
                                Modifier
                                    .weight(if (col == 0) 2.6f else 1f)
                                    .height((3.5 * scale).dp)
                                    .border((0.5 * scale).dp, Color(0xFF888888))
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.48f)
                            .height((9 * scale).dp)
                            .border((0.7 * scale).dp, Color(0xFF333333))
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.width((34 * scale).dp).height((2 * scale).dp).background(Color(0xFF999999)))
                }
            }
        }
    }
}

@Composable
fun TemplateThumbnailCard(
    template: DashboardPrefs.InvoiceTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth = if (isSelected) 3.dp else 0.dp
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(Modifier.weight(1f)) {
                TemplateContentMockup(template, scale = 0.7f)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Text(
                text = template.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TemplateFullPreview(template: DashboardPrefs.InvoiceTemplate) {
    Card(
        modifier = Modifier
            .aspectRatio(0.707f)
            .fillMaxHeight(0.85f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        TemplateContentMockup(template, scale = 1.0f)
    }
}
