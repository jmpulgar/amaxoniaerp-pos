package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.items.data.DepartamentoTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.items.data.ItemsTableVE
import com.amaxoniaerp.features.kiosk.domain.KioskCatalogResponse
import com.amaxoniaerp.features.kiosk.domain.KioskCategoryDto
import com.amaxoniaerp.features.kiosk.domain.KioskItemDto
import com.amaxoniaerp.features.kiosk.domain.KioskModifierGroupDto
import com.amaxoniaerp.features.kiosk.domain.KioskModifierOptionDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal

class KioskCatalogRepository {

    suspend fun getCatalog(
        database: Database,
        countryCode: String,
        companyDb: String,
    ): KioskCatalogResponse = dbQuery(database) {
        val normalizedCountry = countryCode.uppercase()

        // 1. Obtener departamentos visibles en POS (visible_pos = 1 y visible = true)
        val categories = DepartamentoTable
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
        val paramsRow = paramsTable
            .select(paramsTable.validarStock, paramsTable.porcentajeImpuestoPrincipal)
            .limit(1)
            .singleOrNull()
        val validarStock = paramsRow?.get(paramsTable.validarStock)?.trim()?.equals("SI", ignoreCase = true) == true
        val defaultTax = paramsRow?.get(paramsTable.porcentajeImpuestoPrincipal) ?: BigDecimal("7.00")

        // 3. Obtener items según país
        val itemsTable = ItemsTableFactory.getTableForCountry(normalizedCountry)
        val query = itemsTable
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

        val itemRows = query
            .orderBy(itemsTable.descripcion1 to SortOrder.ASC)
            .toList()

        if (itemRows.isEmpty()) {
            return@dbQuery KioskCatalogResponse(categories = categories, items = emptyList())
        }

        val itemIds = itemRows.map { it[itemsTable.idItem] }

        // 4. Modificadores vinculados a los items
        val modifierRelations = (ItemModifierRelationTable innerJoin ItemModifierGroupTable)
            .selectAll()
            .where {
                (ItemModifierRelationTable.idItem inList itemIds) and
                    (ItemModifierGroupTable.activo eq true)
            }
            .orderBy(
                ItemModifierRelationTable.idItem to SortOrder.ASC,
                ItemModifierRelationTable.orden to SortOrder.ASC,
                ItemModifierGroupTable.orden to SortOrder.ASC,
            )
            .toList()

        val groupIds = modifierRelations.map { it[ItemModifierGroupTable.id] }.distinct()

        val modifierOptionsByGroup = if (groupIds.isNotEmpty()) {
            ItemModifierTable
                .selectAll()
                .where {
                    (ItemModifierTable.idGrupo inList groupIds) and
                        (ItemModifierTable.activo eq true)
                }
                .orderBy(ItemModifierTable.orden to SortOrder.ASC)
                .groupBy { it[ItemModifierTable.idGrupo] }
        } else {
            emptyMap()
        }

        // Consultar stock de items asociados a modificadores para marcar soldOut en opciones
        val associatedItemIds = modifierOptionsByGroup.values
            .flatten()
            .mapNotNull { it[ItemModifierTable.idItemAsociado]?.takeIf { id -> id > 0 } }
            .distinct()

        val associatedStockMap = if (validarStock && associatedItemIds.isNotEmpty()) {
            itemsTable
                .selectAll()
                .where { itemsTable.idItem inList associatedItemIds }
                .associate { it[itemsTable.idItem] to (it[itemsTable.existenciaTotal] <= 0) }
        } else {
            emptyMap()
        }

        // Mapear grupos por item
        val groupsByItemId = mutableMapOf<Int, MutableList<KioskModifierGroupDto>>()
        for (rel in modifierRelations) {
            val itemId = rel[ItemModifierRelationTable.idItem]
            val groupId = rel[ItemModifierGroupTable.id]
            val options = modifierOptionsByGroup[groupId]?.map { optRow ->
                val associatedId = optRow[ItemModifierTable.idItemAsociado]
                val optionSoldOut = if (validarStock && associatedId != null && associatedId > 0) {
                    associatedStockMap[associatedId] ?: false
                } else {
                    false
                }
                KioskModifierOptionDto(
                    id = optRow[ItemModifierTable.id],
                    name = optRow[ItemModifierTable.nombre],
                    extraPrice = formatDecimal(optRow[ItemModifierTable.precioAdicional]),
                    soldOut = optionSoldOut,
                )
            } ?: emptyList()

            val groupDto = KioskModifierGroupDto(
                id = groupId,
                name = rel[ItemModifierGroupTable.nombre],
                min = rel[ItemModifierGroupTable.minSeleccion],
                max = rel[ItemModifierGroupTable.maxSeleccion],
                isMandatory = rel[ItemModifierGroupTable.esObligatorio],
                isCombo = rel[ItemModifierGroupTable.esCombo],
                options = options,
            )
            groupsByItemId.getOrPut(itemId) { mutableListOf() }.add(groupDto)
        }

        // 5. Construir items DTO
        val itemsDto = itemRows.map { row ->
            val itemId = row[itemsTable.idItem]
            val resolvedCatId = row[itemsTable.departamentoId].takeIf { it in categoryIds }
                ?: row[itemsTable.codDepartamento]

            val desc = row[itemsTable.descripcion2]?.trim()?.takeIf { it.isNotBlank() }
                ?: row[itemsTable.descripcion3]?.trim()?.takeIf { it.isNotBlank() }

            val rawFoto = row[itemsTable.foto]?.trim()?.takeIf { it.isNotBlank() }
                ?: row[itemsTable.foto1].trim().takeIf { it.isNotBlank() }
            val imageUrl = rawFoto?.let {
                val filename = it.substringAfterLast('/').ifBlank { it }
                "/api/data/$countryCode/$companyDb/item/$filename"
            }

            val stock = row[itemsTable.existenciaTotal]
            val soldOut = if (validarStock) stock <= 0 else false

            val ivaValue = row[itemsTable.iva]
            val taxRate = if (row[itemsTable.montoExento]) {
                "0.00"
            } else if (ivaValue > BigDecimal.ZERO) {
                formatDecimal(ivaValue)
            } else {
                formatDecimal(defaultTax)
            }

            KioskItemDto(
                id = itemId,
                categoryId = resolvedCatId,
                name = row[itemsTable.descripcion1].trim(),
                description = desc,
                price = formatDecimal(row[itemsTable.precio1]),
                taxRate = taxRate,
                imageUrl = imageUrl,
                soldOut = soldOut,
                modifierGroups = groupsByItemId[itemId] ?: emptyList(),
            )
        }

        KioskCatalogResponse(
            categories = categories,
            items = itemsDto,
        )
    }

    private fun formatDecimal(value: BigDecimal): String {
        val scaled = if (value.scale() < 2) value.setScale(2) else value
        return scaled.stripTrailingZeros().let {
            if (it.scale() < 2) it.setScale(2).toPlainString() else it.toPlainString()
        }
    }
}
