package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.mesas.AreasResponse
import com.amaxonia.pos.domain.model.mesas.MesasResponse

/**
 * Consulta de áreas y mesas. Solo lectura: no hay ninguna operación de escritura en esta fase.
 *
 * [cajaId] es el único ámbito que viaja al backend; la sucursal se deriva allí.
 */
interface AreasApi {
    suspend fun getAreas(
        cajaId: String,
        authHeader: String,
    ): Result<AreasResponse>

    suspend fun getMesas(
        cajaId: String,
        areaId: Int,
        authHeader: String,
    ): Result<MesasResponse>
}
