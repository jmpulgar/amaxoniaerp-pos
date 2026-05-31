package com.amaxoniaerp.features.clients.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import org.slf4j.LoggerFactory
import com.amaxoniaerp.features.clients.domain.Client
import com.amaxoniaerp.features.clients.domain.CreateClientRequest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class ClientsRepository {
    private val log = LoggerFactory.getLogger(ClientsRepository::class.java)

    suspend fun listClients(
        database: Database,
        limit: Int,
        offset: Long,
        search: String?,
        includeTotal: Boolean,
    ): Pair<List<Client>, Long> = dbQuery(database) {
        val query = ClientsTable.selectAll()

        if (!search.isNullOrBlank()) {
            val pattern = "%$search%"
            query.andWhere {
                Op.build {
                    (ClientsTable.codCliente like pattern) or
                        (ClientsTable.nombre like pattern) or
                        (ClientsTable.rif like pattern)
                }
            }
        }

        val total = if (includeTotal) query.count() else -1L
        val data = query.orderBy(ClientsTable.codCliente)
            .limit(limit)
            .offset(offset)
            .map { row -> mapRowToClient(row) }

        data.take(3).forEach { c ->
            log.info("[CLIENTE FOTO] id=${c.id} photoFilename=${c.photoFilename?.take(80) ?: "null"}")
        }
        data to total
    }

    suspend fun createClient(database: Database, countryCode: String, request: CreateClientRequest): Client = dbQuery(database) {
        val newId = UUID.randomUUID().toString()
        val generatedCode = getNextCode()

        ClientsTable.insert {
            it[idCliente] = newId
            it[codCliente] = generatedCode
            it[rif] = request.identification
            it[nombre] = request.name.uppercase()
            it[apellido] = request.lastName.uppercase()
            it[dv] = ""
            it[direccion] = request.address.uppercase()
            it[direccionNivel1] = request.addressLevel1
            it[direccionNivel2] = request.addressLevel2
            it[direccionNivel3] = request.addressLevel3
            it[tipoIdentificacionExtranjera] = request.foreignAuthTypeId
            it[telefonos] = request.phone
            it[email] = request.email
            it[estado] = "A"
            it[pais] = request.countryId
            it[codTipoCliente] = request.clientTypeId
            it[tipoContribuyente] = request.taxpayerTypeId
            it[permiteCredito] = false
            it[limite] = 0.00
            it[dias] = 0
            it[fecha] = BusinessClock.todayForCountry(countryCode).toString()
        }

        Client(
            id = newId,
            code = generatedCode,
            identification = request.identification,
            dv = "",
            name = request.name,
            lastName = request.lastName,
            address = request.address,
            addressLevel1 = request.addressLevel1,
            addressLevel2 = request.addressLevel2,
            addressLevel3 = request.addressLevel3,
            phone = request.phone,
            email = request.email,
            status = true,
            clientTypeId = request.clientTypeId,
            taxpayerTypeId = request.taxpayerTypeId,
            foreignAuthTypeId = request.foreignAuthTypeId,
            countryId = request.countryId,
        )
    }

    suspend fun updateClient(database: Database, id: String, request: CreateClientRequest): Client? = dbQuery(database) {
        val updated = ClientsTable.update({ ClientsTable.idCliente eq id }) {
            it[rif] = request.identification
            it[nombre] = request.name.uppercase()
            it[apellido] = request.lastName.uppercase()
            it[direccion] = request.address.uppercase()
            it[direccionNivel1] = request.addressLevel1
            it[direccionNivel2] = request.addressLevel2
            it[direccionNivel3] = request.addressLevel3
            it[tipoIdentificacionExtranjera] = request.foreignAuthTypeId
            it[telefonos] = request.phone
            it[email] = request.email
            it[pais] = request.countryId
            it[codTipoCliente] = request.clientTypeId
            it[tipoContribuyente] = request.taxpayerTypeId
        }

        if (updated == 0) {
            null
        } else {
            ClientsTable.selectAll()
                .andWhere { ClientsTable.idCliente eq id }
                .map { row -> mapRowToClient(row) }
                .singleOrNull()
        }
    }

    suspend fun getClientById(database: Database, id: String): Client? = dbQuery(database) {
        ClientsTable.selectAll()
            .andWhere { ClientsTable.idCliente eq id }
            .map { row -> mapRowToClient(row) }
            .singleOrNull()
    }

    suspend fun getDefaultClient(database: Database, countryCode: String): Client? = dbQuery(database) {
        val parametrosTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
        val defaultCode = parametrosTable
            .select(parametrosTable.defaultCodClienteFactura)
            .orderBy(parametrosTable.codEmpresa)
            .limit(1)
            .map { it[parametrosTable.defaultCodClienteFactura].trim() }
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@dbQuery null

        val candidates = listOf(defaultCode, defaultCode.trimStart('0'))
            .filter { it.isNotBlank() }
            .distinct()

        ClientsTable.selectAll()
            .andWhere { ClientsTable.codCliente inList candidates }
            .orderBy(ClientsTable.codCliente)
            .limit(1)
            .map { row -> mapRowToClient(row) }
            .singleOrNull()
    }

    private fun getNextCode(): String {
        val maxCode = ClientsTable
            .selectAll()
            .mapNotNull { it[ClientsTable.codCliente].toIntOrNull() }
            .maxOrNull() ?: 0

        return (maxCode + 1).toString().padStart(9, '0')
    }

    private fun mapRowToClient(row: ResultRow): Client {
        return Client(
            id = row[ClientsTable.idCliente],
            code = row[ClientsTable.codCliente],
            identification = row[ClientsTable.rif],
            dv = row[ClientsTable.dv],
            name = row[ClientsTable.nombre],
            lastName = row[ClientsTable.apellido],
            address = row[ClientsTable.direccion],
            addressLevel1 = row[ClientsTable.direccionNivel1] ?: "",
            addressLevel2 = row[ClientsTable.direccionNivel2] ?: "",
            addressLevel3 = row[ClientsTable.direccionNivel3] ?: "",
            phone = row[ClientsTable.telefonos],
            email = row[ClientsTable.email],
            status = row[ClientsTable.estado] == "A",
            clientTypeId = row[ClientsTable.codTipoCliente],
            taxpayerTypeId = row[ClientsTable.tipoContribuyente],
            foreignAuthTypeId = row[ClientsTable.tipoIdentificacionExtranjera],
            countryId = row[ClientsTable.pais],
            photoFilename = row[ClientsTable.foto],
        )
    }
}
