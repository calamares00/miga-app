package org.calamares.miga.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import org.calamares.miga.L10n
import org.calamares.miga.R

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen photo viewer drawn over the current screen: swipe left or right to go through
 * [photos], pinch or double-tap to zoom (and drag to move around a zoomed photo), single tap to
 * hide or show the controls. Back or the close button calls [onClose].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer(photos: List<String>, initialIndex: Int, contentDescription: String?, onClose: () -> Unit) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.lastIndex)) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    LightSystemBarIcons()
    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            // A zoomed photo is moved with the finger instead of turning the page.
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomablePhoto(
                model = photos[page],
                contentDescription = contentDescription,
                // Only the photo on screen can stay zoomed; the others reset when they leave.
                active = pagerState.settledPage == page,
                onZoomedChange = { if (pagerState.settledPage == page) zoomed = it },
                onTap = { controlsVisible = !controlsVisible }
            )
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.close), tint = Color.White)
                    }
                    if (photos.size > 1) {
                        Text(
                            "${pagerState.currentPage + 1} / ${photos.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (photos.size > 1) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (index == pagerState.currentPage) 1f else 0.45f))
                        )
                    }
                }
            }
        }
    }
}

/** While shown, the status and navigation bar icons are light, to stay visible on black. */
@Composable
private fun LightSystemBarIcons() {
    val activity = LocalContext.current.findActivity() ?: return
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

/**
 * One photo that can be zoomed with two fingers or a double tap and moved while zoomed. At normal
 * size, one-finger drags are left untouched so the pager can turn the page.
 */
@Composable
private fun ZoomablePhoto(
    model: String,
    contentDescription: String?,
    active: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1f) / 2f
        val maxY = size.height * (forScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun update(newScale: Float, newOffset: Offset) {
        val wasZoomed = scale > 1f
        scale = newScale.coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else clampOffset(newOffset, scale)
        if ((scale > 1f) != wasZoomed) onZoomedChange(scale > 1f)
    }

    LaunchedEffect(active) {
        if (!active && scale != 1f) update(1f, Offset.Zero)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            update(1f, Offset.Zero)
                        } else {
                            // Zoom towards the tapped point.
                            val center = Offset(this.size.width / 2f, this.size.height / 2f)
                            update(DOUBLE_TAP_ZOOM, (center - tap) * (DOUBLE_TAP_ZOOM - 1f))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // Two fingers always zoom; one finger only moves an already zoomed photo.
                        if (fingers >= 2 || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            update(scale * zoomChange, offset * zoomChange + panChange)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
