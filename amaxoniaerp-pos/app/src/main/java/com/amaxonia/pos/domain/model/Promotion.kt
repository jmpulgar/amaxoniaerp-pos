package com.amaxonia.pos.domain.model

import java.math.BigDecimal

data class Promocion(
    val id: String,
    val codigo: String,
    val inicio: String?,
    val fin: String?,
    val nombre: String,
    val imagen: String,
    val descuentoGlobal: BigDecimal,
    val idItem: String,
    val activo: Boolean,
    val detalles: List<PromocionDetalle>
) {
    val tipo: String get() = if (detalles.size > 1) "KIT" else "ESTANDAR"
    val total: BigDecimal get() = detalles.fold(BigDecimal.ZERO) { acc, item -> acc + item.totalConIva }
}

data class PromocionDetalle(
    val id: String,
    val promocionId: String,
    val idItem: String,
    val productName: String,
    val productCode: String,
    val productReference: String,
    val idTipoPrecio: String,
    val cantidad: BigDecimal,
    val cantidadTotal: BigDecimal,
    val unidadEmpaque: String,
    val descuento: BigDecimal,
    val descuentoMonto: BigDecimal,
    val precio: BigDecimal,
    val impuesto: BigDecimal,
    val iva: BigDecimal,
    val totalConIva: BigDecimal,
    val totalSinIva: BigDecimal,
    val grupo: String,
    val product: Product
)

sealed interface ItemCarrito {
    val id: String
    val total: BigDecimal

    data class ProductoIndividual(
        val item: CartItem
    ) : ItemCarrito {
        override val id: String = item.product.id
        override val total: BigDecimal = BigDecimal.valueOf(item.total)
    }

    data class PromocionAgrupada(
        val promocionId: String,
        val promocionCodigo: String,
        val promocionNombre: String,
        val promocionTipo: String,
        val promocionGrupo: String,
        val items: List<CartItem>
    ) : ItemCarrito {
        override val id: String = promocionId
        override val total: BigDecimal = items.fold(BigDecimal.ZERO) { acc, item -> acc + BigDecimal.valueOf(item.total) }
    }
}
