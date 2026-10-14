package com.amaxoniaerp.features.assets.data

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.caja.data.CajaFormaPagoGrupoTable
import com.amaxoniaerp.features.caja.data.MonedaDenominacionTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class AssetsRepository {
    private fun resolveDb(
        countryCode: String,
        companyDb: String,
    ): String {
        if (companyDb.isNotBlank() && companyDb != "default") return companyDb
        return runCatching {
            val configDb = DatabaseManager.getConfigDatabase(countryCode)
            transaction(configDb) {
                exec("SELECT bd FROM nomempresa LIMIT 1") { rs ->
                    if (rs.next()) rs.getString("bd") else null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "demo_administrativo"
    }

    fun findFormaPagoImage(
        countryCode: String,
        companyDb: String,
        id: Int,
    ): String? =
        runCatching {
            val targetDb = resolveDb(countryCode, companyDb)
            val db = DatabaseManager.connectToCompanyDb(countryCode, targetDb)
            transaction(db) {
                CajaFormaPagoTable
                    .selectAll()
                    .where { CajaFormaPagoTable.idFormaPago eq id }
                    .firstOrNull()
                    ?.getOrNull(CajaFormaPagoTable.imagen)
            }
        }.getOrNull()

    fun findFormaPagoGrupoImage(
        countryCode: String,
        companyDb: String,
        id: Int,
    ): String? =
        runCatching {
            val targetDb = resolveDb(countryCode, companyDb)
            val db = DatabaseManager.connectToCompanyDb(countryCode, targetDb)
            transaction(db) {
                CajaFormaPagoGrupoTable
                    .selectAll()
                    .where { CajaFormaPagoGrupoTable.id eq id }
                    .firstOrNull()
                    ?.getOrNull(CajaFormaPagoGrupoTable.imagen)
            }
        }.getOrNull()

    fun findMonedaDenominacionImage(
        countryCode: String,
        companyDb: String,
        id: Int,
    ): String? =
        runCatching {
            val targetDb = resolveDb(countryCode, companyDb)
            val db = DatabaseManager.connectToCompanyDb(countryCode, targetDb)
            transaction(db) {
                MonedaDenominacionTable
                    .selectAll()
                    .where { MonedaDenominacionTable.id eq id }
                    .firstOrNull()
                    ?.getOrNull(MonedaDenominacionTable.imagen)
            }
        }.getOrNull()

    fun findItemPhoto(
        countryCode: String,
        companyDb: String,
        id: Int,
    ): String? =
        runCatching {
            val targetDb = resolveDb(countryCode, companyDb)
            val db = DatabaseManager.connectToCompanyDb(countryCode, targetDb)
            val itemTable = ItemsTableFactory.getTableForCountry(countryCode)
            transaction(db) {
                itemTable
                    .selectAll()
                    .where { itemTable.idItem eq id }
                    .firstOrNull()
                    ?.let { row ->
                        row.getOrNull(itemTable.foto)?.takeIf { it.isNotBlank() }
                            ?: row.getOrNull(itemTable.foto1)?.takeIf { it.isNotBlank() }
                    }
            }
        }.getOrNull()
}
