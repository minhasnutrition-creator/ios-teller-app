package com.minhas.teller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.automirrored.filled.HelpOutline

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    isLightTheme: Boolean = false,
    height: androidx.compose.ui.unit.Dp? = null
) {
    val bgColor = if (isLightTheme) Color(0xFF32194D).copy(alpha = 0.08f) else Color(0x1AFFFFFF)
    val txtColor = if (isLightTheme) Color(0xFF32194D) else Color.White
    val placeholderColor = if (isLightTheme) Color(0xFF32194D).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    val cursorColor = if (isLightTheme) Color(0xFF32194D) else Color.White

    Box(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier.height(56.dp))
            .background(
                color = bgColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = placeholderColor,
                fontSize = 16.sp
            )
        }
        
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = txtColor,
                fontSize = 16.sp
            ),
            keyboardOptions = keyboardOptions,
            readOnly = readOnly,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (trailingIcon != null) 32.dp else 0.dp),
             cursorBrush = androidx.compose.ui.graphics.SolidColor(cursorColor)
        )

        if (trailingIcon != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun JoinedInputButton(
    value: String,
    onValueChange: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false
) {
    Row(modifier = modifier.height(56.dp)) {
        ModernTextField(
            value = value,
            onValueChange = { if (!isReadOnly) onValueChange(it) },
            placeholder = "Amount",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            readOnly = isReadOnly
        )
        // ... (Button remains same)
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White)
        }
    }
}

