package com.minhas.teller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Cosmic / high-tech dark glassmorphism palette. Kept here as a single source of
// truth so the About screen and any future glass surfaces stay consistent.
// ─────────────────────────────────────────────────────────────────────────────

/** Soft metallic gold — used for section titles. */
val CosmicGold = Color(0xFFE6C280)

/** Electric cyan — primary accent / highlights. */
val CosmicCyan = Color(0xFF00F2FE)

/** Lavender — companion tone for the cyan→lavender brand gradient. */
val CosmicLavender = Color(0xFFB388FF)

/** Muted silver — body labels / secondary text. */
val CosmicSilver = Color(0xFFB8C4D9)

/** Soft muted gold/silver — muted brand text (e.g. version label). */
val CosmicSilverGold = Color(0xFFC5B085)

/** Card surface — rgba(15, 25, 45, 0.65). */
val CosmicCardBackground = Color(0xFF0F192D)

/** Card border — rgba(120, 180, 255). */
val CosmicCardBorder = Color(0xFF78B4FF)

/** Copper / rose-gold metallic CTA gradient. */
val CopperRoseGoldGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFA25243),
        Color(0xFFE88F78),
        Color(0xFFA25243)
    )
)

/** Electric cyan → soft purple gradient used for the glowing app-name headline. */
val CyanLavenderGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF00F2FE), Color(0xFFC084FC))
)

/**
 * Reusable glassmorphism card: dark translucent blue surface, glowing translucent
 * border, rounded corners and consistent padding. `backdrop-filter: blur(16px)`
 * is approximated in Compose by a semi-transparent surface over the (blurred)
 * nebula layer drawn behind it on the host screen.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CosmicCardBackground.copy(alpha = 0.65f))
            .border(1.dp, CosmicCardBorder.copy(alpha = 0.22f), shape)
            .padding(contentPadding),
        content = content
    )
}

/** Gold section header used at the top of every [GlassCard]. */
@Composable
fun GlassSectionTitle(text: String) {
    Text(
        text = text,
        color = CosmicGold,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp
    )
}
