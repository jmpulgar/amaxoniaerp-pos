package com.amaxoniaerp.features.clients.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.clients.domain.AccountStatementItem
import com.amaxoniaerp.features.clients.domain.AccountStatementResponse
import com.amaxoniaerp.features.clients.domain.Client
import com.amaxoniaerp.features.clients.domain.ClientSucursal
import com.amaxoniaerp.features.clients.domain.CreateClientRequest
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID

private const val CLIENT_PHOTO_LOG_SAMPLE_SIZE = 3
private const val PHOTO_FILENAME_LOG_LENGTH = 80
private const val CLIENT_CODE_LENGTH = 9
private const val DUMMY_CLIENT_CODE_LIMIT = 999_999_999
private const val DUMMY_CLIENT_CODE = "999999999"
private const val DEFAULT_PANAMA_COUNTRY_ID = 170
private const val DEFAULT_ACCOUNT_NUMBER = "1.10.11.001."
private const val DEFAULT_VENDOR_CODE = "1"
private const val DEFAULT_ZONE_CODE = "1"
private const val DEFAULT_ENTITY_CODE = "1"
private const val DEFAULT_ZERO_AMOUNT_STRING = "0.00"
private const val DEFAULT_FOREIGN_AUTH_TYPE = "01"

private fun String.fallback(
    existing: String?,
    default: String = "",
): String = this.ifBlank { existing?.takeIf { it.isNotBlank() } ?: default }

private data class EffectiveClientValues(
    val identification: String,
    val dv: String,
    val clientType: Int,
    val taxpayerType: Int,
    val country: Int,
    val address: String,
    val level1: String,
    val level2: String,
    val level3: String,
    val phone: String,
    val email: String,
    val priceType: Int,
)

private fun resolveEffectiveClientValues(
    request: CreateClientRequest,
    defaultClient: Client?,
): EffectiveClientValues {
    val isConsumidorFinal = request.identification.isBlank() || request.identification == "00000"
    return if (isConsumidorFinal && defaultClient != null) {
        EffectiveClientValues(
            identification = defaultClient.identification.ifBlank { "00000" },
            dv = defaultClient.dv.ifBlank { "00" },
            clientType = defaultClient.clientTypeId,
            taxpayerType = defaultClient.taxpayerTypeId,
            country = defaultClient.countryId,
            address = defaultClient.address.ifBlank { "PANAMA" },
            level1 = defaultClient.addressLevel1.ifBlank { "8" },
            level2 = defaultClient.addressLevel2.ifBlank { "8-8" },
            level3 = defaultClient.addressLevel3.ifBlank { "8-8-9" },
            phone = defaultClient.phone.ifBlank { "9999-9999" },
            email = defaultClient.email.ifBlank { "email@correo.com" },
            priceType = defaultClient.codTipoPrecio,
        )
    } else {
        EffectiveClientValues(
            identification = request.identification.trim().ifBlank { "00000" },
            dv = request.dv.trim().ifBlank { "00" },
            clientType = if (request.clientTypeId > 0) request.clientTypeId else 2,
            taxpayerType = if (request.taxpayerTypeId > 0) request.taxpayerTypeId else 1,
            country = if (request.countryId > 0) request.countryId else DEFAULT_PANAMA_COUNTRY_ID,
            address = request.address.trim().ifBlank { "PANAMA" },
            level1 = request.addressLevel1.trim().ifBlank { "8" },
            level2 = request.addressLevel2.trim().ifBlank { "8-8" },
            level3 = request.addressLevel3.trim().ifBlank { "8-8-9" },
            phone = request.phone.trim().ifBlank { "9999-9999" },
            email = request.email.trim().ifBlank { "email@correo.com" },
            priceType = if (request.codTipoPrecio > 0) request.codTipoPrecio else 2,
        )
    }
}

class ClientsRepository {
    private val log = LoggerFactory.getLogger(ClientsRepository::class.java)

    suspend fun listClients(
        database: Database,
        limit: Int,
        offset: Long,
        search: String?,
        includeTotal: Boolean,
        branchIds: List<Int>? = null,
        sellerId: Int? = null,
        zoneCodes: List<String>? = null,
    ): Pair<List<Client>, Long> =
        dbQuery(database) {
            val query = ClientsTable.selectAll()

            if (!branchIds.isNullOrEmpty()) {
                query.andWhere { ClientsTable.idSucursal inList branchIds }
            }

            if (!zoneCodes.isNullOrEmpty()) {
                query.andWhere { ClientsTable.codZona inList zoneCodes }
            }

            if (sellerId != null) {
                val sellerRow =
                    VendedorTable
                        .select(VendedorTable.idClientes)
                        .where { VendedorTable.idVendedor eq sellerId }
                        .firstOrNull()
                val rawClientIds = sellerRow?.getOrNull(VendedorTable.idClientes)?.trim().orEmpty()
                if (rawClientIds.isNotBlank()) {
                    val clientIds = rawClientIds.split(',').map { it.trim() }.filter { it.isNotBlank() }
                    if (clientIds.isNotEmpty()) {
                        query.andWhere {
                            (ClientsTable.idCliente inList clientIds) or (ClientsTable.codCliente inList clientIds)
                        }
                    }
                }
            }

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
            val rawClients =
                query
                    .orderBy(ClientsTable.codCliente)
                    .limit(limit)
                    .offset(offset)
                    .map { row -> mapRowToClient(row) }

            val balances = getBalancesForClients(rawClients.map { it.id })
            val data =
                rawClients.map { client ->
                    val clientBalance = balances[client.id] ?: 0.0
                    if (clientBalance != 0.0) client.copy(saldo = clientBalance) else client
                }

            data.take(CLIENT_PHOTO_LOG_SAMPLE_SIZE).forEach { c ->
                log.info(
                    "[CLIENTE FOTO] id=${c.id} " +
                        "photoFilename=${c.photoFilename?.take(PHOTO_FILENAME_LOG_LENGTH) ?: "null"}",
                )
            }
            data to total
        }

