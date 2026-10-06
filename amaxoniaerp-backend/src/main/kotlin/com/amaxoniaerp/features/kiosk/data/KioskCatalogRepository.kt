package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.items.data.DepartamentoTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.items.data.ItemsTableVE
import com.amaxoniaerp.features.kiosk.domain.KioskCatalogResponse
import com.amaxoniaerp.features.kiosk.domain.KioskCategoryDto
import com.amaxoniaerp.features.kiosk.domain.KioskComboGroup
import com.amaxoniaerp.features.kiosk.domain.KioskComboRules
import com.amaxoniaerp.features.kiosk.domain.KioskItemDto
import com.amaxoniaerp.features.kiosk.domain.KioskModifierGroupDto
import com.amaxoniaerp.features.kiosk.domain.KioskModifierOptionDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Catálogo del kiosco: departamentos visibles en POS, precio nivel A y combos/modificadores
 * del modelo del ERP ([KioskComboRepository]). Los grupos INCLUIDO no se eligen: se omiten de
 * `modifierGroups` y se resumen como "Incluye: ..." en la descripción del producto.
 */
class KioskCatalogRepository(
    private val comboRepository: KioskComboRepository = KioskComboRepository(),
) {
    suspend fun getCatalog(
        database: Database,
        countryCode: String,
        companyDb: String,
    ): KioskCatalogResponse {
        val comboSchema = comboRepository.schema(database)
        return dbQuery(database) {
            val normalizedCountry = countryCode.uppercase()

            // 1. Obtener departamentos visibles en POS (visible_pos = 1 y visible = true)
            val categories =
                DepartamentoTable
                    .selectAll()
                    .where { (DepartamentoTable.visible eq true) and (DepartamentoTable.visiblePos eq 1) }
                    .orderBy(DepartamentoTable.descripcion to SortOrder.ASC)
                    .mapIndexed { index, row ->
                        val id = row[DepartamentoTable.id]
                        val desc = row[DepartamentoTable.descripcion]?.trim().orEmpty()
                        val code = row[DepartamentoTable.codigo]?.trim().orEmpty()
                        val name = desc.ifBlank { code.ifBlank { "Categoría $id" } }
                        KioskCategoryDto(
                            id = id,
                            name = name,
                            iconUrl = null,
                            order = index + 1,
                        )
                    }

            if (categories.isEmpty()) {
                return@dbQuery KioskCatalogResponse(categories = emptyList(), items = emptyList())
            }

            val categoryIds = categories.map { it.id }.toSet()

            // 2. Parámetros generales para stock e impuesto por defecto
            val paramsTable = ParametrosGeneralesTableFactory.forCountry(normalizedCountry)
            val paramsRow =
                paramsTable
                    .select(paramsTable.validarStock, paramsTable.porcentajeImpuestoPrincipal)
                    .limit(1)
                    .singleOrNull()
            val validarStock = paramsRow?.get(paramsTable.validarStock)?.trim()?.equals("SI", ignoreCase = true) == true
            val defaultTax = paramsRow?.get(paramsTable.porcentajeImpuestoPrincipal) ?: BigDecimal("7.00")

            // 3. Obtener items según país
            val itemsTable = ItemsTableFactory.getTableForCountry(normalizedCountry)
            val query =
                itemsTable
                    .selectAll()
                    .where {
                        ((itemsTable.departamentoId inList categoryIds) or (itemsTable.codDepartamento inList categoryIds)) and
                            (itemsTable.estatus eq "A")
                    }

            when (normalizedCountry) {
                "PA" -> {
                    val paTable = itemsTable as ItemsTablePA
                    query.andWhere { paTable.visiblePos neq 'F' }
                }
                "VE" -> {
                    val veTable = itemsTable as ItemsTableVE
                    query.andWhere { veTable.visiblePos neq "F" }
                }
            }

            val itemRows =
                query
                    .orderBy(itemsTable.descripcion1 to SortOrder.ASC)
                    .toList()

            if (itemRows.isEmpty()) {
                return@dbQuery KioskCatalogResponse(categories = categories, items = emptyList())
            }

            val itemIds = itemRows.map { it[itemsTable.idItem] }

            // 4. Combos y modificadores (item_combos → grupos → grupo_items)
            val groupsByItemId =
                comboSchema
                    ?.let { comboRepository.loadGroupsByItem(it, itemsTable, itemIds, validarStock) }
                    .orEmpty()

            // 5. Construir items DTO
            val itemsDto =
                itemRows.map { row ->
                    val itemId = row[itemsTable.idItem]
                    val resolvedCatId =
                        row[itemsTable.departamentoId].takeIf { it in categoryIds }
                            ?: row[itemsTable.codDepartamento]

                    val desc =
                        row[itemsTable.descripcion2]?.trim()?.takeIf { it.isNotBlank() }
                            ?: row[itemsTable.descripcion3]?.trim()?.takeIf { it.isNotBlank() }

                    val rawFoto =
                        row[itemsTable.foto]?.trim()?.takeIf { it.isNotBlank() }
                            ?: row[itemsTable.foto1].trim().takeIf { it.isNotBlank() }
                    val imageUrl =
                        rawFoto?.let {
                            val filename = it.substringAfterLast('/').ifBlank { it }
                            "/api/data/$countryCode/$companyDb/item/$filename"
                        }

                    val stock = row[itemsTable.existenciaTotal]
                    val soldOut = if (validarStock) stock <= 0 else false

                    val ivaValue = row[itemsTable.iva]
                    val taxRateValue =
                        when {
                            row[itemsTable.montoExento] -> BigDecimal.ZERO
                            ivaValue > BigDecimal.ZERO -> ivaValue
                            else -> defaultTax
                        }
                    val taxRate = formatDecimal(taxRateValue)
                    val groups = groupsByItemId[itemId].orEmpty()

                    KioskItemDto(
                        id = itemId,
                        categoryId = resolvedCatId,
                        name = row[itemsTable.descripcion1].trim(),
                        description = KioskComboRules.describeItem(desc, groups),
                        price = formatDecimal(row[itemsTable.precio1]),
                        taxRate = taxRate,
                        imageUrl = imageUrl,
                        soldOut = soldOut,
                        modifierGroups = KioskComboRules.selectableGroups(groups).map { it.toDto(taxRateValue) },
                    )
                }

            KioskCatalogResponse(
                categories = categories,
                items = itemsDto,
            )
        }
    }

    /**
     * `extraPrice` va en la misma base que `price` (sin impuesto): el extra con impuesto de
     * `grupo_items` convertido con la tasa del producto, para que price + extras + taxRate cuadre
     * con la cotización del servidor.
     */
    private fun KioskComboGroup.toDto(taxRate: BigDecimal): KioskModifierGroupDto =
        KioskModifierGroupDto(
            id = id,
            name = name,
            min = effectiveMin,
            max = effectiveMax,
            isMandatory = effectiveMin > 0,
            isCombo = tipo == KioskComboRules.TIPO_COMBO,
            options =
                options.map { option ->
                    KioskModifierOptionDto(
                        id = option.id,
                        name = option.name,
                        extraPrice =
                            formatDecimal(
                                KioskComboRules.extraSinIva(option.extraConIva, taxRate).setScale(2, RoundingMode.HALF_UP),
                            ),
                        soldOut = option.soldOut,
                        isDefault = option.isDefault,
                    )
                },
        )

    private fun formatDecimal(value: BigDecimal): String {
        val scaled = if (value.scale() < 2) value.setScale(2) else value
        return scaled.stripTrailingZeros().let {
            if (it.scale() < 2) it.setScale(2).toPlainString() else it.toPlainString()
        }
    }
}
