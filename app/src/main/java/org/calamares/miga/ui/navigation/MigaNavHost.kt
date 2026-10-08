package org.calamares.miga.ui.navigation

import org.calamares.miga.ui.ideas.IdeasViewModel
import org.calamares.miga.ui.ideas.IdeasScreen
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Alignment
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import org.calamares.miga.data.local.SettingsRepository
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.calamares.miga.MigaApp
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.ui.bulkimport.BulkImportScreen
import org.calamares.miga.ui.bulkimport.BulkImportViewModel
import org.calamares.miga.ui.books.RecipeBookEditorScreen
import org.calamares.miga.ui.books.RecipeBookEditorViewModel
import org.calamares.miga.ui.books.RecipeBooksScreen
import org.calamares.miga.ui.books.RecipeBooksViewModel
import org.calamares.miga.ui.components.DocumentScreen
import org.calamares.miga.ui.components.FileExportHost
import org.calamares.miga.ui.detail.RecipeDetailPager
import org.calamares.miga.ui.detail.RecipeDetailViewModel
import org.calamares.miga.ui.dishsearch.DishSearchScreen
import org.calamares.miga.ui.dishsearch.DishSearchViewModel
import org.calamares.miga.ui.editor.RecipeEditorScreen
import org.calamares.miga.ui.editor.RecipeEditorViewModel
import org.calamares.miga.ui.list.RecipeListScreen
import org.calamares.miga.ui.list.RecipeListViewModel
import org.calamares.miga.ui.packs.PackDetailScreen
import org.calamares.miga.ui.packs.PackDetailViewModel
import org.calamares.miga.ui.packs.PacksCatalogScreen
import org.calamares.miga.ui.packs.PacksCatalogViewModel
import org.calamares.miga.ui.search.GlobalSearchScreen
import org.calamares.miga.ui.search.GlobalSearchViewModel
import org.calamares.miga.data.share.ShoppingIntents
import org.calamares.miga.ui.shoppinglist.ShoppingListScreen
import org.calamares.miga.ui.shoppinglist.ShoppingListViewModel
import org.calamares.miga.ui.stats.StatsScreen
import org.calamares.miga.ui.stats.StatsViewModel
import org.calamares.miga.ui.settings.AboutScreen
import org.calamares.miga.ui.settings.ChangelogScreen
import org.calamares.miga.ui.settings.HelpScreen
import org.calamares.miga.ui.settings.ManageCategoriesScreen
import org.calamares.miga.ui.settings.ManageCategoriesViewModel
import org.calamares.miga.ui.settings.ManageIngredientCategoriesScreen
import org.calamares.miga.ui.settings.ManageIngredientCategoriesViewModel
import org.calamares.miga.ui.settings.ManageIngredientsScreen
import org.calamares.miga.ui.settings.ManageIngredientsViewModel
import org.calamares.miga.ui.settings.ManageUtensilsScreen
import org.calamares.miga.ui.settings.ManageUtensilsViewModel
import org.calamares.miga.ui.settings.SettingsHomeScreen
import org.calamares.miga.ui.settings.SettingsSection
import org.calamares.miga.ui.settings.SettingsSectionScreen
import org.calamares.miga.ui.settings.SettingsViewModel
import org.calamares.miga.ui.sync.SyncConnectionsScreen
import org.calamares.miga.ui.sync.SyncConnectionsViewModel

private fun repositoryOf(context: android.content.Context): RecipeRepository =
    (context.applicationContext as MigaApp).repository

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(Destinations.BOOKS_ROUTE, L10n.str(R.string.books), Icons.Outlined.MenuBook),
    BottomTab(Destinations.FAVORITES_ROUTE, L10n.str(R.string.favourites_2), Icons.Filled.Favorite),
    BottomTab(Destinations.SHOPPING_LIST_ROUTE, L10n.str(R.string.shopping), Icons.Filled.ShoppingCart),
    BottomTab(Destinations.SEARCH_ROUTE, L10n.str(R.string.search), Icons.Filled.Search),
    BottomTab(Destinations.SETTINGS_ROUTE, L10n.str(R.string.settings), Icons.Filled.Settings)
)

