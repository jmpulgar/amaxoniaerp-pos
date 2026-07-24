package com.amaxonia.pos.domain.model.caja

/**
 * Estado operativo de la caja desde el punto de vista de la venta.
 *
 * Es un único origen de verdad para responder "¿puedo facturar?": solo
 * [ABIERTA] permite agregar productos y cobrar. Se deriva de la caja
 * seleccionada ([Caja]) más la secuencia realmente abierta ([CajaSecuencia]),
 * evitando que la UI y el flujo de pago se desincronicen.
 */
enum class CajaSessionStatus {
    /** Consultando el estado de la caja contra el backend. */
    VERIFICANDO,

    /** No hay ninguna caja seleccionada. */
    SIN_CAJA,

    /** Hay una caja seleccionada pero sin secuencia abierta: requiere apertura. */
    PENDIENTE_APERTURA,

    /** Caja seleccionada con secuencia abierta: se puede vender. */
    ABIERTA,
}
