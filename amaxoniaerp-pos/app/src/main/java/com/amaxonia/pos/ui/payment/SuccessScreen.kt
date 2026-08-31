package com.amaxonia.pos.ui.payment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.common.injectedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val SCREEN_ROYAL_BLUE = Color(0xFF1664EA)
private val WIDE_CONTENT_MAX_WIDTH = 480.dp

/** Fracciones genéricas de progreso usadas por las animaciones de esta pantalla. */
private const val ANIMATION_START_FRACTION = 0f
private const val ANIMATION_END_FRACTION = 1f

/** Entrada escalonada de los bloques de la pantalla. */
private const val ENTRANCE_SLOTS = 5
private const val ENTRANCE_SLOT_CARD = 0
private const val ENTRANCE_SLOT_HEADLINE = 1
private const val ENTRANCE_SLOT_SUMMARY = 2
private const val ENTRANCE_SLOT_PRINT_BTN = 3
private const val ENTRANCE_SLOT_NEXT_BTN = 4
private const val ENTRANCE_SLIDE_DISTANCE_PX = 32f
private const val ENTRANCE_DURATION_MS = 360
private val ENTRANCE_DELAYS_MS = listOf(40, 160, 280, 400, 500)

/** Conteo ascendente del monto de cambio/vuelto. */
private const val CHANGE_COUNT_ZERO_THRESHOLD = 0.0
private const val CHANGE_COUNT_DURATION_MS = 800

/** Acción de la pantalla agrupada para mantener las firmas de los composables pequeñas. */
internal data class SuccessReceiptState(
    val isSendingReceiptEmail: Boolean = false,
    val isPrinting: Boolean = false,
)

internal class SuccessActions(
    val onPrintReceipt: () -> Unit,
    val onSendReceiptEmail: () -> Unit = {},
    val onNextOrder: () -> Unit,
    val receiptState: SuccessReceiptState = SuccessReceiptState(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    transactionId: String,
    onPrintReceipt: suspend (String) -> Result<String>,
    onNextOrder: () -> Unit,
) {
    val viewModel =
        injectedViewModel {
            AppGraph.payment.paymentSuccessViewModel(transactionId)
        }

    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val payload = uiState.payload

    var isPrinting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    SuccessSnackbarEffects(uiState = uiState, snackbarHostState = snackbarHostState)
    SuccessBackHandler(scope = scope, snackbarHostState = snackbarHostState)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = SCREEN_ROYAL_BLUE,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(SCREEN_ROYAL_BLUE)
                    .padding(paddingValues)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            SuccessContent(
                isLoading = uiState.isLoading || payload == null,
                payload = payload,
                actions =
                    SuccessActions(
                        receiptState =
                            SuccessReceiptState(
                                isSendingReceiptEmail = uiState.isSendingReceiptEmail,
                                isPrinting = isPrinting,
                            ),
                        onPrintReceipt = {
                            if (transactionId.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("No hay transacción para imprimir") }
                                return@SuccessActions
                            }
                            scope.launch {
                                isPrinting = true
                                val result = onPrintReceipt(transactionId)
                                val feedback =
                                    result.getOrElse { error ->
                                        error.message ?: "No se pudo imprimir el recibo"
                                    }
                                snackbarHostState.showSnackbar(feedback)
                                isPrinting = false
                            }
                        },
                        onSendReceiptEmail = { viewModel.sendReceiptEmail() },
                        onNextOrder = onNextOrder,
                    ),
            )
        }
    }
}

/** Muestra como snackbars los mensajes de error y de recibo impreso. */
@Composable
private fun SuccessSnackbarEffects(
    uiState: PaymentSuccessUiState,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uiState.payload?.receiptPrintMessage) {
        val message = uiState.payload?.receiptPrintMessage.orEmpty()
        if (message.isNotBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }
}

/** Back bloqueado: guía al usuario hacia Nueva orden. */
@Composable
private fun SuccessBackHandler(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    BackHandler(enabled = true) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Usa Nueva orden para finalizar y limpiar el estado de venta",
            )
        }
    }
}

