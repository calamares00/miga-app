package org.calamares.miga.ui.welcome

import android.annotation.SuppressLint
import androidx.core.net.toUri
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.calamares.miga.data.cook.CookTimers
import org.calamares.miga.ui.components.openWebLink

/** One page of the tour: what Miga does in one area, in three key points, and where to read more. */
private data class TourPage(
    val icon: ImageVector,
    val kicker: String,
    val title: String,
    val body: String,
    val points: List<Pair<ImageVector, String>> = emptyList(),
    /** Page of the website about it ("funciones/"...), in the app's language. */
    val webPage: String? = null,
    val kind: Kind = Kind.INFO
) {
    enum class Kind { INFO, PERMISSIONS, FINISH }
}

private fun tourPages() = listOf(
    TourPage(
        Icons.Filled.WavingHand, L10n.str(R.string.tour_kicker_welcome), L10n.str(R.string.tour_welcome_title), L10n.str(R.string.tour_welcome_body),
        listOf(
            Icons.AutoMirrored.Filled.MenuBook to L10n.str(R.string.tour_welcome_point_recipes),
            Icons.Filled.ShoppingCart to L10n.str(R.string.tour_welcome_point_shopping),
            Icons.Filled.Lock to L10n.str(R.string.tour_welcome_point_private)
        )
    ),
    TourPage(
        Icons.AutoMirrored.Filled.MenuBook, L10n.str(R.string.tour_kicker_recipes), L10n.str(R.string.tour_recipes_title), L10n.str(R.string.tour_recipes_body),
        listOf(
            Icons.AutoMirrored.Filled.MenuBook to L10n.str(R.string.tour_recipes_point_books),
            Icons.AutoMirrored.Filled.OpenInNew to L10n.str(R.string.tour_recipes_point_import),
            Icons.Filled.Favorite to L10n.str(R.string.tour_recipes_point_packs)
        ),
        L10n.str(R.string.web_page_features)
    ),
    TourPage(
        Icons.Filled.Restaurant, L10n.str(R.string.tour_kicker_cooking), L10n.str(R.string.tour_cooking_title), L10n.str(R.string.tour_cooking_body),
        listOf(
            Icons.Filled.Restaurant to L10n.str(R.string.tour_cooking_point_mode),
            Icons.Filled.Alarm to L10n.str(R.string.tour_cooking_point_timers),
            Icons.Filled.Mic to L10n.str(R.string.tour_cooking_point_voice)
        ),
        L10n.str(R.string.web_page_features)
    ),
    TourPage(
        Icons.Filled.Lightbulb, L10n.str(R.string.tour_kicker_tips), L10n.str(R.string.tour_tips_title), L10n.str(R.string.tour_tips_body),
        listOf(
            Icons.Filled.Lightbulb to L10n.str(R.string.tour_tips_point_daily),
            Icons.AutoMirrored.Filled.MenuBook to L10n.str(R.string.tour_tips_point_recipe),
            Icons.Filled.Star to L10n.str(R.string.tour_tips_point_save)
        ),
        L10n.str(R.string.web_page_features)
    ),
    TourPage(
        Icons.Filled.ShoppingCart, L10n.str(R.string.tour_kicker_shopping), L10n.str(R.string.tour_shopping_title), L10n.str(R.string.tour_shopping_body),
        listOf(
            Icons.Filled.ShoppingCart to L10n.str(R.string.tour_shopping_point_recipe),
            Icons.Filled.PhotoCamera to L10n.str(R.string.tour_shopping_point_scan),
            Icons.Filled.CheckCircle to L10n.str(R.string.tour_shopping_point_aisles)
        ),
        L10n.str(R.string.web_page_features)
    ),
    TourPage(
        Icons.Filled.Shield, L10n.str(R.string.tour_kicker_health), L10n.str(R.string.tour_health_title), L10n.str(R.string.tour_health_body),
        listOf(
            Icons.Filled.Shield to L10n.str(R.string.tour_health_point_allergens),
            Icons.Filled.Favorite to L10n.str(R.string.tour_health_point_coeliac),
            Icons.Filled.CheckCircle to L10n.str(R.string.tour_health_point_guide)
        ),
        L10n.str(R.string.web_page_health)
    ),
    TourPage(
        Icons.Filled.AutoAwesome, L10n.str(R.string.tour_kicker_assistant), L10n.str(R.string.tour_assistant_title), L10n.str(R.string.tour_assistant_body),
        listOf(
            Icons.Filled.AutoAwesome to L10n.str(R.string.tour_assistant_point_what),
            Icons.Filled.Lock to L10n.str(R.string.tour_assistant_point_key),
            Icons.Filled.Shield to L10n.str(R.string.tour_assistant_point_private)
        ),
        L10n.str(R.string.web_page_assistant)
    ),
    TourPage(
        Icons.Filled.Notifications, L10n.str(R.string.tour_kicker_permissions), L10n.str(R.string.tour_permissions_title), L10n.str(R.string.tour_permissions_body),
        kind = TourPage.Kind.PERMISSIONS
    ),
    TourPage(
        Icons.Filled.CheckCircle, L10n.str(R.string.tour_kicker_ready), L10n.str(R.string.tour_ready_title), L10n.str(R.string.tour_ready_body),
        webPage = "", kind = TourPage.Kind.FINISH
    )
)

