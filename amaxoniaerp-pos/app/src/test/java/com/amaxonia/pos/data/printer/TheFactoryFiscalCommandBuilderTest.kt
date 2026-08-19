package com.amaxonia.pos.data.printer

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionFiscalItem
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalLineDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Characterization tests (TASK-074) del constructor de comandos fiscales del
 * protocolo The Factory HKA. Congelan el comportamiento vigente extraído de
 * `TheFactoryPrinterImpl`: mapeo de forma de pago, prefijos de IVA, formato
 * price/qty (2 y 3 decimales implícitos), estructura de comandos de venta y
 * nota de crédito, y parseo de la respuesta S1.
 */
class TheFactoryFiscalCommandBuilderTest {
    private val builder = TheFactoryFiscalCommandBuilder()

    @Test
    fun `mapPaymentCommand mapea formas de pago conocidas a comandos de cierre`() {
        assertEquals("101", builder.mapPaymentCommand("Efectivo"))
        assertEquals("101", builder.mapPaymentCommand("Contado"))
        assertEquals("101", builder.mapPaymentCommand("Divisa"))
        assertEquals("102", builder.mapPaymentCommand("Punto de Venta"))
        assertEquals("102", builder.mapPaymentCommand("Debito"))
        assertEquals("102", builder.mapPaymentCommand("Debit"))
        assertEquals("103", builder.mapPaymentCommand("Credito"))
        assertEquals("103", builder.mapPaymentCommand("Tarjeta"))
        assertEquals("104", builder.mapPaymentCommand("Transferencia"))
        assertEquals("104", builder.mapPaymentCommand("Zelle"))
        assertEquals("104", builder.mapPaymentCommand("Pago Movil"))
        assertEquals("104", builder.mapPaymentCommand("Yappy"))
        assertEquals("104", builder.mapPaymentCommand("Nequi"))
    }

    @Test
    fun `mapPaymentCommand vacio es efectivo y desconocido es 199`() {
        assertEquals("101", builder.mapPaymentCommand(""))
        assertEquals("101", builder.mapPaymentCommand("   "))
        assertEquals("199", builder.mapPaymentCommand("Cripto"))
    }

    @Test
    fun `buildFiscalCommands estructura identificacion items subtotal y cierre`() {
        val transaction =
            transaction(
                invoiceNumber = "018-00015",
                cliente = "V-123456789" to "Juan Perez",
                formaPago = "Efectivo",
                fiscalItems =
                    listOf(
                        TransactionFiscalItem(
                            description = "Producto Uno",
                            quantity = 2.0,
                            unitPriceWithoutTax = 10.0,
                            iva = 16.0,
                        ),
                    ),
            )

        val commands = builder.buildFiscalCommands(transaction, brandReceiptName = "AMAXONIA")

        assertEquals("iR*V-123456789", commands[0])
        assertEquals("iS*JUAN PEREZ", commands[1])
        assertEquals("@AMAXONIA", commands[2])
        assertEquals("@VENTA 018-00015", commands[3])
        // '!' IVA general, price 10.00*100=1000 padded 10, qty 2.000*1000=2000 padded 8
        assertEquals("!000000100000002000PRODUCTO UNO", commands[4])
        assertEquals("3", commands[5])
        assertEquals("101", commands[6])
        assertEquals(7, commands.size)
    }

    @Test
    fun `buildFiscalCommands sin items usa monto global con cantidad 1`() {
        val transaction = transaction(invoiceNumber = "F-1", formaPago = "Credito", fiscalItems = emptyList())

        val commands = builder.buildFiscalCommands(transaction, brandReceiptName = "BRAND")

        // price 100.00*100 = 10000, qty 1*1000 = 1000; sin cliente el item cae en el índice 2
        assertEquals(" 000001000000001000VENTA F-1", commands[2])
        assertEquals("103", commands.last())
    }

    @Test
    fun `prefijo de IVA distingue exento reducido adicional y general`() {
        val commands =
            listOf(0.0, 8.0, 31.0, 16.0).map { iva ->
                builder
                    .buildFiscalCommands(
                        transaction(
                            invoiceNumber = "F",
                            fiscalItems =
                                listOf(
                                    TransactionFiscalItem(
                                        description = "X",
                                        quantity = 1.0,
                                        unitPriceWithoutTax = 1.0,
                                        iva = iva,
                                    ),
                                ),
                        ),
                        brandReceiptName = "B",
                    )[2]
            }
        assertEquals(' ', commands[0][0]) // exento
        assertEquals('"', commands[1][0]) // reducido 8%
        assertEquals('#', commands[2][0]) // adicional 31%
        assertEquals('!', commands[3][0]) // general
    }

    @Test
    fun `el fiscalCode del metodo de pago dominante gana sobre formaPago`() {
        val transaction =
            transaction(
                formaPago = "Efectivo",
                paymentMethods =
                    listOf(
                        TransactionPaymentMethod(amount = 5.0, fiscalCode = "104"),
                        TransactionPaymentMethod(amount = 50.0, fiscalCode = "103"),
                    ),
            )

        val commands = builder.buildFiscalCommands(transaction, brandReceiptName = "B")

        assertEquals("103", commands.last())
    }

