package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.features.items.data.BaseItemsTable
import com.amaxoniaerp.features.kiosk.domain.KioskComboGroup
import com.amaxoniaerp.features.kiosk.domain.KioskComboOption
import com.amaxoniaerp.features.kiosk.domain.KioskComboRules
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import java.math.BigDecimal

/** Qué partes opcionales del modelo de combos tiene el tenant. */
data class KioskComboSchema(
    val hasGroupOrder: Boolean,
)

/**
 * Lee los combos/modificadores de los productos desde el modelo del ERP
 * (`item_combos` → `grupos` → `grupo_items`), igual que `Combos::grupos` y
 * `Composicion::onOpciones` del administrativo:
 * - un producto usa el primer combo de `item_combos` (por `id_item_combo`);
 * - se omiten opciones cuyo producto ya no existe o está inactivo (`estatus = 'I'`);
 * - se omiten grupos sin opciones;
 * - grupos por `orden, id_grupo` y opciones por `orden, id_grupo_item`.
 */
class KioskComboRepository(
    private val schemaInspector: KioskSchemaInspector = KioskSchemaInspector(),
) {
    /** null si el tenant no tiene el modelo de combos migrado (el kiosco vende sin opciones). */
    suspend fun schema(database: Database): KioskComboSchema? {
        if (!schemaInspector.hasComboModel(database)) return null
        val hasGroupOrder = "orden" in schemaInspector.columns(database, ComboGroupTable.tableName)
        return KioskComboSchema(hasGroupOrder = hasGroupOrder)
    }

    /**
     * Grupos por producto. Debe llamarse dentro de una transacción abierta sobre la base de la
     * empresa. [validarStock] marca agotadas las opciones cuyo producto no tiene existencia.
     */
    fun loadGroupsByItem(
        schema: KioskComboSchema,
        itemsTable: BaseItemsTable,
        itemIds: Collection<Int>,
        validarStock: Boolean,
    ): Map<Int, List<KioskComboGroup>> {
        if (itemIds.isEmpty()) return emptyMap()

        val comboByItem =
            ComboItemTable
                .select(ComboItemTable.idItem, ComboItemTable.idCombo)
                .where { ComboItemTable.idItem inList itemIds.distinct() }
                .orderBy(ComboItemTable.idItemCombo to SortOrder.ASC)
                .map { it[ComboItemTable.idItem] to it[ComboItemTable.idCombo] }
                .distinctBy { it.first }
                .toMap()
        if (comboByItem.isEmpty()) return emptyMap()

        val groupsByCombo = loadGroups(schema, comboByItem.values.toSet(), itemsTable, validarStock)
        return comboByItem
            .mapValues { (_, idCombo) -> groupsByCombo[idCombo].orEmpty() }
            .filterValues { it.isNotEmpty() }
    }

    private fun loadGroups(
        schema: KioskComboSchema,
        comboIds: Set<Int>,
        itemsTable: BaseItemsTable,
        validarStock: Boolean,
    ): Map<Int, List<KioskComboGroup>> {
        val groupColumns =
            listOfNotNull(
                ComboGroupTable.idGrupo,
                ComboGroupTable.idCombo,
                ComboGroupTable.nombreGrupo,
                ComboGroupTable.adiciona,
                ComboGroupTable.maximoVeces,
                ComboGroupTable.tipo,
                ComboGroupTable.minimo,
                ComboGroupTable.orden.takeIf { schema.hasGroupOrder },
            )
        val groupRows =
            ComboGroupTable
                .select(groupColumns)
                .where { ComboGroupTable.idCombo inList comboIds }
                .toList()
        if (groupRows.isEmpty()) return emptyMap()

        val optionRows =
            ComboGroupItemTable
                .select(
                    ComboGroupItemTable.idGrupoItem,
                    ComboGroupItemTable.idGrupo,
                    ComboGroupItemTable.idItem,
                    ComboGroupItemTable.nombre,
                    ComboGroupItemTable.precio,
                    ComboGroupItemTable.porDefecto,
                    ComboGroupItemTable.orden,
                ).where { ComboGroupItemTable.idGrupo inList groupRows.map { it[ComboGroupTable.idGrupo] } }
                .orderBy(ComboGroupItemTable.orden to SortOrder.ASC, ComboGroupItemTable.idGrupoItem to SortOrder.ASC)
                .toList()

        val linkedItems = loadLinkedItems(itemsTable, optionRows.mapNotNull { it[ComboGroupItemTable.idItem] }.toSet())
        val adicionaByGroup = groupRows.associate { it[ComboGroupTable.idGrupo] to (it[ComboGroupTable.adiciona] == 1) }

        val optionsByGroup =
            optionRows
                .mapNotNull { row ->
                    val groupId = row[ComboGroupItemTable.idGrupo]
                    val idItem = row[ComboGroupItemTable.idItem]?.takeIf { it > 0 }
                    val linked = idItem?.let { linkedItems[it] }
                    // Opción con producto que ya no existe o está inactivo: no se vende (igual que el POS PHP).
                    if (idItem != null && (linked == null || linked.inactive)) return@mapNotNull null
                    val name =
                        row[ComboGroupItemTable.nombre]?.trim()?.takeIf { it.isNotEmpty() }
                            ?: linked?.description
                            ?: return@mapNotNull null
                    KioskComboOption(
                        id = row[ComboGroupItemTable.idGrupoItem],
                        groupId = groupId,
                        idItem = idItem,
                        name = name,
                        extraConIva =
                            KioskComboRules.resolveExtraConIva(
                                precio = row[ComboGroupItemTable.precio],
                                grupoAdiciona = adicionaByGroup[groupId] == true,
                                linkedItemConIva = linked?.conIva,
                            ),
                        isDefault = row[ComboGroupItemTable.porDefecto] == 1,
                        orden = row[ComboGroupItemTable.orden],
                        soldOut = validarStock && linked != null && linked.stock <= 0,
                    )
                }.groupBy { it.groupId }

        return groupRows
            .map { row ->
                val groupId = row[ComboGroupTable.idGrupo]
                row[ComboGroupTable.idCombo] to
                    KioskComboGroup(
                        id = groupId,
                        name = row[ComboGroupTable.nombreGrupo].trim(),
                        tipo = row[ComboGroupTable.tipo].trim().uppercase(),
                        minimo = row[ComboGroupTable.minimo],
                        maximoVeces = row[ComboGroupTable.maximoVeces],
                        orden = if (schema.hasGroupOrder) row[ComboGroupTable.orden] ?: 0 else 0,
                        options = optionsByGroup[groupId].orEmpty(),
                    )
            }.filter { (_, group) -> group.options.isNotEmpty() }
            .sortedWith(compareBy({ it.second.orden }, { it.second.id }))
            .groupBy({ it.first }, { it.second })
    }

    private fun loadLinkedItems(
        itemsTable: BaseItemsTable,
        ids: Set<Int>,
    ): Map<Int, LinkedItem> {
        if (ids.isEmpty()) return emptyMap()
        return itemsTable
            .select(itemsTable.idItem, itemsTable.descripcion1, itemsTable.coniva1, itemsTable.estatus, itemsTable.existenciaTotal)
            .where { itemsTable.idItem inList ids }
            .associate { row ->
                row[itemsTable.idItem] to
                    LinkedItem(
                        description = row[itemsTable.descripcion1].trim(),
                        conIva = row[itemsTable.coniva1],
                        inactive = row[itemsTable.estatus].equals("I", ignoreCase = true),
                        stock = row[itemsTable.existenciaTotal],
                    )
            }
    }

    private data class LinkedItem(
        val description: String,
        val conIva: BigDecimal,
        val inactive: Boolean,
        val stock: Int,
    )
}
