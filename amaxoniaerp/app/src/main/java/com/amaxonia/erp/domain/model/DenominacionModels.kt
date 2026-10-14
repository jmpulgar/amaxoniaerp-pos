package com.amaxonia.erp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MonedaDenominacion(
    val id: Int,
    val denominacion: String,
    val valor: Double,
    val rollo: Int = 0, // Unidades por rollo (0 si es billete)
    val esMoneda: Boolean = false,
    val cantidadRollo: Int = 0,
    val cantidadSuelta: Int = 0,
    val imagen: String? = null,
) {
    val cantidadTotal: Int
        get() = if (rollo > 0) (cantidadRollo * rollo) + cantidadSuelta else cantidadSuelta

    val monto: Double
        get() = cantidadTotal * valor
}

object DenominacionesCatalogo {
    /**
     * Catálogo canónico de Panamá sincronizado 1:1 con los IDs de la tabla `moneda_denominacion` en MySQL:
     * - IDs 7..13: Billetes ($1, $2, $5, $10, $20, $50, $100)
     * - IDs 1..6:  Monedas (1¢, 5¢, 10¢, 25¢, 50¢, $1.00)
     */
    fun getDenominacionesPanama(): List<MonedaDenominacion> = listOf(
        MonedaDenominacion(id = 13, denominacion = "Billete de $100.00", valor = 100.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 12, denominacion = "Billete de $50.00", valor = 50.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 11, denominacion = "Billete de $20.00", valor = 20.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 10, denominacion = "Billete de $10.00", valor = 10.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 9, denominacion = "Billete de $5.00", valor = 5.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 8, denominacion = "Billete de $2.00", valor = 2.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 7, denominacion = "Billete de $1.00", valor = 1.00, rollo = 0, esMoneda = false),
        MonedaDenominacion(id = 6, denominacion = "Moneda de $1.00", valor = 1.00, rollo = 25, esMoneda = true),
        MonedaDenominacion(id = 5, denominacion = "Moneda de $0.50", valor = 0.50, rollo = 20, esMoneda = true),
        MonedaDenominacion(id = 4, denominacion = "Moneda de $0.25", valor = 0.25, rollo = 40, esMoneda = true),
        MonedaDenominacion(id = 3, denominacion = "Moneda de $0.10", valor = 0.10, rollo = 50, esMoneda = true),
        MonedaDenominacion(id = 2, denominacion = "Moneda de $0.05", valor = 0.05, rollo = 40, esMoneda = true),
        MonedaDenominacion(id = 1, denominacion = "Moneda de $0.01", valor = 0.01, rollo = 50, esMoneda = true),
    )
}