    @Test
    fun `buildCreditNoteCommands estructura referencia items y cierre 199`() {
        val document =
            creditNote(
                originalFiscalNumber = "018-00015",
                originalInvoiceDate = "2026-01-10",
                customer = "V-999" to "Maria Lopez",
                lines =
                    listOf(
                        CreditNoteFiscalLineDto(
                            description = "Devolver",
                            quantity = 1.0,
                            unitPriceWithoutTax = 5.0,
                            totalWithTax = 5.8,
                            taxRate = 16.0,
                        ),
                    ),
            )

        val commands = builder.buildCreditNoteCommands(document, printerSerial = "TFHKA12345")

        assertEquals("iF*01800015", commands[0])
        assertEquals("iD*10/01/2026", commands[1])
        assertEquals("iI*TFHKA12345", commands[2])
        assertEquals("iR*V-999", commands[3])
        assertEquals("iS*MARIA LOPEZ", commands[4])
        assertEquals("ANC NC-1", commands[5])
        // 'd' + taxCode 1 (tasa estándar 16%) + price 5.00*100=500 + qty 1000
        assertEquals("d1000000050000001000DEVOLVER", commands[6])
        assertEquals("3", commands[7])
        assertEquals("199", commands[8])
    }

    @Test
    fun `credit note sin numero fiscal original falla`() {
        val document = creditNote(originalFiscalNumber = "  ")

        val error =
            kotlin
                .runCatching { builder.buildCreditNoteCommands(document, printerSerial = "S") }
                .exceptionOrNull()

        assertEquals(
            "La factura original no tiene número de documento fiscal en el sistema. " +
                "El comando iF* de la nota de crédito debe usar el número fiscal de la factura impresa " +
                "(no el código interno tipo 018-00015). " +
                "Confirma que la venta quedó con número fiscal guardado en el ERP o vuelve a emitir/consultar la factura.",
            error?.message,
        )
    }

    @Test
    fun `credit note sin unitPriceWithoutTax deriva precio desde total con impuesto`() {
        val document =
            creditNote(
                lines =
                    listOf(
                        CreditNoteFiscalLineDto(
                            description = "D",
                            quantity = 2.0,
                            unitPriceWithoutTax = 0.0,
                            totalWithTax = 11.6, // 5.8 por unidad con IVA 16%
                            taxRate = 16.0,
                        ),
                    ),
            )

        val commands = builder.buildCreditNoteCommands(document, printerSerial = "S")

        // 11.6 / 1.16 = 10 total sin IVA; /2 = 5.0 por unidad → 500; sin datos de
        // cliente el item cae tras iF/iD/iI y el comentario A
        assertEquals("d1000000050000002000D", commands[4])
        assertEquals("ANC NC-1", commands[3])
    }

    @Test
    fun `parsePrinterState lee formato extendido y corto de S1`() {
        val extended =
            (0 until 16).joinToString("\n") {
                if (it == 2) {
                    "42"
                } else if (it == 6) {
                    "7"
                } else {
                    "f$it"
                }
            }
        val extendedState = builder.parsePrinterState(extended)
        assertEquals("f13", extendedState.registeredMachineNumber)
        assertEquals(42, extendedState.lastInvoiceNumber)
        assertEquals(7, extendedState.lastCreditNoteNumber)

        val short =
            (0 until 10).joinToString("\n") {
                if (it == 2) {
                    "10"
                } else if (it == 9) {
                    "SN-1"
                } else {
                    "x"
                }
            }
        val shortState = builder.parsePrinterState(short)
        assertEquals("SN-1", shortState.registeredMachineNumber)
        assertEquals(10, shortState.lastInvoiceNumber)
        assertEquals(0, shortState.lastCreditNoteNumber)
    }

    @Test
    fun `padFiscalNumber rellena a 8 digitos y devuelve null en cero`() {
        assertEquals("00000042", builder.padFiscalNumber(42))
        assertEquals("12345678", builder.padFiscalNumber(12345678))
        assertNull(builder.padFiscalNumber(0))
    }

    private fun transaction(
        invoiceNumber: String = "F-1",
        formaPago: String = "",
        cliente: Pair<String, String> = "" to "", // identificación to nombre
        paymentMethods: List<TransactionPaymentMethod> = emptyList(),
        fiscalItems: List<TransactionFiscalItem> = emptyList(),
    ): Transaction =
        Transaction(
            id = "t-1",
            invoiceNumber = invoiceNumber,
            time = "10:00",
            amount = 100.0,
            dateHeader = "HOY",
            clienteIdentificacion = cliente.first,
            clienteNombre = cliente.second,
            formaPago = formaPago,
            paymentMethods = paymentMethods,
            fiscalItems = fiscalItems,
        )

    private fun creditNote(
        originalFiscalNumber: String = "018-00015",
        originalInvoiceDate: String = "2026-01-10",
        customer: Pair<String, String> = "" to "", // identificación to nombre
        lines: List<CreditNoteFiscalLineDto> = emptyList(),
    ): CreditNoteFiscalDocumentDto =
        CreditNoteFiscalDocumentDto(
            creditNoteId = "nc-1",
            creditNoteCode = "NC-1",
            date = "2026-01-11",
            customerName = customer.second,
            customerIdentifier = customer.first,
            customerAddress = "",
            customerPhone = "",
            originalInvoiceCode = "F-1",
            originalFiscalNumber = originalFiscalNumber,
            originalInvoiceDate = originalInvoiceDate,
            printerSerial = "SER",
            comment = "",
            lines = lines,
        )
}
