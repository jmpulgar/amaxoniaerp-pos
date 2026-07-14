package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.domain.repository.CompanyRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

class MockCompanyRepository : CompanyRepository {
    private val mockCompanies =
        listOf(
            Company("1", "Amaxonia S.A.", "20555123451", "Av. Principal 123"),
            Company("2", "Sucursal Norte", "20555987652", "Calle Los Olivos 45"),
            Company("3", "Bodega Central", "20555678903", "Zona Industrial Mz. D"),
        )
    private val failureRate = 0.1

    override suspend fun getAllCompanies(): Result<List<Company>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar empresas desde el servidor"))
        }
        return Result.success(mockCompanies)
    }

    override suspend fun getCompanyById(id: String): Result<Company> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al obtener la empresa"))
        }
        val company = mockCompanies.find { it.id == id }
        return if (company != null) {
            Result.success(company)
        } else {
            Result.failure(Exception("Empresa no encontrada"))
        }
    }

    private suspend fun simulateNetworkDelay() {
        delay((800..2000).random().toLong())
    }

    private fun shouldSimulateError(): Boolean = Random.nextFloat() < failureRate
}
