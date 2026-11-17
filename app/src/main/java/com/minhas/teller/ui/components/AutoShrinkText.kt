package com.minhas.teller.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Single-line text that shrinks its own font until it fits the width it is given,
 * instead of wrapping onto a second line or getting cut off with an ellipsis.
 *
 * Picks the font size inside a [Modifier.layout] block, which hands us the real
 * incoming [Constraints] directly, and measures candidate sizes with
 * [rememberTextMeasurer] until one fits (down to [minFontSize]).
 *
 * This replaced two earlier approaches that both broke under
 * `Row(...).height(IntrinsicSize.Min)` with `Modifier.weight()` children (used
 * elsewhere in this app for equal-height row cells):
 *  1. A live onTextLayout → mutableState feedback loop that locked in "fits" the
 *     first time any pass reported no overflow and never reconsidered — the
 *     intrinsics pass through that Row can report a looser width than the real,
 *     final one, so it latched onto the wrong answer and never re-shrank.
 *  2. A [androidx.compose.foundation.layout.BoxWithConstraints]-based rewrite,
 *     which crashed outright: it's built on `SubcomposeLayout`, and Compose
 *     explicitly disallows asking a `SubcomposeLayout` for intrinsic
 *     measurements — exactly what that `IntrinsicSize.Min` Row does to every
 *     child, including this one.
 *
 * `Modifier.layout` is a plain (non-subcompose) layout modifier, so it supports
 * intrinsics safely, and because we measure directly against whatever
 * `Constraints` we're actually given — not a cached/stale "we already decided
 * this fits" flag — there's no stale-latch failure mode to begin with.
 */
@Composable
fun AutoShrinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxFontSize: TextUnit = TextUnit.Unspecified,
    minFontSize: TextUnit = 13.sp,
    stepSp: Float = 1f
) {
    val startSize = when {
        maxFontSize != TextUnit.Unspecified -> maxFontSize
        style.fontSize != TextUnit.Unspecified -> style.fontSize
        else -> 20.sp
    }
    val textMeasurer = rememberTextMeasurer()
    val measureStyle = style.merge(TextStyle(fontWeight = fontWeight, letterSpacing = letterSpacing))
    var fontSize by remember(text, startSize) { mutableStateOf(startSize) }

    Text(
        text = text,
        modifier = modifier.layout { measurable, constraints ->
            val maxWidthPx = constraints.maxWidth
            if (maxWidthPx in 1 until Constraints.Infinity) {
                var size = startSize.value
                while (size > minFontSize.value) {
                    val result = textMeasurer.measure(
                        text = text,
                        style = measureStyle.copy(fontSize = size.sp),
                        maxLines = 1,
                        softWrap = false
                    )
                    if (result.size.width <= maxWidthPx) break
                    size -= stepSp
                }
                val resolved = size.coerceAtLeast(minFontSize.value).sp
                if (resolved != fontSize) fontSize = resolved
            }
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = style
    )
}
