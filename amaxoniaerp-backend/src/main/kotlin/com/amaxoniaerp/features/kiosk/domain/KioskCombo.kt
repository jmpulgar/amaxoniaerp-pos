package com.amaxoniaerp.features.kiosk.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Combo/modificadores de un producto en el modelo del ERP (módulo Combos del administrativo):
 * `item_combos` → `combos` → `grupos` → `grupo_items`.
 *
 * - `tipo` INCLUIDO: lo que el combo trae siempre (no se pregunta al cliente).
 * - `tipo` COMBO: paso del combo donde el cliente elige.
 * - `tipo` MODIFICADOR: término, extras, quitar ingredientes...
 */
data class KioskComboGroup(
    val id: Int,
    val name: String,
    val tipo: String,
    val minimo: Int,
    val maximoVeces: Int,
    val orden: Int,
    val options: List<KioskComboOption>,
) {
    val isIncluded: Boolean get() = tipo == KioskComboRules.TIPO_INCLUIDO

    /** Máximo de selecciones: `maximo_veces` (≥ 1); 0 = sin límite = cantidad de opciones. */
    val effectiveMax: Int get() = if (maximoVeces > 0) maximoVeces else options.size.coerceAtLeast(1)

    val effectiveMin: Int get() = minimo.coerceAtLeast(0)
}

/**
 * Opción de un grupo (`grupo_items`). [extraConIva] es el extra con impuesto que paga el
 * cliente por unidad, ya resuelto igual que `Combos::grupos` del PHP.
 */
data class KioskComboOption(
    val id: Int,
    val groupId: Int,
    val idItem: Int?,
    val name: String,
    val extraConIva: BigDecimal,
    val isDefault: Boolean,
    val orden: Int,
    val soldOut: Boolean,
)

/** Opción elegida en una línea, con las veces que se eligió (opciones repetidas en la petición). */
data class KioskSelectedComboOption(
    val option: KioskComboOption,
    val group: KioskComboGroup,
    val veces: Int,
) {
    /** Extra con impuesto de la opción por las veces elegida (2 decimales, como el POS PHP). */
    val extraConIva: BigDecimal get() = (option.extraConIva * BigDecimal(veces)).setScale(2, RoundingMode.HALF_UP)

    /** Nombre para comprobantes: "2 x PAPAS" cuando se eligió más de una vez. */
    val displayName: String get() = if (veces > 1) "$veces x ${option.name}" else option.name
}

/**
 * Reglas de precio y selección de combos, replicando el POS PHP (`combos.class.php`,
 * `amaxonia_composicion.js`, `Composicion::filasOpciones`):
 *
 * - Extra por opción: `grupo_items.precio`; si es NULL (datos viejos), el `coniva1` del producto
 *   enlazado cuando `grupos.adiciona = 1`, si no 0. Es un precio CON impuesto.
 * - Extra de la línea con impuesto = Σ(precio × veces) redondeado a 2 decimales; sin impuesto =
 *   extra / (1 + iva/100) redondeado a 4 decimales (exento o iva 0 → el mismo valor).
 * - Precio unitario sin impuesto de la línea = `precio1` del producto + extra sin impuesto.
 */
object KioskComboRules {
    const val TIPO_INCLUIDO = "INCLUIDO"
    const val TIPO_COMBO = "COMBO"
    const val TIPO_MODIFICADOR = "MODIFICADOR"

    private val HUNDRED = BigDecimal("100")
    private const val EXTRA_SIN_IVA_SCALE = 4

    fun resolveExtraConIva(
        precio: BigDecimal?,
        grupoAdiciona: Boolean,
        linkedItemConIva: BigDecimal?,
    ): BigDecimal {
        val raw =
            precio
                ?: if (grupoAdiciona && linkedItemConIva != null) linkedItemConIva else BigDecimal.ZERO
        return raw.setScale(2, RoundingMode.HALF_UP)
    }

