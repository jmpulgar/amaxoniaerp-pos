package com.amaxonia.pos.ui.cart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.LotAssignment
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the Cart screen components.
 *
 * Exercises the production composables — [CartItemRow], [CartBottomBar] and
 * [CartClientVendorPanel] — at the target Android widths and the trickiest content variants
 * (huge amounts that must never ellipsize/wrap, item rows with unit switch + discount + lots,
 * compact landscape bottom bar) so they can be validated in the IDE preview pane.
 */
@Preview(name = "Item · 320×568 · completo", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun CartItemRowCompact320() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                PreviewCartItemRow(item = previewItem(unitPrice = 12.50))
            }
        }
    }

@Preview(name = "Item · 412×915 · montos enormes", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun CartItemRowBigAmounts() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                PreviewCartItemRow(
                    item =
                        previewItem(
                            description = "Combo corporativo edición limitada con estuche y grabado láser",
                            unitPrice = 9_876_543.21,
                            quantity = 999,
                        ),
                )
            }
        }
    }

@Preview(name = "Item · simple sin extras", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
internal fun CartItemRowSimple() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                PreviewCartItemRow(
                    item = previewItem(description = "Café molido 500g", unitPrice = 7.90, quantity = 2, bulk = 1.0),
                )
            }
        }
    }

@Preview(name = "Bottom bar · 320×568 · monto normal", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun CartBottomBar320() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CartBottomBar(
                total = 248.75,
                secondaryTotal = null,
                onSaveDraft = {},
                onCheckout = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

@Preview(name = "Bottom bar · 320×568 · monto enorme sin wrap", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun CartBottomBarHugeTotal() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CartBottomBar(
                total = 9_876_543.21,
                secondaryTotal = null,
                onSaveDraft = {},
                onCheckout = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

@Preview(name = "Bottom bar · 390×844 · multi-moneda", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun CartBottomBarMultiCurrency() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CartBottomBar(
                total = 1_289.50,
                secondaryTotal = "Bs. 41264.00",
                onSaveDraft = {},
                onCheckout = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

@Preview(name = "Bottom bar · landscape compacto · 733×340", showBackground = true, widthDp = 733, heightDp = 340)
@Composable
internal fun CartBottomBarLandscapeCompact() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CartBottomBar(
                total = 248.75,
                secondaryTotal = "Bs. 7940.00",
                onSaveDraft = {},
                onCheckout = {},
            )
        }
    }

@Preview(name = "Panel cliente+vendedor · asignados", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
internal fun CartPanelAssigned() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CartClientVendorPanel(
                state =
                    CartState(
                        selectedClient = Client(firstName = "María", lastName = "Fernández"),
                        currentSeller = Seller(id = 3, nombre = "Carlos Pérez"),
                        availableSellers = listOf(Seller(id = 3, nombre = "Carlos Pérez")),
                    ),
                onSelectClient = {},
                onRemoveClient = {},
                onChangeSeller = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

@Preview(name = "Panel cliente+vendedor · vacío", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
internal fun CartPanelEmpty() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CartClientVendorPanel(
                state = CartState(),
                onSelectClient = {},
                onRemoveClient = {},
                onChangeSeller = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Preview-only builders.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PreviewCartItemRow(item: CartItem) {
    CartItemRow(
        item = item,
        onIncrease = {},
        onDecrease = {},
        onRemove = {},
        allowEditPrice = true,
        allowDiscount = true,
        onEditPrice = {},
        onEditDiscount = {},
        onUnitChange = {},
        onQuantityChange = {},
    )
}

private fun previewItem(
    description: String = "Coca-Cola 2L Pack retornable con descuento aplicado",
    unitPrice: Double,
    quantity: Int = 3,
    bulk: Double = 6.0,
): CartItem {
    val product =
        Product(
            id = "preview-1",
            description = description,
            taxRate = 16.0,
            bulkQuantity = bulk,
            unitPackage = "CAJA",
        )
    return CartItem(
        product = product,
        quantity = quantity,
        unitPriceWithTax = unitPrice,
        discountPercent = 10.0,
        lotAssignments =
            listOf(
                LotAssignment(
                    idLoteItem = "L1",
                    codigoLote = "L-2026-08",
                    vencimiento = "2027-01-15",
                    cantidad = 3,
                ),
            ),
    )
}