private val TAB_ROUTES = BOTTOM_TABS.map { it.route }.toSet()

/** Height of the Material 3 navigation bar, without the system navigation bar under it. */
private val BOTTOM_BAR_HEIGHT = 80.dp

// Transitions follow Material's motion patterns: "shared axis" when opening or closing a screen
// (a short slide plus a fade) and "fade through" between tabs. The outgoing screen fades out
// quickly, so it is never seen half-transparent behind the new one (the default was a 700 ms
// cross-fade that showed both screens, and any change in the old one, for most of a second).
private const val PUSH_MILLIS = 300
private const val FADE_OUT_MILLIS = 90
private const val FADE_IN_MILLIS = 210
private const val BAR_ANIMATION_MILLIS = 200

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

private fun pushEnter(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(PUSH_MILLIS, easing = FastOutSlowInEasing)) { width -> if (forward) width / 10 else -width / 10 } +
        fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))

private fun pushExit(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(PUSH_MILLIS, easing = FastOutSlowInEasing)) { width -> if (forward) -width / 10 else width / 10 } +
        fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))

private fun tabEnter(): EnterTransition =
    fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing), initialScale = 0.96f)

private fun tabExit(): ExitTransition = fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))

/** A destination; see [ScreenFrame]. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(route, arguments) { entry -> ScreenFrame(isTab = route in TAB_ROUTES) { content(entry) } }
}

/**
 * Frame of every screen. The app draws edge to edge and the window is never panned for the
 * keyboard (adjustResize in the manifest), so every screen is lifted above it here, once. Tabs
 * also leave room for the bottom bar drawn over them, which already covers part of the keyboard's
 * height. That room is fixed, not taken from the bar, so a tab keeps its size while it animates
 * out even though the bar is already hiding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenFrame(isTab: Boolean, content: @Composable () -> Unit) {
    val frame = if (isTab) {
        val barSpace = PaddingValues(bottom = BOTTOM_BAR_HEIGHT + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        Modifier.padding(barSpace).consumeWindowInsets(barSpace)
    } else {
        Modifier
    }
    Box(modifier = Modifier.fillMaxSize().then(frame).imePadding()) { content() }
}

@Composable
fun MigaNavHost(initialRoute: String? = null) {
    val navController = rememberNavController()
    // Destination chosen on the welcome screen (packs, restore a backup...), opened on top of the
    // main screen.
    LaunchedEffect(initialRoute) {
        if (initialRoute != null) runCatching { navController.navigate(initialRoute) }
    }
    val context = LocalContext.current
    val repository = repositoryOf(context)
    val settingsRepository = (context.applicationContext as MigaApp).settingsRepository
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    /**
     * Text shared to Miga or the widget button: go to the shopping tab, which consumes the event.
     */
    val shoppingEvent by ShoppingIntents.event.collectAsState()
    LaunchedEffect(shoppingEvent) {
        if (shoppingEvent != null) {
            navController.navigate(Destinations.SHOPPING_LIST_ROUTE) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // The bar is drawn over the screens instead of shrinking them: the tabs leave room for it
    // themselves (see ScreenFrame), so a screen never changes size while it animates out. Before,
    // the bar vanished as soon as a non-tab screen opened and the outgoing tab grew and jumped
    // under the transition.
    val showBottomBar = currentRoute == null || currentRoute in TAB_ROUTES
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Destinations.BOOKS_ROUTE,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (isTabSwitch()) tabEnter() else pushEnter(forward = true) },
            exitTransition = { if (isTabSwitch()) tabExit() else pushExit(forward = true) },
            popEnterTransition = { if (isTabSwitch()) tabEnter() else pushEnter(forward = false) },
            popExitTransition = { if (isTabSwitch()) tabExit() else pushExit(forward = false) }
        ) {
            screens(navController, repository, settingsRepository, context)
        }
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(tween(BAR_ANIMATION_MILLIS)) { it },
            exit = slideOutVertically(tween(BAR_ANIMATION_MILLIS)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NavigationBar {
                BOTTOM_TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
        FileExportHost()
    }
}

