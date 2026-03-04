package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.CompanyDetailsDto
import com.amaxonia.pos.data.remote.dto.CompanyDto
import com.amaxonia.pos.data.remote.dto.CreateClientRequest
import com.amaxonia.pos.data.remote.dto.CreateProductRequest
import com.amaxonia.pos.data.remote.dto.PriceDto
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.remote.dto.ProductStockResponseDto
import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.AuthUser
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.domain.model.CompanySession
import com.amaxonia.pos.domain.model.CompanySummary
import com.amaxonia.pos.domain.model.ForeignIdType
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.model.ProductWarehouseStock
import com.amaxonia.pos.domain.model.SelectedCompany
import com.amaxonia.pos.domain.model.TaxpayerType
import com.amaxonia.pos.domain.model.generateDefaultPrices
import java.util.UUID

// --- Company Mappers ---
fun CompanyDto.toDomainCompany(): Company {
    return Company(
        id = id.toString(),
        name = name,
        ruc = rif.orEmpty(),
        address = ""
    )
}

fun CompanyDto.toSummary(): CompanySummary {
    return CompanySummary(id = id, name = name)
}

fun CompanyDetailsDto.toSelectedCompany(): SelectedCompany {
    return SelectedCompany(
        id = id,
        name = name,
        adminDb = adminDb,
        accountingDb = accountingDb,
        payrollDb = payrollDb
    )
}

// --- Product Mappers ---
fun ProductDto.toDomain(): Product {
    val mappedPrices = if (prices.isNotEmpty()) {
        prices.map { it.toDomain() }
    } else {
        generateDefaultPrices()
    }
    return Product(
        id = id?.toString() ?: UUID.randomUUID().toString(),
        code = code.orEmpty(),
        reference = reference.orEmpty(),
        description = description.orEmpty(),
        barcode1 = barcode1.orEmpty(),
        photoUrl = photoUrl.orEmpty(),
        department = department?.toString().orEmpty(),
        taxRate = taxRate ?: 0.0,
        costActual = costActual ?: 0.0,
        prices = mappedPrices
    )
}

fun PriceDto.toDomain(): PriceLevel {
    return PriceLevel(
        label = label,
        price = price,
        utilityPercent = utilityPercent,
        pricePlusUtility = 0.0,
        pricePlusTax = pricePlusTax,
        discountPercent = discountPercent
    )
}

fun Product.toCreateRequest(): CreateProductRequest {
    return CreateProductRequest(
        code = code,
        description = description,
        reference = reference,
        barcode1 = barcode1,
        department = department.toIntOrNull() ?: 0,
        taxRate = taxRate,
        costActual = costActual,
        prices = prices.map {
            PriceDto(
                label = it.label,
                price = it.price,
                utilityPercent = it.utilityPercent,
                pricePlusTax = it.pricePlusTax,
                discountPercent = it.discountPercent
            )
        }
    )
}

fun ProductStockResponseDto.toDomain(): ProductStock {
    return ProductStock(
        itemId = itemId.toString(),
        stockTotalDisponible = stockTotalDisponible,
        almacenes = almacenes.map {
            ProductWarehouseStock(
                almacenId = it.almacenId,
                almacenNombre = it.almacenNombre,
                almacenTipo = it.almacenTipo,
                cantidad = it.cantidad,
                cantidadMuestra = it.cantidadMuestra,
                cantidadPrecomprometida = it.cantidadPrecomprometida,
                cantidadDisponible = it.cantidadDisponible,
                stockMinimo = it.stockMinimo,
                stockMaximo = it.stockMaximo
            )
        }
    )
}

// --- Client Mappers (CORREGIDOS) ---

fun ClientDto.toDomain(): Client {
    return Client(
        id = id.orEmpty(),
        code = code.orEmpty(),
        ruc = identification.orEmpty(),
        dv = dv.orEmpty(),
        firstName = name.orEmpty(),
        lastName = lastName.orEmpty(),
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        addressDetail = address.orEmpty(),
        status = status ?: true,

        // CORRECCIÓN 1: Usamos taxpayerTypeId (Int) que viene del backend
        taxpayerType = taxpayerTypeId.toTaxpayerType(),

        countryId = countryId ?: 0,
        clientTypeId = clientTypeId ?: 1,
        addressLevel1 = addressLevel1.orEmpty(),
        addressLevel2 = addressLevel2.orEmpty(),
        addressLevel3 = addressLevel3.orEmpty(),

        // CORRECCIÓN 2: Mapear tipo de identificación extranjera
        foreignIdType = foreignAuthTypeId.toForeignIdType(),
        foreignIdNumber = if (clientTypeId == 4) identification.orEmpty() else "",
        photoFilename = photoFilename.orEmpty()
    )
}

fun Client.toCreateRequest(): CreateClientRequest {
    return CreateClientRequest(
        identification = if (clientTypeId == 4) foreignIdNumber else ruc.ifBlank { cedula.ifBlank { id } },
        name = firstName,
        lastName = lastName,
        address = addressDetail,
        phone = phone,
        email = email,
        clientTypeId = clientTypeId,

        // CORRECCIÓN 3: El campo ahora se llama taxpayerTypeId y espera un Int
        taxpayerTypeId = taxpayerType.toApiValue(),

        // CORRECCIÓN 4: Enviar el código de extranjero si aplica
        foreignAuthTypeId = if (clientTypeId == 4) foreignIdType.toApiCode() else null,

        countryId = countryId,
        addressLevel1 = addressLevel1,
        addressLevel2 = addressLevel2,
        addressLevel3 = addressLevel3
    )
}

// --- Helpers para conversión de Tipos ---

// Convierte el INT del Backend (1, 2) a Enum del App
private fun Int?.toTaxpayerType(): TaxpayerType {
    return when (this) {
        2 -> TaxpayerType.JURIDICO
        else -> TaxpayerType.NATURAL // 1 o null es Natural
    }
}

// Convierte el Enum del App a INT para el Backend (1, 2)
private fun TaxpayerType.toApiValue(): Int {
    return when (this) {
        TaxpayerType.NATURAL -> 1
        TaxpayerType.JURIDICO -> 2
    }
}

// Convierte String "01"/"02" a Enum
private fun String?.toForeignIdType(): ForeignIdType {
    return when (this) {
        "02" -> ForeignIdType.PASAPORTE
        else -> ForeignIdType.TRIBUTARIA // "01" por defecto
    }
}

// Convierte Enum a String "01"/"02"
private fun ForeignIdType.toApiCode(): String {
    return when (this) {
        ForeignIdType.PASAPORTE -> "02"
        ForeignIdType.TRIBUTARIA -> "01"
    }
}
