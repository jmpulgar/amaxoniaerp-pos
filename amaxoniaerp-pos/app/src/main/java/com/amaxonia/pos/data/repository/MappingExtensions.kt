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

private fun priceByLabel(prices: List<PriceLevel>, label: String): PriceLevel {
    return prices.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: PriceLevel(label = label)
}

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
        id = id ?: UUID.randomUUID().toString(),
        code = code.orEmpty(),
        reference = reference.orEmpty(),
        description = description.orEmpty(),
        barcode1 = barcode1.orEmpty(),
        barcode2 = barcode2.orEmpty(),
        barcode3 = barcode3.orEmpty(),
        photoUrl = photoUrl.orEmpty(),
        department = department.orEmpty(),
        section = section.orEmpty(),
        family = family.orEmpty(),
        subFamily = subFamily.orEmpty(),
        brand = brand.orEmpty(),
        line = line.orEmpty(),
        gobSegment = gobSegment.orEmpty(),
        gobFamily = gobFamily.orEmpty(),
        isExempt = isExempt ?: ((taxRate ?: 0.0) <= 0.0),
        taxRate = taxRate ?: 0.0,
        costActual = costActual ?: 0.0,
        costAverage = costAverage ?: 0.0,
        costPrevious = costPrevious ?: 0.0,
        costCIF = costCIF ?: 0.0,
        costFOB = costFOB ?: 0.0,
        costProcessed = costProcessed ?: 0.0,
        commissionPercent = commissionPercent ?: 0.0,
        costEuroOrigin = costEuroOrigin ?: 0.0,
        costFranco = costFranco ?: 0.0,
        prices = mappedPrices
    )
}

fun PriceDto.toDomain(): PriceLevel {
    return PriceLevel(
        label = label,
        price = price,
        utilityPercent = utilityPercent,
        pricePlusUtility = if (pricePlusUtility > 0.0) pricePlusUtility else price,
        pricePlusTax = pricePlusTax,
        discountPercent = discountPercent
    )
}

fun Product.toCreateRequest(): CreateProductRequest {
    return CreateProductRequest(
        code = code,
        name = description,
        description = description,
        reference = reference,
        barcode = barcode1,
        barcode2 = barcode2,
        barcode3 = barcode3,
        departmentId = department.toIntOrNull() ?: 0,
        sectionId = section.toIntOrNull() ?: 0,
        familyId = family.toIntOrNull() ?: 0,
        subfamilyId = subFamily.toIntOrNull() ?: 0,
        brandId = brand.toIntOrNull() ?: 0,
        lineId = line.toIntOrNull() ?: 0,
        price1 = priceByLabel(prices, "A").price,
        utility1 = priceByLabel(prices, "A").utilityPercent,
        priceWithTax1 = if (isExempt) priceByLabel(prices, "A").price else priceByLabel(prices, "A").pricePlusTax,
        price2 = priceByLabel(prices, "B").price,
        utility2 = priceByLabel(prices, "B").utilityPercent,
        priceWithTax2 = if (isExempt) priceByLabel(prices, "B").price else priceByLabel(prices, "B").pricePlusTax,
        price3 = priceByLabel(prices, "C").price,
        utility3 = priceByLabel(prices, "C").utilityPercent,
        priceWithTax3 = if (isExempt) priceByLabel(prices, "C").price else priceByLabel(prices, "C").pricePlusTax,
        price4 = priceByLabel(prices, "D").price,
        utility4 = priceByLabel(prices, "D").utilityPercent,
        priceWithTax4 = if (isExempt) priceByLabel(prices, "D").price else priceByLabel(prices, "D").pricePlusTax,
        price5 = priceByLabel(prices, "E").price,
        utility5 = priceByLabel(prices, "E").utilityPercent,
        priceWithTax5 = if (isExempt) priceByLabel(prices, "E").price else priceByLabel(prices, "E").pricePlusTax,
        currentCost = costActual,
        isTaxExempt = isExempt,
        taxRate = if (isExempt) 0.0 else taxRate,
        totalStock = 0
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
