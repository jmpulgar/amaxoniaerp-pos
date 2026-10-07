package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.clients.data.ClientsTable
import org.jetbrains.exposed.sql.Database

/** Resuelve el cliente genérico del kiosco a partir del código configurado en el ERP. */
class KioskCustomerRepository {
    /**
     * `clientes.id_cliente` del cliente con `cod_cliente` = [code] (también sin ceros a la
     * izquierda, igual que `ClientsRepository.getDefaultClient`); null si no existe.
     */
    suspend fun resolveClientId(
        database: Database,
        code: String,
    ): String? {
        val candidates = listOf(code.trim(), code.trim().trimStart('0')).filter { it.isNotBlank() }.distinct()
        if (candidates.isEmpty()) return null
        return dbQuery(database) {
            ClientsTable
                .select(ClientsTable.idCliente)
                .where { ClientsTable.codCliente inList candidates }
                .orderBy(ClientsTable.codCliente)
                .limit(1)
                .singleOrNull()
                ?.get(ClientsTable.idCliente)
                ?.trim()
        }
    }
}
