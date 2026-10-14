package org.calamares.miga.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * Progress of an AI operation: a spinner, the current step, a changing hint of what the AI is
 * doing and the elapsed seconds. [fallback] is shown until the first step arrives.
 */
@Composable
fun AiProgressView(
    progress: AiProgress?,
    fallback: String,
    modifier: Modifier = Modifier,
    spinnerSize: Dp = 22.dp,
    centered: Boolean = false
) {
    val startedAt = progress?.startedAt
    val elapsedSeconds by produceState(0L, startedAt) {
        while (true) {
            value = startedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
            delay(1000)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(spinnerSize), strokeWidth = 2.5.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
        ) {
            AnimatedContent(
                targetState = progress?.step?.takeIf { it.isNotBlank() } ?: fallback,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "aiStep"
            ) { step ->
                Text(step, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            AnimatedContent(
                targetState = progress?.detail,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "aiDetail"
            ) { detail ->
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (elapsedSeconds >= 3) {
                Text(
                    L10n.str(R.string.ai_elapsed_x, elapsedSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
