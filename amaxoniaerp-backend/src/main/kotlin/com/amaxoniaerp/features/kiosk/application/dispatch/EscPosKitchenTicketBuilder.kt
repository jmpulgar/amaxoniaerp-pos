package com.amaxoniaerp.features.kiosk.application.dispatch

import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EscPosKitchenTicketBuilder {
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val FEED_AND_CUT = byteArrayOf(0x1B, 0x64, 0x03, 0x1D, 0x56, 0x41, 0x00)

    fun buildTicket(
        order: KioskOrderRecord,
        itemNames: Map<Int, String> = emptyMap(),
        charset: Charset = Charsets.ISO_8859_1,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = out.write(bytes)

        fun writeText(text: String) = out.write(text.toByteArray(charset))

        fun writeLine(text: String = "") {
            writeText(text)
            out.write('\n'.code)
        }

        // 1. Inicializar impresora
        write(ESC_INIT)

        // 2. Encabezado centrado
        write(ALIGN_CENTER)
        write(BOLD_ON)
        writeLine("================================")
        writeLine("*** COMANDA DE COCINA ***")
        writeLine("================================")
        write(BOLD_OFF)

        // 3. Número de pedido destacado en doble tamaño
        writeLine()
        write(DOUBLE_SIZE)
        write(BOLD_ON)
        writeLine(order.codigoPedido)
        write(NORMAL_SIZE)
        write(BOLD_OFF)
        writeLine()

        // 4. Datos del pedido
        write(ALIGN_LEFT)
        writeLine("MODALIDAD: ${order.modalidad.replace('_', ' ')}")
        if (!order.portamesa.isNullOrBlank()) {
            write(BOLD_ON)
            writeLine("PORTAMESA / MESA: ${order.portamesa}")
            write(BOLD_OFF)
        }
        writeLine("FECHA: ${order.fecha}")
        writeLine("--------------------------------")
        write(BOLD_ON)
        writeLine("CANT  PRODUCTO")
        write(BOLD_OFF)
        writeLine("--------------------------------")

        // 5. Detalle de productos y modificadores
        for (item in order.items) {
            val name = itemNames[item.idItem]?.trim() ?: "Item ${item.idItem}"
            val qty = item.cantidad.toInt()
            write(BOLD_ON)
            writeLine("${qty}x   $name")
            write(BOLD_OFF)

            for (mod in item.modifiers) {
                writeLine("      + ${mod.nombre}")
            }

            if (!item.nota.isNullOrBlank()) {
                write(BOLD_ON)
                writeLine("      * NOTA: ${item.nota}")
                write(BOLD_OFF)
            }
            writeLine()
        }

        // 6. Pie
        write(ALIGN_CENTER)
        writeLine("================================")

        // 7. Avance y corte
        write(FEED_AND_CUT)

        return out.toByteArray()
    }
}