    suspend fun createClient(
        database: Database,
        countryCode: String,
        request: CreateClientRequest,
    ): Client =
        dbQuery(database) {
            val newId = UUID.randomUUID().toString()
            var generatedCode = getNextCode()
            var codeInt = generatedCode.toIntOrNull() ?: 1
            while (ClientsTable.select(ClientsTable.codCliente).where { ClientsTable.codCliente eq generatedCode }.count() > 0) {
                codeInt++
                generatedCode = codeInt.toString().padStart(CLIENT_CODE_LENGTH, '0')
            }

            val defaultClient = runCatching { findDefaultClientDirect(countryCode) }.getOrNull()
            val effective = resolveEffectiveClientValues(request, defaultClient)

            ClientsTable.insert {
                it[idCliente] = newId
                it[codCliente] = generatedCode
                it[rif] = effective.identification
                it[nombre] = request.name.uppercase()
                it[apellido] = request.lastName.uppercase()
                it[dv] = effective.dv
                it[direccion] = effective.address.uppercase()
                it[direccionNivel1] = effective.level1
                it[direccionNivel2] = effective.level2
                it[direccionNivel3] = effective.level3
                it[tipoIdentificacionExtranjera] = request.foreignAuthTypeId.takeIf { !it.isNullOrBlank() } ?: "01"
                it[telefonos] = effective.phone
                it[email] = effective.email
                it[estado] = if (request.status) "A" else "I"
                it[pais] = effective.country
                it[codTipoCliente] = effective.clientType
                it[tipoContribuyente] = effective.taxpayerType
                it[permiteCredito] = request.permiteCredito
                it[limite] = request.limiteCredito
                it[dias] = request.diasCredito
                it[tolerancia] = request.tolerancia
                it[porcParcial] = request.porcParcial
                it[porcDescuentoGlobal] = request.porcDescuentoGlobal
                it[codTipoPrecio] = effective.priceType
                it[cuentaContable] = request.cuentaContable.fallback(defaultClient?.cuentaContable, DEFAULT_ACCOUNT_NUMBER)
                it[codVendedor] = request.codVendedor.fallback(defaultClient?.codVendedor, DEFAULT_VENDOR_CODE)
                it[codZona] = request.codZona.fallback(defaultClient?.codZona, DEFAULT_ZONE_CODE)
                it[lugarEntrega] = request.lugarEntrega
                it[contribuyenteEspecial] = request.contribuyenteEspecial
                it[retenidoPorCliente] = request.retenidoPorCliente.fallback(null, DEFAULT_ZERO_AMOUNT_STRING)
                it[codEntidad] = request.codEntidad.fallback(null, DEFAULT_ENTITY_CODE)
                it[aplicaItbms] = request.aplicaItbms
                it[nombreComercial] = request.nombreComercial
                it[representante] = request.representante
                it[telefono2] = request.phone2
                it[web] = request.web
                it[fax] = request.fax
                it[codigoCrm] = request.codigoCrm
                it[actividad] = request.actividad
                it[empresaZonaLibre] = request.empresaZonaLibre
                it[pedidosOnline] = request.pedidosOnline
                it[fnacimiento] = request.fnacimiento.trim().ifBlank { "1970-01-01" }
                it[genero] = request.genero
                it[clienteFidelizacion] = request.clienteFidelizacion
                it[clienteFidelizacionSucursal] = request.clienteFidelizacionSucursal
                it[clienteFidelizacionNivel] = request.clienteFidelizacionNivel
                it[clienteFidelizacionFecha] = request.clienteFidelizacionFecha.trim().ifBlank { "1970-01-01 00:00:00" }
                it[direccionCiudad] = request.direccionCiudad
                it[localidad] = request.localidad
                it[direccionSector] = request.direccionSector
                it[direccionUrbanizacion] = request.direccionUrbanizacion
                it[direccionCalleAv] = request.direccionCalleAv
                it[direccionEdificioCasa] = request.direccionEdificioCasa
                it[direccionPiso] = request.direccionPiso
                it[direccionOficina] = request.direccionOficina
                it[direccionReferencia] = request.direccionReferencia
                it[alterna] = request.alterna
                it[alterna2] = request.alterna2
                it[fecha] = BusinessClock.todayForCountry(countryCode).toString()
            }

            if (request.sellerId != null) {
                val seller =
                    VendedorTable
                        .select(VendedorTable.idClientes)
                        .where { VendedorTable.idVendedor eq request.sellerId }
                        .firstOrNull()

                if (seller != null) {
                    val currentIds = seller[VendedorTable.idClientes]?.trim().orEmpty()
                    val updatedIds = if (currentIds.isBlank()) newId else "$currentIds,$newId"
                    VendedorTable.update({ VendedorTable.idVendedor eq request.sellerId }) {
                        it[idClientes] = updatedIds
                    }
                }
            }

            ClientsTable
                .selectAll()
                .where { ClientsTable.idCliente eq newId }
                .map { mapRowToClient(it) }
                .singleOrNull() ?: Client(
                id = newId,
                code = generatedCode,
                identification = effective.identification,
                dv = effective.dv,
                name = request.name,
                lastName = request.lastName,
                address = request.address,
                addressLevel1 = request.addressLevel1,
                addressLevel2 = request.addressLevel2,
                addressLevel3 = request.addressLevel3,
                phone = request.phone,
                email = request.email,
                status = request.status,
                clientTypeId = request.clientTypeId,
                taxpayerTypeId = request.taxpayerTypeId,
                foreignAuthTypeId = request.foreignAuthTypeId,
                countryId = request.countryId,
                permiteCredito = request.permiteCredito,
                diasCredito = request.diasCredito,
            )
        }

