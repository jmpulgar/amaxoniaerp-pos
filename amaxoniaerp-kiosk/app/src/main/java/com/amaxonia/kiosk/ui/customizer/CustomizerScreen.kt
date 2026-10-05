package com.amaxonia.kiosk.ui.customizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskModifierGroupDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizerScreen(
    viewModel: ProductCustomizerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.item

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar with Close Action
            TopAppBar(
                title = {
                    Text(
                        text = "Personalizar Producto",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            // Scrollable Content
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                // Product Summary Card
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Fastfood,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp).align(Alignment.Center),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        if (!item.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Money.fromString(item.price).toDisplayString(),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                }

                // Modifier Groups List
                item.modifierGroups.forEach { group ->
                    ModifierGroupSection(
                        group = group,
                        selectedOptions = uiState.selectedOptions[group.id].orEmpty(),
                        currency = uiState.currency,
                        errorMessage = uiState.validationErrors[group.id],
                        onOptionToggled = { option -> viewModel.toggleOption(group, option) },
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }

                // Special Instructions / Note Field
                Text(
                    text = "Instrucciones Especiales (Opcional)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChanged,
                    placeholder = { Text("Ej. Sin sal, salsa aparte, etc. (Máx. 80 caracteres)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )

                // Quantity Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = viewModel::decrementQuantity,
                        enabled = uiState.quantity > 1,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .border(
                                    1.dp,
                                    if (uiState.quantity > 1) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    CircleShape,
                                ),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }

                    Text(
                        text = uiState.quantity.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    IconButton(
                        onClick = viewModel::incrementQuantity,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }
            }

            // Bottom Sticky Action Bar
            CustomizerBottomBar(
                totalPrice = uiState.totalPrice,
                currency = uiState.currency,
                isValid = uiState.isValid,
                onAdd = { viewModel.addToCart(onSuccess = onDismiss) },
            )
        }
    }
}

@Composable
private fun ModifierGroupSection(
    group: KioskModifierGroupDto,
    selectedOptions: List<KioskModifierOptionDto>,
    currency: KioskCurrencyConfig,
    errorMessage: String?,
    onOptionToggled: (KioskModifierOptionDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )

                val badgeText =
                    when {
                        group.min == 1 && group.max == 1 -> "Obligatorio (Elige 1)"
                        group.min > 0 -> "Obligatorio (Mín ${group.min})"
                        group.max == 1 -> "Opcional (Máx 1)"
                        else -> "Opcional (Hasta ${group.max})"
                    }

                Surface(
                    color =
                        if (group.isMandatory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color =
                            if (group.isMandatory) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.options.forEach { option ->
                    val isSelected = selectedOptions.any { it.id == option.id }
                    ModifierOptionItem(
                        option = option,
                        isSelected = isSelected,
                        currency = currency,
                        onClick = { onOptionToggled(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModifierOptionItem(
    option: KioskModifierOptionDto,
    isSelected: Boolean,
    currency: KioskCurrencyConfig,
    onClick: () -> Unit,
) {
    val borderColor =
        when {
            option.soldOut -> Color.Transparent
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    val backgroundColor =
        when {
            option.soldOut -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        }

    val extraMoney = remember(option.extraPrice) { Money.fromString(option.extraPrice) }
    val secondaryExtra =
        remember(extraMoney, currency) {
            val rate = runCatching { BigDecimal(currency.rate) }.getOrDefault(BigDecimal.ZERO)
            extraMoney.toSecondaryCurrency(rate, currency.secondary ?: "Bs")
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .clickable(enabled = !option.soldOut, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Radio/Check Indicator
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape,
                    border =
                        if (isSelected) null else androidx.compose.foundation.BorderStroke(2.dp, Color.Gray),
                    modifier = Modifier.size(24.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = option.name,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (option.soldOut) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        ),
                )
            }

            // Price Extra Badge
            if (option.soldOut) {
                Text(
                    text = "Agotado",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (extraMoney.amount > BigDecimal.ZERO) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+${extraMoney.toDisplayString()}",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                    )
                    if (secondaryExtra.isNotBlank()) {
                        Text(
                            text = "+$secondaryExtra",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizerBottomBar(
    totalPrice: Money,
    currency: KioskCurrencyConfig,
    isValid: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryTotal =
        remember(totalPrice, currency) {
            val rate = runCatching { BigDecimal(currency.rate) }.getOrDefault(BigDecimal.ZERO)
            totalPrice.toSecondaryCurrency(rate, currency.secondary ?: "Bs")
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            Button(
                onClick = onAdd,
                enabled = isValid,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Agregar al Pedido",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = totalPrice.toDisplayString(),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                        if (secondaryTotal.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "($secondaryTotal)",
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
