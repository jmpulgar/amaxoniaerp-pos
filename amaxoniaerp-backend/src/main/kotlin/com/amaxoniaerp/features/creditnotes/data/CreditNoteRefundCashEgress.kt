package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableVE
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import org.jetbrains.exposed.sql.insert
import java.math.BigDecimal
import java.util.UUID

internal fun registerRefundCashEgress(cmd: RefundEgress) {
    val cajaId = UUID.randomUUID().toString()
    val transactionId = UUID.randomUUID().toString()
    val detalleId = UUID.randomUUID().toString()
    val concepto = "Reintegro por nota de crédito ${cmd.creditNoteCode} / factura ${cmd.invoice.codFactura}"

    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(cmd.countryCode)
    cajaNuevaTable.insert {
        it[this.cajaId] = cajaId
        it[idTransaccion] = transactionId
        it[fecha] = cmd.date
        it[ingEg] = CajaIngresoEgreso.E
        it[monto] = cmd.total
        it[comprobante] = "NC"
        it[comprobanteNumero] = cmd.creditNoteCode
        it[idFactura] = cmd.invoice.idFactura
        it[idCliente] = cmd.invoice.idCliente
        it[status] = CajaStatus.Pagada
        it[sucursalId] = cmd.cajaContext.idSucursal
        it[usuarioCreacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[fechaCreacion] = cmd.now
        it[idCompra] = ""
        it[idProveedor] = ""
        it[cajaNuevaTable.concepto] = concepto
        it[idOrdenPago] = ""
        it[serieSucursal] = cmd.cajaContext.serieSucursal
        it[idCajaSecuencia] = cmd.invoice.idCajaSecuencia
        it[idPedido] = ""
        it[idAbono] = ""
        it[idNotaCredito] = cmd.creditNoteId
    }

    val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(cmd.countryCode)
    cajaNuevaDetalleTable.insert {
        it[cajaDetalleId] = detalleId
        it[this.cajaId] = cajaId
        it[this.idFormaPago] = cmd.idFormaPago
        it[idTransaccion] = transactionId
        it[cajaReciboId] = ""
        it[monto] = cmd.total
        it[montoOriginal] = cmd.total
        it[cajaNuevaDetalleTable.concepto] = concepto
        it[usuarioCreacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[fechaCreacion] = cmd.now
        it[retencionTipo] = ""
        it[retencionPorcentaje] = ""
        it[numero] = ""
        it[observacion] = ""
        it[retencionBaseCalculo] = ""
        it[serieSucursal] = cmd.cajaContext.serieSucursal
        it[cajaSecuencia] = cmd.cajaContext.cajaSecuencia
        it[numeroControl] = ""
        it[numeroComprobante] = cmd.creditNoteCode
        it[retencionMonto] = ""
        it[retencionDetalleJson] = ""
        if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
            it[cajaNuevaDetalleTable.montoRecibido] = cmd.total
            it[cajaNuevaDetalleTable.montoMonedaPrincipal] = cmd.total
        }
    }

    SalesCajaNuevaDetalleFormaPagoTable.insert {
        it[cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
        it[this.cajaId] = cajaId
        it[this.cajaDetalleId] = detalleId
        it[tipoMovimiento] = "OT"
        it[this.idFormaPago] = cmd.idFormaPago
        it[comprobante] = "NC"
        it[SalesCajaNuevaDetalleFormaPagoTable.concepto] = concepto
        it[monto] = cmd.total
        it[montoOriginal] = cmd.total
        it[tdcProveedor] = ""
        it[tdcNumero] = ""
        it[tdcTitular] = ""
        it[tdcVencimiento] = ""
        it[tdcCvv] = ""
        it[codigoVerificacion] = ""
        it[idAbonoDetalle] = ""
        it[efectivoCambio] = BigDecimal.ZERO.setScale(2)
    }
}
