package com.bmo00.miga.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bmo00.miga.RecetarioApp
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.ui.books.RecipeBookEditorScreen
import com.bmo00.miga.ui.books.RecipeBookEditorViewModel
import com.bmo00.miga.ui.books.RecipeBooksScreen
import com.bmo00.miga.ui.books.RecipeBooksViewModel
import com.bmo00.miga.ui.detail.RecipeDetailScreen
import com.bmo00.miga.ui.detail.RecipeDetailViewModel
import com.bmo00.miga.ui.editor.RecipeEditorScreen
import com.bmo00.miga.ui.editor.RecipeEditorViewModel
import com.bmo00.miga.ui.list.RecipeListScreen
import com.bmo00.miga.ui.list.RecipeListViewModel
import com.bmo00.miga.ui.settings.HelpScreen
import com.bmo00.miga.ui.settings.ManageCategoriesScreen
import com.bmo00.miga.ui.settings.ManageCategoriesViewModel
import com.bmo00.miga.ui.settings.ManageIngredientCategoriesScreen
import com.bmo00.miga.ui.settings.ManageIngredientCategoriesViewModel
import com.bmo00.miga.ui.settings.ManageIngredientsScreen
import com.bmo00.miga.ui.settings.ManageIngredientsViewModel
import com.bmo00.miga.ui.settings.ManageUtensilsScreen
import com.bmo00.miga.ui.settings.ManageUtensilsViewModel
import com.bmo00.miga.ui.settings.SettingsScreen
import com.bmo00.miga.ui.settings.SettingsViewModel

private fun repositoryOf(context: android.content.Context): RecipeRepository =
    (context.applicationContext as RecetarioApp).repository

@Composable
fun RecetarioNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = repositoryOf(context)
    val settingsRepository = (context.applicationContext as RecetarioApp).settingsRepository

    NavHost(navController = navController, startDestination = Destinations.BOOKS_ROUTE) {
        composable(Destinations.BOOKS_ROUTE) {
            val viewModel: RecipeBooksViewModel = viewModel(
                factory = viewModelFactory { initializer { RecipeBooksViewModel(repository, settingsRepository) } }
            )
            RecipeBooksScreen(
                viewModel = viewModel,
                onBookClick = { navController.navigate(Destinations.book(it)) },
                onAddBookClick = { navController.navigate(Destinations.bookEditor()) },
                onEditBookClick = { navController.navigate(Destinations.bookEditor(it)) },
                onSettingsClick = { navController.navigate(Destinations.SETTINGS_ROUTE) }
            )
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
                factory = viewModelFactory { initializer { RecipeListViewModel(repository, bookId) } }
            )
            RecipeListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRecipeClick = { navController.navigate(Destinations.detail(it)) },
                onEditRecipeClick = { navController.navigate(Destinations.editor(bookId = Destinations.NEW_BOOK_ID, recipeId = it)) },
                onAddRecipeClick = { navController.navigate(Destinations.editor(bookId = bookId)) }
            )
        }

        composable(
            route = Destinations.DETAIL_ROUTE,
            arguments = listOf(navArgument(Destinations.ARG_RECIPE_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong(Destinations.ARG_RECIPE_ID) ?: return@composable
            val viewModel: RecipeDetailViewModel = viewModel(
                key = "detail_$recipeId",
                factory = viewModelFactory { initializer { RecipeDetailViewModel(repository, recipeId) } }
            )
            RecipeDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Destinations.editor(bookId = Destinations.NEW_BOOK_ID, recipeId = recipeId)) }
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
                }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong(Destinations.ARG_RECIPE_ID) ?: Destinations.NEW_RECIPE_ID
            val bookId = backStackEntry.arguments?.getLong(Destinations.ARG_BOOK_ID) ?: Destinations.NEW_BOOK_ID
            val viewModel: RecipeEditorViewModel = viewModel(
                key = "editor_${recipeId}_$bookId",
                factory = viewModelFactory { initializer { RecipeEditorViewModel(repository, recipeId, bookId) } }
            )
            RecipeEditorScreen(
                viewModel = viewModel,
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
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onManageCategories = { navController.navigate(Destinations.MANAGE_CATEGORIES_ROUTE) },
                onManageUtensils = { navController.navigate(Destinations.MANAGE_UTENSILS_ROUTE) },
                onManageIngredients = { navController.navigate(Destinations.MANAGE_INGREDIENTS_ROUTE) },
                onManageIngredientCategories = { navController.navigate(Destinations.MANAGE_INGREDIENT_CATEGORIES_ROUTE) },
                onHelp = { navController.navigate(Destinations.HELP_ROUTE) }
            )
        }

        composable(Destinations.HELP_ROUTE) {
            HelpScreen(onBack = { navController.popBackStack() })
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
