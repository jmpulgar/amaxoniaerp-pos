package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.domain.repository.CompanyRepository

class CachedCompanyRepository(
    private val localStore: LocalStore
) : CompanyRepository {
    override suspend fun getAllCompanies(): Result<List<Company>> {
        val snapshot = localStore.readAuthSnapshot()
            ?: return Result.failure(IllegalStateException("No hay sesion iniciada"))
        val companies = snapshot.companies.map { Company(it.id.toString(), it.name, it.rif.orEmpty(), "") }
        return Result.success(companies)
    }

    override suspend fun getCompanyById(id: String): Result<Company> {
        val snapshot = localStore.readAuthSnapshot()
            ?: return Result.failure(IllegalStateException("No hay sesion iniciada"))
        val company = snapshot.companies.firstOrNull { it.id.toString() == id }
            ?: return Result.failure(IllegalArgumentException("Empresa no encontrada"))
        return Result.success(Company(company.id.toString(), company.name, company.rif.orEmpty(), ""))
    }
}