/**
 * Capa de presentación visual de la pantalla de éxito de cobro:
 * Tarjeta blanca flotante sobre fondo azul con héroe animado, resumen de pago
 * y botones inferiores de imprimir recibo y siguiente orden.
 */
@Composable
internal fun SuccessContent(
    isLoading: Boolean,
    payload: PaymentSuccessPayload?,
    actions: SuccessActions,
    modifier: Modifier = Modifier,
) {
    val entrances = rememberStaggeredEntrance()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(SCREEN_ROYAL_BLUE)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = WIDE_CONTENT_MAX_WIDTH)
                    .weight(1f, fill = false),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading || payload == null) {
                LoadingConfirmationCard()
            } else {
                SuccessMainCard(
                    payload = payload,
                    cardProgress = entrances[ENTRANCE_SLOT_CARD],
                    headlineProgress = entrances[ENTRANCE_SLOT_HEADLINE],
                    summaryProgress = entrances[ENTRANCE_SLOT_SUMMARY],
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Botones de acción inferiores sobre el fondo azul
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = WIDE_CONTENT_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrintReceiptButton(
                isPrinting = actions.receiptState.isPrinting,
                onPrintReceipt = actions.onPrintReceipt,
                entranceProgress = entrances[ENTRANCE_SLOT_PRINT_BTN],
            )
            NextOrderButton(
                onNextOrder = actions.onNextOrder,
                entranceProgress = entrances[ENTRANCE_SLOT_NEXT_BTN],
            )
        }
    }
}

/** Tarjeta blanca principal que contiene el héroe animado, título y resumen. */
@Composable
private fun SuccessMainCard(
    payload: PaymentSuccessPayload,
    cardProgress: Float,
    headlineProgress: Float,
    summaryProgress: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 10.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = cardProgress
                    scaleX = 0.90f + 0.10f * cardProgress
                    scaleY = 0.90f + 0.10f * cardProgress
                    translationY = ENTRANCE_SLIDE_DISTANCE_PX * (ANIMATION_END_FRACTION - cardProgress)
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CelebrationHero()

            Spacer(modifier = Modifier.height(14.dp))

            SuccessHeadline(
                payload = payload,
                entranceProgress = headlineProgress,
            )

            payload.feError?.takeIf { it.isNotBlank() }?.let { feError ->
                Spacer(modifier = Modifier.height(12.dp))
                PosFeedbackCard(
                    title = "Confirmación fiscal pendiente",
                    message = feError,
                    tone = PosVisualTone.Warning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (payload.isTableSale) {
                Spacer(modifier = Modifier.height(10.dp))
                PosStatusBadge(
                    label =
                        if (payload.tableSessionClosed) {
                            "Mesa cerrada y disponible"
                        } else {
                            "Mesa con saldo o consumo pendiente"
                        },
                    tone = if (payload.tableSessionClosed) PosVisualTone.Success else PosVisualTone.Info,
                    icon = Icons.Default.TableRestaurant,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            BluePaymentSummaryCard(
                payload = payload,
                entranceProgress = summaryProgress,
            )
        }
    }
}

/** Título y nota de servicio al cliente. */
@Composable
private fun SuccessHeadline(
    payload: PaymentSuccessPayload,
    entranceProgress: Float,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .entrance(entranceProgress),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "¡Transacción exitosa!",
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp,
                ),
            color = SCREEN_ROYAL_BLUE,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "NOTA : No olvides sonreír al cliente.",
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                ),
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
        )

        if (payload.codFactura.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
            ) {
                Text(
                    text = "Factura #${payload.codFactura}",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                        ),
                    color = SCREEN_ROYAL_BLUE,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Recuadro azul oscuro con los detalles del método de pago y el cambio devuelto. */
@Composable
private fun BluePaymentSummaryCard(
    payload: PaymentSuccessPayload,
    entranceProgress: Float,
) {
    val countFraction = rememberChangeCountFraction(changeDue = payload.changeDue)

    Surface(
        color = SCREEN_ROYAL_BLUE,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 3.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .entrance(entranceProgress),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val paymentMethod = payload.paymentMethodsLabel.ifBlank { "EFECTIVO" }.uppercase()
            Text(
                text = "Modo de pago : $paymentMethod",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        letterSpacing = 0.4.sp,
                    ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.28f),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth(0.94f),
            )

            val hasChange = payload.changeDue > 0.0
            val changeAmountText =
                if (hasChange) {
                    "$ ${Money.format(Money.fromDouble(payload.changeDue * countFraction))}"
                } else {
                    "$ 0.00"
                }

            Text(
                text = "Cambio : $changeAmountText",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp,
                    ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (payload.isMultiCurrency && payload.changeDueBs > 0.0) {
                val changeBsFormatted =
                    String.format(
                        Locale.getDefault(),
                        "%.2f",
                        payload.changeDueBs * countFraction,
                    )
                Text(
                    text = "${payload.abrMonedaSecundaria} $changeBsFormatted",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                )
            } else if (payload.isMultiCurrency && payload.totalBs > 0.0 && !hasChange) {
                val totalBsFormatted =
                    String.format(
                        Locale.getDefault(),
                        "%.2f",
                        payload.totalBs,
                    )
                Text(
                    text = "Total: ${payload.abrMonedaSecundaria} $totalBsFormatted",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Tarjeta de carga mientras se confirma la factura. */
@Composable
private fun LoadingConfirmationCard() {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                color = SCREEN_ROYAL_BLUE,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Confirmando transacción…",
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = Color(0xFF475569),
            )
        }
    }
}

/** Botón de Imprimir Recibo con borde blanco y fondo translúcido sobre el fondo azul. */
@Composable
private fun PrintReceiptButton(
    isPrinting: Boolean,
    onPrintReceipt: () -> Unit,
    entranceProgress: Float,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onPrintReceipt,
        enabled = !isPrinting,
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp)
                .entrance(entranceProgress),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, Color.White),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            ),
    ) {
        if (isPrinting) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "IMPRIMIENDO...",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.6.sp),
                color = Color.White,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "IMPRIMIR RECIBO",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.6.sp),
                color = Color.White,
            )
        }
    }
}

