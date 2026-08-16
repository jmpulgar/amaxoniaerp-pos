from pathlib import Path

PATH = Path("amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/creditnotes/data/CreditNoteRepository.kt")


def replace_exact(old: str, new: str, expected: int = 1) -> None:
    text = PATH.read_text()
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches, found {count}: {old!r}")
    PATH.write_text(text.replace(old, new))


replace_exact(
    "import java.math.RoundingMode\n",
    "import java.math.RoundingMode.DOWN\nimport java.math.RoundingMode.HALF_UP\n",
)
replace_exact("RoundingMode.HALF_UP", "HALF_UP", 32)
replace_exact("RoundingMode.DOWN", "DOWN", 1)

replace_exact(
    "private const val INVENTORY_QUANTITY_SCALE = 4\n",
    '''private const val INVENTORY_QUANTITY_SCALE = 4
private const val ERR_FULL_RETURN_REQUIRED = "Para anular la factura debes devolver la totalidad de las líneas restantes"
private const val ERR_REFUND_PAYMENT_REQUIRED = "Forma de pago de reintegro requerida"
private const val ERR_FISCAL_NUMBER_FORMAT = "El número fiscal de la nota de crédito debe ser numérico de hasta 10 dígitos"
''',
)
replace_exact(
    'throw CreditNoteValidationException("Para anular la factura debes devolver la totalidad de las líneas restantes")',
    "throw CreditNoteValidationException(ERR_FULL_RETURN_REQUIRED)",
    2,
)
replace_exact(
    'request.idFormaPagoReintegro ?: throw CreditNoteValidationException("Forma de pago de reintegro requerida")',
    "request.idFormaPagoReintegro ?: throw CreditNoteValidationException(ERR_REFUND_PAYMENT_REQUIRED)",
)
replace_exact(
    'throw CreditNoteValidationException("El número fiscal de la nota de crédito debe ser numérico de hasta 10 dígitos")',
    "throw CreditNoteValidationException(ERR_FISCAL_NUMBER_FORMAT)",
)

