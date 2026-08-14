@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod")

package com.amaxonia.pos.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the Dashboard (POS catalog) screen components.
 *
 * Exercises the production composables — [ProductCard], [ProductListRow] and
 * [ManualEntryContent] — inside the exact grid layout used by the dashboard
 * (`GridCells.Adaptive(140dp)`) at every target width, so a designer can verify
 * in the IDE that: 320dp renders two columns without overflowing the action row,
 * long product names/prices ellipsize instead of wrapping badly, and huge prices
 * shrink via AdaptiveAmountText instead of clipping.
 */
@Preview(name = "Grid · 320×568 · 2 columnas", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun DashboardGrid320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CatalogGridPreviewContent()
    }
}

@Preview(name = "Grid · 360×640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DashboardGrid360() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CatalogGridPreviewContent()
    }
}

@Preview(name = "Grid · 412×915", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DashboardGrid412() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CatalogGridPreviewContent()
    }
}

@Preview(name = "Grid · 480×960", showBackground = true, widthDp = 480, heightDp = 960)
@Composable
private fun DashboardGrid480() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CatalogGridPreviewContent()
    }
}

@Preview(name = "Grid · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
private fun DashboardGridLandscape() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CatalogGridPreviewContent()
    }
}

@Preview(name = "Fila lista · 320×568 · nombre largo + precio enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun DashboardListRowNarrow() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            ProductListRow(
                product =
                    previewProduct(
                        name = "Aceite lubricante premium sintético para motor 4T garrafa 5L",
                        price = 9_876_543.21,
                    ),
                onAddClick = {},
                onQuantityClick = {},
            )
            ProductListRow(
                product = previewProduct(name = "Café molido 500g", price = 7.90, code = null),
                onAddClick = {},
                onQuantityClick = {},
            )
        }
    }
}

@Preview(name = "Entrada manual · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun DashboardManualEntry320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ManualEntryContent(
            currentValue = "1250",
            onKeyClick = {},
            onClearClick = {},
            onBackspaceClick = {},
            onEnterClick = {},
        )
    }
}

@Preview(name = "Entrada manual · monto enorme", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DashboardManualEntryHuge() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ManualEntryContent(
            currentValue = "9876543.21",
            onKeyClick = {},
            onClearClick = {},
            onBackspaceClick = {},
            onEnterClick = {},
        )
    }
}

@Preview(name = "Botón carrito · 320×568 · monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun DashboardCartButtonHuge() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            CartCheckoutPillPreview(itemCount = 12, total = 9_876_543.21)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview-only helpers — mirror the production layouts.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatalogGridPreviewContent() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        items(previewCatalog()) { product ->
            ProductCard(
                product = product,
                onAddClick = {},
                onQuantityClick = {},
            )
        }
    }
}

@Composable
private fun CartCheckoutPillPreview(
    itemCount: Int,
    total: Double,
) {
    Button(
        onClick = {},
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${itemCount} artículos")
            AdaptiveAmountText(
                text = "$${String.format(java.util.Locale.getDefault(), "%.2f", total)}",
                baseStyle = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                minFontSizeSp = 13f,
                maxLines = 1,
            )
        }
    }
}

private fun previewCatalog(): List<DashboardProduct> =
    listOf(
        previewProduct(name = "Coca-Cola 2L", price = 2.50, code = "REF-001"),
        previewProduct(
            name = "Papel higiénico doble hoja pack familiar 12 rollos premium",
            price = 6.75,
            code = "REF-002",
        ),
        previewProduct(name = "Arroz blanco 1kg", price = 1.40, code = null),
        previewProduct(name = "Detergente líquido 3L", price = 9_876_543.21, code = "REF-004"),
        previewProduct(name = "Café molido 500g", price = 7.90, code = "REF-005"),
        previewProduct(name = "Leche entera 1L", price = 1.95, code = "REF-006"),
    )

private fun previewProduct(
    name: String,
    price: Double,
    code: String? = "REF-000",
): DashboardProduct =
    DashboardProduct(
        id = name,
        name = name,
        price = price,
        code = code,
        imageUrl = null,
    )
