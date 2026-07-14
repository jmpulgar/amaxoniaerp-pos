package com.amaxonia.pos.domain.usecase.payment

class ValidatePaymentUseCase {
    fun validateAmount(isPaymentEnough: Boolean): PaymentValidationFailure? =
        if (isPaymentEnough) null else PaymentValidationFailure.InsufficientAmount

    fun validatePaymentMethods(detailCount: Int): PaymentValidationFailure? =
        if (detailCount > 0) null else PaymentValidationFailure.MissingPaymentMethod

    fun validateSaleContext(
        itemCount: Int,
        hasClient: Boolean,
        hasCaja: Boolean,
    ): PaymentValidationFailure? =
        when {
            itemCount == 0 -> PaymentValidationFailure.EmptyCart
            !hasClient -> PaymentValidationFailure.MissingClient
            !hasCaja -> PaymentValidationFailure.MissingCaja
            else -> null
        }

    fun validateClientBranch(
        availableBranchCount: Int,
        hasSelectedBranch: Boolean,
    ): PaymentValidationFailure? =
        if (availableBranchCount > 0 && !hasSelectedBranch) {
            PaymentValidationFailure.MissingClientBranch
        } else {
            null
        }

    fun validateSaleReadiness(
        isOnline: Boolean,
        hasCajaSequence: Boolean,
        invalidItemCount: Int,
    ): PaymentValidationFailure? =
        when {
            isOnline && !hasCajaSequence -> PaymentValidationFailure.MissingCajaSequence
            invalidItemCount > 0 -> PaymentValidationFailure.UnsynchronizedItems
            else -> null
        }
}

sealed class PaymentValidationFailure(
    val message: String,
) {
    data object InsufficientAmount : PaymentValidationFailure("El monto recibido no cubre el total")

    data object MissingPaymentMethod : PaymentValidationFailure("Debes indicar al menos una forma de pago valida")

    data object EmptyCart : PaymentValidationFailure("No hay items en el carrito")

    data object MissingClient : PaymentValidationFailure("Debes seleccionar un cliente")

    data object MissingCaja : PaymentValidationFailure("Debes seleccionar una caja")

    data object MissingClientBranch : PaymentValidationFailure("Debes seleccionar la sucursal del cliente")

    data object MissingCajaSequence : PaymentValidationFailure("La caja no esta abierta o no tiene secuencia activa")

    data object UnsynchronizedItems :
        PaymentValidationFailure("Hay items manuales/no sincronizados que no se pueden facturar aun")
}