    suspend fun updateClient(
        database: Database,
        id: String,
        request: CreateClientRequest,
    ): Client? =
        dbQuery(database) {
            val existing =
                ClientsTable
                    .selectAll()
                    .where { (ClientsTable.idCliente eq id) or (ClientsTable.codCliente eq id) }
                    .firstOrNull() ?: return@dbQuery null

            val targetId =
                existing[ClientsTable.idCliente].ifBlank {
                    val generatedId = UUID.randomUUID().toString()
                    ClientsTable.update({ ClientsTable.codCliente eq existing[ClientsTable.codCliente] }) {
                        it[idCliente] = generatedId
                    }
                    generatedId
                }

            val updated =
                ClientsTable.update({
                    (ClientsTable.idCliente eq targetId) or (ClientsTable.idCliente eq id) or
                        (ClientsTable.codCliente eq id)
                }) { stmt ->
                    applyClientUpdate(stmt, request, existing)
                }

            if (updated == 0) {
                null
            } else {
                ClientsTable
                    .selectAll()
                    .where { (ClientsTable.idCliente eq targetId) or (ClientsTable.idCliente eq id) or (ClientsTable.codCliente eq id) }
                    .map { row -> mapRowToClient(row) }
                    .firstOrNull()
            }
        }

    private fun applyClientUpdate(
        stmt: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>,
        request: CreateClientRequest,
        existing: ResultRow,
    ) {
        stmt[ClientsTable.rif] = request.identification.fallback(existing[ClientsTable.rif])
        stmt[ClientsTable.nombre] = request.name.uppercase()
        stmt[ClientsTable.apellido] = request.lastName.uppercase()
        stmt[ClientsTable.dv] = request.dv
        stmt[ClientsTable.direccion] = request.address.uppercase().fallback(existing[ClientsTable.direccion])
        stmt[ClientsTable.direccionNivel1] = request.addressLevel1.fallback(existing[ClientsTable.direccionNivel1])
        stmt[ClientsTable.direccionNivel2] = request.addressLevel2.fallback(existing[ClientsTable.direccionNivel2])
        stmt[ClientsTable.direccionNivel3] = request.addressLevel3.fallback(existing[ClientsTable.direccionNivel3])
        stmt[ClientsTable.tipoIdentificacionExtranjera] =
            request.foreignAuthTypeId.takeIf { !it.isNullOrBlank() }
                ?: existing[ClientsTable.tipoIdentificacionExtranjera] ?: DEFAULT_FOREIGN_AUTH_TYPE
        stmt[ClientsTable.telefonos] = request.phone.fallback(existing[ClientsTable.telefonos])
        stmt[ClientsTable.email] = request.email.fallback(existing[ClientsTable.email])
        stmt[ClientsTable.estado] = if (request.status) "A" else "I"
        stmt[ClientsTable.pais] = if (request.countryId > 0) request.countryId else existing[ClientsTable.pais]
        stmt[ClientsTable.codTipoCliente] =
            if (request.clientTypeId > 0) request.clientTypeId else existing[ClientsTable.codTipoCliente]
        stmt[ClientsTable.tipoContribuyente] =
            if (request.taxpayerTypeId > 0) request.taxpayerTypeId else existing[ClientsTable.tipoContribuyente]
        stmt[ClientsTable.permiteCredito] = request.permiteCredito
        stmt[ClientsTable.limite] = request.limiteCredito
        stmt[ClientsTable.dias] = request.diasCredito
        stmt[ClientsTable.tolerancia] = request.tolerancia
        stmt[ClientsTable.porcParcial] = request.porcParcial
        stmt[ClientsTable.porcDescuentoGlobal] = request.porcDescuentoGlobal
        stmt[ClientsTable.codTipoPrecio] =
            if (request.codTipoPrecio > 0) request.codTipoPrecio else existing[ClientsTable.codTipoPrecio]
        stmt[ClientsTable.cuentaContable] =
            request.cuentaContable.fallback(existing[ClientsTable.cuentaContable], DEFAULT_ACCOUNT_NUMBER)
        stmt[ClientsTable.codVendedor] =
            request.codVendedor.fallback(existing[ClientsTable.codVendedor], DEFAULT_VENDOR_CODE)
        stmt[ClientsTable.codZona] = request.codZona.fallback(existing[ClientsTable.codZona], DEFAULT_ZONE_CODE)
        stmt[ClientsTable.lugarEntrega] = request.lugarEntrega.fallback(existing[ClientsTable.lugarEntrega])
        stmt[ClientsTable.contribuyenteEspecial] = request.contribuyenteEspecial
        stmt[ClientsTable.retenidoPorCliente] =
            request.retenidoPorCliente.fallback(existing[ClientsTable.retenidoPorCliente], DEFAULT_ZERO_AMOUNT_STRING)
        stmt[ClientsTable.codEntidad] = request.codEntidad.fallback(existing[ClientsTable.codEntidad], DEFAULT_ENTITY_CODE)
        stmt[ClientsTable.aplicaItbms] = request.aplicaItbms
        stmt[ClientsTable.nombreComercial] = request.nombreComercial.fallback(existing[ClientsTable.nombreComercial])
        stmt[ClientsTable.representante] = request.representante.fallback(existing[ClientsTable.representante])
        stmt[ClientsTable.telefono2] = request.phone2.fallback(existing[ClientsTable.telefono2])
        stmt[ClientsTable.web] = request.web.fallback(existing[ClientsTable.web])
        stmt[ClientsTable.fax] = request.fax.fallback(existing[ClientsTable.fax])
        stmt[ClientsTable.codigoCrm] = request.codigoCrm.fallback(existing[ClientsTable.codigoCrm])
        stmt[ClientsTable.actividad] = request.actividad.fallback(existing[ClientsTable.actividad])
        stmt[ClientsTable.empresaZonaLibre] = request.empresaZonaLibre
        stmt[ClientsTable.pedidosOnline] = request.pedidosOnline
        stmt[ClientsTable.fnacimiento] = request.fnacimiento.fallback(existing[ClientsTable.fnacimiento], "1970-01-01")
        stmt[ClientsTable.genero] = request.genero.fallback(existing[ClientsTable.genero])
        stmt[ClientsTable.clienteFidelizacion] = request.clienteFidelizacion
        stmt[ClientsTable.clienteFidelizacionSucursal] = request.clienteFidelizacionSucursal
        stmt[ClientsTable.clienteFidelizacionNivel] = request.clienteFidelizacionNivel
        stmt[ClientsTable.clienteFidelizacionFecha] =
            request.clienteFidelizacionFecha.fallback(existing[ClientsTable.clienteFidelizacionFecha], "1970-01-01 00:00:00")
        stmt[ClientsTable.direccionCiudad] = request.direccionCiudad.fallback(existing[ClientsTable.direccionCiudad])
        stmt[ClientsTable.localidad] = request.localidad.fallback(existing[ClientsTable.localidad])
        stmt[ClientsTable.direccionSector] = request.direccionSector.fallback(existing[ClientsTable.direccionSector])
        stmt[ClientsTable.direccionUrbanizacion] =
            request.direccionUrbanizacion.fallback(existing[ClientsTable.direccionUrbanizacion])
        stmt[ClientsTable.direccionCalleAv] = request.direccionCalleAv.fallback(existing[ClientsTable.direccionCalleAv])
        stmt[ClientsTable.direccionEdificioCasa] =
            request.direccionEdificioCasa.fallback(existing[ClientsTable.direccionEdificioCasa])
        stmt[ClientsTable.direccionPiso] = request.direccionPiso.fallback(existing[ClientsTable.direccionPiso])
        stmt[ClientsTable.direccionOficina] = request.direccionOficina.fallback(existing[ClientsTable.direccionOficina])
        stmt[ClientsTable.direccionReferencia] =
            request.direccionReferencia.fallback(existing[ClientsTable.direccionReferencia])
        stmt[ClientsTable.alterna] = request.alterna.fallback(existing[ClientsTable.alterna])
        stmt[ClientsTable.alterna2] = request.alterna2.fallback(existing[ClientsTable.alterna2])
    }

