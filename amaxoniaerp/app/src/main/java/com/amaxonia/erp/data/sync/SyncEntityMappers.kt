package com.amaxonia.erp.data.sync

import com.amaxonia.erp.data.local.db.CajaPaymentMethodEntity
import com.amaxonia.erp.data.local.db.ClientEntity
import com.amaxonia.erp.data.local.db.ClientSucursalEntity
import com.amaxonia.erp.data.local.db.ClientTypeEntity
import com.amaxonia.erp.data.local.db.PaymentMethodEntity
import com.amaxonia.erp.data.local.db.ProductEntity
import com.amaxonia.erp.data.local.db.PromocionDetalleEntity
import com.amaxonia.erp.data.local.db.PromocionEntity
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.TaxpayerType
import com.amaxonia.erp.data.remote.ImageUrlHelper
import com.amaxonia.erp.data.remote.dto.FormaPagoDto

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
        isService = isService,
        prices = prices.map { level ->
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
        idTipoPrecio = idTipoPrecio ?: "2",
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

fun ProductEntity.toDomain(
    baseUrl: String = "",
    countryCode: String = "",
    companyDb: String = "",
): Product {
    val photo = ImageUrlHelper.productImageUrl(
        baseUrl = baseUrl,
        countryCode = countryCode,
        companyDb = companyDb,
        photoPath = "",
    )
    return Product(
        id = id,
        code = code,
        description = description,
        reference = reference,
        barcode1 = barcode1,
        barcode2 = barcode2,
        barcode3 = barcode3,
        photoUrl = photo,
        department = department.toString(),
        isExempt = isExempt,
        taxRate = taxRate,
        costActual = costActual,
        unitPackage = unitPackage,
        bulkQuantity = bulkQuantity,
        portionUnit = portionUnit,
        unitOrPackage = unitOrPackage,
        isService = isService,
        prices = prices,
    )
}

fun ClientEntity.toDomain(): Client =
    Client(
        id = id,
        code = code,
        identification = identification,
        dv = dv,
        name = name,
        lastName = lastName,
        email = email,
        phone = phone,
        address = address,
        status = status,
        taxpayerType = if (taxpayerTypeId == 2) TaxpayerType.JURIDICO else TaxpayerType.NATURAL,
        clientTypeId = clientTypeId,
        permiteCredito = permiteCredito,
        diasCredito = diasCredito,
    )

fun ClientSucursalEntity.toDomain(): ClientBranch =
    ClientBranch(
        sucursalId = sucursalId,
        clienteCodigo = clienteCodigo,
        nombreSucursal = nombreSucursal,
        nombreContacto = nombreContacto,
        telefonoContacto = telefonoContacto,
        correoContacto = correoContacto,
        direccion = direccion,
        observaciones = observaciones,
    )

fun PaymentMethodEntity.toDto(): FormaPagoDto =
    FormaPagoDto(
        idFormaPago = idFormaPago,
        siglas = siglas,
        codigo = codigo?.toString() ?: siglas,
        descripcion = descripcion ?: "Forma de Pago",
        activo = activo,
        pos = pos,
        tipoMoneda = tipoMoneda.orEmpty(),
    )
