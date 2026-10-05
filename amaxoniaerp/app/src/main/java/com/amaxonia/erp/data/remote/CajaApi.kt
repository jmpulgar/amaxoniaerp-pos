package com.amaxonia.erp.data.remote

import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuenciaCodigoResponse
import com.amaxonia.erp.domain.model.CajaSecuenciaGetResponse
import com.amaxonia.erp.domain.model.CajaStatusResponse
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaResponse
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.model.SaveCajaResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

suspend fun ApiService.getCajas(
    token: String,
    companyDb: String,
    all: Boolean = true,
): List<Caja> =
    client.get("api/cajas") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        if (all) {
            parameter("all", "true")
        }
    }.body()

suspend fun ApiService.createCaja(
    token: String,
    companyDb: String,
    request: SaveCajaRequest,
): Caja {
    val response: SaveCajaResponse =
        client.post("api/cajas") {
            header("Authorization", "Bearer $token")
            header("Company-DB", companyDb)
            setBody(request)
        }.body()
    return response.data
}

suspend fun ApiService.updateCaja(
    token: String,
    companyDb: String,
    id: String,
    request: SaveCajaRequest,
): Caja {
    val response: SaveCajaResponse =
        client.put("api/cajas/$id") {
            header("Authorization", "Bearer $token")
            header("Company-DB", companyDb)
            setBody(request)
        }.body()
    return response.data
}

suspend fun ApiService.checkCajaStatus(
    token: String,
    companyDb: String,
    cajaId: String,
): CajaStatusResponse =
    client.get("api/cajas/$cajaId/status") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
    }.body()

suspend fun ApiService.getNextSecuenciaCodigo(
    token: String,
    companyDb: String,
    idCaja: String,
): CajaSecuenciaCodigoResponse =
    client.get("api/cajas/secuencia/codigo") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        parameter("id", idCaja)
    }.body()

suspend fun ApiService.getCajaSecuencia(
    token: String,
    companyDb: String,
    idSecuencia: String,
    verifyFacturasTemporales: Boolean = true,
): CajaSecuenciaGetResponse =
    client.get("api/cajas/secuencia") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        parameter("id", idSecuencia)
        parameter("by.verificar_facturas_temporales", if (verifyFacturasTemporales) "1" else "0")
    }.body()

suspend fun ApiService.openCaja(
    token: String,
    companyDb: String,
    request: AperturaRequest,
): CajaStatusResponse =
    client.post("api/cajas/open") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        setBody(request)
    }.body()

suspend fun ApiService.closeCaja(
    token: String,
    companyDb: String,
    request: CierreCajaRequest,
): CierreCajaResponse =
    client.post("api/cajas/close") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        setBody(request)
    }.body()