    suspend fun getClientById(
        database: Database,
        id: String,
    ): Client? =
        dbQuery(database) {
            ClientsTable
                .selectAll()
                .andWhere { ClientsTable.idCliente eq id }
                .map { row -> mapRowToClient(row) }
                .singleOrNull()
        }

    suspend fun listClientSucursales(
        database: Database,
        countryCode: String,
        clientId: String,
    ): List<ClientSucursal> =
        dbQuery(database) {
            if (!countryCode.equals("PA", ignoreCase = true)) return@dbQuery emptyList()
            val clientCode =
                ClientsTable
                    .select(ClientsTable.codCliente)
                    .where { (ClientsTable.idCliente eq clientId) or (ClientsTable.codCliente eq clientId) }
                    .map { it[ClientsTable.codCliente].take(CLIENT_CODE_LENGTH) }
                    .firstOrNull()
                    ?: return@dbQuery emptyList()

            ClientSucursalTable
                .selectAll()
                .where { ClientSucursalTable.clienteCodigo eq clientCode }
                .orderBy(ClientSucursalTable.nombreSucursal)
                .map { row ->
                    ClientSucursal(
                        sucursalId = row[ClientSucursalTable.sucursalId],
                        clienteCodigo = row[ClientSucursalTable.clienteCodigo],
                        nombreSucursal = row[ClientSucursalTable.nombreSucursal],
                        nombreContacto = row[ClientSucursalTable.nombreContacto],
                        telefonoContacto = row[ClientSucursalTable.telefonoContacto],
                        correoContacto = row[ClientSucursalTable.correoContacto],
                        direccion = row[ClientSucursalTable.direccion],
                        observaciones = row[ClientSucursalTable.observaciones],
                    )
                }
        }

    suspend fun getDefaultClient(
        database: Database,
        countryCode: String,
        sucursalId: Int? = null,
    ): Client? =
        dbQuery(database) {
            findDefaultClient(countryCode, sucursalId)
        }

    private fun findDefaultClient(
        countryCode: String,
        sucursalId: Int?,
    ): Client? {
        if (sucursalId != null) {
            val sucursalClientId = findSucursalDefaultClientId(sucursalId)
            if (!sucursalClientId.isNullOrBlank()) {
                val client = findClientByIdOrCode(sucursalClientId)
                if (client != null) return client
            }
        }
        return findDefaultClientDirect(countryCode)
    }

    private fun findSucursalDefaultClientId(sucursalId: Int): String? =
        runCatching {
            SucursalTable
                .select(SucursalTable.idClienteDefault)
                .where { SucursalTable.idSucursal eq sucursalId }
                .limit(1)
                .mapNotNull { it[SucursalTable.idClienteDefault]?.trim() }
                .firstOrNull()
        }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun findClientByIdOrCode(clientId: String): Client? =
        ClientsTable
            .selectAll()
            .where { (ClientsTable.idCliente eq clientId) or (ClientsTable.codCliente eq clientId) }
            .limit(1)
            .map { row -> mapRowToClient(row) }
            .singleOrNull()

    private fun findDefaultClientDirect(countryCode: String): Client? {
        val parametrosTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
        val defaultCode =
            parametrosTable
                .select(parametrosTable.defaultCodClienteFactura)
                .orderBy(parametrosTable.codEmpresa)
                .limit(1)
                .map { it[parametrosTable.defaultCodClienteFactura].trim() }
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return null

        val candidates =
            listOf(defaultCode, defaultCode.trimStart('0'))
                .filter { it.isNotBlank() }
                .distinct()

        return ClientsTable
            .selectAll()
            .andWhere { ClientsTable.codCliente inList candidates }
            .orderBy(ClientsTable.codCliente)
            .limit(1)
            .map { row -> mapRowToClient(row) }
            .singleOrNull()
    }