/** Destination opened when the welcome flow ends (null for the main screen). */
typealias WelcomeDestination = String?

/**
 * The welcome tour: what Miga does (recipes, cooking, tips, shopping, health, the assistant), in a few pages with three key points each and a link to the
 * website; the permissions it may ask for and what each one is for; and, at the end, start from
 * scratch, explore recipe packs or restore a backup. [onFinish] receives the route to open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    packsRoute: String,
    backupRoute: String,
    onFinish: (WelcomeDestination) -> Unit
) {
    val pages = remember { tourPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = pages[pagerState.currentPage]
    val isLast = current.kind == TourPage.Kind.FINISH

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!isLast) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pages.lastIndex) } }) { Text(L10n.str(R.string.skip)) }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PageHeader(page)
                when (page.kind) {
                    TourPage.Kind.INFO -> KeyPoints(page.points)
                    TourPage.Kind.PERMISSIONS -> PermissionList()
                    TourPage.Kind.FINISH -> Unit
                }
                page.webPage?.let { path ->
                    TextButton(onClick = { openWebLink(context, L10n.str(R.string.web_site) + path) }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(L10n.str(R.string.tour_more_on_web))
                    }
                }
            }
        }
        Dots(count = pages.size, current = pagerState.currentPage)
        Column(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isLast) {
                Button(onClick = { onFinish(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(L10n.str(R.string.get_started)) }
                OutlinedButton(onClick = { onFinish(packsRoute) }, modifier = Modifier.fillMaxWidth()) { Text(L10n.str(R.string.explore_recipe_packs)) }
                TextButton(onClick = { onFinish(backupRoute) }, modifier = Modifier.fillMaxWidth()) { Text(L10n.str(R.string.restore_backup)) }
            } else {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(L10n.str(if (current.kind == TourPage.Kind.PERMISSIONS) R.string.tour_continue else R.string.next_2)) }
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(L10n.str(R.string.tour_back)) }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun PageHeader(page: TourPage) {
    Box(
        modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(46.dp))
    }
    Text(
        page.kicker.uppercase(L10n.locale()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp)
    )
    Text(
        page.title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp).widthIn(max = 440.dp)
    )
    Text(
        page.body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp).widthIn(max = 440.dp)
    )
}

@Composable
private fun KeyPoints(points: List<Pair<ImageVector, String>>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(top = 24.dp).widthIn(max = 440.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            points.forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .padding(vertical = 18.dp)
            .semantics { contentDescription = L10n.str(R.string.page_x_x, current + 1, count) },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dot")
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dotWidth")
            Box(modifier = Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/** A permission Miga may ask for, with what it is for. All of them are optional. */
private enum class TourPermission(val icon: ImageVector, val title: Int, val purpose: Int, val permission: String?) {
    // Only asked on Android 13+, where the permission exists (see isGranted).
    @SuppressLint("InlinedApi")
    NOTIFICATIONS(Icons.Filled.Notifications, R.string.permission_notifications, R.string.permission_notifications_purpose, Manifest.permission.POST_NOTIFICATIONS),
    ALARMS(Icons.Filled.Alarm, R.string.permission_alarms, R.string.permission_alarms_purpose, null),
    MICROPHONE(Icons.Filled.Mic, R.string.permission_microphone, R.string.permission_microphone_purpose, Manifest.permission.RECORD_AUDIO),
    CAMERA(Icons.Filled.PhotoCamera, R.string.permission_camera, R.string.permission_camera_purpose, Manifest.permission.CAMERA);

    fun isGranted(context: Context): Boolean = when (this) {
        NOTIFICATIONS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ALARMS -> CookTimers.canBeExact(context)
        else -> ContextCompat.checkSelfPermission(context, permission!!) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * The permissions, each with what it is for, whether it is granted and a button to allow it.
 * They are checked again when the user comes back from Android's settings.
 */
@Composable
private fun PermissionList() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A permission denied after asking can only be given in Android's settings.
    var askedOnce by remember { mutableStateOf(emptySet<TourPermission>()) }
    var asking by remember { mutableStateOf<TourPermission?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asking?.let { askedOnce = askedOnce + it }
        refresh++
    }
    Column(
        modifier = Modifier.padding(top = 20.dp).widthIn(max = 440.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TourPermission.entries.forEach { item ->
            val granted = remember(refresh) { item.isGranted(context) }
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L10n.str(item.title), style = MaterialTheme.typography.titleSmall)
                        Text(L10n.str(item.purpose), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (granted) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = L10n.str(R.string.permission_granted),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        FilledTonalButton(onClick = {
                            when {
                                item == TourPermission.ALARMS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                                    openSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                                item in askedOnce ->
                                    openSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                                else -> {
                                    asking = item
                                    launcher.launch(item.permission!!)
                                }
                            }
                        }) { Text(L10n.str(R.string.permission_allow)) }
                    }
                }
            }
        }
        Text(
            L10n.str(R.string.tour_permissions_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

private fun openSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
