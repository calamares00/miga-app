package com.bmo00.miga.ui.dishsearch

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
import com.bmo00.miga.data.search.DishSuggestion

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
                title = { Text("Buscar recetas con IA") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Zona, país, ingrediente o tipo de plato") },
                placeholder = { Text("p. ej. \"platos típicos de Andalucía\"") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (val current = state) {
                DishSearchUiState.Idle -> Text(
                    "Busca platos típicos de una zona o país, o describe qué te apetece cocinar; " +
                        "la IA te sugerirá varias ideas para elegir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DishSearchUiState.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                DishSearchUiState.NotConfigured -> Text(
                    "Configura un proveedor de IA en Ajustes para usar el buscador.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is DishSearchUiState.Error -> Text(
                    current.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                is DishSearchUiState.Loaded -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