/** Every destination of the app. */
private fun NavGraphBuilder.screens(
    navController: NavHostController,
    repository: RecipeRepository,
    settingsRepository: SettingsRepository,
    context: android.content.Context
) {
    screen(Destinations.BOOKS_ROUTE) {
        val viewModel: RecipeBooksViewModel = viewModel(
            factory = viewModelFactory { initializer { RecipeBooksViewModel(repository, settingsRepository) } }
        )
        RecipeBooksScreen(
            viewModel = viewModel,
            onBookClick = { navController.navigate(Destinations.book(it)) },
            onAddBookClick = { navController.navigate(Destinations.bookEditor()) },
            onEditBookClick = { navController.navigate(Destinations.bookEditor(it)) },
            onExplorePacks = { navController.navigate(Destinations.PACKS_CATALOG_ROUTE) },
            onOpenIdeas = { navController.navigate(Destinations.IDEAS_ROUTE) }
        )
    }

    screen(Destinations.IDEAS_ROUTE) {
        val viewModel: IdeasViewModel = viewModel(
            factory = viewModelFactory { initializer { IdeasViewModel(repository, settingsRepository) } }
        )
        IdeasScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onRecipeClick = { navController.navigate(Destinations.detail(it)) },
            onCreateDish = { bookId, dish ->
                navController.navigate(
                    Destinations.editor(bookId = bookId, sourceDishName = dish.name, sourceDishDescription = dish.description)
                )
            }
        )
    }

    screen(Destinations.SEARCH_ROUTE) {
        val viewModel: GlobalSearchViewModel = viewModel(
            factory = viewModelFactory { initializer { GlobalSearchViewModel(repository) } }
        )
        GlobalSearchScreen(
            viewModel = viewModel,
            onRecipeClick = { navController.navigate(Destinations.detail(it)) }
        )
    }

    screen(Destinations.FAVORITES_ROUTE) {
        val viewModel: GlobalSearchViewModel = viewModel(
            factory = viewModelFactory { initializer { GlobalSearchViewModel(repository, initialOnlyFavorites = true) } }
        )
        GlobalSearchScreen(
            viewModel = viewModel,
            onRecipeClick = { navController.navigate(Destinations.detail(it)) },
            title = L10n.str(R.string.favourites),
            showQueryField = false
        )
    }

    screen(Destinations.SHOPPING_LIST_ROUTE) {
        val viewModel: ShoppingListViewModel = viewModel(
            factory = viewModelFactory { initializer { ShoppingListViewModel(repository, settingsRepository) } }
        )
        ShoppingListScreen(viewModel = viewModel)
    }

    screen(
        route = Destinations.BOOK_EDITOR_ROUTE,
        arguments = listOf(
            navArgument(Destinations.ARG_BOOK_ID) {
                type = NavType.LongType
                defaultValue = Destinations.NEW_BOOK_ID
            }
        )
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: Destinations.NEW_BOOK_ID
        val viewModel: RecipeBookEditorViewModel = viewModel(
            key = "bookEditor_$bookId",
            factory = viewModelFactory { initializer { RecipeBookEditorViewModel(repository, bookId) } }
        )
        RecipeBookEditorScreen(
            viewModel = viewModel,
            onSaved = { navController.popBackStack() },
            onCancel = { navController.popBackStack() }
        )
    }

    screen(
        route = Destinations.BOOK_ROUTE,
        arguments = listOf(navArgument(Destinations.ARG_BOOK_ID) { type = NavType.LongType })
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: return@screen
        val viewModel: RecipeListViewModel = viewModel(
            key = "book_$bookId",
            factory = viewModelFactory { initializer { RecipeListViewModel(repository, bookId, settingsRepository) } }
        )
        RecipeListScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onRecipeClick = { id ->
                // The book's recipes in the order shown, to swipe to the next or previous one.
                val shownIds = viewModel.uiState.value.groups.flatMap { group -> group.recipes.map { it.id } }
                navController.navigate(Destinations.detail(id, shownIds))
            },
            onEditRecipeClick = { navController.navigate(Destinations.editor(bookId = Destinations.NEW_BOOK_ID, recipeId = it)) },
            onAddRecipeClick = { navController.navigate(Destinations.editor(bookId = bookId)) },
            onAddRecipeFromPhoto = { photoUris -> navController.navigate(Destinations.editor(bookId = bookId, sourcePhotoUris = photoUris)) },
            onAddRecipesBulk = { photoUris -> navController.navigate(Destinations.bulkImport(bookId = bookId, photoUris = photoUris)) },
            onSearchDishClick = { navController.navigate(Destinations.dishSearch(bookId)) },
            onAddRecipeFromUrl = { url -> navController.navigate(Destinations.editor(bookId = bookId, sourceRecipeUrl = url)) }
        )
    }

    screen(
        route = Destinations.DISH_SEARCH_ROUTE,
        arguments = listOf(
            navArgument(Destinations.ARG_BOOK_ID) {
                type = NavType.LongType
                defaultValue = Destinations.NEW_BOOK_ID
            }
        )
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: Destinations.NEW_BOOK_ID
        val viewModel: DishSearchViewModel = viewModel(
            factory = viewModelFactory { initializer { DishSearchViewModel(settingsRepository) } }
        )
        DishSearchScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDishSelected = { dish ->
                navController.navigate(
                    Destinations.editor(
                        bookId = bookId,
                        sourceDishName = dish.name,
                        sourceDishDescription = dish.description,
                        sourceDishOrigin = dish.origin
                    )
                )
            }
        )
    }

    screen(
        route = Destinations.BULK_IMPORT_ROUTE,
        arguments = listOf(
            navArgument(Destinations.ARG_BOOK_ID) {
                type = NavType.LongType
                defaultValue = Destinations.NEW_BOOK_ID
            },
            navArgument(Destinations.ARG_PHOTO_URIS) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: Destinations.NEW_BOOK_ID
        val photoUris = Destinations.decodeUriList(backStackEntry.arguments?.getString(Destinations.ARG_PHOTO_URIS))
        val viewModel: BulkImportViewModel = viewModel(
            key = "bulkImport_${photoUris.hashCode()}_$bookId",
            factory = viewModelFactory { initializer { BulkImportViewModel(repository, settingsRepository, bookId, photoUris) } }
        )
        BulkImportScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onOpenRecipe = { recipeId -> navController.navigate(Destinations.editor(bookId = Destinations.NEW_BOOK_ID, recipeId = recipeId)) }
        )
    }

    screen(
        route = Destinations.DETAIL_ROUTE,
        arguments = listOf(
            navArgument(Destinations.ARG_RECIPE_ID) { type = NavType.LongType },
            navArgument(Destinations.ARG_BROWSE_IDS) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val recipeId = backStackEntry.arguments?.getLong(Destinations.ARG_RECIPE_ID) ?: return@screen
        val browseIds = remember(backStackEntry) {
            Destinations.decodeIdList(backStackEntry.arguments?.getString(Destinations.ARG_BROWSE_IDS))
        }
        RecipeDetailPager(
            recipeIds = browseIds.takeIf { recipeId in it } ?: listOf(recipeId),
            initialRecipeId = recipeId,
            viewModelFor = { id ->
                viewModel(
                    key = "detail_$id",
                    factory = viewModelFactory { initializer { RecipeDetailViewModel(repository, id, settingsRepository) } }
                )
            },
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(Destinations.editor(bookId = Destinations.NEW_BOOK_ID, recipeId = id)) }
        )
    }

    screen(
        route = Destinations.EDITOR_ROUTE,
        arguments = listOf(
            navArgument(Destinations.ARG_RECIPE_ID) {
                type = NavType.LongType
                defaultValue = Destinations.NEW_RECIPE_ID
            },
            navArgument(Destinations.ARG_BOOK_ID) {
                type = NavType.LongType
                defaultValue = Destinations.NEW_BOOK_ID
            },
            navArgument(Destinations.ARG_SOURCE_PHOTO_URIS) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(Destinations.ARG_SOURCE_DISH_NAME) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(Destinations.ARG_SOURCE_DISH_DESCRIPTION) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(Destinations.ARG_SOURCE_DISH_ORIGIN) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(Destinations.ARG_SOURCE_RECIPE_URL) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val recipeId = backStackEntry.arguments?.getLong(Destinations.ARG_RECIPE_ID) ?: Destinations.NEW_RECIPE_ID
        val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: Destinations.NEW_BOOK_ID
        val sourcePhotoUris = Destinations.decodeUriList(backStackEntry.arguments?.getString(Destinations.ARG_SOURCE_PHOTO_URIS))
        val sourceDishName = backStackEntry.arguments?.getString(Destinations.ARG_SOURCE_DISH_NAME)
        val sourceDishDescription = backStackEntry.arguments?.getString(Destinations.ARG_SOURCE_DISH_DESCRIPTION).orEmpty()
        val sourceDishOrigin = backStackEntry.arguments?.getString(Destinations.ARG_SOURCE_DISH_ORIGIN)?.takeIf { it.isNotBlank() }
        val sourceRecipeUrl = backStackEntry.arguments?.getString(Destinations.ARG_SOURCE_RECIPE_URL)?.takeIf { it.isNotBlank() }
        val viewModel: RecipeEditorViewModel = viewModel(
            key = "editor_${recipeId}_$bookId",
            factory = viewModelFactory { initializer { RecipeEditorViewModel(repository, settingsRepository, recipeId, bookId) } }
        )
        RecipeEditorScreen(
            viewModel = viewModel,
            sourcePhotoUris = sourcePhotoUris,
            sourceDishName = sourceDishName,
            sourceDishDescription = sourceDishDescription,
            sourceDishOrigin = sourceDishOrigin,
            sourceRecipeUrl = sourceRecipeUrl,
            onSaved = { savedId ->
                navController.popBackStack()
                if (recipeId == Destinations.NEW_RECIPE_ID) {
                    navController.navigate(Destinations.detail(savedId)) {
                        popUpTo(Destinations.BOOKS_ROUTE)
                    }
                }
            },
            onCancel = { navController.popBackStack() }
        )
    }

    screen(Destinations.SETTINGS_ROUTE) {
        val viewModel: SettingsViewModel = viewModel(
            factory = viewModelFactory { initializer { SettingsViewModel(repository, settingsRepository) } }
        )
        val hasChangelog = remember { settingsRepository.listAvailableChangelogVersionCodes().isNotEmpty() }
        SettingsHomeScreen(
            viewModel = viewModel,
            hasChangelog = hasChangelog,
            onOpenSection = { navController.navigate(Destinations.settingsSection(it.id)) },
            onOpenStats = { navController.navigate(Destinations.STATS_ROUTE) },
            onOpenChangelog = { navController.navigate(Destinations.HELP_CHANGELOG_ROUTE) },
            onHelp = { navController.navigate(Destinations.HELP_ROUTE) },
            onAbout = { navController.navigate(Destinations.ABOUT_ROUTE) }
        )
    }

    screen(
        route = Destinations.SETTINGS_SECTION_ROUTE,
        arguments = listOf(navArgument(Destinations.ARG_SETTINGS_SECTION) { type = NavType.StringType })
    ) { backStackEntry ->
        val section = SettingsSection.fromId(backStackEntry.arguments?.getString(Destinations.ARG_SETTINGS_SECTION))
        val viewModel: SettingsViewModel = viewModel(
            factory = viewModelFactory { initializer { SettingsViewModel(repository, settingsRepository) } }
        )
        SettingsSectionScreen(
            viewModel = viewModel,
            section = section,
            onBack = { navController.popBackStack() },
            onManageCategories = { navController.navigate(Destinations.MANAGE_CATEGORIES_ROUTE) },
            onManageUtensils = { navController.navigate(Destinations.MANAGE_UTENSILS_ROUTE) },
            onManageIngredients = { navController.navigate(Destinations.MANAGE_INGREDIENTS_ROUTE) },
            onManageIngredientCategories = { navController.navigate(Destinations.MANAGE_INGREDIENT_CATEGORIES_ROUTE) },
            onOpenPacksCatalog = { navController.navigate(Destinations.PACKS_CATALOG_ROUTE) },
            onOpenSyncConnections = { navController.navigate(Destinations.SYNC_CONNECTIONS_ROUTE) }
        )
    }

    screen(Destinations.STATS_ROUTE) {
        val viewModel: StatsViewModel = viewModel(
            factory = viewModelFactory { initializer { StatsViewModel(repository, context.applicationContext) } }
        )
        StatsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onRecipeClick = { navController.navigate(Destinations.detail(it)) }
        )
    }

    screen(Destinations.SYNC_CONNECTIONS_ROUTE) {
        val viewModel: SyncConnectionsViewModel = viewModel(
            factory = viewModelFactory { initializer { SyncConnectionsViewModel(repository) } }
        )
        SyncConnectionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }

    screen(Destinations.PACKS_CATALOG_ROUTE) {
        val viewModel: PacksCatalogViewModel = viewModel(
            factory = viewModelFactory { initializer { PacksCatalogViewModel(repository, settingsRepository) } }
        )
        PacksCatalogScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onPackClick = { navController.navigate(Destinations.packDetail(it)) }
        )
    }

    screen(
        route = Destinations.PACK_DETAIL_ROUTE,
        arguments = listOf(navArgument(Destinations.ARG_PACK_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val packId = backStackEntry.arguments?.getString(Destinations.ARG_PACK_ID) ?: return@screen
        val viewModel: PackDetailViewModel = viewModel(
            key = "packDetail_$packId",
            factory = viewModelFactory { initializer { PackDetailViewModel(repository, settingsRepository, packId) } }
        )
        PackDetailScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onInstalled = { bookId ->
                navController.popBackStack(Destinations.PACKS_CATALOG_ROUTE, inclusive = true)
                navController.navigate(Destinations.book(bookId))
            }
        )
    }

    screen(Destinations.HELP_ROUTE) {
        val hasChangelog = remember { settingsRepository.listAvailableChangelogVersionCodes().isNotEmpty() }
        HelpScreen(
            onBack = { navController.popBackStack() },
            onOpenPrivacy = { navController.navigate(Destinations.PRIVACY_ROUTE) },
            onOpenChangelog = { navController.navigate(Destinations.HELP_CHANGELOG_ROUTE) }.takeIf { hasChangelog }
        )
    }

    screen(Destinations.HELP_CHANGELOG_ROUTE) {
        ChangelogScreen(settingsRepository = settingsRepository, onBack = { navController.popBackStack() })
    }

    screen(Destinations.ABOUT_ROUTE) {
        AboutScreen(
            onBack = { navController.popBackStack() },
            onOpenPrivacy = { navController.navigate(Destinations.PRIVACY_ROUTE) }
        )
    }

    screen(Destinations.PRIVACY_ROUTE) {
        DocumentScreen(
            title = L10n.str(R.string.privacy_policy),
            assetPath = L10n.str(R.string.privacy_asset),
            onBack = { navController.popBackStack() }
        )
    }

    screen(Destinations.MANAGE_CATEGORIES_ROUTE) {
        val viewModel: ManageCategoriesViewModel = viewModel(
            factory = viewModelFactory { initializer { ManageCategoriesViewModel(repository) } }
        )
        ManageCategoriesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }

    screen(Destinations.MANAGE_UTENSILS_ROUTE) {
        val viewModel: ManageUtensilsViewModel = viewModel(
            factory = viewModelFactory { initializer { ManageUtensilsViewModel(repository) } }
        )
        ManageUtensilsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }

    screen(Destinations.MANAGE_INGREDIENTS_ROUTE) {
        val viewModel: ManageIngredientsViewModel = viewModel(
            factory = viewModelFactory { initializer { ManageIngredientsViewModel(repository) } }
        )
        ManageIngredientsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }

    screen(Destinations.MANAGE_INGREDIENT_CATEGORIES_ROUTE) {
        val viewModel: ManageIngredientCategoriesViewModel = viewModel(
            factory = viewModelFactory { initializer { ManageIngredientCategoriesViewModel(repository) } }
        )
        ManageIngredientCategoriesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
}
