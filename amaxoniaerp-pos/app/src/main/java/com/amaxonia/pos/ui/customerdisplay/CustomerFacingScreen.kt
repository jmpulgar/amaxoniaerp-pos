package com.amaxonia.pos.ui.customerdisplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.getDisplayItems
import com.amaxonia.pos.ui.theme.InfoBlue
import com.amaxonia.pos.ui.theme.PosStatusColors
import com.amaxonia.pos.ui.theme.SuccessGreen
import com.amaxonia.pos.ui.theme.cartBrandGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.util.Locale

private fun formatMoney(amount: Double): String = String.format(Locale.US, "$ %.2f", amount)

@Composable
fun CustomerFacingScreen(
    cartRepository: CartRepository,
    cashCloseReader: CashCloseContextReader,
    activeCajaReader: ActiveCajaReader,
    saleSuccessMessageFlow: StateFlow<String?>,
    isPaymentProcessingFlow: StateFlow<Boolean> = MutableStateFlow(false),
    paymentProcessingMessageFlow: StateFlow<String?> = MutableStateFlow(null),
) {
    val cartItems by cartRepository.cartItems.collectAsStateWithLifecycle()
    val financialSnapshot by cartRepository.financialSnapshot.collectAsStateWithLifecycle()
    val selectedClient by cartRepository.selectedClient.collectAsStateWithLifecycle()
    val saleSuccessMessage by saleSuccessMessageFlow.collectAsStateWithLifecycle()
    val isPaymentProcessing by isPaymentProcessingFlow.collectAsStateWithLifecycle()
    val paymentProcessingMessage by paymentProcessingMessageFlow.collectAsStateWithLifecycle()
    val activeCaja by activeCajaReader.activeCaja.collectAsStateWithLifecycle()

    var companyName by remember { mutableStateOf("") }
    var isPanama by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val company = cashCloseReader.currentCompany()
        companyName = company?.name ?: "Amaxonia POS"
        val countryCode = cashCloseReader.currentCountryCode()
        isPanama = countryCode.equals("PA", ignoreCase = true)
    }

    val currency = activeCaja?.currency
    val isMultiCurrency = currency?.multiMoneda.equals("SI", ignoreCase = true)
    val tasa = if (isMultiCurrency) currency?.tasa?.takeIf { rate -> rate > 0.0 } ?: 0.0 else 0.0
    val secondaryCurrencySymbol = if (isMultiCurrency) currency?.abrMonedaSecundaria.orEmpty().ifBlank { "Bs" } else "Bs"

    val displayItems = remember(cartItems) { cartRepository.getDisplayItems() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = when {
                saleSuccessMessage != null -> CustomerScreenState.Success
                isPaymentProcessing -> CustomerScreenState.ProcessingPayment
                displayItems.isNotEmpty() -> CustomerScreenState.ActiveCart
                else -> CustomerScreenState.Idle
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "CustomerScreenTransition",
        ) { state ->
            when (state) {
                CustomerScreenState.Success -> {
                    CustomerSuccessView(
                        companyName = companyName,
                        message = saleSuccessMessage ?: "¡Muchas gracias por su compra!",
                    )
                }

                CustomerScreenState.ProcessingPayment -> {
                    CustomerProcessingPaymentView(
                        companyName = companyName,
                        statusMessage = paymentProcessingMessage,
                    )
                }

                CustomerScreenState.Idle -> {
                    CustomerWelcomeIdleView(
                        companyName = companyName,
                    )
                }

                CustomerScreenState.ActiveCart -> {
                    val clientDisplayName = selectedClient?.let {
                        listOf(it.firstName, it.lastName).filter { part -> part.isNotBlank() }.joinToString(" ")
                    }?.takeIf { it.isNotBlank() }

                    CustomerActiveCartView(
                        companyName = companyName,
                        clientName = clientDisplayName,
                        displayItems = displayItems,
                        total = financialSnapshot?.total ?: cartItems.sumOf { it.total },
                        subtotal = financialSnapshot?.subtotalNet ?: cartItems.sumOf { it.subtotalWithoutTax },
                        taxAmount = financialSnapshot?.tax ?: 0.0,
                        discountAmount = financialSnapshot?.itemDiscounts ?: 0.0,
                        isPanama = isPanama,
                        isMultiCurrency = isMultiCurrency,
                        tasa = tasa,
                        secondaryCurrencySymbol = secondaryCurrencySymbol,
                    )
                }
            }
        }
    }
}

private enum class CustomerScreenState {
    Idle,
    ActiveCart,
    ProcessingPayment,
    Success,
}