    private fun getNextCode(): String {
        var maxCode = 0
        val querySuccess =
            runCatching {
                val sql =
                    "SELECT MAX(CAST(cod_cliente AS UNSIGNED)) AS max_code " +
                        "FROM clientes WHERE cod_cliente <> '$DUMMY_CLIENT_CODE' AND cod_cliente <> ''"
                TransactionManager.current().exec(sql) { rs ->
                    if (rs.next()) {
                        maxCode = rs.getInt("max_code")
                    }
                }
            }.isSuccess

        if (!querySuccess || maxCode == 0) {
            val candidate =
                ClientsTable
                    .select(ClientsTable.codCliente)
                    .where { ClientsTable.codCliente neq DUMMY_CLIENT_CODE }
                    .mapNotNull { it[ClientsTable.codCliente].trim().toIntOrNull() }
                    .filter { it < DUMMY_CLIENT_CODE_LIMIT }
                    .maxOrNull() ?: 0
            if (candidate > maxCode) {
                maxCode = candidate
            }
        }

        return (maxCode + 1).toString().padStart(CLIENT_CODE_LENGTH, '0')
    }

    private fun getBalancesForClients(clientIds: List<String>): Map<String, Double> {
        if (clientIds.isEmpty()) return emptyMap()
        val inClause = clientIds.joinToString(",") { "'$it'" }
        val sql =
            """
            SELECT 
                COALESCE(f.id_cliente, c.id_cliente) AS client_id,
                SUM(c.monto - COALESCE(payments.total_paid, 0)) AS saldo
            FROM caja_nueva c
            LEFT JOIN (
                SELECT cd.caja_id, SUM(cd.monto) AS total_paid
                FROM caja_nueva_detalle cd
                LEFT JOIN caja_forma_pago fp ON cd.id_forma_pago = fp.id_forma_pago
                WHERE fp.siglas NOT IN ('CREDITO','CRED','CXC') OR fp.siglas IS NULL
                GROUP BY cd.caja_id
            ) payments ON payments.caja_id = c.caja_id
            LEFT JOIN factura f ON f.id_factura = c.id_factura
            WHERE c.status = 'Pendiente'
              AND c.comprobante LIKE 'FACT'
              AND COALESCE(f.id_cliente, c.id_cliente) IN ($inClause)
            GROUP BY COALESCE(f.id_cliente, c.id_cliente)
            """.trimIndent()
        val balances = mutableMapOf<String, Double>()
        runCatching {
            TransactionManager.current().exec(sql) { rs ->
                while (rs.next()) {
                    val cid = rs.getString("client_id")
                    val saldo = rs.getDouble("saldo")
                    if (cid != null) {
                        balances[cid] = saldo
                    }
                }
            }
        }
        return balances
    }

