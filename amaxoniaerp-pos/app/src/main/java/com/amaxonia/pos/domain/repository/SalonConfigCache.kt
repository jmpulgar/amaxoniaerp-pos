package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.Lienzo
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.MesasResult

/**
 * Última configuración de salón descargada, para poder abrir la pantalla sin conexión.
 *
 * Implementado por `LocalStore` sobre DataStore, igual que [CountrySelectionStore] y
 * [PaymentSessionReader]. Las implementaciones **deben** descartar el snapshot cuando cambia la
 * empresa o la caja: es lo que impide que, tras cambiar de sesión o de caja, se vean áreas y
 * mesas de otra sucursal.
 *
 * No se ha añadido una entidad Room ni una migración: el snapshot se serializa como JSON en
 * DataStore, exactamente como ya pasaba con áreas. Añadir lienzo/imagen del plano es una
 * evolución del snapshot serialized, no del esquema SQL.
 */
interface SalonConfigCache {
    suspend fun readCachedAreas(cajaId: String): AreasResult?

    suspend fun cacheAreas(
        cajaId: String,
        sucursalId: Int,
        areas: List<Area>,
    )

    suspend fun readCachedMesas(
        cajaId: String,
        areaId: Int,
    ): MesasResult?

    suspend fun cacheMesas(
        cajaId: String,
        areaId: Int,
        lienzo: Lienzo,
        imagenUrl: String?,
        mesas: List<Mesa>,
    )
}

/**
 * Token de la empresa seleccionada.
 *
 * Nota de deuda técnica: `CajaRepositoryImpl` y `FormaPagoRepositoryImpl` siguen construyendo su
 * cabecera `Authorization` con un `getAuthHeader()` privado propio. No se unifican aquí para no
 * tocar el flujo de cobro en esta fase.
 */
fun interface CompanyTokenReader {
    suspend fun companyToken(): String?
}
