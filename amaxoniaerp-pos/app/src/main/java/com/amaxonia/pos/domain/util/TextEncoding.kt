package com.amaxonia.pos.domain.util

import java.nio.charset.StandardCharsets

/**
 * Sanea cadenas con mojibake producidas por doble codificación UTF-8 / ISO-8859-1 o Windows-1252.
 * Corrige secuencias comunes como "CafÃ©" -> "Café", "PorciÃ³n" -> "Porción", etc.
 */
fun cleanMojibake(text: String): String {
    if (!text.contains("Ã") && !text.contains("Â")) return text
    return runCatching {
        val fixed = String(text.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
        if (!fixed.contains('\uFFFD')) fixed else text
    }.getOrDefault(text)
}
