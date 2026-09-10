package com.amaxoniaerp.features.sync.data

import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sync.domain.CajaPaymentMethodSyncDto
import com.amaxoniaerp.features.sync.domain.ClientBranchSyncDto
import com.amaxoniaerp.features.sync.domain.ClientSyncDto
import com.amaxoniaerp.features.sync.domain.PaymentMethodSyncDto
import org.jetbrains.exposed.sql.ResultRow

/**
 * Mappers ResultRow → DTO slim para las entidades respaldadas por tablas
 * Exposed. Las entidades leídas por raw SQL (tipo_cliente, promociones) se
 * mapean en sus repositorios al construir la página del endpoint.
 */

internal fun mapClientSync(row: ResultRow): ClientSyncDto =
    ClientSyncDto(
        id = row[ClientsTable.idCliente],
        code = row[ClientsTable.codCliente],
        rif = row[ClientsTable.rif],
        dv = row[ClientsTable.dv],
        nombre = row[ClientsTable.nombre],
        apellido = row[ClientsTable.apellido],
        direccion = row[ClientsTable.direccion],
        direccionNivel1 = row[ClientsTable.direccionNivel1],
        direccionNivel2 = row[ClientsTable.direccionNivel2],
        direccionNivel3 = row[ClientsTable.direccionNivel3],
        telefonos = row[ClientsTable.telefonos],
        email = row[ClientsTable.email],
        activo = row[ClientsTable.estado] == STATE_ACTIVE,
        codTipoCliente = row[ClientsTable.codTipoCliente],
        codTipoPrecio = row[ClientsTable.codTipoPrecio],
        tipoContribuyente = row[ClientsTable.tipoContribuyente],
        pais = row[ClientsTable.pais],
        permiteCredito = row[ClientsTable.permiteCredito],
        limite = row[ClientsTable.limite],
        dias = row[ClientsTable.dias],
        idSucursal = row[ClientsTable.idSucursal]?.takeIf { it > 0 },
    )

internal fun mapClientBranchSync(row: ResultRow): ClientBranchSyncDto =
    ClientBranchSyncDto(
        sucursalId = row[ClientSucursalTable.sucursalId],
        clienteCodigo = row[ClientSucursalTable.clienteCodigo],
        nombreSucursal = row[ClientSucursalTable.nombreSucursal],
        nombreContacto = row[ClientSucursalTable.nombreContacto],
        telefonoContacto = row[ClientSucursalTable.telefonoContacto],
        correoContacto = row[ClientSucursalTable.correoContacto],
        direccion = row[ClientSucursalTable.direccion],
        observaciones = row[ClientSucursalTable.observaciones],
    )

internal fun mapPaymentMethodSync(row: ResultRow): PaymentMethodSyncDto =
    PaymentMethodSyncDto(
        idFormaPago = row[CajaFormaPagoTable.idFormaPago],
        siglas = row[CajaFormaPagoTable.siglas],
        codigo = row[CajaFormaPagoTable.codigo],
        descripcion = row[CajaFormaPagoTable.descripcion],
        idCajaTpConcepto = row[CajaFormaPagoTable.idCajaTpConcepto],
        cuentaContable = row[CajaFormaPagoTable.cuentaContable],
        idCajaTpRegistro = row[CajaFormaPagoTable.idCajaTpRegistro],
        formaPagoFact = row[CajaFormaPagoTable.formaPagoFact],
        activo = row[CajaFormaPagoTable.activo],
        pos = row[CajaFormaPagoTable.pos],
        imagen = row[CajaFormaPagoTable.imagen],
        grupo = row[CajaFormaPagoTable.grupo],
        orden = row[CajaFormaPagoTable.orden],
        idBancoCuenta = row[CajaFormaPagoTable.idBancoCuenta],
        idBancoOperacion = row[CajaFormaPagoTable.idBancoOperacion],
        tipoMoneda = row[CajaFormaPagoTable.tipoMoneda],
    )

internal fun mapCajaPaymentMethodSync(row: ResultRow): CajaPaymentMethodSyncDto =
    CajaPaymentMethodSyncDto(
        idCaja = row[CajaFormaTable.idCaja],
        idFormaPago = row[CajaFormaTable.idFormaPago],
        activo = row[CajaFormaTable.activo],
    )

private const val STATE_ACTIVE = "1"
