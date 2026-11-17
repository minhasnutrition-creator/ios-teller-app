package com.minhas.teller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhas.teller.utils.HsnFuzzyMatcher
import com.minhas.teller.utils.SmartTaxService
import com.minhas.teller.utils.TerminologyManager

/**
 * Inline clarification picker displayed directly beneath the "Find HSN & GST" button
 * when the typed item name is misspelled, has reordered words, or matches multiple
 * distinct HSN/tax options ("Did you mean this?" / "Is it either one of these?").
 */
@Composable
fun HsnDidYouMeanSection(
    confidence: HsnFuzzyMatcher.MatchConfidence,
    suggestions: List<SmartTaxService.TaxSuggestion>,
    taxCodeLabel: String,
    taxLabel: String,
    onSelect: (SmartTaxService.TaxSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty() ||
        confidence == HsnFuzzyMatcher.MatchConfidence.NONE ||
        confidence == HsnFuzzyMatcher.MatchConfidence.EXACT_CONFIDENT
    ) {
        return
    }

    val headerText = when {
        confidence == HsnFuzzyMatcher.MatchConfidence.DID_YOU_MEAN_ONE || suggestions.size == 1 ->
            TerminologyManager.getTerm("Did you mean this?")
        suggestions.size == 2 ->
            TerminologyManager.getTerm("Is it either one of these?")
        else ->
            TerminologyManager.getTerm("Is it one of these?")
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = TerminologyManager.getTerm("Tap an option below to select its $taxCodeLabel and apply $taxLabel"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            suggestions.forEach { suggestion ->
                val info = suggestion.taxInfo
                val rateFormatted = if (info.gstRate % 1.0 == 0.0) {
                    info.gstRate.toInt().toString()
                } else {
                    info.gstRate.toString()
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.matchedLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$taxCodeLabel ${info.hsnCode} • $taxLabel $rateFormatted%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = TerminologyManager.getTerm("Select"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