    /** Convierte un extra con impuesto a su base sin impuesto (4 decimales). */
    fun extraSinIva(
        extraConIva: BigDecimal,
        taxRate: BigDecimal,
    ): BigDecimal {
        if (taxRate.signum() <= 0) return extraConIva.setScale(EXTRA_SIN_IVA_SCALE, RoundingMode.HALF_UP)
        val divisor = BigDecimal.ONE + taxRate.divide(HUNDRED, 10, RoundingMode.HALF_UP)
        return extraConIva.divide(divisor, EXTRA_SIN_IVA_SCALE, RoundingMode.HALF_UP)
    }

    /** Extra sin impuesto por unidad de la línea según la regla PHP (suma con impuesto, luego base). */
    fun lineExtraSinIva(
        selected: List<KioskSelectedComboOption>,
        taxRate: BigDecimal,
    ): BigDecimal {
        val extraConIva = selected.fold(BigDecimal.ZERO) { acc, s -> acc + s.extraConIva }.setScale(2, RoundingMode.HALF_UP)
        return extraSinIva(extraConIva, taxRate)
    }

    /** Grupos que el cliente puede elegir (los INCLUIDO no se preguntan). */
    fun selectableGroups(groups: List<KioskComboGroup>): List<KioskComboGroup> = groups.filterNot { it.isIncluded }

    /** "Incluye: a, b, c" con las opciones de los grupos INCLUIDO, o null si no hay. */
    fun includedSummary(groups: List<KioskComboGroup>): String? {
        val names = groups.filter { it.isIncluded }.flatMap { group -> group.options.map { it.name } }
        return if (names.isEmpty()) null else "Incluye: " + names.joinToString(", ")
    }

    /** Descripción del producto con el resumen de lo incluido en el combo. */
    fun describeItem(
        description: String?,
        groups: List<KioskComboGroup>,
    ): String? {
        val included = includedSummary(groups) ?: return description
        return if (description.isNullOrBlank()) included else "$description. $included"
    }

    /**
     * Valida los `id_grupo_item` elegidos para un producto: cada id debe pertenecer a un grupo
     * seleccionable del producto, no estar agotado y cada grupo cumplir mínimo/máximo.
     * Un id repetido cuenta como varias veces la misma opción.
     *
     * @throws IllegalArgumentException con un mensaje para el cliente.
     */
    fun validateSelection(
        itemName: String,
        groups: List<KioskComboGroup>,
        selectedIds: List<Int>,
    ): List<KioskSelectedComboOption> {
        val selectable = selectableGroups(groups)
        val optionsById =
            selectable
                .flatMap { group -> group.options.map { it.id to (it to group) } }
                .toMap()

        val counts = LinkedHashMap<Int, Int>()
        for (id in selectedIds) {
            require(id in optionsById) { "Modificador $id no válido para el producto '$itemName'" }
            counts[id] = (counts[id] ?: 0) + 1
        }

        val selected =
            counts
                .map { (id, veces) ->
                    val (option, group) = optionsById.getValue(id)
                    require(!option.soldOut) { "Modificador '${option.name}' se encuentra agotado" }
                    KioskSelectedComboOption(option = option, group = group, veces = veces)
                }.sortedWith(
                    compareBy<KioskSelectedComboOption>({ selectable.indexOf(it.group) }, { it.option.orden }, { it.option.id }),
                )

        val countByGroup = selected.groupBy { it.group.id }.mapValues { (_, list) -> list.sumOf { it.veces } }
        for (group in selectable) {
            val count = countByGroup[group.id] ?: 0
            require(count >= group.effectiveMin) {
                "El grupo '${group.name}' requiere al menos ${group.effectiveMin} selección(es)"
            }
            require(count <= group.effectiveMax) {
                "El grupo '${group.name}' permite un máximo de ${group.effectiveMax} selección(es)"
            }
        }
        return selected
    }
}