    suspend fun getAccountStatement(
        database: Database,
        clientId: String,
    ): AccountStatementResponse? =
        dbQuery(database) {
            val client =
                ClientsTable
                    .selectAll()
                    .where { (ClientsTable.idCliente eq clientId) or (ClientsTable.codCliente eq clientId) }
                    .map { mapRowToClient(it) }
                    .firstOrNull() ?: return@dbQuery null

            val sqlUnion =
                """
                (
                    SELECT 
                        c.caja_id AS id,
                        c.id_cliente AS id_cliente,
                        CONCAT(COALESCE(cl.cod_cliente, ''), ' ', COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente,
                        CONCAT(COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente_nombre,
                        COALESCE(c.fecha, '') AS fecha,
                        COALESCE(DATE_FORMAT(c.fecha_creacion, '%H:%i:%s'), '00:00:00') AS hora_creacion,
                        COALESCE(DATE_FORMAT(c.fecha, '%d/%m/%Y'), '') AS ffecha,
                        COALESCE(DATE_FORMAT(f.fecha_vencimiento, '%d/%m/%Y'), '-') AS ffecha_vencimiento,
                        COALESCE(c.comprobante, 'FACT') AS comprobante,
                        COALESCE(c.comprobante_numero, '') AS comprobante_numero,
                        COALESCE(f.numeroDocumentoFiscal, '') AS serie_numero,
                        COALESCE(c.monto, 0.0) AS monto,
                        COALESCE(c.status, '') AS status,
                        COALESCE(c.concepto, '') AS concepto,
                        '' AS siglas,
                        COALESCE(f.observacion, '') AS observacion_factura,
                        COALESCE(c.monto - (SELECT COALESCE(SUM(cnd.monto), 0.0) FROM caja_nueva_detalle cnd WHERE cnd.caja_id = c.caja_id AND cnd.monto > 0), c.monto) AS monto_pendiente
                    FROM caja_nueva c
                    LEFT JOIN clientes cl ON cl.id_cliente = c.id_cliente
                    LEFT JOIN factura f ON f.id_factura = c.id_factura
                    WHERE c.ing_eg = 'I' AND c.comprobante <> 'COBRO' AND c.comprobante <> 'CERT'
                      AND (c.id_cliente = '${client.id}' OR cl.cod_cliente = '${client.code}')
                )
                UNION ALL
                (
                    SELECT
                        cd.caja_detalle_id AS id,
                        icc.id_cliente AS id_cliente,
                        CONCAT(COALESCE(cl.cod_cliente, ''), ' ', COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente,
                        CONCAT(COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente_nombre,
                        COALESCE(icc.fecha, '') AS fecha,
                        COALESCE(DATE_FORMAT(cd.fecha_creacion, '%H:%i:%s'), '00:00:00') AS hora_creacion,
                        COALESCE(DATE_FORMAT(icc.fecha, '%d/%m/%Y'), '') AS ffecha,
                        '-' AS ffecha_vencimiento,
                        IF(fp.siglas = 'NC', 'ICC-NC', 'ICC') AS comprobante,
                        COALESCE(icc.nro_recibo, '') AS comprobante_numero,
                        COALESCE(icc.nro_referencia, '') AS serie_numero,
                        IF(fp.siglas IN ('CREDITO', 'CRED', 'CXC') AND cd.monto = 0, cd.monto_original, cd.monto) AS monto,
                        IF(icc.status = 'AN', 'Anulada', 'Pagada') AS status,
                        CONCAT(IF(icc.observacion IS NOT NULL AND icc.observacion != '', icc.observacion, COALESCE(cd.concepto, '')), ' [', COALESCE(fp.descripcion, ''), ']') AS concepto,
                        COALESCE(fp.siglas, '') AS siglas,
                        '' AS observacion_factura,
                        0.0 AS monto_pendiente
                    FROM caja_nueva_detalle cd
                    LEFT JOIN caja_nueva_recibo icc ON cd.caja_recibo_id = icc.caja_recibo_id
                    LEFT JOIN factura f ON f.id_factura = icc.id_factura
                    LEFT JOIN clientes cl ON cl.id_cliente = icc.id_cliente
                    LEFT JOIN caja_forma_pago fp ON fp.id_forma_pago = cd.id_forma_pago
                    WHERE icc.tipo_recibo = 'ICC'
                      AND icc.status <> 'AN'
                      AND (icc.id_cliente = '${client.id}' OR cl.cod_cliente = '${client.code}')
                )
                UNION ALL
                (
                    SELECT
                        A.id_abono AS id,
                        A.id_cliente AS id_cliente,
                        CONCAT(COALESCE(cl.cod_cliente, ''), ' ', COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente,
                        CONCAT(COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente_nombre,
                        COALESCE(A.fecha_emision, '') AS fecha,
                        COALESCE(DATE_FORMAT(A.fecha_creacion, '%H:%i:%s'), '00:00:00') AS hora_creacion,
                        COALESCE(DATE_FORMAT(A.fecha_emision, '%d/%m/%Y'), '') AS ffecha,
                        '-' AS ffecha_vencimiento,
                        'ABONO' AS comprobante,
                        COALESCE(A.codigo, '') AS comprobante_numero,
                        '' AS serie_numero,
                        COALESCE(A.monto, 0.0) AS monto,
                        IF(A.estatus = 2, 'Anulada', 'Pagada') AS status,
                        COALESCE(A.descripcion, '') AS concepto,
                        '' AS siglas,
                        '' AS observacion_factura,
                        0.0 AS monto_pendiente
                    FROM abono A
                    LEFT JOIN clientes cl ON cl.id_cliente = A.id_cliente
                    WHERE A.tipo = 'nota_credito'
                      AND (A.id_cliente = '${client.id}' OR cl.cod_cliente = '${client.code}')
                )
                UNION ALL
                (
                    SELECT
                        cd.caja_detalle_id AS id,
                        cn.id_cliente AS id_cliente,
                        CONCAT(COALESCE(cl.cod_cliente, ''), ' ', COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente,
                        CONCAT(COALESCE(cl.nombre, ''), ' ', COALESCE(cl.apellido, '')) AS cliente_nombre,
                        COALESCE(d.fecha_devolucion, '') AS fecha,
                        COALESCE(DATE_FORMAT(d.fecha_creacion, '%H:%i:%s'), '00:00:00') AS hora_creacion,
                        COALESCE(DATE_FORMAT(d.fecha_devolucion, '%d/%m/%Y'), '') AS ffecha,
                        '-' AS ffecha_vencimiento,
                        'NC' AS comprobante,
                        COALESCE(d.cod_devolucion, '') AS comprobante_numero,
                        IF(d.cod_devolucion_fiscal = '0', '', COALESCE(d.cod_devolucion_fiscal, '')) AS serie_numero,
                        IF(cd.monto = 0, cd.monto_original, cd.monto) AS monto,
                        IF(cn.status = 'Pendiente', 'Pagada', cn.status) AS status,
                        COALESCE(d.observacion, '') AS concepto,
                        'NC' AS siglas,
                        '' AS observacion_factura,
                        0.0 AS monto_pendiente
                    FROM caja_nueva_detalle cd
                    LEFT JOIN caja_nueva cn ON cd.caja_id = cn.caja_id
                    LEFT JOIN factura_devolucion d ON d.id_devolucion = cd.id_transaccion
                    LEFT JOIN abono a ON a.id_operacion = cd.id_transaccion
                    LEFT JOIN clientes cl ON cl.id_cliente = d.id_cliente
                    LEFT JOIN caja_forma_pago fp ON fp.id_forma_pago = cd.id_forma_pago
                    WHERE a.id_operacion IS NULL
                      AND fp.siglas = 'NC'
                      AND cn.status <> 'Anulada'
                      AND (d.id_cliente = '${client.id}' OR cl.cod_cliente = '${client.code}')
                )
                ORDER BY fecha ASC, hora_creacion ASC, IF(comprobante IN ('ICC', 'NC', 'ICC-NC'), 1, 0) ASC, comprobante_numero ASC
                """.trimIndent()

            val items = mutableListOf<AccountStatementItem>()
            var runningSaldo = 0.0
            var totalDebt = 0.0
            var totalCredit = 0.0

            val querySuccess =
                runCatching {
                    TransactionManager.current().exec(sqlUnion) { rs ->
                        while (rs.next()) {
                            val id = rs.getString("id") ?: ""
                            val fecha = rs.getString("fecha") ?: ""
                            val ffecha = rs.getString("ffecha") ?: fecha
                            val ffechaVenc = rs.getString("ffecha_vencimiento") ?: "-"
                            val comprobante = rs.getString("comprobante") ?: "FACT"
                            val compNum = rs.getString("comprobante_numero") ?: ""
                            val serieNum = rs.getString("serie_numero") ?: ""
                            val monto = rs.getDouble("monto")
                            val status = rs.getString("status") ?: ""
                            var concepto = rs.getString("concepto") ?: ""
                            val siglas = rs.getString("siglas") ?: ""
                            val obsFact = rs.getString("observacion_factura") ?: ""
                            val montoPend = rs.getDouble("monto_pendiente")
                            val clienteStr = rs.getString("cliente") ?: "${client.code} ${client.name}"
                            val clienteNombre = rs.getString("cliente_nombre") ?: client.name

                            var debito = 0.0
                            var credito = 0.0

                            if (siglas !in listOf("CREDITO", "CRED", "CXC", "ABONO")) {
                                if (comprobante in listOf("ICC", "NC", "ABONO", "COBRO", "ANTICIPO", "ICC-NC")) {
                                    credito = monto
                                } else {
                                    debito = monto
                                }
                                if (status.equals("Anulada", ignoreCase = true)) {
                                    debito = monto
                                    credito = monto
                                }
                            }

                            runningSaldo += (debito - credito)
                            totalDebt += debito
                            totalCredit += credito

                            if (concepto.isBlank()) {
                                concepto = "$clienteNombre - $comprobante - $ffecha - $compNum"
                            }

                            items.add(
                                AccountStatementItem(
                                    id = id,
                                    fecha = fecha,
                                    fechaEmision = ffecha,
                                    fechaVencimiento = if (ffechaVenc.isBlank() || ffechaVenc == "00/00/0000") "-" else ffechaVenc,
                                    comprobante = comprobante,
                                    comprobanteNumero = compNum,
                                    comprobanteFiscal = serieNum,
                                    serieNumero = serieNum,
                                    concepto = concepto,
                                    observacionFactura = obsFact,
                                    montoOriginal = monto,
                                    montoPendiente = if (debito > 0.0 && status.equals("Pendiente", ignoreCase = true)) montoPend else 0.0,
                                    debito = debito,
                                    credito = credito,
                                    saldo = runningSaldo,
                                    status = status,
                                    cliente = clienteStr,
                                ),
                            )
                        }
                    }
                }.isSuccess

            if (!querySuccess) {
                val fallback = executeStatementFallback(client, items, runningSaldo, totalDebt, totalCredit)
                runningSaldo = fallback.first
                totalDebt = fallback.second
                totalCredit = fallback.third
            }

            val totalSaldoPendiente =
                items
                    .filter {
                        it.status.equals("Pendiente", ignoreCase = true) ||
                            it.status.equals("Disponible", ignoreCase = true)
                    }.sumOf { if (it.montoPendiente > 0.0) it.montoPendiente else it.saldo }
            val totalAnticipos =
                items
                    .filter {
                        it.comprobante.equals("ANTICIPO", ignoreCase = true) ||
                            it.comprobante.equals("ABONO", ignoreCase = true)
                    }.sumOf { it.credito }
            val totalNC = items.filter { it.comprobante.equals("NC", ignoreCase = true) }.sumOf { it.credito }

            AccountStatementResponse(
                clientId = client.id,
                clientCode = client.code,
                clientName = client.name,
                identification = client.identification,
                totalDebt = totalDebt,
                totalCredit = totalCredit,
                currentBalance = runningSaldo,
                totalSaldoPendiente = totalSaldoPendiente,
                totalAnticipos = totalAnticipos,
                totalNC = totalNC,
                items = items,
            )
        }

