package org.calamares.miga.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.MarkdownDocument

private const val LINK_TAG = "link"
private val EMAIL = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
private val WEB_URL = Regex("""https?://[^\s)]+[^\s).,;:]""")

/** Reads a Markdown file from the app assets, or an empty text if it is missing. */
fun readDocumentAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")

/** [text] with its inline formatting applied and e-mail addresses and web links made tappable. */
private fun documentText(text: String, linkColor: Color): AnnotatedString {
    val styled = richAnnotatedString(text)
    return buildAnnotatedString {
        append(styled)
        val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        EMAIL.findAll(styled.text).forEach { match ->
            addStyle(linkStyle, match.range.first, match.range.last + 1)
            addStringAnnotation(LINK_TAG, "mailto:${match.value}", match.range.first, match.range.last + 1)
        }
        WEB_URL.findAll(styled.text).forEach { match ->
            addStyle(linkStyle, match.range.first, match.range.last + 1)
            addStringAnnotation(LINK_TAG, match.value, match.range.first, match.range.last + 1)
        }
    }
}

private fun openLink(context: Context, target: String) {
    val uri = Uri.parse(target)
    val intent = if (uri.scheme == "mailto") Intent(Intent.ACTION_SENDTO, uri) else Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun DocumentText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { documentText(text, linkColor) }
    ClickableText(
        text = annotated,
        style = style.merge(TextStyle(color = LocalContentColor.current)),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { openLink(context, it.item) }
        }
    )
}

/** The blocks of a document (see [MarkdownDocument]) styled with the app theme. */
@Composable
fun MarkdownBlocks(blocks: List<MarkdownDocument.Block>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            val indent = Modifier.padding(start = (block.level * 20).dp)
            when (block.kind) {
                MarkdownDocument.Kind.HEADING -> Text(
                    block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = if (block.level == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (index == 0 || block.level == 1) 0.dp else 14.dp)
                )
                MarkdownDocument.Kind.PARAGRAPH ->
                    DocumentText(block.text, MaterialTheme.typography.bodyLarge, indent)
                MarkdownDocument.Kind.BULLET, MarkdownDocument.Kind.NUMBERED -> Row(modifier = indent) {
                    val marker = if (block.kind == MarkdownDocument.Kind.NUMBERED) "${block.number}." else if (block.level == 0) "•" else "◦"
                    Text(
                        marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(if (block.kind == MarkdownDocument.Kind.NUMBERED) 26.dp else 18.dp)
                    )
                    DocumentText(block.text, MaterialTheme.typography.bodyLarge, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Full-screen reader for a Markdown document bundled in the app assets (for example the privacy policy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(title: String, assetPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember(assetPath) { MarkdownDocument.parse(readDocumentAsset(context, assetPath)) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                }
            )
        }
    ) { padding ->
        MarkdownBlocks(
            blocks = blocks,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .fillMaxWidth()
        )
    }
}
