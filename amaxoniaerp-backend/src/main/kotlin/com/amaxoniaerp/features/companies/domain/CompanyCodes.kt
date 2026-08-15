package com.amaxoniaerp.features.companies.domain

fun parseCompanyCodes(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()

    return raw
        .split(',')
        .mapNotNull { value ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
        }
}
