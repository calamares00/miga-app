package org.calamares.miga.ui.components

import org.calamares.miga.data.model.CookModeText
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.RichText
import org.calamares.miga.data.model.RichTextEditing

/**
 * [text] (see [RichText]) as styled text: syntax removed, lists with bullets or numbers. Bold text
 * also takes [boldColor] when given, for places where the base style is already heavy.
 */
fun richAnnotatedString(text: String, boldColor: Color = Color.Unspecified, linkColor: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    RichText.blocks(text).forEachIndexed { index, block ->
        if (index > 0) append('\n')
        when (block.kind) {
            RichText.LineKind.BULLET -> append("•  ")
            RichText.LineKind.NUMBERED -> append("${block.number ?: 1}. ")
            else -> Unit
        }
        val heading = block.kind == RichText.LineKind.HEADING
        block.runs.forEach { run ->
            val start = length
            withStyle(runStyle(run.bold, run.italic, run.strike, heading, if (run.bold) boldColor else Color.Unspecified)) { append(run.text) }
            run.url?.let { url ->
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, length)
                addStringAnnotation(RICH_LINK_TAG, url, start, length)
            }
        }
    }
}

private fun runStyle(bold: Boolean, italic: Boolean, strike: Boolean, heading: Boolean = false, color: Color = Color.Unspecified) = SpanStyle(
    color = color,
    fontWeight = if (bold || heading) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (strike) TextDecoration.LineThrough else null,
    fontSize = if (heading) 1.15.em else TextUnit.Unspecified
)

private const val RICH_LINK_TAG = "rich_link"

/**
 * Shows formatted text (steps, notes); plain text is shown as is. Links ("[text](https://...)")
 * show only their text, underlined, and open in the browser when tapped.
 */
@Composable
fun FormattedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    /** Times, temperatures and speeds in the theme colour, as in cooking mode (recipe steps). */
    highlightTimes: Boolean = false,
    /** The recipe's ingredients, shown in italics where the text names them (see StepMarks). */
    ingredients: List<Ingredient> = emptyList()
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor, highlightTimes, ingredients) {
        val rich = withBareLinks(richAnnotatedString(text, linkColor = linkColor), linkColor)
        if (!highlightTimes && ingredients.isEmpty()) rich else withStepStyles(rich, SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), ingredients)
    }
    if (annotated.getStringAnnotations(RICH_LINK_TAG, 0, annotated.length).isEmpty()) {
        Text(annotated, style = style, color = color, modifier = modifier)
        return
    }
    val context = LocalContext.current
    val baseColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    ClickableText(
        text = annotated,
        style = style.copy(color = baseColor),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(RICH_LINK_TAG, offset, offset).firstOrNull()?.let { link ->
                openWebLink(context, link.item)
            }
        }
    )
}

/**
 * The styles the app gives every step (see StepMarks): the recipe's [ingredients] in italics and
 * times, temperatures, heat and settings in [highlight]. The highlight goes last, so a bold word
 * inside it ("fuego medio-**fuerte**") looks like the rest of it.
 */
fun withStepStyles(text: AnnotatedString, highlight: SpanStyle, ingredients: List<Ingredient>): AnnotatedString = buildAnnotatedString {
    append(text)
    CookModeText.ingredientMentions(text.text, ingredients).forEach { range ->
        addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first, range.last + 1)
    }
    CookModeText.highlights(text.text).forEach { range -> addStyle(highlight, range.first, range.last + 1) }
}

/** Addresses written as they are ("https://..."), not already inside a [text](url) link. */
private val BARE_URL = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]'»]""")

/** [text] with its bare web addresses underlined and tappable, like [text](url) links. */
fun withBareLinks(text: AnnotatedString, linkColor: Color): AnnotatedString {
    val found = BARE_URL.findAll(text.text)
        .filter { match -> text.getStringAnnotations(RICH_LINK_TAG, match.range.first, match.range.last + 1).isEmpty() }
        .toList()
    if (found.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        found.forEach { match ->
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), match.range.first, match.range.last + 1)
            addStringAnnotation(RICH_LINK_TAG, match.value, match.range.first, match.range.last + 1)
        }
    }
}

