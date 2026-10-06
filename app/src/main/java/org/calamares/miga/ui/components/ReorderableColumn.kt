package org.calamares.miga.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * Columna (no perezosa, para listas cortas) cuyos elementos se reordenan arrastrando el
 * modificador `dragHandle` que recibe cada uno. El orden nuevo se entrega en [onReorder] al
 * soltar; mientras se arrastra se trabaja sobre una copia local para que el movimiento sea fluido.
 */
@Composable
fun <T : Any> ReorderableColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int, dragHandle: Modifier, isDragging: Boolean) -> Unit
) {
    var order by remember { mutableStateOf(items) }
    var draggingItem by remember { mutableStateOf<T?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<T, Int>() }
    val currentOnReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(items) { if (draggingItem == null) order = items }

    Column(modifier) {
        order.forEachIndexed { index, item ->
            key(item) {
                val isDragging = draggingItem == item
                val handle = Modifier.pointerInput(item) {
                    detectDragGestures(
                        onDragStart = {
                            draggingItem = item
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            draggingItem = null
                            dragOffset = 0f
                            currentOnReorder(order)
                        },
                        onDragCancel = {
                            draggingItem = null
                            dragOffset = 0f
                            currentOnReorder(order)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val from = order.indexOf(item)
                            val next = order.getOrNull(from + 1)
                            val previous = order.getOrNull(from - 1)
                            val nextHeight = next?.let { heights[it] } ?: 0
                            val previousHeight = previous?.let { heights[it] } ?: 0
                            if (next != null && dragOffset > nextHeight / 2f) {
                                order = order.toMutableList().apply { add(from + 1, removeAt(from)) }
                                dragOffset -= nextHeight
                            } else if (previous != null && dragOffset < -previousHeight / 2f) {
                                order = order.toMutableList().apply { add(from - 1, removeAt(from)) }
                                dragOffset += previousHeight
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .onSizeChanged { heights[item] = it.height }
                ) {
                    itemContent(item, index, handle, isDragging)
                }
            }
        }
    }
}

/** Devuelve [list] con el elemento de [from] movido a [to] (para acciones de accesibilidad). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
