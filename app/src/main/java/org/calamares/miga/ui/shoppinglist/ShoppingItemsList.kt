package org.calamares.miga.ui.shoppinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.CategoryStyle
import org.calamares.miga.data.model.ShoppingListGroup
import org.calamares.miga.data.model.ShoppingListItem
import org.calamares.miga.data.model.ShoppingVisuals
import org.calamares.miga.data.model.displayCategoryName

private const val CART_KEY = "__cart__"

/**
 * The items still to buy, grouped by aisle in the supermarket's order, then the trolley. Each group
 * folds away; [collapsed] holds the folded aisle names ([CART_KEY] for the trolley).
 */
@Composable
internal fun ShoppingItemsList(
    pendingGroups: List<ShoppingListGroup>,
    cartItems: List<ShoppingListItem>,
    collapsed: Set<String>,
    onToggleCollapsed: (String) -> Unit,
    onClearCart: () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (ShoppingListItem) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        pendingGroups.forEach { group ->
            item(key = "header_${group.categoryName}") {
                CategoryHeader(
                    title = displayCategoryName(group.categoryName),
                    count = group.items.size,
                    style = ShoppingVisuals.categoryStyle(group.categoryName),
                    collapsed = group.categoryName in collapsed,
                    onToggle = { onToggleCollapsed(group.categoryName) }
                )
            }
            if (group.categoryName !in collapsed) {
                items(group.items, key = { it.id }) { shoppingItem -> row(shoppingItem) }
            }
        }
        if (cartItems.isNotEmpty()) {
            item(key = "header_cart") {
                CategoryHeader(
                    title = L10n.str(R.string.trolley),
                    count = cartItems.size,
                    style = CategoryStyle("🛒", 0xFF7B8794),
                    collapsed = CART_KEY in collapsed,
                    onToggle = { onToggleCollapsed(CART_KEY) },
                    trailing = { TextButton(onClick = onClearCart) { Text(L10n.str(R.string.remove)) } }
                )
            }
            if (CART_KEY !in collapsed) {
                items(cartItems, key = { it.id }) { shoppingItem -> row(shoppingItem) }
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp)) } // room for the shop mode button
    }
}
