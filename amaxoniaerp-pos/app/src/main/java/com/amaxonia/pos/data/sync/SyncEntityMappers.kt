package com.amaxonia.pos.data.sync

import com.amaxonia.pos.data.local.db.CajaPaymentMethodEntity
import com.amaxonia.pos.data.local.db.ClientEntity
import com.amaxonia.pos.data.local.db.ClientSucursalEntity
import com.amaxonia.pos.data.local.db.ClientTypeEntity
import com.amaxonia.pos.data.local.db.PaymentMethodEntity
import com.amaxonia.pos.data.local.db.ProductEntity
import com.amaxonia.pos.data.local.db.PromocionDetalleEntity
import com.amaxonia.pos.data.local.db.PromocionEntity
import com.amaxonia.pos.domain.model.PriceLevel

fun ProductSyncDto.toEntity(): ProductEntity =
    ProductEntity(
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
        estatus = estatus ?: "A",
        prices =
            prices.map { level ->
                PriceLevel(
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

fun ClientSyncDto.toEntity(): ClientEntity =
    ClientEntity(
        id = id,
        code = code,
        identification = rif,
        dv = dv ?: "0",
        name = nombre,
        lastName = apellido ?: "",
        address = direccion,
        phone = telefonos,
        email = email,
        status = activo,
        clientTypeId = codTipoCliente,
        taxpayerTypeId = tipoContribuyente,
        countryId = pais,
        addressLevel1 = direccionNivel1 ?: "",
        addressLevel2 = direccionNivel2 ?: "",
        addressLevel3 = direccionNivel3 ?: "",
        permiteCredito = permiteCredito,
        diasCredito = dias,
        codTipoPrecio = codTipoPrecio,
        idSucursal = idSucursal,
    )

fun ClientBranchSyncDto.toEntity(): ClientSucursalEntity =
    ClientSucursalEntity(
        sucursalId = sucursalId,
        clienteCodigo = clienteCodigo,
        nombreSucursal = nombreSucursal,
        nombreContacto = nombreContacto,
        telefonoContacto = telefonoContacto,
        correoContacto = correoContacto,
        direccion = direccion,
        observaciones = observaciones,
    )

fun ClientTypeSyncDto.toEntity(): ClientTypeEntity =
    ClientTypeEntity(
        id = id,
        name = descripcion,
    )

fun PromotionSyncDto.toEntity(): PromocionEntity =
    PromocionEntity(
        id = id,
        codigo = codigo,
        inicio = inicio,
        fin = fin,
        nombre = promocion,
        imagen = imagen,
        descuentoGlobal = descuentoGlobal,
        idItem = idItem,
        activo = activo == 1,
    )

fun PromotionDetailSyncDto.toEntity(): PromocionDetalleEntity =
    PromocionDetalleEntity(
        id = id,
        promocionId = idPromocion,
        idItem = idItem,
        idTipoPrecio = idTipoPrecio ?: "",
        cantidad = cantidad,
        cantidadTotal = cantidadTotal,
        unidadEmpaque = unidadEmpaque,
        descuento = descuento,
        descuentoMonto = descuentoMonto,
        precio = precio,
        impuesto = impuesto,
        impuestoPorcentaje = impuestoPorcentaje,
        importe = importe,
        grupo = grupo,
    )

fun PaymentMethodSyncDto.toEntity(): PaymentMethodEntity =
    PaymentMethodEntity(
        idFormaPago = idFormaPago,
        siglas = siglas,
        codigo = codigo,
        descripcion = descripcion,
        idCajaTpConcepto = idCajaTpConcepto,
        cuentaContable = cuentaContable,
        idCajaTpRegistro = idCajaTpRegistro,
        formaPagoFact = formaPagoFact,
        activo = activo,
        pos = pos,
        imagen = imagen,
        grupo = grupo,
        orden = orden,
        idBancoCuenta = idBancoCuenta,
        idBancoOperacion = idBancoOperacion,
        tipoMoneda = tipoMoneda,
    )

fun CajaPaymentMethodSyncDto.toEntity(): CajaPaymentMethodEntity =
    CajaPaymentMethodEntity(
        idCaja = idCaja,
        idFormaPago = idFormaPago,
        activo = activo,
    )
