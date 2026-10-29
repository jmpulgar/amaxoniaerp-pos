package com.amaxoniaerp.features.companies.data

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.companies.domain.CompanyConfig
import com.amaxoniaerp.features.companies.domain.CompanyResponse
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

open class CompanyRepository(
    private val database: Database,
) {
    suspend fun loadCompanies(companyCodes: List<Int>): List<CompanyResponse> {
        if (companyCodes.isEmpty()) return emptyList()

        return dbQuery(database) {
            val rifsByCode =
                runCatching {
                    DatosEmpresaTable
                        .selectAll()
                        .where { DatosEmpresaTable.codEmpresa inList companyCodes }
                        .associate { row ->
                            val rawRif = row[DatosEmpresaTable.rif]?.trim().orEmpty()
                            val rawNit = row[DatosEmpresaTable.nit]?.trim().orEmpty()
                            val formattedRif =
                                when {
                                    rawRif.isBlank() -> ""
                                    rawNit.isNotBlank() && !rawRif.contains("DV", ignoreCase = true) -> "$rawRif DV $rawNit"
                                    else -> rawRif
                                }
                            row[DatosEmpresaTable.codEmpresa] to formattedRif
                        }
                }.getOrDefault(emptyMap())

            CompaniesTable
                .selectAll()
                .where { (CompaniesTable.codigo inList companyCodes) and (CompaniesTable.admisActivo eq true) }
                .map { row ->
                    val code = row[CompaniesTable.codigo]
                    CompanyResponse(
                        id = code,
                        name = row[CompaniesTable.nombre],
                        rif = rifsByCode[code]?.takeIf { it.isNotBlank() },
                    )
                }
        }
    }

    suspend fun loadCompanyConfig(companyId: Int): CompanyConfig? =
        dbQuery(database) {
            CompaniesTable
                .selectAll()
                .where { CompaniesTable.codigo eq companyId }
                .map { row ->
                    CompanyConfig(
                        id = row[CompaniesTable.codigo],
                        name = row[CompaniesTable.nombre],
                        adminDb = row[CompaniesTable.bd],
                        accountingDb = row[CompaniesTable.bdContabilidad],
                        payrollDb = row[CompaniesTable.bdNomina],
                        admisActivo = row[CompaniesTable.admisActivo],
                    )
                }.singleOrNull()
        }

    open suspend fun loadCompanyRifByAdminDb(
        adminDb: String?,
        countryCode: String,
    ): String? {
        if (adminDb.isNullOrBlank()) return null

        return runCatching {
            val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
            val parametrosTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
            dbQuery(companyDb) {
                parametrosTable
                    .selectAll()
                    .limit(1)
                    .map { it[parametrosTable.rif] }
                    .singleOrNull()
            }
        }.getOrNull()
    }
}
