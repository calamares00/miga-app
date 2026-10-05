package org.calamares.miga.ui.welcome

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
        "Tu recetario familiar",
        "Guarda las recetas de casa en libros, con fotos, subrecetas, raciones ajustables y un modo cocina paso a paso."
    ),
    WelcomePage(
        Icons.Filled.ShoppingCart,
        "La compra, más rápida",
        "Añade escribiendo, dictando o escaneando el código de barras. La lista se ordena por categorías y por los pasillos de tu súper."
    ),
    WelcomePage(
        Icons.Filled.Lock,
        "Tus datos, en tu móvil",
        "Sin cuentas ni anuncios. La IA con tu propia clave, Open Food Facts y la sincronización con tu servidor son opcionales."
    )
)

/** Destino que se abre al terminar la bienvenida (null = pantalla principal). */
typealias WelcomeDestination = String?

/**
 * Bienvenida de la primera ejecución: tres páginas deslizables y, al final, empezar desde cero,
 * explorar packs de recetas o restaurar una copia de seguridad. [onFinish] recibe la ruta a abrir.
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
            if (!isLast) TextButton(onClick = { onFinish(null) }) { Text("Saltar") }
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
                .semantics { contentDescription = "Página ${pagerState.currentPage + 1} de ${PAGES.size}" },
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
                Button(onClick = { onFinish(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Empezar") }
                OutlinedButton(onClick = { onFinish(packsRoute) }, modifier = Modifier.fillMaxWidth()) { Text("Explorar packs de recetas") }
                TextButton(onClick = { onFinish(backupRoute) }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar una copia de seguridad") }
            } else {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Siguiente") }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}