    private fun executeStatementFallback(
        client: Client,
        items: MutableList<AccountStatementItem>,
        initialSaldo: Double,
        initialDebt: Double,
        initialCredit: Double,
    ): Triple<Double, Double, Double> {
        var runningSaldo = initialSaldo
        var totalDebt = initialDebt
        var totalCredit = initialCredit

        val sqlSimple =
            """
            SELECT 
                c.caja_id AS id,
                COALESCE(c.fecha, '') AS fecha,
                COALESCE(c.comprobante, 'FACT') AS comprobante,
                COALESCE(c.comprobante_numero, f.cod_factura, '') AS comprobante_numero,
                COALESCE(f.numeroDocumentoFiscal, f.cod_factura_fiscal, '') AS comprobante_fiscal,
                COALESCE(c.monto, 0) AS monto_original,
                COALESCE(cd.monto, 0) AS monto,
                COALESCE(cd.id_forma_pago, 0) AS id_forma_pago,
                COALESCE(c.status, '') AS status,
                COALESCE(c.concepto, '') AS concepto
            FROM caja_nueva c
            LEFT JOIN caja_nueva_detalle cd ON cd.caja_id = c.caja_id
            LEFT JOIN factura f ON f.id_factura = c.id_factura
            WHERE (COALESCE(f.id_cliente, c.id_cliente) = '${client.id}' OR COALESCE(f.id_cliente, c.id_cliente) = '${client.code}')
            ORDER BY c.fecha ASC, c.caja_id ASC
            """.trimIndent()

        runCatching {
            TransactionManager.current().exec(sqlSimple) { rs ->
                while (rs.next()) {
                    val id = rs.getString("id") ?: ""
                    val fecha = rs.getString("fecha") ?: ""
                    var comprobante = rs.getString("comprobante") ?: "FACT"
                    val compNum = rs.getString("comprobante_numero") ?: ""
                    val compFisc = rs.getString("comprobante_fiscal") ?: ""
                    val montoOrig = rs.getDouble("monto_original")
                    val monto = rs.getDouble("monto")
                    val idFormaPago = rs.getInt("id_forma_pago")
                    val status = rs.getString("status") ?: ""
                    val concepto = rs.getString("concepto") ?: ""

                    var debito = 0.0
                    var credito = 0.0

                    if (status.equals("Anulada", ignoreCase = true)) {
                        debito = montoOrig
                        credito = montoOrig
                    } else if (idFormaPago == 30 || comprobante.equals("NC", ignoreCase = true)) {
                        credito = if (monto > 0.0) monto else montoOrig
                        comprobante = "NOTA CREDITO"
                    } else if (comprobante.equals("CERT", ignoreCase = true) || comprobante.equals("ABONO", ignoreCase = true)) {
                        credito = if (monto > 0.0) monto else montoOrig
                    } else {
                        debito = montoOrig
                        credito =
                            if (status.equals("Pagada", ignoreCase = true)) {
                                if (monto > 0.0) monto else montoOrig
                            } else {
                                monto
                            }
                    }

                    totalDebt += debito
                    totalCredit += credito
                    runningSaldo += (debito - credito)

                    val montoPend = if (debito > 0.0 && status.equals("Pendiente", ignoreCase = true)) (debito - credito) else 0.0
                    items.add(
                        AccountStatementItem(
                            id = id,
                            fecha = fecha,
                            fechaEmision = fecha,
                            fechaVencimiento = "-",
                            comprobante = comprobante,
                            comprobanteNumero = compNum,
                            comprobanteFiscal = compFisc,
                            serieNumero = compFisc,
                            concepto = concepto.ifBlank { "${client.name} - $comprobante - $fecha - $compNum" },
                            montoOriginal = montoOrig,
                            montoPendiente = montoPend,
                            debito = debito,
                            credito = credito,
                            saldo = runningSaldo,
                            status = status,
                            cliente = "${client.code} ${client.name}",
                        ),
                    )
                }
            }
        }

        return Triple(runningSaldo, totalDebt, totalCredit)
    }

