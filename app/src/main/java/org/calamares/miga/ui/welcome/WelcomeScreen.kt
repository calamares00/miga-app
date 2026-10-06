package org.calamares.miga.ui.welcome

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class WelcomePage(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    WelcomePage(
        Icons.Filled.MenuBook,
        L10n.str(R.string.family_recipe_book),
        L10n.str(R.string.keep_family_recipes_books_photos)
    ),
    WelcomePage(
        Icons.Filled.ShoppingCart,
        L10n.str(R.string.faster_shopping),
        L10n.str(R.string.add_items_typing_dictating_scanning)
    ),
    WelcomePage(
        Icons.Filled.Lock,
        L10n.str(R.string.data_phone),
        L10n.str(R.string.no_accounts_ads_ai_own)
    )
)

/** Destination opened when the welcome flow ends (null for the main screen). */
typealias WelcomeDestination = String?

/**
 * First-run welcome: three swipeable pages and, at the end, start from scratch, explore recipe
 * packs or restore a backup. [onFinish] receives the route to open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    packsRoute: String,
    backupRoute: String,
    onFinish: (WelcomeDestination) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLast) TextButton(onClick = { onFinish(null) }) { Text(L10n.str(R.string.skip)) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = PAGES[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(56.dp))
                }
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp)
                )
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp).widthIn(max = 420.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(vertical = 24.dp)
                .semantics { contentDescription = L10n.str(R.string.page_x_x, pagerState.currentPage + 1, PAGES.size) },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PAGES.indices.forEach { index ->
                val color by animateColorAsState(
                    if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "dot"
                )
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            }
        }
        Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isLast) {
                Button(onClick = { onFinish(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(L10n.str(R.string.get_started)) }
                OutlinedButton(onClick = { onFinish(packsRoute) }, modifier = Modifier.fillMaxWidth()) { Text(L10n.str(R.string.explore_recipe_packs)) }
                TextButton(onClick = { onFinish(backupRoute) }, modifier = Modifier.fillMaxWidth()) { Text(L10n.str(R.string.restore_backup)) }
            } else {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(L10n.str(R.string.next_2)) }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}
