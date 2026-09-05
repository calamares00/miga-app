package com.bmo00.miga.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bmo00.miga.data.local.PhotoStorage
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Editor de foto a pantalla completa: rotar en pasos de 90º y recortar con un marco de ancho y
 * alto libres (arrastrando sus esquinas, como el recorte nativo de Android) antes de guardarla
 * normalizada (JPEG recomprimido y redimensionado, ver [PhotoStorage]) en el almacenamiento interno.
 */
@Composable
fun PhotoEditorOverlay(sourceUri: Uri, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    LaunchedEffect(sourceUri) {
        val loaded = withContext(Dispatchers.IO) { PhotoStorage.loadBitmap(context, sourceUri) }
        bitmap = loaded
        loadFailed = loaded == null
    }

    val bmp = bitmap
    val imageRect = if (bmp != null && boxSize.width > 0 && boxSize.height > 0) {
        val scale = minOf(boxSize.width.toFloat() / bmp.width, boxSize.height.toFloat() / bmp.height)
        val width = bmp.width * scale
        val height = bmp.height * scale
        val left = (boxSize.width - width) / 2f
        val top = (boxSize.height - height) / 2f
        Rect(left, top, left + width, top + height)
    } else {
        null
    }

    // Al cargar la foto (o al rotarla, lo que cambia sus proporciones) el marco de recorte
    // empieza abarcando toda la imagen; el usuario lo reduce arrastrando las esquinas.
    LaunchedEffect(imageRect) {
        if (imageRect != null) cropRect = imageRect
    }

    val handleRadiusPx = with(density) { 24.dp.toPx() }
    val minCropSizePx = with(density) { 48.dp.toPx() }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancelar") }
                    Row {
                        IconButton(
                            enabled = bmp != null,
                            onClick = {
                                bitmap = bitmap?.let { PhotoStorage.rotateBitmap(it, -90f) }
                                cropRect = null
                            }
                        ) { Icon(Icons.Filled.RotateLeft, contentDescription = "Rotar a la izquierda") }
                        IconButton(
                            enabled = bmp != null,
                            onClick = {
                                bitmap = bitmap?.let { PhotoStorage.rotateBitmap(it, 90f) }
                                cropRect = null
                            }
                        ) { Icon(Icons.Filled.RotateRight, contentDescription = "Rotar a la derecha") }
                    }
                    IconButton(
                        enabled = bmp != null && imageRect != null && cropRect != null,
                        onClick = {
                            val bitmapNow = bmp ?: return@IconButton
                            val rect = cropRect ?: return@IconButton
                            val frame = imageRect ?: return@IconButton
                            val scale = frame.width / bitmapNow.width
                            val left = ((rect.left - frame.left) / scale).roundToInt().coerceIn(0, bitmapNow.width - 1)
                            val top = ((rect.top - frame.top) / scale).roundToInt().coerceIn(0, bitmapNow.height - 1)
                            val right = ((rect.right - frame.left) / scale).roundToInt().coerceIn(left + 1, bitmapNow.width)
                            val bottom = ((rect.bottom - frame.top) / scale).roundToInt().coerceIn(top + 1, bitmapNow.height)
                            val cropped = Bitmap.createBitmap(bitmapNow, left, top, right - left, bottom - top)
                            onSave(PhotoStorage.saveNormalized(context, cropped))
                        }
                    ) { Icon(Icons.Filled.Check, contentDescription = "Guardar") }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .onSizeChanged { boxSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadFailed -> Text(
                            "No se pudo leer la foto.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        bmp == null -> CircularProgressIndicator()
                        else -> {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            val frame = imageRect
                            val rect = cropRect
                            if (frame != null && rect != null) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(frame) {
                                            detectDragGestures(
                                                onDragStart = { start ->
                                                    val r = cropRect ?: return@detectDragGestures
                                                    dragMode = when {
                                                        (start - r.topLeft).getDistance() <= handleRadiusPx -> DragMode.TOP_LEFT
                                                        (start - r.topRight).getDistance() <= handleRadiusPx -> DragMode.TOP_RIGHT
                                                        (start - r.bottomLeft).getDistance() <= handleRadiusPx -> DragMode.BOTTOM_LEFT
                                                        (start - r.bottomRight).getDistance() <= handleRadiusPx -> DragMode.BOTTOM_RIGHT
                                                        r.contains(start) -> DragMode.MOVE
                                                        else -> DragMode.NONE
                                                    }
                                                },
                                                onDragEnd = { dragMode = DragMode.NONE },
                                                onDragCancel = { dragMode = DragMode.NONE }
                                            ) { change, dragAmount ->
                                                val r = cropRect ?: return@detectDragGestures
                                                if (dragMode == DragMode.NONE) return@detectDragGestures
                                                change.consume()
                                                cropRect = when (dragMode) {
                                                    DragMode.MOVE -> {
                                                        val dx = dragAmount.x.coerceIn(frame.left - r.left, frame.right - r.right)
                                                        val dy = dragAmount.y.coerceIn(frame.top - r.top, frame.bottom - r.bottom)
                                                        Rect(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
                                                    }
                                                    DragMode.TOP_LEFT -> Rect(
                                                        left = (r.left + dragAmount.x).coerceIn(frame.left, r.right - minCropSizePx),
                                                        top = (r.top + dragAmount.y).coerceIn(frame.top, r.bottom - minCropSizePx),
                                                        right = r.right,
                                                        bottom = r.bottom
                                                    )
                                                    DragMode.TOP_RIGHT -> Rect(
                                                        left = r.left,
                                                        top = (r.top + dragAmount.y).coerceIn(frame.top, r.bottom - minCropSizePx),
                                                        right = (r.right + dragAmount.x).coerceIn(r.left + minCropSizePx, frame.right),
                                                        bottom = r.bottom
                                                    )
                                                    DragMode.BOTTOM_LEFT -> Rect(
                                                        left = (r.left + dragAmount.x).coerceIn(frame.left, r.right - minCropSizePx),
                                                        top = r.top,
                                                        right = r.right,
                                                        bottom = (r.bottom + dragAmount.y).coerceIn(r.top + minCropSizePx, frame.bottom)
                                                    )
                                                    DragMode.BOTTOM_RIGHT -> Rect(
                                                        left = r.left,
                                                        top = r.top,
                                                        right = (r.right + dragAmount.x).coerceIn(r.left + minCropSizePx, frame.right),
                                                        bottom = (r.bottom + dragAmount.y).coerceIn(r.top + minCropSizePx, frame.bottom)
                                                    )
                                                    DragMode.NONE -> r
                                                }
                                            }
                                        }
                                ) {
                                    val scrim = Color.Black.copy(alpha = 0.55f)
                                    drawRect(color = scrim, topLeft = Offset(0f, 0f), size = Size(size.width, rect.top))
                                    drawRect(color = scrim, topLeft = Offset(0f, rect.bottom), size = Size(size.width, size.height - rect.bottom))
                                    drawRect(color = scrim, topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
                                    drawRect(color = scrim, topLeft = Offset(rect.right, rect.top), size = Size(size.width - rect.right, rect.height))
                                    drawRect(
                                        color = Color.White,
                                        topLeft = rect.topLeft,
                                        size = rect.size,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    val handleSize = 18.dp.toPx()
                                    listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { corner ->
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(corner.x - handleSize / 2f, corner.y - handleSize / 2f),
                                            size = Size(handleSize, handleSize)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    "Arrastra las esquinas para ajustar el recorte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
}
