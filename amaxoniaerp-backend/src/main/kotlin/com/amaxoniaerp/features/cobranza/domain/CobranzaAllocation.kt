package com.amaxoniaerp.features.cobranza.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Porción de una forma de pago aplicada a una factura. */
data class PaymentAllocation(
    val facturaIndex: Int,
    val pagoIndex: Int,
    val monto: BigDecimal,
)

/**
 * Reglas puras del cobro de facturas pendientes.
 *
 * El web deja que el cajero reparta el cobro por factura y registra las formas de pago a nivel
 * de recibo; aquí las formas de pago se distribuyen sobre las facturas **en orden** (llenando
 * cada factura antes de pasar a la siguiente), al centavo y sin residuos de redondeo, para que
 * cada `caja_nueva_detalle` quede atado a la factura cuyo saldo reduce.
 */
object CobranzaRules {
    private const val SCALE = 2
    private val CREDIT_SIGLAS = setOf("CREDITO", "CRED", "CXC", "CR")

    fun money(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP)

    fun isCreditSigla(siglas: String?): Boolean = siglas?.trim()?.uppercase() in CREDIT_SIGLAS

    /** Valida la forma del request (sin tocar base de datos). */
    fun validate(request: CreateCobranzaRequest) {
        val id = request.idCobranza.trim()

        fun fail(message: String): Nothing = throw CobranzaValidationException(message)
        when {
            id.isEmpty() || id.length > MAX_ID_LENGTH -> fail("idCobranza inválido")
            request.idCliente.isBlank() -> fail("Debes indicar el cliente")
            request.idCaja.isBlank() -> fail("Debes indicar la caja")
            request.facturas.isEmpty() -> fail("Selecciona al menos una factura a cobrar")
            request.pagos.isEmpty() -> fail("Agrega al menos una forma de pago")
            request.facturas
                .map { it.idFactura }
                .toSet()
                .size != request.facturas.size ->
                fail("Una factura no puede repetirse en el mismo cobro")
            request.facturas.any { money(it.monto) <= BigDecimal.ZERO } -> fail("Los montos a cobrar deben ser mayores a cero")
            request.pagos.any { money(it.monto) <= BigDecimal.ZERO } -> fail("Los montos de pago deben ser mayores a cero")
            request.pagos.any { money(it.efectivoCambio) < BigDecimal.ZERO } -> fail("El vuelto no puede ser negativo")
        }
        val totalFacturas = request.facturas.sumOf { money(it.monto) }
        val totalPagos = request.pagos.sumOf { money(it.monto) }
        if (totalFacturas.compareTo(totalPagos) != 0) {
            fail("El total de las formas de pago ($totalPagos) debe ser igual al total a cobrar ($totalFacturas)")
        }
    }

    /**
     * Reparte [pagos] sobre [facturas] en orden. Precondición: ambos suman lo mismo
     * (garantizado por [validate]).
     */
    fun allocate(
        facturas: List<BigDecimal>,
        pagos: List<BigDecimal>,
    ): List<PaymentAllocation> {
        val pendingFacturas = facturas.map { it.setScale(SCALE, RoundingMode.HALF_UP) }.toMutableList()
        val pendingPagos = pagos.map { it.setScale(SCALE, RoundingMode.HALF_UP) }.toMutableList()
        val allocations = mutableListOf<PaymentAllocation>()
        var f = 0
        var p = 0
        while (f < pendingFacturas.size && p < pendingPagos.size) {
            val amount = pendingFacturas[f].min(pendingPagos[p])
            if (amount > BigDecimal.ZERO) {
                allocations += PaymentAllocation(f, p, amount)
                pendingFacturas[f] -= amount
                pendingPagos[p] -= amount
            }
            if (pendingFacturas[f].signum() == 0) f++
            if (p < pendingPagos.size && pendingPagos[p].signum() == 0) p++
        }
        return allocations
    }

    private const val MAX_ID_LENGTH = 36
}
