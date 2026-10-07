package org.calamares.miga.ui.detail

import org.calamares.miga.data.model.RichText
import org.calamares.miga.ui.components.FormattedText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import org.calamares.miga.data.model.CookModeVoiceCommands
import org.calamares.miga.data.model.CookVoiceCommand
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.StepTimerParsing
import org.calamares.miga.data.voice.DictationResult
import org.calamares.miga.data.voice.SpeechDictation
import kotlinx.coroutines.delay
import java.util.Locale

private data class CookStep(val groupName: String?, val stepNumberInGroup: Int, val instruction: String)

private fun Recipe.flattenSteps(): List<CookStep> =
    stepGroups.flatMap { group -> group.instructions.mapIndexed { idx, instruction -> CookStep(group.name, idx + 1, instruction) } }

/**
 * Active cook mode timer: the step it belongs to (index in [CookStep], not the page) and its
 * starting seconds. [startToken] changes on every "Start" tap, even when restarting the same step,
 * so LaunchedEffect always restarts the countdown.
 */
private data class ActiveTimer(val stepIndex: Int, val totalSeconds: Int, val startToken: Int)

private fun formatTimer(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun CookModeOverlay(recipe: Recipe, ttsVoiceName: String?, onClose: () -> Unit) {
    val steps = remember(recipe) { recipe.flattenSteps() }
    val ingredientGroups = remember(recipe) { recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() } }
    val hasIngredients = ingredientGroups.isNotEmpty()
    val totalPages = steps.size + (if (hasIngredients) 1 else 0)
    var pageIndex by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    var activeTimer by remember { mutableStateOf<ActiveTimer?>(null) }
    var timerSecondsLeft by remember { mutableIntStateOf(0) }
    var timerFinished by remember { mutableStateOf(false) }

    val speechAvailable = remember { SpeechDictation.isAvailable(context) }
    val dictationLanguage = org.calamares.miga.ui.components.rememberDictationLanguage()
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isListeningForCommand by remember { mutableStateOf(false) }
    var voiceFeedback by remember { mutableStateOf<String?>(null) }

    /**
     * Computed here rather than only in the branch that draws the step because executeVoiceCommand
     * needs them too, and it can receive a timer or repeat command at any point.
     */
    val currentStepIndex = pageIndex - (if (hasIngredients) 1 else 0)
    val currentStep = steps.getOrNull(currentStepIndex)
    val currentDetectedSeconds = remember(currentStep?.instruction) { currentStep?.let { StepTimerParsing.findTimerSeconds(it.instruction) } }

    LaunchedEffect(activeTimer) {
        val timer = activeTimer ?: return@LaunchedEffect
        timerSecondsLeft = timer.totalSeconds
        timerFinished = false
        while (timerSecondsLeft > 0) {
            delay(1000)
            timerSecondsLeft--
        }
        timerFinished = true
        repeat(4) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setLanguage(L10n.locale())
                if (!ttsVoiceName.isNullOrBlank()) {
                    engine?.voices?.firstOrNull { it.name == ttsVoiceName }?.let { voice -> engine?.setVoice(voice) }
                }
            }
        }
        tts = engine
        onDispose {
            view.keepScreenOn = false
            engine?.stop()
            engine?.shutdown()
            activeRecognizer?.destroy()
        }
    }

    fun goToPage(index: Int) {
        tts?.stop()
        pageIndex = index
    }

    /**
     * Runs an already recognised command and returns the status text shown next to the microphone.
     */
    fun executeVoiceCommand(command: CookVoiceCommand): String = when (command) {
        CookVoiceCommand.NextStep ->
            if (pageIndex < totalPages - 1) { goToPage(pageIndex + 1); L10n.str(R.string.next_step) } else L10n.str(R.string.youre_already_last_step)
        CookVoiceCommand.PreviousStep ->
            if (pageIndex > 0) { goToPage(pageIndex - 1); L10n.str(R.string.previous_step) } else L10n.str(R.string.youre_already_first_step)
        CookVoiceCommand.RepeatStep -> {
            val instruction = currentStep?.instruction
            if (instruction != null) {
                tts?.speak(RichText.toPlainText(instruction), TextToSpeech.QUEUE_FLUSH, null, "cook_step_voice")
                L10n.str(R.string.repeating_step)
            } else {
                L10n.str(R.string.theres_no_step_repeat_here)
            }
        }
        CookVoiceCommand.StartTimer -> {
            val seconds = currentDetectedSeconds
            if (seconds != null) {
                activeTimer = ActiveTimer(currentStepIndex, seconds, (activeTimer?.startToken ?: 0) + 1)
                L10n.str(R.string.timer_started)
            } else {
                L10n.str(R.string.no_duration_was_detected_step)
            }
        }
        CookVoiceCommand.CancelTimer ->
            if (activeTimer != null) { activeTimer = null; L10n.str(R.string.timer_cancelled) } else L10n.str(R.string.theres_no_active_timer)
    }

    fun beginListeningForCommand() {
        voiceFeedback = null
        isListeningForCommand = true
        activeRecognizer = SpeechDictation.startListening(context, dictationLanguage) { result ->
            isListeningForCommand = false
            activeRecognizer?.destroy()
            activeRecognizer = null
            voiceFeedback = when (result) {
                is DictationResult.Success -> {
                    val command = CookModeVoiceCommands.parse(result.text)
                    if (command != null) executeVoiceCommand(command) else L10n.str(R.string.i_didnt_understand_x, result.text)
                }
                is DictationResult.Error -> result.reason
            }
        }
    }

    val voiceCommandPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListeningForCommand()
    }

    fun onMicClick() {
        if (isListeningForCommand) {
            activeRecognizer?.stopListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) beginListeningForCommand() else voiceCommandPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Full-screen layer inside the app window rather than a Dialog, so it gets the real system bar
    // insets and never shows the screen underneath.
    BackHandler(onBack = onClose)
    Box(modifier = Modifier.fillMaxSize()) {
        // Since Android 15 full-screen windows draw under the system bars, so the insets are
        // applied here to keep the bottom buttons clear of the navigation bar.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (totalPages == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(L10n.str(R.string.recipe_has_no_steps), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(recipe.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                        if (speechAvailable) {
                            IconButton(onClick = { onMicClick() }) {
                                if (isListeningForCommand) {
                                    Icon(Icons.Filled.Stop, contentDescription = L10n.str(R.string.stop_voice_command), tint = MaterialTheme.colorScheme.error)
                                } else {
                                    Icon(Icons.Filled.Mic, contentDescription = L10n.str(R.string.voice_command))
                                }
                            }
                        }
                        IconButton(onClick = { tts?.stop(); onClose() }) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.close_cooking_mode))
                        }
                    }
                    if (isListeningForCommand) {
                        Text(
                            L10n.str(R.string.listening_next_previous_repeat_timer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        voiceFeedback?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (pageIndex + 1f) / totalPages },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    activeTimer?.let { timer ->
                        val bannerColor = if (timerFinished) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                        val bannerContentColor = if (timerFinished) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bannerColor)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, tint = bannerContentColor)
                            Text(
                                text = if (timerFinished) L10n.str(R.string.done_step_x, timer.stepIndex + 1) else L10n.str(R.string.step_x_x, timer.stepIndex + 1, formatTimer(timerSecondsLeft)),
                                style = MaterialTheme.typography.titleMedium,
                                color = bannerContentColor,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { activeTimer = null }) {
                                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel_timer), tint = bannerContentColor)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (hasIngredients && pageIndex == 0) {
                        Text(
                            text = L10n.str(R.string.ingredients),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            ingredientGroups.forEach { group ->
                                if (ingredientGroups.size > 1) {
                                    Text(
                                        text = (group.name ?: L10n.str(R.string.main_recipe)).uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                group.ingredients.forEach { ingredient ->
                                    Text(
                                        text = "•  ${formatIngredient(ingredient, 1.0)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else if (currentStep != null) {
                        val stepIndex = currentStepIndex
                        val step = currentStep
                        val detectedSeconds = currentDetectedSeconds
                        if (step.groupName != null) {
                            Text(
                                text = step.groupName.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = L10n.str(R.string.step_x, step.stepNumberInGroup),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val engine = tts ?: return@IconButton
                                if (engine.isSpeaking) engine.stop() else engine.speak(RichText.toPlainText(step.instruction), TextToSpeech.QUEUE_FLUSH, null, "cook_step")
                            }) {
                                Icon(
                                    Icons.Filled.VolumeUp,
                                    contentDescription = L10n.str(R.string.listen_step),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FormattedText(
                            text = step.instruction,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (detectedSeconds != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val isThisStepActive = activeTimer?.stepIndex == stepIndex
                            OutlinedButton(
                                onClick = {
                                    activeTimer = if (isThisStepActive) {
                                        null
                                    } else {
                                        ActiveTimer(stepIndex, detectedSeconds, (activeTimer?.startToken ?: 0) + 1)
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.height(18.dp))
                                Text(
                                    text = if (isThisStepActive) L10n.str(R.string.stop_timer) else L10n.str(R.string.start_timer_x, formatTimer(detectedSeconds))
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (pageIndex > 0) goToPage(pageIndex - 1) },
                            enabled = pageIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(Icons.Filled.ArrowBackIosNew, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(modifier = Modifier.height(0.dp))
                            Text(L10n.str(R.string.previous))
                        }
                        Button(
                            onClick = { if (pageIndex < totalPages - 1) goToPage(pageIndex + 1) else { tts?.stop(); onClose() } },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Text(if (pageIndex < totalPages - 1) L10n.str(R.string.next) else L10n.str(R.string.finish))
                            if (pageIndex < totalPages - 1) {
                                Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
