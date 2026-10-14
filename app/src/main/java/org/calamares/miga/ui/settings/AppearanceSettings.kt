package org.calamares.miga.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.calamares.miga.AppLanguage
import org.calamares.miga.L10n
import org.calamares.miga.R
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.PhotoFrame
import org.calamares.miga.ui.theme.recipePhotoFrame
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.ui.theme.Ink
import org.calamares.miga.ui.theme.NightBackground
import org.calamares.miga.ui.theme.NightContainerHigh
import org.calamares.miga.ui.theme.NightSurface
import org.calamares.miga.ui.theme.Paper
import org.calamares.miga.ui.theme.PaperContainerHigh
import org.calamares.miga.ui.theme.PaperElevated
import org.calamares.miga.ui.theme.accentColorFor
import org.calamares.miga.ui.theme.accentSoftColorFor

private const val SWATCHES_PER_ROW = 4
private const val FRAMES_PER_ROW = 3

/**
 * Settings > Appearance: a live preview, the light/dark mode as three picture cards, the accent
 * colour as named swatches, the photo frame as framed miniatures and the language as a segmented
 * control. Each group sits in its own card so the screen reads as clear, separate choices.
 */
@Composable
fun AppearanceSettings(
    themeMode: ThemeMode,
    colorTheme: ColorTheme,
    onThemeMode: (ThemeMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    photoFrame: PhotoFrame,
    onPhotoFrame: (PhotoFrame) -> Unit,
    /** One of the user's recipe photos for the previews; a drawn dish without any. */
    samplePhoto: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        ThemePreview(photoFrame, samplePhoto)

        AppearanceGroup(L10n.str(R.string.appearance_theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeCard(
                        mode = mode,
                        selected = themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AppearanceGroup(L10n.str(R.string.colour)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorTheme.entries.chunked(SWATCHES_PER_ROW).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { theme ->
                            ColorSwatch(
                                theme = theme,
                                selected = colorTheme == theme,
                                onClick = { onColorTheme(theme) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keeps the swatches of a shorter last row in the same columns.
                        repeat(SWATCHES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        AppearanceGroup(L10n.str(R.string.photo_frame_title)) {
            Text(
                L10n.str(R.string.photo_frame_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoFrame.entries.chunked(FRAMES_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { frame ->
                            FrameOption(
                                frame = frame,
                                samplePhoto = samplePhoto,
                                selected = photoFrame == frame,
                                onClick = { onPhotoFrame(frame) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(FRAMES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        AppearanceGroup(L10n.str(R.string.language_title)) {
            LanguagePicker()
        }
    }
}

@Composable
private fun AppearanceGroup(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/**
 * A small recipe card drawn with the current colours, so a change of theme or colour can be seen
 * at once.
 */
@Composable
private fun ThemePreview(photoFrame: PhotoFrame, samplePhoto: String?) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                L10n.str(R.string.appearance_preview),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
            Surface(color = colors.surface, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SamplePhoto(
                        samplePhoto = samplePhoto,
                        frame = photoFrame,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            L10n.str(R.string.appearance_preview_recipe),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PreviewChip(L10n.str(R.string.easy), colors.secondaryContainer, colors.onSecondaryContainer)
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Text("30 min", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String, background: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Light, dark or system mode as a card with a miniature of the app in that mode. */
@Composable
private fun ThemeModeCard(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        label = "themeModeBorder"
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "themeModeBorderWidth")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(16.dp))
        ) {
            when (mode) {
                ThemeMode.LIGHT -> MiniScreen(dark = false, accent = accent)
                ThemeMode.DARK -> MiniScreen(dark = true, accent = accent)
                ThemeMode.SYSTEM -> Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))) {
                        MiniScreen(dark = false, accent = accent)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { MiniScreen(dark = true, accent = accent) }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                when (mode) {
                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                },
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                // The long "System default" does not fit in a third of the width.
                if (mode == ThemeMode.SYSTEM) L10n.str(R.string.system_short) else mode.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A miniature screen: a top bar line, two content cards and a button, in light or dark colours. */
@Composable
private fun MiniScreen(dark: Boolean, accent: Color) {
    val background = if (dark) NightBackground else Paper
    val card = if (dark) NightSurface else PaperElevated
    val line = if (dark) NightContainerHigh else PaperContainerHigh
    val text = if (dark) Color.White.copy(alpha = 0.75f) else Ink.copy(alpha = 0.7f)
    Column(
        modifier = Modifier.fillMaxSize().background(background).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(text))
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(card).padding(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.7f)))
                Spacer(modifier = Modifier.width(5.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(text))
                    Box(modifier = Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(line))
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .width(24.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent)
        )
    }
}

/** A colour theme: its accent over its soft tone, with the name under it. */
@Composable
private fun ColorSwatch(theme: ColorTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = accentColorFor(theme)
    val ringColor by animateColorAsState(if (selected) accent else Color.Transparent, label = "swatchRing")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // A ring with a gap around the selected colour, as in Android's own wallpaper colours.
        Box(
            modifier = Modifier.size(52.dp).border(2.dp, ringColor, CircleShape).padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.linearGradient(0.5f to accent, 0.5f to accentSoftColorFor(theme))),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(
            // "Jardín (por defecto)" is too long under a swatch: the part in brackets goes.
            theme.label.substringBefore(" (").trim(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** UI language: system default, Spanish or English. Changing it restarts the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = remember { L10n.language(context) }
    var pending by remember { mutableStateOf<AppLanguage?>(null) }
    val options = AppLanguage.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, language ->
            SegmentedButton(
                selected = current == language,
                onClick = { if (language != current) pending = language },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(languageLabel(language), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Text(
        L10n.str(R.string.language_restart_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    pending?.let { language ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(L10n.str(R.string.language_restart_title)) },
            text = { Text(L10n.str(R.string.language_restart_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val activity = context.findActivity()
                    if (activity != null) L10n.setLanguage(activity, language) else pending = null
                }) { Text(L10n.str(R.string.language_restart_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

/** Language names are shown in their own language, as in Android's settings. */
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> L10n.str(R.string.system_short)
    AppLanguage.SPANISH -> "Español"
    AppLanguage.ENGLISH -> "English"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** One frame as a miniature of a recipe photo wearing it, selectable like the theme cards. */
@Composable
private fun FrameOption(frame: PhotoFrame, samplePhoto: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        label = "frameBorder"
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "frameBorderWidth")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(16.dp))
        ) {
            SamplePhoto(samplePhoto = samplePhoto, frame = frame, modifier = Modifier.fillMaxSize())
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            frame.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The user's [samplePhoto] with [frame], or a drawn dish when there is no photo yet. */
@Composable
private fun SamplePhoto(samplePhoto: String?, frame: PhotoFrame, modifier: Modifier = Modifier) {
    if (samplePhoto != null) {
        AsyncImage(
            model = samplePhoto,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.recipePhotoFrame(frame)
        )
    } else {
        DrawnDish(modifier.recipePhotoFrame(frame))
    }
}

/** A plate of food on a wooden table, drawn, for the previews before the user has any photo. */
@Composable
private fun DrawnDish(modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Brush.linearGradient(listOf(Color(0xFFB07A4F), Color(0xFF7A4E2D)), start = Offset.Zero, end = Offset(size.width, size.height)))
        val plate = size.minDimension * 0.4f
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = plate * 1.02f, center = center + Offset(plate * 0.06f, plate * 0.08f))
        drawCircle(Color(0xFFF7F4EE), radius = plate, center = center)
        drawCircle(Color(0xFFE9E3D8), radius = plate * 0.72f, center = center)
        // Pasta, tomato and basil.
        drawCircle(Color(0xFFF1C25B), radius = plate * 0.5f, center = center)
        drawCircle(Color(0xFFD9442E), radius = plate * 0.2f, center = center + Offset(-plate * 0.18f, -plate * 0.1f))
        drawCircle(Color(0xFFC7382A), radius = plate * 0.15f, center = center + Offset(plate * 0.22f, plate * 0.12f))
        drawCircle(Color(0xFF4E9A3E), radius = plate * 0.1f, center = center + Offset(plate * 0.05f, -plate * 0.3f))
        drawCircle(Color(0xFF3F8A33), radius = plate * 0.08f, center = center + Offset(-plate * 0.25f, plate * 0.22f))
    }
}