/**
 * Opens a link in the browser. Only http and https: a recipe can come from anyone (shared,
 * imported), and other schemes could open apps or files.
 */
fun openWebLink(context: android.content.Context, url: String) {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
}

/**
 * Styles the text being edited without changing it: bold, italic and strikethrough are applied
 * and the syntax characters are dimmed, so the user sees the result while typing.
 */
private class RichTextVisualTransformation(private val markerColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val parsed = RichText.parse(text.text)
        val styled = buildAnnotatedString {
            append(text.text)
            val headingChars = BooleanArray(text.length)
            parsed.lines.filter { it.kind == RichText.LineKind.HEADING }.forEach { line ->
                for (i in line.contentStart until line.end) headingChars[i] = true
            }
            var i = 0
            while (i < text.length) {
                val start = i
                val key = listOf(parsed.marker[i], parsed.bold[i] || headingChars[i], parsed.italic[i], parsed.strike[i])
                while (i < text.length && listOf(parsed.marker[i], parsed.bold[i] || headingChars[i], parsed.italic[i], parsed.strike[i]) == key) i++
                val style = if (key[0]) SpanStyle(color = markerColor) else runStyle(key[1], key[2], key[3])
                addStyle(style, start, i)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Text field with light formatting (see [RichText]): the text is shown styled while typing, a
 * toolbar with bold, italic, strikethrough and lists appears while the field has focus, and
 * pressing Enter on a list item starts the next one.
 */
@Composable
fun RichTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    minLines: Int = 1
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // The text can also change from outside (dictation, AI): then the cursor goes to the end.
    val current = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val transformation = remember(markerColor) { RichTextVisualTransformation(markerColor) }

    fun update(newValue: TextFieldValue) {
        fieldValue = newValue
        if (newValue.text != value) onValueChange(newValue.text)
    }

    fun apply(edit: RichTextEditing.Edit) = update(TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd)))

    Column(modifier = modifier) {
        OutlinedTextField(
            value = current,
            onValueChange = { newValue ->
                val typedLineBreak = newValue.text.length == current.text.length + 1 &&
                    newValue.selection.collapsed &&
                    newValue.selection.start > 0 &&
                    newValue.text[newValue.selection.start - 1] == '\n'
                val continued = if (typedLineBreak) RichTextEditing.continueList(newValue.text, newValue.selection.start) else null
                if (continued != null) apply(continued) else update(newValue)
            },
            label = label,
            minLines = minLines,
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
        if (focused) {
            Row {
                FormatButton(Icons.Filled.FormatBold, L10n.str(R.string.format_bold)) {
                    apply(RichTextEditing.toggleWrap(current.text, current.selection.start, current.selection.end, "**"))
                }
                FormatButton(Icons.Filled.FormatItalic, L10n.str(R.string.format_italic)) {
                    apply(RichTextEditing.toggleWrap(current.text, current.selection.start, current.selection.end, "*"))
                }
                FormatButton(Icons.Filled.FormatStrikethrough, L10n.str(R.string.format_strikethrough)) {
                    apply(RichTextEditing.toggleWrap(current.text, current.selection.start, current.selection.end, "~~"))
                }
                FormatButton(Icons.Filled.FormatListBulleted, L10n.str(R.string.format_bullet_list)) {
                    apply(RichTextEditing.toggleList(current.text, current.selection.start, current.selection.end, numbered = false))
                }
                FormatButton(Icons.Filled.FormatListNumbered, L10n.str(R.string.format_numbered_list)) {
                    apply(RichTextEditing.toggleList(current.text, current.selection.start, current.selection.end, numbered = true))
                }
            }
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