/** Botón blanco sólido de Siguiente Venta / Nueva Orden sobre el fondo azul. */
@Composable
private fun NextOrderButton(
    onNextOrder: () -> Unit,
    entranceProgress: Float,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onNextOrder,
        modifier =
            modifier
                .fillMaxWidth()
                .height(54.dp)
                .entrance(entranceProgress),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = SCREEN_ROYAL_BLUE,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
    ) {
        Text(
            text = "SIGUIENTE VENTA",
            color = SCREEN_ROYAL_BLUE,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = SCREEN_ROYAL_BLUE,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Anima una fracción 0→1 para el conteo ascendente del cambio devuelto. */
@Composable
private fun rememberChangeCountFraction(changeDue: Double): Float {
    val fraction = remember(changeDue) { Animatable(ANIMATION_START_FRACTION) }
    LaunchedEffect(changeDue) {
        if (changeDue > CHANGE_COUNT_ZERO_THRESHOLD) {
            fraction.animateTo(
                targetValue = ANIMATION_END_FRACTION,
                animationSpec = tween(durationMillis = CHANGE_COUNT_DURATION_MS, easing = FastOutSlowInEasing),
            )
        } else {
            fraction.snapTo(ANIMATION_END_FRACTION)
        }
    }
    return fraction.value
}

// ─────────────────────────────────────────────────────────────────────────────
// Entrada escalonada del contenido.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun rememberStaggeredEntrance(): List<Float> {
    val animatables = remember { List(ENTRANCE_SLOTS) { Animatable(ANIMATION_START_FRACTION) } }
    LaunchedEffect(Unit) {
        ENTRANCE_DELAYS_MS.forEachIndexed { slot, delayMillis ->
            launch {
                delay(delayMillis.toLong())
                animatables[slot].animateTo(
                    targetValue = ANIMATION_END_FRACTION,
                    animationSpec = tween(durationMillis = ENTRANCE_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
        }
    }
    return animatables.map { it.value }
}

private fun Modifier.entrance(progress: Float): Modifier =
    graphicsLayer {
        alpha = progress
        translationY = ENTRANCE_SLIDE_DISTANCE_PX * (ANIMATION_END_FRACTION - progress)
    }
