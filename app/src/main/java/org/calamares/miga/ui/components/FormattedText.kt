package org.calamares.miga.ui.components

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
import org.calamares.miga.data.model.RichText
import org.calamares.miga.data.model.RichTextEditing

/** [text] (see [RichText]) as styled text: syntax removed, lists with bullets or numbers. */
fun richAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
    RichText.blocks(text).forEachIndexed { index, block ->
        if (index > 0) append('\n')
        when (block.kind) {
            RichText.LineKind.BULLET -> append("•  ")
            RichText.LineKind.NUMBERED -> append("${block.number ?: 1}. ")
            else -> Unit
        }
        val heading = block.kind == RichText.LineKind.HEADING
        block.runs.forEach { run ->
            withStyle(runStyle(run.bold, run.italic, run.strike, heading)) { append(run.text) }
        }
    }
}

private fun runStyle(bold: Boolean, italic: Boolean, strike: Boolean, heading: Boolean = false) = SpanStyle(
    fontWeight = if (bold || heading) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (strike) TextDecoration.LineThrough else null,
    fontSize = if (heading) 1.15.em else TextUnit.Unspecified
)

/** Shows formatted text (steps, notes); plain text is shown as is. */
@Composable
fun FormattedText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val annotated = remember(text) { richAnnotatedString(text) }
    Text(annotated, style = style, color = color, modifier = modifier)
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
