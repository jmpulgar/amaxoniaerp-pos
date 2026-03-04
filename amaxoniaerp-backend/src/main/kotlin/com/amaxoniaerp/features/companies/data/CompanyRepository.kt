package com.amaxoniaerp.features.companies.data

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.companies.domain.CompanyConfig
import com.amaxoniaerp.features.companies.domain.CompanyResponse
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and

class CompanyRepository(private val database: Database) {
    suspend fun loadCompanies(companyCodes: List<Int>): List<CompanyResponse> {
        if (companyCodes.isEmpty()) return emptyList()

        val companies = dbQuery(database) {
            CompaniesTable.selectAll()
                .where { (CompaniesTable.codigo inList companyCodes) and (CompaniesTable.admisActivo eq true) }
                .map { row ->
                    CompanyInfo(
                        id = row[CompaniesTable.codigo],
                        name = row[CompaniesTable.nombre],
                        adminDb = row[CompaniesTable.bd],
                    )
                }
        }

        return companies.map { company ->
            val rif = loadCompanyRif(company.adminDb)
            CompanyResponse(
                id = company.id,
                name = company.name,
                rif = rif,
            )
        }
    }

    suspend fun loadCompanyConfig(companyId: Int): CompanyConfig? = dbQuery(database) {
        CompaniesTable.selectAll()
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
            }
            .singleOrNull()
    }

    private suspend fun loadCompanyRif(adminDb: String?): String? {
        if (adminDb.isNullOrBlank()) return null

        return runCatching {
            val companyDb = DatabaseManager.connectToCompanyDb(adminDb)
            dbQuery(companyDb) {
                ParametrosGeneralesTable.selectAll()
                    .limit(1)
                    .map { it[ParametrosGeneralesTable.rif] }
                    .singleOrNull()
            }
        }.getOrNull()
    }

    private data class CompanyInfo(
        val id: Int,
        val name: String,
        val adminDb: String?,
    )
}