replace_exact(
    "    private fun loadInvoiceLines(invoiceId: String): List<SourceInvoiceLine> = loadInvoiceLines(countryCode = null, invoiceId = invoiceId)",
    '''    private fun loadInvoiceLines(invoiceId: String): List<SourceInvoiceLine> =
        loadInvoiceLines(countryCode = null, invoiceId = invoiceId)''',
)
replace_exact(
    "                    ) { acc, row -> acc + row[CreditNoteDetailTable.itemCantidad].setScale(QUANTITY_SCALE, HALF_UP) }",
    '''                    ) { acc, row ->
                        acc + row[CreditNoteDetailTable.itemCantidad].setScale(QUANTITY_SCALE, HALF_UP)
                    }''',
)
replace_exact(
    "            val quantityOriginal = row[CreditNoteFacturaDetalleTable.itemCantidadTotal].setScale(QUANTITY_SCALE, HALF_UP)",
    '''            val quantityOriginal =
                row[CreditNoteFacturaDetalleTable.itemCantidadTotal].setScale(QUANTITY_SCALE, HALF_UP)''',
)
replace_exact(
    "            val unitTotalSinIva = divideSafe(row[CreditNoteFacturaDetalleTable.itemTotalSinIva], quantityOriginal, UNIT_CALCULATION_SCALE)",
    '''            val unitTotalSinIva =
                divideSafe(
                    row[CreditNoteFacturaDetalleTable.itemTotalSinIva],
                    quantityOriginal,
                    UNIT_CALCULATION_SCALE,
                )''',
)
replace_exact(
    "            val unitTotalConIva = divideSafe(row[CreditNoteFacturaDetalleTable.itemTotalConIva], quantityOriginal, UNIT_CALCULATION_SCALE)",
    '''            val unitTotalConIva =
                divideSafe(
                    row[CreditNoteFacturaDetalleTable.itemTotalConIva],
                    quantityOriginal,
                    UNIT_CALCULATION_SCALE,
                )''',
)
replace_exact(
    "            val unitDiscount = divideSafe(row[CreditNoteFacturaDetalleTable.itemMontoDescuento], quantityOriginal, UNIT_CALCULATION_SCALE)",
    '''            val unitDiscount =
                divideSafe(
                    row[CreditNoteFacturaDetalleTable.itemMontoDescuento],
                    quantityOriginal,
                    UNIT_CALCULATION_SCALE,
                )''',
)
replace_exact(
    '                        ?: throw CreditNoteValidationException("La línea $idDetalleFactura no pertenece a la factura origen")',
    '''                        ?: throw CreditNoteValidationException(
                            "La línea $idDetalleFactura no pertenece a la factura origen",
                        )''',
)
replace_exact(
    '                        "La cantidad a devolver para ${sourceLine.descripcion} excede lo disponible (${sourceLine.availableQuantity.toDouble()})",',
    '''                        "La cantidad a devolver para ${sourceLine.descripcion} excede lo disponible " +
                            "(${sourceLine.availableQuantity.toDouble()})",''',
)
replace_exact(
    "                val unitTotalSinIva = divideSafe(sourceLine.totalSinIvaOriginal, sourceLine.quantityOriginal, UNIT_CALCULATION_SCALE)",
    '''                val unitTotalSinIva =
                    divideSafe(
                        sourceLine.totalSinIvaOriginal,
                        sourceLine.quantityOriginal,
                        UNIT_CALCULATION_SCALE,
                    )''',
)
replace_exact(
    "                val unitTotalConIva = divideSafe(sourceLine.totalConIvaOriginal, sourceLine.quantityOriginal, UNIT_CALCULATION_SCALE)",
    '''                val unitTotalConIva =
                    divideSafe(
                        sourceLine.totalConIvaOriginal,
                        sourceLine.quantityOriginal,
                        UNIT_CALCULATION_SCALE,
                    )''',
)
replace_exact(
    "                .join(CreditNoteCajaTable, JoinType.INNER, CreditNoteCajaSecuenciaTable.idCaja, CreditNoteCajaTable.idCaja)",
    '''                .join(
                    CreditNoteCajaTable,
                    JoinType.INNER,
                    CreditNoteCajaSecuenciaTable.idCaja,
                    CreditNoteCajaTable.idCaja,
                )''',
)
replace_exact(
    '    ): String = "${codigoCaja.takeIf { it.isNotBlank() } ?: "NC"}-${nextCorrelative.toString().padStart(CORRELATIVE_CODE_LENGTH, \'0\')}"',
    '''    ): String =
        "${codigoCaja.takeIf { it.isNotBlank() } ?: "NC"}-" +
            nextCorrelative.toString().padStart(CORRELATIVE_CODE_LENGTH, '0')''',
)
replace_exact(
    "                val currentQuantity = stockRow[ItemExistenciaAlmacenTable.cantidad] ?: BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)",
    '''                val currentQuantity =
                    stockRow[ItemExistenciaAlmacenTable.cantidad]
                        ?: BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)''',
)
replace_exact(
    "                    it[cantidad] = currentQuantity.add(quantity).setScale(INVENTORY_QUANTITY_SCALE, HALF_UP)",
    '''                    it[cantidad] =
                        currentQuantity.add(quantity).setScale(INVENTORY_QUANTITY_SCALE, HALF_UP)''',
)
replace_exact(
    '            printerSerial = if (headerTable is CreditNoteHeaderTableVE) row[headerTable.impresoraSerial].orEmpty() else "",',
    '''            printerSerial =
                if (headerTable is CreditNoteHeaderTableVE) row[headerTable.impresoraSerial].orEmpty() else "",''',
    2,
)
replace_exact(
    "                if (hasFiscalCode || hasDocumentNumber) CreditNoteFiscalStatus.CONFIRMADA else CreditNoteFiscalStatus.PENDIENTE",
    '''                if (hasFiscalCode || hasDocumentNumber) {
                    CreditNoteFiscalStatus.CONFIRMADA
                } else {
                    CreditNoteFiscalStatus.PENDIENTE
                }''',
)
replace_exact(
    "private fun BigDecimal.coerceAtLeastZero(scale: Int): BigDecimal =\n    if (this < BigDecimal.ZERO) BigDecimal.ZERO.setScale(scale, HALF_UP) else setScale(scale, HALF_UP)",
    '''private fun BigDecimal.coerceAtLeastZero(scale: Int): BigDecimal =
    if (this < BigDecimal.ZERO) {
        BigDecimal.ZERO.setScale(scale, HALF_UP)
    } else {
        setScale(scale, HALF_UP)
    }''',
)
replace_exact(
    "private fun BigDecimal.isEffectivelyZero(): Boolean =\n    setScale(QUANTITY_SCALE, HALF_UP).compareTo(BigDecimal.ZERO.setScale(QUANTITY_SCALE, HALF_UP)) == 0",
    '''private fun BigDecimal.isEffectivelyZero(): Boolean =
    setScale(QUANTITY_SCALE, HALF_UP)
        .compareTo(BigDecimal.ZERO.setScale(QUANTITY_SCALE, HALF_UP)) == 0''',
)

print("wrapped CreditNoteRepository long lines without changing operations, values, or messages")
