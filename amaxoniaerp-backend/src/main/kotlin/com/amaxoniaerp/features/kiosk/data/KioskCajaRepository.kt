package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.kiosk.domain.KioskCaja
import org.jetbrains.exposed.sql.Database

/**
 * Caja del ERP sobre la que vende el kiosco, leída con el mismo catálogo de cajas del POS
 * (`GET /api/cajas`): almacén de la caja → por defecto de ventas de la sucursal →
 * `parametros_generales.cod_almacen`; vendedor del usuario → de la caja → de la sucursal.
 */
class KioskCajaRepository(
    private val cajaRepository: CajaRepository = CajaRepository(),
) {
    /** La caja si existe y está activa (`caja.activo = 1`); null en otro caso. */
    suspend fun findActiveCaja(
        database: Database,
        countryCode: String,
        idCaja: String,
        userId: Int?,
    ): KioskCaja? {
        val caja =
            cajaRepository
                .getCajaById(database, countryCode, idCaja, userId)
                ?.takeIf { it.estatus == CAJA_ACTIVA }
                ?: return null
        return KioskCaja(
            idCaja = caja.idCaja,
            name =
                caja.descripcion?.trim()?.takeIf { it.isNotEmpty() }
                    ?: caja.codCaja?.trim()?.takeIf { it.isNotEmpty() }
                    ?: caja.idCaja,
            idSucursal = caja.idSucursal ?: 0,
            idAlmacen = caja.codAlmacen ?: caja.defaultWarehouseId ?: 0,
            // Sin vendedor asignado el POS vende con 0.
            codVendedor = caja.defaultSellerId ?: 0,
        )
    }

    private companion object {
        const val CAJA_ACTIVA = 1
    }
}
