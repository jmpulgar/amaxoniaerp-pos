package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.kiosk.domain.KioskDevice
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class KioskDeviceRepository {
    suspend fun findDeviceById(
        database: Database,
        id: String,
    ): KioskDevice? =
        dbQuery(database) {
            KioskDeviceTable
                .selectAll()
                .where { (KioskDeviceTable.id eq id) and (KioskDeviceTable.activo eq true) }
                .singleOrNull()
                ?.toKioskDevice()
        }

    suspend fun findCandidateDevices(database: Database): List<KioskDevice> =
        dbQuery(database) {
            KioskDeviceTable
                .selectAll()
                .where { KioskDeviceTable.activo eq true }
                .map { it.toKioskDevice() }
        }

    /**
     * Current time on the company's database server. Pairing codes are written by the PHP admin with
     * that server's clock, which may sit in a different time zone than the backend host (PA vs VE).
     */
    suspend fun currentDatabaseTime(database: Database): LocalDateTime =
        dbQuery(database) {
            TransactionManager.current().exec("SELECT NOW()") { rs ->
                rs.next()
                rs.getTimestamp(1).toLocalDateTime()
            } ?: LocalDateTime.now()
        }

    suspend fun updateDevicePairing(
        database: Database,
        deviceId: String,
        tokenHash: String,
        now: LocalDateTime,
    ): Boolean =
        dbQuery(database) {
            val updated =
                KioskDeviceTable.update({ KioskDeviceTable.id eq deviceId }) {
                    it[KioskDeviceTable.tokenHash] = tokenHash
                    it[KioskDeviceTable.codigoEmparejamientoHash] = null
                    it[KioskDeviceTable.codigoExpiraEn] = null
                    it[KioskDeviceTable.ultimoContacto] = now
                }
            updated > 0
        }

    suspend fun touchLastContact(
        database: Database,
        deviceId: String,
        now: LocalDateTime,
    ) = dbQuery(database) {
        KioskDeviceTable.update({ KioskDeviceTable.id eq deviceId }) {
            it[ultimoContacto] = now
        }
    }

    suspend fun getClaveKiosko(database: Database): String? =
        dbQuery(database) {
            KioskParametrosTable
                .select(KioskParametrosTable.claveKiosko)
                .limit(1)
                .singleOrNull()
                ?.get(KioskParametrosTable.claveKiosko)
        }

    private fun ResultRow.toKioskDevice(): KioskDevice =
        KioskDevice(
            id = this[KioskDeviceTable.id].trim(),
            nombre = this[KioskDeviceTable.nombre],
            prefijoPedido = this[KioskDeviceTable.prefijoPedido],
            idCaja = this[KioskDeviceTable.idCaja],
            idSucursal = this[KioskDeviceTable.idSucursal],
            idAlmacen = this[KioskDeviceTable.idAlmacen],
            codVendedor = this[KioskDeviceTable.codVendedor],
            idClienteGenerico = this[KioskDeviceTable.idClienteGenerico],
            tokenHash = this[KioskDeviceTable.tokenHash]?.trim(),
            codigoEmparejamientoHash = this[KioskDeviceTable.codigoEmparejamientoHash]?.trim(),
            codigoExpiraEn = this[KioskDeviceTable.codigoExpiraEn],
            activo = this[KioskDeviceTable.activo],
            ultimoContacto = this[KioskDeviceTable.ultimoContacto],
            creadoEn = this[KioskDeviceTable.creadoEn],
        )
}
