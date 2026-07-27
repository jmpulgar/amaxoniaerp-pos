package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.MesasResult

/**
 * Configuración de salón (áreas y mesas) de la sucursal a la que pertenece la caja activa.
 *
 * Solo lectura. La sucursal no es un parámetro: se deriva en el backend a partir de [cajaId],
 * de modo que manipular ids en el cliente no da acceso a otra sucursal.
 */
interface AreaRepository {
    suspend fun getAreas(cajaId: String): Result<AreasResult>

    suspend fun getMesas(
        cajaId: String,
        areaId: Int,
    ): Result<MesasResult>
}
