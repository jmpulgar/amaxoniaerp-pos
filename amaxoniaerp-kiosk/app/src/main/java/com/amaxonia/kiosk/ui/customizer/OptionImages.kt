package com.amaxonia.kiosk.ui.customizer

import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import java.text.Normalizer

private val Accents = Regex("""\p{Mn}+""")
private val NonAlphanumeric = Regex("""[^a-z0-9]+""")

/**
 * Photo for a modifier option, borrowed from the catalog product with the same name: combo choices
 * ("COCA COLA LATA", "AGUA MINALBA") are usually products of their own, and the catalog does not send
 * option photos. Names are compared without case, accents or punctuation.
 */
fun optionImageResolver(items: List<KioskItemDto>): (KioskModifierOptionDto) -> String? {
    val byName =
        items
            .filterNot { it.imageUrl.isNullOrBlank() }
            .associate { normalizedName(it.name) to it.imageUrl }
    return { option -> byName[normalizedName(option.name)] }
}

internal fun normalizedName(name: String): String =
    Normalizer
        .normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(Accents, "")
        .replace(NonAlphanumeric, " ")
        .trim()
