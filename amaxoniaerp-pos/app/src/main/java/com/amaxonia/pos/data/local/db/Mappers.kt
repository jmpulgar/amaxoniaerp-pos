package com.amaxonia.pos.data.local.db

import com.amaxonia.pos.data.remote.dto.AddressLevelDto
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.ClientSucursalDto
import com.amaxonia.pos.data.remote.dto.ClientTypeDto
import com.amaxonia.pos.data.remote.dto.CountryDto
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.remote.dto.PromocionDetalleDto
import com.amaxonia.pos.data.remote.dto.PromocionDto
import com.amaxonia.pos.data.repository.toDomain
import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientTypeOption
import com.amaxonia.pos.domain.model.Country
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.TaxpayerType
import com.amaxonia.pos.domain.model.generateDefaultPrices

// --- CLIENT MAPPERS ---

fun ClientDto.toEntity(): ClientEntity =
    ClientEntity(
        id = id.orEmpty(),
        code = code.orEmpty(),
        identification = identification.orEmpty(),
        dv = dv.orEmpty(),
        name = name.orEmpty(),
        lastName = lastName.orEmpty(),
        address = address.orEmpty(),
        phone = phone.orEmpty(),
        email = email.orEmpty(),
        status = status ?: true,
        clientTypeId = clientTypeId ?: 1,
        // FIX: Mapeamos el ID que viene del backend (o 1 por defecto)
        taxpayerTypeId = taxpayerTypeId ?: 1,
        countryId = countryId ?: 0,
        addressLevel1 = addressLevel1.orEmpty(),
        addressLevel2 = addressLevel2.orEmpty(),
        addressLevel3 = addressLevel3.orEmpty(),
        permiteCredito = permiteCredito,
        diasCredito = diasCredito,
    )

fun ClientEntity.toDomain(): Client {
    // FIX: Convertimos el ID numérico de la BD al Enum del Dominio
    val mappedTaxpayerType =
        when (taxpayerTypeId) {
            2 -> TaxpayerType.JURIDICO
            else -> TaxpayerType.NATURAL
        }

    return Client(
        id = id,
        code = code,
        ruc = identification,
        cedula = identification,
        dv = dv,
        firstName = name,
        lastName = lastName,
        email = email,
        phone = phone,
        addressDetail = address,
        status = status,
        taxpayerType = mappedTaxpayerType, // Usamos el mapeo corregido
        countryId = countryId,
        clientTypeId = clientTypeId,
        addressLevel1 = addressLevel1,
        addressLevel2 = addressLevel2,
        addressLevel3 = addressLevel3,
        permiteCredito = permiteCredito,
        diasCredito = diasCredito,
    )
}

fun ClientSucursalDto.toEntity(): ClientSucursalEntity =
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

// --- KEEP THE REST OF THE MAPPERS AS IS ---
// (Copia el resto de mappers de Productos, Countries, etc. igual que antes)

fun ProductDto.toEntity(): ProductEntity =
    ProductEntity(
        id = id ?: code.orEmpty(),
        code = code.orEmpty(),
        description = description.orEmpty(),
        reference = reference.orEmpty(),
        barcode1 = barcode1.orEmpty(),
        barcode2 = barcode2.orEmpty(),
        barcode3 = barcode3.orEmpty(),
        department = department?.toIntOrNull() ?: 0,
        isExempt = isExempt ?: ((taxRate ?: 0.0) <= 0.0),
        taxRate = taxRate ?: 0.0,
        costActual = costActual ?: 0.0,
        unitPackage = unitPackage.orEmpty(),
        bulkQuantity = bulkQuantity?.takeIf { it > 0.0 } ?: 1.0,
        portionUnit = portionUnit,
        unitOrPackage = unitOrPackage.orEmpty().ifBlank { "UNIDAD" },
        prices = if (prices.isNotEmpty()) prices.map { it.toDomain() } else generateDefaultPrices(),
    )

fun ProductEntity.toDomain(): Product =
    Product(
        id = id,
        code = code,
        description = description,
        reference = reference,
        barcode1 = barcode1,
        barcode2 = barcode2,
        barcode3 = barcode3,
        department = department.toString(),
        isExempt = isExempt,
        taxRate = taxRate,
        costActual = costActual,
        unitPackage = unitPackage,
        bulkQuantity = bulkQuantity.takeIf { it > 0.0 } ?: 1.0,
        portionUnit = portionUnit,
        unitOrPackage = unitOrPackage.ifBlank { "UNIDAD" },
        prices = prices.ifEmpty { generateDefaultPrices() },
    )

fun CountryDto.toEntity(): CountryEntity = CountryEntity(id = id, iso = iso, name = name)

fun AddressLevelDto.toLevel1Entity(): AddressLevel1Entity = AddressLevel1Entity(countryCode = countryCode, code = code, name = name)

fun AddressLevelDto.toLevel2Entity(): AddressLevel2Entity = AddressLevel2Entity(countryCode = countryCode, code = code, name = name)

fun AddressLevelDto.toLevel3Entity(): AddressLevel3Entity = AddressLevel3Entity(countryCode = countryCode, code = code, name = name)

fun CountryEntity.toDomain(): Country = Country(id = id, iso = iso, name = name)

fun AddressLevel1Entity.toDomain(): AddressLevel = AddressLevel(countryCode = countryCode, code = code, name = name)

fun AddressLevel2Entity.toDomain(): AddressLevel = AddressLevel(countryCode = countryCode, code = code, name = name)

fun AddressLevel3Entity.toDomain(): AddressLevel = AddressLevel(countryCode = countryCode, code = code, name = name)

fun ClientTypeDto.toEntity(): ClientTypeEntity = ClientTypeEntity(id = id, name = name)

fun ClientTypeEntity.toDomain(): ClientTypeOption = ClientTypeOption(id = id, name = name)

fun PromocionDto.toEntity(): PromocionEntity =
    PromocionEntity(
        id = id,
        codigo = codigo,
        inicio = inicio,
        fin = fin,
        nombre = promocion,
        imagen = imagen,
        descuentoGlobal = descuentoGlobal,
        idItem = idItem,
        activo = activo,
    )

fun PromocionDetalleDto.toEntity(promocionId: String): PromocionDetalleEntity =
    PromocionDetalleEntity(
        id = idPromocionDetalle.ifBlank { "$promocionId-$idItem-$grupo" },
        promocionId = promocionId,
        idItem = idItem,
        idTipoPrecio = idTipoPrecio,
        cantidad = cantidad,
        cantidadTotal = cantidadTotal,
        unidadEmpaque = unidadEmpaque,
        descuento = descuento,
        descuentoMonto = descuentoMonto,
        precio = precio,
        impuesto = impuesto,
        impuestoPorcentaje = resolvedTaxPercent,
        importe = importe,
        grupo = grupo,
    )
