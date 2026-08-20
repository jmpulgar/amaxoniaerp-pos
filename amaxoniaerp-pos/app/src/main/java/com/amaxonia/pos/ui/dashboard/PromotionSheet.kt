package com.amaxonia.pos.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.usecase.BigDecimalMoneyFormatter
import com.amaxonia.pos.ui.common.components.QuantityStepper
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosPalette

/** Líneas de detalle de promoción mostradas como vista previa en la tarjeta. */
private const val PROMO_DETAILS_PREVIEW_COUNT = 4

/** Hoja para elegir entre vender el producto individual o una promoción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionChoiceSheet(
    product: DashboardProduct,
    promotions: List<Promocion>,
    onAddIndividual: (Int) -> Unit,
    onAddPromotion: (Promocion, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var individualQuantityText by remember { mutableStateOf("1") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 22.dp),
        ) {
            PromotionSheetHeader(product = product)

            Spacer(Modifier.height(14.dp))

            IndividualProductSection(
                quantityText = individualQuantityText,
                onQuantityTextChange = { individualQuantityText = sanitizeQuantityInput(it) },
                onQuantitySet = { individualQuantityText = it },
                onAdd = onAddIndividual,
            )

            Spacer(Modifier.height(14.dp))
            Text("Promociones disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(promotions, key = { it.id }) { promo ->
                    PromotionOptionCard(promo = promo, onAddPromotion = { times -> onAddPromotion(promo, times) })
                }
            }
        }
    }
}

/** Encabezado degradado de la hoja con el nombre del producto en promoción. */
@Composable
private fun PromotionSheetHeader(product: DashboardProduct) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
            ) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = PosPalette.FixedWhite,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Este producto tiene promoción",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    product.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Sección para vender el producto individual con su cantidad. */
@Composable
private fun IndividualProductSection(
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    onQuantitySet: (String) -> Unit,
    onAdd: (Int) -> Unit,
) {
    val quantity = quantityText.toIntOrNull() ?: 0
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Producto individual", fontWeight = FontWeight.Bold)
            QuantityStepper(
                quantityText = quantityText,
                onQuantityTextChange = onQuantityTextChange,
                onDecrease = {
                    onQuantitySet(
                        ((quantityText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString(),
                    )
                },
                onIncrease = {
                    onQuantitySet(
                        ((quantityText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1).toString(),
                    )
                },
                onDone = { if (quantity >= 1) onAdd(quantity) },
                isError = quantityText.isNotBlank() && quantity < 1,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { if (quantity >= 1) onAdd(quantity) },
                enabled = quantity >= 1,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Vender producto individual", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Tarjeta de una promoción disponible con detalles y cantidad. */
@Composable
fun PromotionOptionCard(
    promo: Promocion,
    onAddPromotion: (Int) -> Unit,
) {
    val accent = if (promo.tipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    var timesText by remember { mutableStateOf("1") }
    val times = timesText.toIntOrNull() ?: 0
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = accent.copy(alpha = 0.12f), shape = PosExtraShapes.Pill) {
                    Text(
                        text = promo.tipo,
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(promo.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Código ${promo.codigo}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(BigDecimalMoneyFormatter.money(promo.total), color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }

            Spacer(Modifier.height(10.dp))
            PromotionDetailsPreview(promo = promo, accent = accent)

            Spacer(Modifier.height(12.dp))
            QuantityStepper(
                quantityText = timesText,
                onQuantityTextChange = { timesText = sanitizeQuantityInput(it) },
                onDecrease = { timesText = ((timesText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() },
                onIncrease = { timesText = ((timesText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1).toString() },
                onDone = { if (times >= 1) onAddPromotion(times) },
                isError = timesText.isNotBlank() && times < 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { if (times >= 1) onAddPromotion(times) },
                enabled = times >= 1,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PosPalette.FixedWhite),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar promoción x${times.coerceAtLeast(1)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Vista previa de las líneas de la promoción. */
@Composable
private fun PromotionDetailsPreview(
    promo: Promocion,
    accent: androidx.compose.ui.graphics.Color,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            promo.detalles.take(PROMO_DETAILS_PREVIEW_COUNT).forEach { detail ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${detail.cantidadTotal.stripTrailingZeros().toPlainString()} x ${detail.productName}",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        BigDecimalMoneyFormatter.money(detail.totalConIva),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (promo.detalles.size > PROMO_DETAILS_PREVIEW_COUNT) {
                val hiddenCount = promo.detalles.size - PROMO_DETAILS_PREVIEW_COUNT
                Text("+$hiddenCount productos más", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
