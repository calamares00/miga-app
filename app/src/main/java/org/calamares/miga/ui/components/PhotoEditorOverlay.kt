package org.calamares.miga.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import org.calamares.miga.L10n
import org.calamares.miga.R
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
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.calamares.miga.MigaApp
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.vision.PhotoEnhanceResult
import org.calamares.miga.data.vision.applyAdjustments
import org.calamares.miga.data.vision.suggestPhotoAdjustments
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Full-screen photo editor: rotate in 90 degree steps and crop with a free-ratio frame (dragging
 * its corners, like Android's own cropper) before saving it normalised (recompressed and resized
 * JPEG, see [PhotoStorage]) to internal storage.
 *
 * With AI on, "Improve with AI" asks the model how to correct light and colour (see
 * [suggestPhotoAdjustments]), applies it on the device and saves the result at the size a phone
 * needs ([PhotoStorage.saveOptimized]). Holding "Compare" shows the original.
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
    val aiEnabled = rememberAiEnabled()
    val scope = rememberCoroutineScope()
    /** The photo before the AI improvement, null while it has not been improved. */
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var enhancing by remember { mutableStateOf(false) }
    var enhanceError by remember { mutableStateOf<String?>(null) }
    val compareSource = remember { MutableInteractionSource() }
    val comparing by compareSource.collectIsPressedAsState()

    fun enhance() {
        val photo = bitmap ?: return
        enhancing = true
        enhanceError = null
        scope.launch {
            val settings = (context.applicationContext as MigaApp).settingsRepository
            val result = settings.runAi<PhotoEnhanceResult>(
                needsImages = true,
                errorOf = { (it as? PhotoEnhanceResult.Error)?.reason },
                error = { PhotoEnhanceResult.Error(it) }
            ) { ai -> ai.suggestPhotoAdjustments(photo) }
            when (result) {
                is PhotoEnhanceResult.Success -> {
                    val improved = withContext(Dispatchers.Default) { applyAdjustments(photo, result.adjustments) }
                    // The user may have rotated it meanwhile: the result is only kept for the photo it was made from.
                    if (bitmap === photo) {
                        original = photo
                        bitmap = improved
                    }
                }
                is PhotoEnhanceResult.Error -> enhanceError = result.reason
                null -> enhanceError = L10n.str(R.string.ai_no_provider)
            }
            enhancing = false
        }
    }

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

    // When the photo loads or is rotated (which changes its proportions) the crop frame starts
    // covering the whole image; the user shrinks it by dragging the corners.
    LaunchedEffect(imageRect) {
        if (imageRect != null) cropRect = imageRect
    }

    val handleRadiusPx = with(density) { 24.dp.toPx() }
    val minCropSizePx = with(density) { 48.dp.toPx() }

    // Full-screen layer inside the app window rather than a Dialog, so it gets the real system bar
    // insets and never shows the screen underneath.
    BackHandler(onBack = onCancel)
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel)) }
                    Row {
                        IconButton(
                            enabled = bmp != null && !enhancing,
                            onClick = {
                                bitmap = bitmap?.let { PhotoStorage.rotateBitmap(it, -90f) }
                                original = original?.let { PhotoStorage.rotateBitmap(it, -90f) }
                                cropRect = null
                            }
                        ) { Icon(Icons.Filled.RotateLeft, contentDescription = L10n.str(R.string.rotate_left)) }
                        IconButton(
                            enabled = bmp != null && !enhancing,
                            onClick = {
                                bitmap = bitmap?.let { PhotoStorage.rotateBitmap(it, 90f) }
                                original = original?.let { PhotoStorage.rotateBitmap(it, 90f) }
                                cropRect = null
                            }
                        ) { Icon(Icons.Filled.RotateRight, contentDescription = L10n.str(R.string.rotate_right)) }
                    }
                    IconButton(
                        enabled = bmp != null && imageRect != null && cropRect != null && !enhancing,
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
                            if (original != null) {
                                val saved = PhotoStorage.saveOptimized(context, cropped)
                                PhotoStorage.sizeOf(context, sourceUri)?.let { before ->
                                    Toast.makeText(
                                        context,
                                        L10n.str(
                                            R.string.photo_optimized_x_y,
                                            Formatter.formatShortFileSize(context, before),
                                            Formatter.formatShortFileSize(context, saved.bytes)
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                onSave(saved.uri)
                            } else {
                                onSave(PhotoStorage.saveNormalized(context, cropped))
                            }
                        }
                    ) { Icon(Icons.Filled.Check, contentDescription = L10n.str(R.string.save)) }
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
                            L10n.str(R.string.couldnt_read_photo_2),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        bmp == null -> CircularProgressIndicator()
                        else -> {
                            Image(
                                bitmap = (original.takeIf { comparing } ?: bmp).asImageBitmap(),
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

                if (aiEnabled && bmp != null) {
                    EnhanceBar(
                        enhanced = original != null,
                        enhancing = enhancing,
                        compareSource = compareSource,
                        onEnhance = { enhance() },
                        onUndo = {
                            original?.let { bitmap = it }
                            original = null
                        }
                    )
                    enhanceError?.let { reason ->
                        ErrorMessage(reason, modifier = Modifier.padding(horizontal = 16.dp), onRetry = { enhance() })
                    }
                }
                Text(
                    L10n.str(
                        when {
                            comparing -> R.string.photo_showing_original
                            original != null -> R.string.photo_compare_hint
                            else -> R.string.drag_corners_adjust_crop
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
        }
    }
}

/** "Improve with AI", or once improved, "Compare" (hold to see the original) and "Undo". */
@Composable
private fun EnhanceBar(
    enhanced: Boolean,
    enhancing: Boolean,
    compareSource: MutableInteractionSource,
    onEnhance: () -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            enhancing -> FilledTonalButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(L10n.str(R.string.photo_enhancing), maxLines = 1)
            }
            !enhanced -> FilledTonalButton(onClick = onEnhance) {
                ButtonContent(Icons.Filled.AutoAwesome, L10n.str(R.string.photo_enhance_ai))
            }
            else -> {
                // Pressed and held rather than clicked: the original shows only while the finger is down.
                OutlinedButton(onClick = {}, interactionSource = compareSource) {
                    ButtonContent(Icons.Filled.Compare, L10n.str(R.string.photo_compare))
                }
                TextButton(onClick = onUndo) {
                    ButtonContent(Icons.Filled.Undo, L10n.str(R.string.photo_undo_enhance))
                }
            }
        }
    }
}