@Composable
private fun CustomerWelcomeIdleView(
    companyName: String,
) {
    val brandColors = cartBrandGradient()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        brandColors.first().copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = companyName.ifBlank { "Amaxonia POS" },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "¡Bienvenido!",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Le atenderemos en un momento con gusto.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CustomerProcessingPaymentView(
    companyName: String,
    statusMessage: String?,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        elapsedSeconds = 0
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }

    val estimatedSeconds = 30
    val progress = (elapsedSeconds / estimatedSeconds.toFloat()).coerceIn(0.08f, 0.94f)
    val stageTitle =
        when {
            elapsedSeconds < 4 -> "Preparando la factura"
            elapsedSeconds < 10 -> "Conectando con facturación electrónica"
            elapsedSeconds < 24 -> "Esperando autorización de la DGI"
            else -> "Últimos segundos de validación"
        }
    val stageSubtitle =
        statusMessage?.takeIf { it.isNotBlank() } ?: when {
            elapsedSeconds < 4 -> "Validando pago, caja e inventario..."
            elapsedSeconds < 10 -> "Enviando el documento al proveedor fiscal..."
            elapsedSeconds < 24 -> "TheFactory está procesando el CUFE y el QR..."
            else -> "La respuesta está tardando un poco más de lo normal, seguimos esperando."
        }

    val brandColors = cartBrandGradient()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        brandColors.first().copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    )
                    Text(
                        text = "${elapsedSeconds}s",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Procesando cobro",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stageTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stageSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = companyName.ifBlank { "Amaxonia POS" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CustomerSuccessView(
    companyName: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PosStatusColors.confirmedContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(96.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = SuccessGreen,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Gracias por su preferencia en $companyName",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CustomerActiveCartView(
    companyName: String,
    clientName: String?,
    displayItems: List<ItemCarrito>,
    total: Double,
    subtotal: Double,
    taxAmount: Double,
    discountAmount: Double,
    isPanama: Boolean,
    isMultiCurrency: Boolean,
    tasa: Double,
    secondaryCurrencySymbol: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Top Header
        CustomerHeader(
            companyName = companyName,
            clientName = clientName,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main Dual Section: Left = Items Table, Right = Summary Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left: Items Table
            Card(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                ) {
                    CustomerTableHeader()
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(displayItems, key = { it.id }) { item ->
                            when (item) {
                                is ItemCarrito.ProductoIndividual -> {
                                    CustomerItemRow(item = item.item)
                                }
                                is ItemCarrito.PromocionAgrupada -> {
                                    CustomerPromotionRow(promo = item)
                                }
                            }
                        }
                    }
                }
            }

            // Right: Totals Summary
            Card(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                CustomerSummaryCard(
                    subtotal = subtotal,
                    taxAmount = taxAmount,
                    discountAmount = discountAmount,
                    total = total,
                    isPanama = isPanama,
                    isMultiCurrency = isMultiCurrency,
                    tasa = tasa,
                    secondaryCurrencySymbol = secondaryCurrencySymbol,
                )
            }
        }
    }
}

@Composable
private fun CustomerHeader(
    companyName: String,
    clientName: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PointOfSale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = companyName.ifBlank { "Amaxonia POS" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        if (!clientName.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = InfoBlue,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Cliente: $clientName",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CustomerTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Descripción",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2.0f),
        )
        Text(
            text = "Cant.",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = "P. Unit",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = "Total",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f),
        )
    }
}

@Composable
private fun CustomerItemRow(
    item: CartItem,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(2.0f)) {
                Text(
                    text = item.product.description.ifBlank { item.product.code },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.discountPercent > 0.0) {
                    Text(
                        text = "Desc. ${String.format(Locale.getDefault(), "%.0f", item.discountPercent)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }

            Text(
                text = "${item.quantity}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.7f),
            )

            Text(
                text = formatMoney(item.unitPriceWithTax),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.0f),
            )

            Text(
                text = formatMoney(item.total),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.1f),
            )
        }
    }
}

@Composable
private fun CustomerPromotionRow(
    promo: ItemCarrito.PromocionAgrupada,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = promo.promocionNombre,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatMoney(promo.total.toDouble()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            promo.items.forEach { promoItem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "• ${promoItem.quantity}x ${promoItem.product.description}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerSummaryCard(
    subtotal: Double,
    taxAmount: Double,
    discountAmount: Double,
    total: Double,
    isPanama: Boolean,
    isMultiCurrency: Boolean,
    tasa: Double,
    secondaryCurrencySymbol: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Resumen de Cuenta",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SummaryLine(label = "Subtotal", amount = subtotal)

            if (discountAmount > 0.0) {
                SummaryLine(
                    label = "Descuento",
                    amount = discountAmount,
                    isDiscount = true,
                )
            }

            val taxLabel = if (isPanama) "Impuesto (ITBMS)" else "Impuesto (IVA)"
            SummaryLine(label = taxLabel, amount = taxAmount)
        }

        Column {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "TOTAL A PAGAR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text = formatMoney(total),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 36.sp,
                        ),
                    )

                    if (isMultiCurrency && tasa > 0.0) {
                        val totalBs = Money.fromDouble(total).times(BigDecimal.valueOf(tasa))
                        val formattedBs = String.format(Locale.getDefault(), "%.2f", totalBs.toDouble())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$secondaryCurrencySymbol $formattedBs",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Text(
                            text = "Tasa: ${String.format(Locale.getDefault(), "%.2f", tasa)} $secondaryCurrencySymbol/$",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    amount: Double,
    isDiscount: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isDiscount) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isDiscount) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        Text(
            text = (if (isDiscount) "- " else "") + formatMoney(amount),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (isDiscount) SuccessGreen else MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}