@Composable
fun SummaryCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF1A1A2E),
    indicatorColor: Color? = null,
    containerColor: Color = Color(0xFFF0F0F0),
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    borderColor: Color = Color.Transparent
) {
    val cleanAmount = amount.replace("₹", "")
    val amtFontSize = when {
        cleanAmount.length > 12 -> 11.sp
        cleanAmount.length > 9 -> 13.sp
        cleanAmount.length > 6 -> 14.sp
        else -> 16.sp
    }
    Card(
        modifier = modifier.then(
            if (borderColor != Color.Transparent)
                Modifier.border(2.dp, borderColor, shape)
            else Modifier
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(vertical = 14.dp, horizontal = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AutoShrinkText(
                    text = label,
                    color = textColor.copy(alpha = 0.6f),
                    maxFontSize = 10.sp,
                    minFontSize = 7.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                AutoShrinkText(
                    text = amount,
                    color = textColor,
                    maxFontSize = amtFontSize,
                    minFontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 10.dp)
                        .width(36.dp)
                        .height(5.dp)
                        .background(indicatorColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
fun ModernHeader(
    title: String,
    onCameraClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoShrinkText(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onCameraClick) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color.White
                )
            }
            IconButton(onClick = onHomeClick) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ModernDropdownField(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    showDropdownIcon: Boolean = true,
    isLightTheme: Boolean = false
) {
    val bgColor = if (isLightTheme) Color(0xFF32194D).copy(alpha = 0.08f) else Color(0x1AFFFFFF)
    val txtColor = if (isLightTheme) Color(0xFF32194D) else Color.White
    val placeholderColor = if (isLightTheme) Color(0xFF32194D).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    val iconColor = if (isLightTheme) Color(0xFF32194D) else Color.White

    Box(
        modifier = modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AutoShrinkText(
            text = if (text.isNotEmpty()) text else placeholder,
            color = if (text.isNotEmpty()) txtColor else placeholderColor,
            maxFontSize = 16.sp,
            modifier = Modifier.padding(end = if (showDropdownIcon) 24.dp else 0.dp)
        )
        if (showDropdownIcon) {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
fun JoinedLedgerSelection(
    selectedName: String?,
    onSelectionClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select Mode",
    showDropdownIcon: Boolean = true
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModernDropdownField(
            text = selectedName ?: "",
            onClick = onSelectionClick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            placeholder = placeholder,
            showDropdownIcon = showDropdownIcon
        )
    }
}

@Composable
fun ModernSectionLabel(
    text: String,
    onHelpClick: (() -> Unit)? = null,
    textColor: Color = Color.White,
    helpIconTint: Color = Color.LightGray,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoShrinkText(
            text = text.uppercase(),
            color = textColor,
            maxFontSize = fontSize,
            minFontSize = (fontSize.value * 0.7f).sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
        if (onHelpClick != null) {
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Help",
                    tint = helpIconTint
                )
            }
        }
    }
}

@Composable
fun ModernInventoryRow(
    itemName: String,
    onItemClick: () -> Unit,
    qty: String,
    onQtyChange: (String) -> Unit,
    rate: String,
    onRateChange: (String) -> Unit,
    amount: Double,
    onDeleteClick: () -> Unit,
    showRate: Boolean = true,
    onAmountChange: ((String) -> Unit)? = null,
    showDeleteIcon: Boolean = true
) {
    // Every field box in this row shares one fixed height so they all line up
    // on a straight baseline regardless of label/content differences.
    val rowHeight = 52.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ITEM Button (Clickable Box) — widened to take more of the row
            Box(
                modifier = Modifier
                    .weight(2.05f)
                    .height(rowHeight)
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp))
                    .clickable { onItemClick() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AutoShrinkText(
                    text = itemName.ifEmpty { "Select Item" },
                    color = if (itemName.isNotEmpty()) Color(0xFF37474F) else Color(0xFF90A4AE),
                    maxFontSize = if (itemName.isEmpty()) 15.sp else 14.sp,
                    fontWeight = if (itemName.isEmpty()) FontWeight.Bold else FontWeight.Medium
                )
            }

            // QTY Input (narrowed and slightly smaller text to make room for the item box)
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(rowHeight)
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = qty,
                    onValueChange = onQtyChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF37474F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (qty.isEmpty()) {
                    Text("0", color = Color(0xFF90A4AE), fontSize = 12.sp)
                }
            }

            // RATE Input (hidden when showRate = false)
            if (showRate) Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(rowHeight)
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = rate,
                    onValueChange = onRateChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF37474F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (rate.isEmpty()) {
                    Text("0.00", color = Color(0xFF90A4AE), fontSize = 14.sp)
                }
            }

            // AMOUNT (read-only, Qty x Rate) — label sits directly on top of its own box only,
            // no wider than the box itself.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AutoShrinkText(
                    text = "AMOUNT",
                    color = Color(0xFF37474F).copy(alpha = 0.6f),
                    maxFontSize = if (showRate) 9.sp else 11.sp,
                    minFontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(if (showRate) 100.dp else 124.dp)
                        .height(rowHeight)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                        .border(1.5.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (onAmountChange != null) {
                        // Editable amount (used when the rate box is hidden)
                        var amountText by remember(amount) {
                            mutableStateOf(
                                if (amount == 0.0) ""
                                else String.format("%.2f", amount).trimEnd('0').trimEnd('.')
                            )
                        }
                        // Auto-shrink font as the number of digits grows so even large
                        // amounts (e.g. 1000000000) stay inside the box on one line.
                        val digitCount = amountText.ifEmpty { "0.00" }.length
                        val amountFontSize = when {
                            digitCount <= 5 -> if (showRate) 15.sp else 18.sp
                            digitCount <= 7 -> if (showRate) 13.sp else 16.sp
                            digitCount <= 9 -> if (showRate) 11.sp else 14.sp
                            digitCount <= 11 -> if (showRate) 10.sp else 12.sp
                            else -> if (showRate) 9.sp else 10.sp
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "₹",
                                color = Color(0xFF37474F),
                                fontSize = amountFontSize,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.weight(1f)
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = amountText,
                                    onValueChange = {
                                        amountText = it
                                        onAmountChange(it)
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color(0xFF37474F),
                                        fontSize = amountFontSize,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                if (amountText.isEmpty()) {
                                    Text(
                                        "0.00",
                                        color = Color(0xFF90A4AE),
                                        fontSize = (amountFontSize.value + 2).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        val digitCount = String.format("%.2f", amount).length
                        val amountFontSize = when {
                            digitCount <= 5 -> 14.sp
                            digitCount <= 7 -> 13.sp
                            digitCount <= 9 -> 12.sp
                            digitCount <= 11 -> 10.sp
                            else -> 9.sp
                        }
                        AutoShrinkText(
                            text = "₹${String.format("%.2f", amount)}",
                            color = Color(0xFF37474F),
                            maxFontSize = amountFontSize,
                            minFontSize = 6.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Delete Icon — shifted below the row, aligned under its own end so it no longer
        // crowds the item/qty/amount boxes on the main line. Uses a plain clickable Box
        // instead of IconButton so it doesn't reserve Material's invisible 48dp minimum
        // touch target, which was the main source of the large empty gap below the row.
        // Only rendered once a 2nd item exists - a single item can't be removed anyway
        // (a voucher always needs at least one), so showing it there was just wasted space.
        if (showDeleteIcon) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onDeleteClick)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun PaymentTypeCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColorOverride: Color? = null,
    gradientTopSelected: Color = Color(0xFF32194D).copy(alpha = 0.15f),
    gradientBottomSelected: Color = Color(0xFF32194D).copy(alpha = 0.05f),
    gradientTopUnselected: Color = Color.White.copy(alpha = 0.7f),
    gradientBottomUnselected: Color = Color.White.copy(alpha = 0.7f),
    borderColorSelected: Color = Color(0xFF32194D).copy(alpha = 0.3f),
    borderColorUnselected: Color = Color(0xFF32194D).copy(alpha = 0.08f),
    cardHeight: androidx.compose.ui.unit.Dp = 96.dp,
    textColor: Color = Color(0xFF32194D)
) {
    // Detect if this is a dark-mode style card (Money In/Out brown buttons or Sales teal buttons)
    val isDarkStyle = (gradientTopUnselected.red < 0.5f && gradientTopUnselected.green < 0.4f && gradientTopUnselected.blue < 0.3f) ||
                       (gradientTopUnselected.red < 0.1f && gradientTopUnselected.green < 0.6f && gradientTopUnselected.blue < 0.5f) ||
                       (textColor == Color.White)

    if (isDarkStyle) {
        // 3D dark brown/teal button matching reference screenshots exactly
        val cardBg = if (isSelected)
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gradientTopSelected, gradientBottomSelected))
        else
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gradientTopUnselected, gradientBottomUnselected))

        val borderColor = if (isSelected) borderColorSelected else borderColorUnselected

        Box(
            modifier = modifier
                .height(cardHeight)
                // Draw shadow first (before clipping) so it doesn't get clipped
                .drawBehind {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.28f),
                        topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 4.dp.toPx()),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(if (isSelected) 2.2.dp else 1.2.dp, borderColor, RoundedCornerShape(20.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Top highlight strip — creates the 3D raised look
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFFFD54F).copy(alpha = 0.5f),
                                Color(0xFFFFE082).copy(alpha = 0.8f),
                                Color(0xFFFFD54F).copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColorOverride ?: Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AutoShrinkText(
                    text = label,
                    color = Color.White,
                    maxFontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Standard light-theme payment card (Sales screen)
        val bgBrush = if (isSelected) {
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gradientTopSelected, gradientBottomSelected))
        } else {
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gradientTopUnselected, gradientBottomUnselected))
        }
        val borderColor = if (isSelected) borderColorSelected else borderColorUnselected
        val borderStroke = androidx.compose.foundation.BorderStroke(1.dp, borderColor)

        Card(
            modifier = modifier
                .height(cardHeight)
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            border = borderStroke,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgBrush)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconColorOverride ?: Color(0xFF32194D),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AutoShrinkText(
                        text = label,
                        color = textColor,
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun MoneyStepCard(
    stepNumber: Int,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF0D488), Color(0xFFB8860B)))),
                contentAlignment = Alignment.Center
            ) {
                AutoShrinkText(text = stepNumber.toString(), color = Color(0xFF2A1B12), fontWeight = FontWeight.ExtraBold, maxFontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            AutoShrinkText(
                text = title,
                color = Color(0xFF32194D),
                fontWeight = FontWeight.ExtraBold,
                maxFontSize = 15.sp,
                letterSpacing = 0.3.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF32194D).copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Color(0xFF32194D).copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(start = 38.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun CreamDropdownField(
    text: String,
    onClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .debossedCapsule(cornerRadius = 12.dp, backgroundColor = Color(0xFFD6E2EC))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AutoShrinkText(
            text = text.ifEmpty { placeholder },
            color = if (text.isNotEmpty()) Color(0xFF37474F) else Color(0xFF78909C),
            maxFontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 24.dp)
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = Color(0xFF78909C),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun CreamAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .debossedCapsule(cornerRadius = 12.dp, backgroundColor = Color(0xFFD6E2EC))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = Color(0xFF78909C), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { new -> if (!readOnly && (new.isEmpty() || new.all { it.isDigit() || it == '.' })) onValueChange(new) },
            readOnly = readOnly,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF37474F), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF37474F)),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Modifier.debossedCapsule(
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
    backgroundColor: Color = Color(0xFFCBD5E1),
    shadowColor: Color = Color.Black.copy(alpha = 0.28f),
    highlightColor: Color = Color.White.copy(alpha = 0.75f)
): Modifier = this.drawBehind {
    val W = this@drawBehind.size.width
    val H = this@drawBehind.size.height
    val r = cornerRadius.toPx()
    val sw = strokeWidth.toPx()
    val inset = sw / 2f

    // 1. Draw the sunken pocket background (slightly darker/cooler gray/blue)
    drawRoundRect(
        color = backgroundColor,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
    )

    // 2. Draw Top-Left Inner Shadow Path (dark shadow)
    val shadowPath = Path().apply {
        // Bottom-left corner (from 135 to 180 degrees)
        arcTo(
            rect = Rect(
                left = inset,
                top = H - inset - 2 * r,
                right = inset + 2 * r,
                bottom = H - inset
            ),
            startAngleDegrees = 135f,
            sweepAngleDegrees = 45f,
            forceMoveTo = true
        )
        // Top-left corner (from 180 to 270 degrees)
        arcTo(
            rect = Rect(
                left = inset,
                top = inset,
                right = inset + 2 * r,
                bottom = inset + 2 * r
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Top-right corner (from 270 to 315 degrees)
        arcTo(
            rect = Rect(
                left = W - inset - 2 * r,
                top = inset,
                right = W - inset,
                bottom = inset + 2 * r
            ),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 45f,
            forceMoveTo = false
        )
    }

    // 3. Draw Bottom-Right Highlight Path (white highlight)
    val highlightPath = Path().apply {
        // Top-right corner (from 315 to 360/0 degrees)
        arcTo(
            rect = Rect(
                left = W - inset - 2 * r,
                top = inset,
                right = W - inset,
                bottom = inset + 2 * r
            ),
            startAngleDegrees = 315f,
            sweepAngleDegrees = 45f,
            forceMoveTo = true
        )
        // Bottom-right corner (from 0 to 90 degrees)
        arcTo(
            rect = Rect(
                left = W - inset - 2 * r,
                top = H - inset - 2 * r,
                right = W - inset,
                bottom = H - inset
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Bottom-left corner (from 90 to 135 degrees)
        arcTo(
            rect = Rect(
                left = inset,
                top = H - inset - 2 * r,
                right = inset + 2 * r,
                bottom = H - inset
            ),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 45f,
            forceMoveTo = false
        )
    }

    // Draw the paths
    drawPath(
        path = shadowPath,
        color = shadowColor,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
    drawPath(
        path = highlightPath,
        color = highlightColor,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
}
