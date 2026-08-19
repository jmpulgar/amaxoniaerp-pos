package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.TaxpayerType
import com.amaxonia.pos.domain.repository.ClientRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Cada 3er cliente demo es jurídico; códigos "CL-001..050". */
private const val JURIDICO_EVERY_N_CLIENTS = 3
private const val MOCK_CLIENT_CODE_LENGTH = 3

class MockClientRepository : ClientRepository {
    private val mockClients = mutableListOf<Client>()
    private val failureRate = 0.1

    init {
        generateMockClients()
    }

    override suspend fun getAllClients(
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> {
        simulateNetworkDelay()
        return when {
            shouldSimulateError() -> Result.failure(Exception("Error al cargar clientes desde el servidor"))
            else -> {
                val start = (page - 1) * pageSize
                val end = (start + pageSize).coerceAtMost(mockClients.size)
                if (start >= mockClients.size) {
                    Result.success(emptyList())
                } else {
                    Result.success(mockClients.subList(start, end).toList())
                }
            }
        }
    }

    override suspend fun getClientById(id: String): Result<Client> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al obtener el cliente"))
        }
        val client = mockClients.find { it.id == id }
        return if (client != null) {
            Result.success(client)
        } else {
            Result.failure(Exception("Cliente no encontrado"))
        }
    }

    override suspend fun getDefaultClient(): Result<Client> {
        simulateNetworkDelay()
        val defaultClient =
            mockClients.firstOrNull()
                ?: return Result.failure(Exception("No hay cliente por defecto"))
        return Result.success(defaultClient)
    }

    override suspend fun searchClients(query: String): Result<List<Client>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error en la búsqueda de clientes"))
        }
        val filtered =
            mockClients.filter {
                it.firstName.contains(query, ignoreCase = true) ||
                    it.lastName.contains(query, ignoreCase = true) ||
                    it.ruc.contains(query) ||
                    it.cedula.contains(query) ||
                    it.code.contains(query, ignoreCase = true)
            }
        return Result.success(filtered)
    }

    override suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error en la bǧsqueda de clientes"))
        }
        val filtered =
            mockClients.filter {
                it.firstName.contains(query, ignoreCase = true) ||
                    it.lastName.contains(query, ignoreCase = true) ||
                    it.ruc.contains(query) ||
                    it.cedula.contains(query) ||
                    it.code.contains(query, ignoreCase = true)
            }
        val start = (page - 1) * pageSize
        val end = (start + pageSize).coerceAtMost(filtered.size)
        return when {
            start >= filtered.size -> Result.success(emptyList())
            else -> Result.success(filtered.subList(start, end).toList())
        }
    }

    override suspend fun saveClient(client: Client): Result<Unit> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al guardar el cliente"))
        }
        val existingIndex = mockClients.indexOfFirst { it.id == client.id }
        if (existingIndex >= 0) {
            mockClients[existingIndex] = client
        } else {
            mockClients.add(client)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteClient(id: String): Result<Unit> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al eliminar el cliente"))
        }
        val removed = mockClients.removeIf { it.id == id }
        return if (removed) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Cliente no encontrado"))
        }
    }

    private suspend fun simulateNetworkDelay() {
        delay((500..2000).random().toLong())
    }

    private fun shouldSimulateError(): Boolean = Random.nextFloat() < failureRate

    private fun generateMockClients() {
        mockClients.clear()
        for (i in 1..50) {
            val typeId = if (i % JURIDICO_EVERY_N_CLIENTS == 0) 2 else 1
            val taxpayer = if (typeId == 2) TaxpayerType.JURIDICO else TaxpayerType.NATURAL
            mockClients.add(
                Client(
                    code = "CL-${i.toString().padStart(MOCK_CLIENT_CODE_LENGTH, '0')}",
                    firstName = if (typeId == 2) "Empresa $i S.A." else "Cliente $i",
                    lastName = if (typeId == 2) "Comercial $i" else "Apellido $i",
                    clientTypeId = typeId,
                    taxpayerType = taxpayer,
                    ruc = if (typeId == 2) "20555$i" else "",
                    cedula = if (typeId == 1) "8-700-$i" else "",
                    dv = "${i % 10}",
                ),
            )
        }
    }
}
