package org.calamares.miga.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiProgress
import org.calamares.miga.data.ai.AiStepLine

/** Steps already finished shown at most: the latest ones. */
private const val MAX_DONE_STEPS = 6

/**
 * Progress of an AI operation as the steps it really goes through: those finished (✓, or ✗ with
 * why) and the current one with a spinner and its own seconds. [fallback] is shown until the first
 * step arrives. With [showDone] false only the current step is shown (a row of a list).
 */
@Composable
fun AiProgressView(
    progress: AiProgress?,
    fallback: String,
    modifier: Modifier = Modifier,
    spinnerSize: Dp = 22.dp,
    centered: Boolean = false,
    showDone: Boolean = true
) {
    val stepStartedAt = progress?.stepStartedAt
    val stepSeconds by produceState(0L, stepStartedAt) {
        while (true) {
            value = stepStartedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
            delay(1000)
        }
    }
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (showDone) progress?.done?.takeLast(MAX_DONE_STEPS)?.forEach { DoneStep(it, spinnerSize) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(spinnerSize), strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val step = progress?.step?.takeIf { it.isNotBlank() } ?: fallback
                Text(
                    // Non-breaking spaces: "· 6 s" never splits across lines.
                    stepText(step) + if (stepSeconds >= 2) " ·\u00A0" + L10n.str(R.string.ai_elapsed_x, stepSeconds).replace(' ', '\u00A0') else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                // A countdown while a provider's rate limit passes.
                progress?.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DoneStep(line: AiStepLine, spinnerSize: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (line.failed) Icons.Filled.Close else Icons.Filled.Check,
            contentDescription = null,
            tint = if (line.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            // Centred under the spinner, so the steps read as one column.
            modifier = Modifier.padding(horizontal = (spinnerSize - 18.dp).coerceAtLeast(0.dp) / 2).size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            stepText(line.text),
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Steps are written as "Downloading the page…"; in the list they read without the dots. */
private fun stepText(text: String) = text.trimEnd('…', ' ')
