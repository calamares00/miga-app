package org.calamares.miga.ui.ideas

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.calamares.miga.data.ideas.RecipeReferences
import org.calamares.miga.ui.components.richAnnotatedString

private const val RECIPE_TAG = "recipe"

// Private-use characters that carry a link through the rich text parser.
private const val LINK_START = ''
private const val LINK_NAME = ''
private const val LINK_END = ''
private val MARKED_LINK = Regex("$LINK_START(\\d+)$LINK_NAME([^$LINK_END]*)$LINK_END")

/**
 * [text] (see [org.calamares.miga.data.model.RichText]) with every `[[id]]` (see [RecipeReferences])
 * shown as the recipe name in [linkStyle] and annotated with its id.
 */
internal fun recipeLinkedString(text: String, names: Map<Long, String>, linkStyle: SpanStyle): AnnotatedString {
    val marked = RecipeReferences.TOKEN.replace(text) { match ->
        val id = match.groupValues[1].toLong()
        // The name must not be read as formatting by the parser.
        names[id]?.let { name -> "$LINK_START$id$LINK_NAME${name.replace('*', '∗').replace('_', ' ').replace('~', '∼')}$LINK_END" }.orEmpty()
    }
    val styled = richAnnotatedString(marked)
    return buildAnnotatedString {
        var last = 0
        MARKED_LINK.findAll(styled.text).forEach { match ->
            append(styled.subSequence(last, match.range.first))
            pushStringAnnotation(RECIPE_TAG, match.groupValues[1])
            withStyle(linkStyle) { append(match.groupValues[2]) }
            pop()
            last = match.range.last + 1
        }
        append(styled.subSequence(last, styled.length))
    }
}

/** Formatted answer text where recipe references are links that open the recipe. */
@Composable
internal fun RecipeLinkText(
    text: String,
    names: Map<Long, String>,
    style: TextStyle,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, names, linkColor) {
        recipeLinkedString(
            text,
            names,
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        )
    }
    ClickableText(
        text = annotated,
        style = style.merge(TextStyle(color = LocalContentColor.current)),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(RECIPE_TAG, offset, offset).firstOrNull()?.let { onRecipeClick(it.item.toLong()) }
        }
    )
}
