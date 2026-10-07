package org.calamares.miga.ui.navigation

import org.calamares.miga.ui.ideas.IdeasViewModel
import org.calamares.miga.ui.ideas.IdeasScreen
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
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

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (BOTTOM_TABS.any { it.route == currentRoute }) {
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.BOOKS_ROUTE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destinations.BOOKS_ROUTE) {
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

            composable(Destinations.IDEAS_ROUTE) {
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

            composable(Destinations.SEARCH_ROUTE) {
                val viewModel: GlobalSearchViewModel = viewModel(
                    factory = viewModelFactory { initializer { GlobalSearchViewModel(repository) } }
                )
                GlobalSearchScreen(
                    viewModel = viewModel,
                    onRecipeClick = { navController.navigate(Destinations.detail(it)) }
                )
            }

            composable(Destinations.FAVORITES_ROUTE) {
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

            composable(Destinations.SHOPPING_LIST_ROUTE) {
                val viewModel: ShoppingListViewModel = viewModel(
                    factory = viewModelFactory { initializer { ShoppingListViewModel(repository, settingsRepository) } }
                )
                ShoppingListScreen(viewModel = viewModel)
            }

            composable(
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

            composable(
                route = Destinations.BOOK_ROUTE,
                arguments = listOf(navArgument(Destinations.ARG_BOOK_ID) { type = NavType.LongType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: return@composable
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

            composable(
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

            composable(
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

            composable(
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
                val recipeId = backStackEntry.arguments?.getLong(Destinations.ARG_RECIPE_ID) ?: return@composable
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

            composable(
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

            composable(Destinations.SETTINGS_ROUTE) {
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

            composable(
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

            composable(Destinations.STATS_ROUTE) {
                val viewModel: StatsViewModel = viewModel(
                    factory = viewModelFactory { initializer { StatsViewModel(repository) } }
                )
                StatsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { navController.navigate(Destinations.detail(it)) }
                )
            }

            composable(Destinations.SYNC_CONNECTIONS_ROUTE) {
                val viewModel: SyncConnectionsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SyncConnectionsViewModel(repository) } }
                )
                SyncConnectionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(Destinations.PACKS_CATALOG_ROUTE) {
                val viewModel: PacksCatalogViewModel = viewModel(
                    factory = viewModelFactory { initializer { PacksCatalogViewModel(repository, settingsRepository) } }
                )
                PacksCatalogScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onPackClick = { navController.navigate(Destinations.packDetail(it)) }
                )
            }

            composable(
                route = Destinations.PACK_DETAIL_ROUTE,
                arguments = listOf(navArgument(Destinations.ARG_PACK_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val packId = backStackEntry.arguments?.getString(Destinations.ARG_PACK_ID) ?: return@composable
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

            composable(Destinations.HELP_ROUTE) {
                HelpScreen(onBack = { navController.popBackStack() })
            }

            composable(Destinations.HELP_CHANGELOG_ROUTE) {
                ChangelogScreen(settingsRepository = settingsRepository, onBack = { navController.popBackStack() })
            }

            composable(Destinations.ABOUT_ROUTE) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Destinations.MANAGE_CATEGORIES_ROUTE) {
                val viewModel: ManageCategoriesViewModel = viewModel(
                    factory = viewModelFactory { initializer { ManageCategoriesViewModel(repository) } }
                )
                ManageCategoriesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(Destinations.MANAGE_UTENSILS_ROUTE) {
                val viewModel: ManageUtensilsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ManageUtensilsViewModel(repository) } }
                )
                ManageUtensilsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(Destinations.MANAGE_INGREDIENTS_ROUTE) {
                val viewModel: ManageIngredientsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ManageIngredientsViewModel(repository) } }
                )
                ManageIngredientsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(Destinations.MANAGE_INGREDIENT_CATEGORIES_ROUTE) {
                val viewModel: ManageIngredientCategoriesViewModel = viewModel(
                    factory = viewModelFactory { initializer { ManageIngredientCategoriesViewModel(repository) } }
                )
                ManageIngredientCategoriesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
