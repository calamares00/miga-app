package com.bmo00.miga.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bmo00.miga.data.local.PhotoStorage
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Editor de foto a pantalla completa: rotar en pasos de 90º y recortar (arrastrando la foto para
 * elegir qué parte queda dentro del marco de [aspectRatio]) antes de guardarla normalizada
 * (JPEG recomprimido y redimensionado, ver [PhotoStorage]) en el almacenamiento interno.
 */
@Composable
fun PhotoEditorOverlay(sourceUri: Uri, aspectRatio: Float, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(sourceUri) {
        val loaded = withContext(Dispatchers.IO) { PhotoStorage.loadBitmap(context, sourceUri) }
        bitmap = loaded
        loadFailed = loaded == null
    }

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
                            enabled = bitmap != null,
                            onClick = {
                                bitmap = bitmap?.let { PhotoStorage.rotateBitmap(it, -90f) }
                                panOffset = Offset.Zero
                            }
                        ) { Icon(Icons.Filled.RotateLeft, contentDescription = "Rotar a la izquierda") }
                        IconButton(
                            enabled = bitmap != null,
                            onClick = {
                                bitmap = bitmap?.let { PhotoStorage.rotateBitmap(it, 90f) }
                                panOffset = Offset.Zero
                            }
                        ) { Icon(Icons.Filled.RotateRight, contentDescription = "Rotar a la derecha") }
                    }
                    IconButton(
                        enabled = bitmap != null,
                        onClick = {
                            val bmp = bitmap ?: return@IconButton
                            val cropped = PhotoStorage.cropToFrame(
                                bmp,
                                frameSize.width.toFloat(),
                                frameSize.height.toFloat(),
                                panOffset.x,
                                panOffset.y
                            )
                            onSave(PhotoStorage.saveNormalized(context, cropped))
                        }
                    ) { Icon(Icons.Filled.Check, contentDescription = "Guardar") }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    when {
                        loadFailed -> Text(
                            "No se pudo leer la foto.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        bmp == null -> CircularProgressIndicator()
                        else -> Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .border(2.dp, Color.White)
                                .onSizeChanged { frameSize = it }
                                .pointerInput(bmp) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val scale = maxOf(
                                            frameSize.width.toFloat() / bmp.width,
                                            frameSize.height.toFloat() / bmp.height
                                        )
                                        val maxOffsetX = ((bmp.width * scale - frameSize.width) / 2f).coerceAtLeast(0f)
                                        val maxOffsetY = ((bmp.height * scale - frameSize.height) / 2f).coerceAtLeast(0f)
                                        panOffset = Offset(
                                            (panOffset.x + dragAmount.x).coerceIn(-maxOffsetX, maxOffsetX),
                                            (panOffset.y + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        )
                                    }
                                }
                        ) {
                            val scale = maxOf(size.width / bmp.width, size.height / bmp.height)
                            val destWidth = bmp.width * scale
                            val destHeight = bmp.height * scale
                            val left = (size.width - destWidth) / 2f + panOffset.x
                            val top = (size.height - destHeight) / 2f + panOffset.y
                            drawImage(
                                image = bmp.asImageBitmap(),
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bmp.width, bmp.height),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(destWidth.roundToInt(), destHeight.roundToInt())
                            )
                        }
                    }
                }

                Text(
                    "Arrastra la foto para elegir qué parte quieres conservar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
}
