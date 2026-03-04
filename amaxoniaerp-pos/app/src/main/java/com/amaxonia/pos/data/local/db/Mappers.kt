package com.amaxonia.pos.data.local.db

import com.amaxonia.pos.data.remote.dto.*
import com.amaxonia.pos.data.repository.toDomain
import com.amaxonia.pos.domain.model.*

// --- CLIENT MAPPERS ---

fun ClientDto.toEntity(): ClientEntity {
    return ClientEntity(
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
        addressLevel3 = addressLevel3.orEmpty()
    )
}

fun ClientEntity.toDomain(): Client {
    // FIX: Convertimos el ID numérico de la BD al Enum del Dominio
    val mappedTaxpayerType = when(taxpayerTypeId) {
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
        addressLevel3 = addressLevel3
    )
}

// --- KEEP THE REST OF THE MAPPERS AS IS ---
// (Copia el resto de mappers de Productos, Countries, etc. igual que antes)

fun ProductDto.toEntity(): ProductEntity {
    return ProductEntity(
        id = id?.toString() ?: code.orEmpty(),
        code = code.orEmpty(),
        description = description.orEmpty(),
        reference = reference.orEmpty(),
        barcode1 = barcode1.orEmpty(),
        department = department ?: 0,
        taxRate = taxRate ?: 0.0,
        costActual = costActual ?: 0.0,
        prices = if (prices.isNotEmpty()) prices.map { it.toDomain() } else generateDefaultPrices()
    )
}

fun ProductEntity.toDomain(): Product {
    return Product(
        id = id,
        code = code,
        description = description,
        reference = reference,
        barcode1 = barcode1,
        department = department.toString(),
        taxRate = taxRate,
        costActual = costActual,
        prices = prices.ifEmpty { generateDefaultPrices() }
    )
}

fun CountryDto.toEntity(): CountryEntity {
    return CountryEntity(id = id, iso = iso, name = name)
}

fun AddressLevelDto.toLevel1Entity(): AddressLevel1Entity {
    return AddressLevel1Entity(countryCode = countryCode, code = code, name = name)
}

fun AddressLevelDto.toLevel2Entity(): AddressLevel2Entity {
    return AddressLevel2Entity(countryCode = countryCode, code = code, name = name)
}

fun AddressLevelDto.toLevel3Entity(): AddressLevel3Entity {
    return AddressLevel3Entity(countryCode = countryCode, code = code, name = name)
}

fun CountryEntity.toDomain(): Country {
    return Country(id = id, iso = iso, name = name)
}

fun AddressLevel1Entity.toDomain(): AddressLevel {
    return AddressLevel(countryCode = countryCode, code = code, name = name)
}

fun AddressLevel2Entity.toDomain(): AddressLevel {
    return AddressLevel(countryCode = countryCode, code = code, name = name)
}

fun AddressLevel3Entity.toDomain(): AddressLevel {
    return AddressLevel(countryCode = countryCode, code = code, name = name)
}

fun ClientTypeDto.toEntity(): ClientTypeEntity {
    return ClientTypeEntity(id = id, name = name)
}

fun ClientTypeEntity.toDomain(): ClientTypeOption {
    return ClientTypeOption(id = id, name = name)
}