    private fun ResultRow.text(
        column: org.jetbrains.exposed.sql.Column<String?>,
        default: String = "",
    ): String = this[column] ?: default

    private fun ResultRow.int(
        column: org.jetbrains.exposed.sql.Column<Int?>,
        default: Int = 0,
    ): Int = this[column] ?: default

    private fun ResultRow.double(
        column: org.jetbrains.exposed.sql.Column<Double?>,
        default: Double = 0.0,
    ): Double = this[column] ?: default

    private fun mapRowToClient(row: ResultRow): Client {
        val rawId = row[ClientsTable.idCliente]
        val rawCode = row[ClientsTable.codCliente]
        val id = if (!rawId.isNullOrBlank()) rawId else rawCode
        return Client(
            id = id,
            code = rawCode,
            identification = row[ClientsTable.rif],
            dv = row.text(ClientsTable.dv),
            name = row[ClientsTable.nombre],
            lastName = row.text(ClientsTable.apellido),
            address = row[ClientsTable.direccion],
            addressLevel1 = row.text(ClientsTable.direccionNivel1),
            addressLevel2 = row.text(ClientsTable.direccionNivel2),
            addressLevel3 = row.text(ClientsTable.direccionNivel3),
            phone = row[ClientsTable.telefonos],
            email = row[ClientsTable.email],
            status = row[ClientsTable.estado] == "A" || row[ClientsTable.estado] == "1" || row[ClientsTable.estado] == "",
            clientTypeId = row[ClientsTable.codTipoCliente],
            codTipoPrecio = row[ClientsTable.codTipoPrecio],
            taxpayerTypeId = row[ClientsTable.tipoContribuyente],
            foreignAuthTypeId = row[ClientsTable.tipoIdentificacionExtranjera],
            countryId = row[ClientsTable.pais],
            photoFilename = row[ClientsTable.foto],
            permiteCredito = row[ClientsTable.permiteCredito],
            diasCredito = row[ClientsTable.dias],
            saldo = 0.0,
            nombreComercial = row.text(ClientsTable.nombreComercial),
            representante = row.text(ClientsTable.representante),
            telefono2 = row.text(ClientsTable.telefono2),
            web = row.text(ClientsTable.web),
            fax = row.text(ClientsTable.fax),
            codigoCrm = row.text(ClientsTable.codigoCrm),
            actividad = row.text(ClientsTable.actividad),
            empresaZonaLibre = row.text(ClientsTable.empresaZonaLibre, "0"),
            pedidosOnline = row.text(ClientsTable.pedidosOnline, "0"),
            fnacimiento = row.text(ClientsTable.fnacimiento),
            genero = row.text(ClientsTable.genero),
            tolerancia = row.int(ClientsTable.tolerancia, 0),
            limiteCredito = row[ClientsTable.limite],
            porcParcial = row.double(ClientsTable.porcParcial, 0.0),
            porcDescuentoGlobal = row.double(ClientsTable.porcDescuentoGlobal, 0.0),
            cuentaContable = row.text(ClientsTable.cuentaContable),
            codVendedor = row.text(ClientsTable.codVendedor),
            codZona = row.text(ClientsTable.codZona),
            lugarEntrega = row.text(ClientsTable.lugarEntrega),
            contribuyenteEspecial = row.text(ClientsTable.contribuyenteEspecial, "0"),
            retenidoPorCliente = row.text(ClientsTable.retenidoPorCliente),
            codEntidad = row.text(ClientsTable.codEntidad),
            aplicaItbms = row.text(ClientsTable.aplicaItbms, "1"),
            clienteFidelizacion = row.int(ClientsTable.clienteFidelizacion, 0),
            clienteFidelizacionSucursal = row.int(ClientsTable.clienteFidelizacionSucursal, 0),
            clienteFidelizacionNivel = row.int(ClientsTable.clienteFidelizacionNivel, 0),
            clienteFidelizacionPuntos = row.double(ClientsTable.clienteFidelizacionPuntos, 0.0),
            clienteFidelizacionFecha = row.text(ClientsTable.clienteFidelizacionFecha),
            direccionCiudad = row.text(ClientsTable.direccionCiudad),
            localidad = row.text(ClientsTable.localidad),
            direccionSector = row.text(ClientsTable.direccionSector),
            direccionUrbanizacion = row.text(ClientsTable.direccionUrbanizacion),
            direccionCalleAv = row.text(ClientsTable.direccionCalleAv),
            direccionEdificioCasa = row.text(ClientsTable.direccionEdificioCasa),
            direccionPiso = row.text(ClientsTable.direccionPiso),
            direccionOficina = row.text(ClientsTable.direccionOficina),
            direccionReferencia = row.text(ClientsTable.direccionReferencia),
            alterna = row.text(ClientsTable.alterna),
            alterna2 = row.text(ClientsTable.alterna2),
        )
    }

    suspend fun canSellerCreateClients(
        database: Database,
        sellerId: Int,
    ): Boolean =
        dbQuery(database) {
            val seller =
                VendedorTable
                    .select(VendedorTable.crearClientes)
                    .where { VendedorTable.idVendedor eq sellerId }
                    .firstOrNull()
            seller == null || seller[VendedorTable.crearClientes] != 0
        }
}
