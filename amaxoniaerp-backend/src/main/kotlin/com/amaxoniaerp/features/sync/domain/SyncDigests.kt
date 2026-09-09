package com.amaxoniaerp.features.sync.domain

import com.amaxoniaerp.features.sync.domain.CatalogContentHash

/**
 * Puentes entre los DTOs slim y el hash canónico de contenido (§3.1.1).
 *
 * CONTRATO: el orden de campos codificado aquí debe ser idéntico al que
 * implemente el POS en F2; lo fija el fixture cruzado de productos y la
 * documentación de cada entidad en SyncEntities.kt.
 */

fun ProductSyncDto.toProductDigest(): CatalogContentHash.ProductDigest =
    CatalogContentHash.ProductDigest(
        id = id,
        code = code,
        description = description,
        reference = reference,
        barcode1 = barcode1,
        barcode2 = barcode2,
        barcode3 = barcode3,
        department = department,
        isExempt = isExempt,
        taxRate = taxRate,
        costActual = costActual,
        unitPackage = unitPackage,
        bulkQuantity = bulkQuantity,
        portionUnit = portionUnit,
        unitOrPackage = unitOrPackage,
        estatus = estatus,
        prices =
            prices.map { level ->
                CatalogContentHash.PriceLevelDigest(
                    label = level.label,
                    price = level.price,
                    utilityPercent = level.utilityPercent,
                    pricePlusUtility = level.pricePlusUtility,
                    pricePlusTax = level.pricePlusTax,
                    unitPrice = level.unitPrice,
                    unitPricePlusTax = level.unitPricePlusTax,
                    discountPercent = level.discountPercent,
                )
            },
    )

fun productContentHash(dto: ProductSyncDto): Long = CatalogContentHash.productRowHash(dto.toProductDigest())

fun clientContentHash(dto: ClientSyncDto): Long =
    CatalogContentHash.rowHash(dto.id) {
        str(dto.code)
        str(dto.rif)
        str(dto.dv)
        str(dto.nombre)
        str(dto.apellido)
        str(dto.direccion)
        str(dto.direccionNivel1)
        str(dto.direccionNivel2)
        str(dto.direccionNivel3)
        str(dto.telefonos)
        str(dto.email)
        bool(dto.activo)
        int(dto.codTipoCliente.toLong())
        int(dto.codTipoPrecio.toLong())
        int(dto.tipoContribuyente.toLong())
        int(dto.pais.toLong())
        bool(dto.permiteCredito)
        dbl(dto.limite)
        int(dto.dias.toLong())
        str(dto.idSucursal?.toString())
    }

fun clientBranchContentHash(dto: ClientBranchSyncDto): Long =
    CatalogContentHash.rowHash(dto.sucursalId.toString()) {
        str(dto.clienteCodigo)
        str(dto.nombreSucursal)
        str(dto.nombreContacto)
        str(dto.telefonoContacto)
        str(dto.correoContacto)
        str(dto.direccion)
        str(dto.observaciones)
    }

fun clientTypeContentHash(dto: ClientTypeSyncDto): Long =
    CatalogContentHash.rowHash(dto.id.toString()) {
        str(dto.descripcion)
        str(dto.tipoClienteFe)
    }

fun promotionContentHash(dto: PromotionSyncDto): Long =
    CatalogContentHash.rowHash(dto.id) {
        str(dto.idItem)
        str(dto.codigo)
        str(dto.inicio)
        str(dto.fin)
        str(dto.promocion)
        str(dto.imagen)
        int(dto.activo.toLong())
        dbl(dto.descuentoGlobal)
    }

fun promotionDetailContentHash(dto: PromotionDetailSyncDto): Long =
    CatalogContentHash.rowHash(dto.id) {
        str(dto.idPromocion)
        str(dto.idItem)
        dbl(dto.cantidad)
        dbl(dto.cantidadTotal)
        str(dto.unidadEmpaque)
        dbl(dto.descuento)
        dbl(dto.descuentoMonto)
        str(dto.idTipoPrecio)
        dbl(dto.precio)
        dbl(dto.impuesto)
        dbl(dto.impuestoPorcentaje)
        dbl(dto.importe)
        str(dto.grupo)
    }

fun paymentMethodContentHash(dto: PaymentMethodSyncDto): Long =
    CatalogContentHash.rowHash(dto.idFormaPago.toString()) {
        str(dto.siglas)
        str(dto.codigo?.toString())
        str(dto.descripcion)
        str(dto.idCajaTpConcepto?.toString())
        str(dto.cuentaContable)
        str(dto.idCajaTpRegistro?.toString())
        str(dto.formaPagoFact)
        int(dto.activo.toLong())
        int(dto.pos.toLong())
        str(dto.imagen)
        int(dto.grupo.toLong())
        int(dto.orden.toLong())
        int(dto.idBancoCuenta.toLong())
        int(dto.idBancoOperacion.toLong())
        str(dto.tipoMoneda)
    }

fun cajaPaymentMethodContentHash(dto: CajaPaymentMethodSyncDto): Long =
    CatalogContentHash.rowHash("${dto.idCaja}:${dto.idFormaPago}") {
        str(dto.activo?.toString())
    }
