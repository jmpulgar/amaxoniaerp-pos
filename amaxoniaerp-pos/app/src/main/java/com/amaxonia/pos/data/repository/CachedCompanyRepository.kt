package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.isInitialSyncCompleted
import com.amaxonia.pos.data.local.readAuthSnapshot
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.domain.repository.CompanyRepository

class CachedCompanyRepository(
    private val localStore: LocalStore,
    private val networkMonitor: NetworkMonitor? = null,
) : CompanyRepository {
    override suspend fun getAllCompanies(): Result<List<Company>> {
        val snapshot =
            localStore.readAuthSnapshot()
                ?: return Result.failure(IllegalStateException("No hay sesion iniciada"))
        val companies = snapshot.companies.map { Company(it.id.toString(), it.name, it.rif.orEmpty(), "") }
        val isOffline = snapshot.token.startsWith("offline_session_") || networkMonitor?.isOnline() == false
        if (isOffline) {
            val syncedCompanies =
                companies.filter { company ->
                    val id = company.id.toIntOrNull() ?: return@filter false
                    localStore.isInitialSyncCompleted(id)
                }
            if (syncedCompanies.isNotEmpty()) {
                return Result.success(syncedCompanies)
            }
        }
        return Result.success(companies)
    }

    override suspend fun getCompanyById(id: String): Result<Company> {
        val snapshot =
            localStore.readAuthSnapshot()
                ?: return Result.failure(IllegalStateException("No hay sesion iniciada"))
        return snapshot.companies
            .firstOrNull { it.id.toString() == id }
            ?.let { Result.success(Company(it.id.toString(), it.name, it.rif.orEmpty(), "")) }
            ?: Result.failure(IllegalArgumentException("Empresa no encontrada"))
    }
}
