package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.repository.PosSettingsRepository
import com.amaxonia.pos.domain.repository.TableAccountPaymentReader
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.DefaultPaymentOperation
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowExecutor
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Transitional constructor-compatible adapter for callers that have not yet moved their wiring
 * to [com.amaxonia.pos.domain.usecase.payment.PaymentOperation]. The ViewModel itself only stores
 * and consumes the canonical operation seam; legacy plumbing is contained here during migration.
 */
@Suppress("FunctionName", "LongParameterList")
internal fun PaymentViewModel(
    loadPaymentContext: LoadPaymentContextUseCase,
    loadPaymentCountry: LoadPaymentCountryUseCase,
    validatePayment: ValidatePaymentUseCase,
    buildPaymentDetails: BuildPaymentDetailsUseCase,
    executePaymentFlow: PaymentFlowExecutor,
    posSettings: PosSettingsRepository,
    selectedClient: StateFlow<Client?> = MutableStateFlow(null),
    tableAccountPaymentReader: TableAccountPaymentReader? = null,
    cartFinancialSnapshot: StateFlow<SaleFinancialSnapshot?> = MutableStateFlow(null),
): PaymentViewModel =
    PaymentViewModel(
        loadPaymentContext = loadPaymentContext,
        loadPaymentCountry = loadPaymentCountry,
        validatePayment = validatePayment,
        buildPaymentDetails = buildPaymentDetails,
        paymentOperation =
            DefaultPaymentOperation(
                executeLegacyFlow = { input, onEvent -> executePaymentFlow(input, onEvent) },
                printerTypeProvider = { posSettings.selectedPrinterType.first() },
            ),
        selectedClient = selectedClient,
        tableAccountPaymentReader = tableAccountPaymentReader,
        cartFinancialSnapshot = cartFinancialSnapshot,
    )
