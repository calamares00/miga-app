package org.calamares.miga.ui.dishsearch

import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.ui.components.AiContentNotice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.calamares.miga.data.search.DishSuggestion

/**
 * Buscador de ideas de recetas con IA: el usuario describe una zona/país, un tipo de plato o
 * cualquier petición libre, y recibe una lista de platos sugeridos. Elegir uno navega al editor
 * precargado con la receta completa generada por IA (ver RecipeEditorViewModel.startDishGeneration),
 * mismo patrón que "Desde imagen" - se revisa/edita antes de guardar, no se guarda directamente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishSearchScreen(
    viewModel: DishSearchViewModel,
    onBack: () -> Unit,
    onDishSelected: (DishSuggestion) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.str(R.string.buscar_recetas_ia)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.volver)) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(L10n.str(R.string.zona_pais_ingrediente_tipo_plato)) },
                placeholder = { Text(L10n.str(R.string.p_ej_platos_tipicos_andalucia)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Filled.Search, contentDescription = L10n.str(R.string.buscar))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (val current = state) {
                DishSearchUiState.Idle -> Text(
                    L10n.str(R.string.busca_platos_tipicos_zona_pais),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DishSearchUiState.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                DishSearchUiState.NotConfigured -> Text(
                    L10n.str(R.string.configura_proveedor_ia_ajustes_usar_2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is DishSearchUiState.Error -> ErrorMessage(current.reason)
                is DishSearchUiState.Loaded -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AiContentNotice(
                            feature = L10n.str(R.string.buscar_receta_ia),
                            content = { current.dishes.joinToString("\n") { "${it.name}: ${it.description}" } }
                        )
                    }
                    items(current.dishes) { dish ->
                        DishCard(dish = dish, onClick = { onDishSelected(dish) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DishCard(dish: DishSuggestion, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(dish.name, style = MaterialTheme.typography.titleMedium)
            if (!dish.origin.isNullOrBlank()) {
                Text(
                    dish.origin,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (dish.description.isNotBlank()) {
                Text(
                    dish.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
