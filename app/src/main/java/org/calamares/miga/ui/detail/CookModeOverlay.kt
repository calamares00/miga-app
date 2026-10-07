package org.calamares.miga.ui.detail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.CookModeText
import org.calamares.miga.data.model.CookModeVoiceCommands
import org.calamares.miga.data.model.CookVoiceCommand
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RichText
import org.calamares.miga.data.model.StepTimerParsing
import org.calamares.miga.data.voice.DictationResult
import org.calamares.miga.data.voice.SpeechDictation
import org.calamares.miga.ui.components.richAnnotatedString

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

private const val COOK_PREFS = "miga_cook_mode"
private const val KEY_TEXT_SIZE = "text_size"

/** Step text sizes the user can cycle through, as a factor of the base size. */
private val TEXT_SIZES = listOf(0.85f, 1f, 1.2f, 1.4f)
private const val DEFAULT_TEXT_SIZE = 1

private fun cookPrefs(context: Context) = context.getSharedPreferences(COOK_PREFS, Context.MODE_PRIVATE)

/**
 * A step as shown in cooking mode: the formatting of [RichText] with bold in [boldColor] (the
 * base text is large, so weight alone is not enough to tell it apart) and the times,
 * temperatures, speeds and heat levels in [highlight].
 */
private fun cookStepText(instruction: String, boldColor: Color, highlight: SpanStyle): AnnotatedString {
    val formatted = richAnnotatedString(instruction, boldColor)
    return buildAnnotatedString {
        append(formatted)
        CookModeText.highlights(formatted.text).forEach { range -> addStyle(highlight, range.first, range.last + 1) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CookModeOverlay(recipe: Recipe, ttsVoiceName: String?, onClose: () -> Unit) {
    val steps = remember(recipe) { recipe.flattenSteps() }
    val ingredientGroups = remember(recipe) { recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() } }
    val hasIngredients = ingredientGroups.isNotEmpty()
    val totalPages = steps.size + (if (hasIngredients) 1 else 0)
    val pagerState = rememberPagerState { totalPages }
    val pageIndex = pagerState.currentPage
    val scope = rememberCoroutineScope()
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

    var textSizeIndex by remember {
        mutableIntStateOf(cookPrefs(context).getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE).coerceIn(TEXT_SIZES.indices))
    }
    // Ingredients ticked off on the first page while getting everything ready.
    val readyIngredients = remember(recipe) { mutableStateListOf<String>() }

    /**
     * Computed here rather than only in the page that draws the step because executeVoiceCommand
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

    // Reading aloud stops when the page changes, by swipe, button or voice.
    LaunchedEffect(pageIndex) { tts?.stop() }

    fun goToPage(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    fun speak(instruction: String, utteranceId: String) {
        tts?.speak(RichText.toPlainText(instruction), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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
                speak(instruction, "cook_step_voice")
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

    fun cycleTextSize() {
        textSizeIndex = (textSizeIndex + 1) % TEXT_SIZES.size
        cookPrefs(context).edit().putInt(KEY_TEXT_SIZE, textSizeIndex).apply()
    }

    fun close() {
        tts?.stop()
        onClose()
    }

    // Full-screen layer inside the app window rather than a Dialog, so it gets the real system bar
    // insets and never shows the screen underneath.
    BackHandler(onBack = ::close)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Since Android 15 full-screen windows draw under the system bars, so the insets are
        // applied here to keep the bottom buttons clear of the navigation bar.
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (totalPages == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(L10n.str(R.string.recipe_has_no_steps), style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = ::close, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.close_cooking_mode))
                }
            } else Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = ::cycleTextSize) {
                        Icon(Icons.Filled.FormatSize, contentDescription = L10n.str(R.string.cook_text_size))
                    }
                    if (speechAvailable) {
                        IconButton(onClick = { onMicClick() }) {
                            if (isListeningForCommand) {
                                Icon(Icons.Filled.Stop, contentDescription = L10n.str(R.string.stop_voice_command), tint = MaterialTheme.colorScheme.error)
                            } else {
                                Icon(Icons.Filled.Mic, contentDescription = L10n.str(R.string.voice_command))
                            }
                        }
                    }
                    IconButton(onClick = ::close) {
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

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (pageIndex + 1f) / totalPages },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = if (currentStep == null) {
                        L10n.str(R.string.ingredients)
                    } else {
                        L10n.str(R.string.cook_step_x_of_y, currentStepIndex + 1, steps.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                activeTimer?.let { timer ->
                    TimerBanner(
                        stepNumber = timer.stepIndex + 1,
                        secondsLeft = timerSecondsLeft,
                        finished = timerFinished,
                        onCancel = { activeTimer = null },
                        onOpenStep = { goToPage(timer.stepIndex + if (hasIngredients) 1 else 0) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val stepIndex = page - (if (hasIngredients) 1 else 0)
                    val step = steps.getOrNull(stepIndex)
                    if (step == null) {
                        IngredientsPage(
                            groups = ingredientGroups.map { (it.name ?: L10n.str(R.string.main_recipe)) to it.ingredients },
                            showGroupNames = ingredientGroups.size > 1,
                            textScale = TEXT_SIZES[textSizeIndex],
                            ready = readyIngredients,
                            onToggle = { key -> if (key in readyIngredients) readyIngredients.remove(key) else readyIngredients.add(key) }
                        )
                    } else {
                        val stepIngredients = remember(recipe, step) {
                            val sameGroup = recipe.ingredientGroups.firstOrNull { it.name != null && it.name == step.groupName }
                            CookModeText.ingredientsIn(step.instruction, sameGroup?.ingredients ?: recipe.ingredientGroups.flatMap { it.ingredients })
                        }
                        val detectedSeconds = remember(step.instruction) { StepTimerParsing.findTimerSeconds(step.instruction) }
                        StepPage(
                            step = step,
                            textScale = TEXT_SIZES[textSizeIndex],
                            ingredients = stepIngredients,
                            detectedSeconds = detectedSeconds,
                            timerRunningHere = activeTimer?.stepIndex == stepIndex,
                            onListen = {
                                val engine = tts
                                if (engine != null && engine.isSpeaking) engine.stop() else speak(step.instruction, "cook_step")
                            },
                            onTimer = {
                                activeTimer = if (activeTimer?.stepIndex == stepIndex || detectedSeconds == null) {
                                    null
                                } else {
                                    ActiveTimer(stepIndex, detectedSeconds, (activeTimer?.startToken ?: 0) + 1)
                                }
                            }
                        )
                    }
                }

                // A glance at what comes next, so the user can get it ready.
                val nextStep = steps.getOrNull(currentStepIndex + 1)
                if (nextStep != null) {
                    Text(
                        text = L10n.str(R.string.cook_up_next_x, RichText.toPlainText(nextStep.instruction).replace('\n', ' ')),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
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
                        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(L10n.str(R.string.previous).trim())
                    }
                    Button(
                        onClick = { if (pageIndex < totalPages - 1) goToPage(pageIndex + 1) else close() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text(if (pageIndex < totalPages - 1) L10n.str(R.string.next).trim() else L10n.str(R.string.finish))
                        if (pageIndex < totalPages - 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerBanner(stepNumber: Int, secondsLeft: Int, finished: Boolean, onCancel: () -> Unit, onOpenStep: () -> Unit) {
    val bannerColor = if (finished) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val bannerContentColor = if (finished) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bannerColor)
            .clickable(onClick = onOpenStep)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = bannerContentColor)
        Text(
            text = if (finished) L10n.str(R.string.done_step_x, stepNumber) else L10n.str(R.string.step_x_x, stepNumber, formatTimer(secondsLeft)),
            style = MaterialTheme.typography.titleLarge,
            color = bannerContentColor,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel_timer), tint = bannerContentColor)
        }
    }
}

/** First page: every ingredient, which can be ticked off while getting them ready. */
@Composable
private fun IngredientsPage(
    groups: List<Pair<String, List<Ingredient>>>,
    showGroupNames: Boolean,
    textScale: Float,
    ready: List<String>,
    onToggle: (String) -> Unit
) {
    val total = groups.sumOf { it.second.size }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            L10n.str(R.string.cook_ingredients_ready_x_y, ready.size, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        groups.forEachIndexed { groupIndex, (name, ingredients) ->
            if (showGroupNames) {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
            }
            ingredients.forEachIndexed { index, ingredient ->
                val key = "$groupIndex/$index"
                val done = key in ready
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(key) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = formatIngredient(ingredient, 1.0),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 19.sp * textScale,
                            lineHeight = 26.sp * textScale
                        ),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (done) TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPage(
    step: CookStep,
    textScale: Float,
    ingredients: List<Ingredient>,
    detectedSeconds: Int?,
    timerRunningHere: Boolean,
    onListen: () -> Unit,
    onTimer: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val text = remember(step.instruction, colors.primary, colors.tertiary) {
        cookStepText(
            step.instruction,
            boldColor = colors.primary,
            highlight = SpanStyle(color = colors.tertiary, fontWeight = FontWeight.SemiBold)
        )
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    step.stepNumberInGroup.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = step.groupName?.uppercase().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onListen) {
                Icon(Icons.Filled.VolumeUp, contentDescription = L10n.str(R.string.listen_step), tint = colors.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Regular weight on purpose: the heading styles are semi-bold, which hid bold text.
        Text(
            text = text,
            style = TextStyle(
                fontSize = 25.sp * textScale,
                lineHeight = 36.sp * textScale,
                fontWeight = FontWeight.Normal,
                color = colors.onSurface
            )
        )

        if (ingredients.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(L10n.str(R.string.cook_in_this_step), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ingredients.forEach { ingredient ->
                    Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            formatIngredient(ingredient, 1.0),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (detectedSeconds != null) {
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(onClick = onTimer) {
                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (timerRunningHere) L10n.str(R.string.stop_timer).trim() else L10n.str(R.string.start_timer_x, formatTimer(detectedSeconds)).trim()
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
