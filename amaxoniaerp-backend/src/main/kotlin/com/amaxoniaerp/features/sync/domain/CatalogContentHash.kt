package com.amaxoniaerp.features.sync.domain

import java.io.ByteArrayOutputStream

/**
 * Hash de contenido por fila y agregado conmutativo de reconciliación.
 *
 * Esquema v1 ("[SCHEME]"): por cada fila,
 *
 *   row_hash = XXH64( encode(entity_id, campos_canonicos_del_dto_slim) )
 *
 * y el agregado por entidad es la suma modular de 64 bits de los row_hash.
 * La suma es conmutativa y forma grupo: permite mantenimiento incremental
 * (sumar el aporte de una fila nueva, restar el de la eliminada) y compara
 * igual sin importar el orden de iteración entre servidor y dispositivo.
 *
 * A diferencia de un hash solo sobre entity_id, este detecta filas presentes
 * en ambos lados con contenido distinto (precio cambiado, código de barras
 * cambiado, etc.) aunque el conteo coincida.
 *
 * CONTRATO CRUZADO: este archivo se duplica verbatim (salvo package) en el
 * cliente Android. Cualquier cambio aquí debe reflejarse ahí y bumpar SCHEME.
 * El test de cada lado fija el digest esperado de un fixture idéntico.
 */
object CatalogContentHash {
    const val SCHEME = "xxh64-content-v1"

    /** Nivel de precio canónico: exactamente los valores del DTO slim. */
    data class PriceLevelDigest(
        val label: String,
        val price: Double,
        val utilityPercent: Double,
        val pricePlusUtility: Double,
        val pricePlusTax: Double,
        val unitPrice: Double,
        val unitPricePlusTax: Double,
        val discountPercent: Double,
    )

    /** Campos canónicos de PRODUCT: exactamente lo que el POS almacena en Room. */
    data class ProductDigest(
        val id: String,
        val code: String,
        val description: String,
        val reference: String,
        val barcode1: String,
        val barcode2: String,
        val barcode3: String,
        val department: Int,
        val isExempt: Boolean,
        val taxRate: Double,
        val costActual: Double,
        val unitPackage: String,
        val bulkQuantity: Double,
        val portionUnit: String?,
        val unitOrPackage: String,
        val estatus: String?,
        val prices: List<PriceLevelDigest>,
    )

    fun productRowHash(p: ProductDigest): Long =
        rowHash(p.id) {
            str(p.code)
            str(p.description)
            str(p.reference)
            str(p.barcode1)
            str(p.barcode2)
            str(p.barcode3)
            int(p.department.toLong())
            bool(p.isExempt)
            dbl(p.taxRate)
            dbl(p.costActual)
            str(p.unitPackage)
            dbl(p.bulkQuantity)
            str(p.portionUnit)
            str(p.unitOrPackage)
            str(p.estatus)
            int(p.prices.size.toLong())
            p.prices.forEach { level ->
                str(level.label)
                dbl(level.price)
                dbl(level.utilityPercent)
                dbl(level.pricePlusUtility)
                dbl(level.pricePlusTax)
                dbl(level.unitPrice)
                dbl(level.unitPricePlusTax)
                dbl(level.discountPercent)
            }
        }

    /**
     * Hash de una fila: XXH64 sobre la codificación canónica de
     * (entity_id, campos). La codificación es no ambigua: cada campo lleva
     * tag de tipo y longitud; NULL se distingue de "" y de campo ausente.
     */
    fun rowHash(
        entityId: String,
        fields: FieldWriter.() -> Unit,
    ): Long {
        val writer = FieldWriter()
        writer.str(entityId)
        writer.fields()
        return XxHash64.hash(writer.toByteArray())
    }

    /** Suma modular de 64 bits: conmutativa y asociativa con wrap-around. */
    fun aggregate(rowHashes: Iterable<Long>): Long = rowHashes.fold(0L) { acc, h -> acc + h }

    class FieldWriter internal constructor() {
        private val out = ByteArrayOutputStream(INITIAL_CAPACITY)

        fun str(value: String?) {
            if (value == null) {
                out.write(TAG_NULL)
                return
            }
            out.write(TAG_STR)
            writeBytes(value.toByteArray(Charsets.UTF_8))
        }

        fun dbl(value: Double) {
            out.write(TAG_DBL)
            writeLong(value.toBits())
        }

        fun int(value: Long) {
            out.write(TAG_INT)
            writeLong(value)
        }

        fun bool(value: Boolean) {
            out.write(TAG_BOOL)
            out.write(if (value) 1 else 0)
        }

        internal fun toByteArray(): ByteArray = out.toByteArray()

        private fun writeBytes(bytes: ByteArray) {
            writeInt(bytes.size)
            out.write(bytes)
        }

        private fun writeInt(value: Int) {
            out.write((value ushr INT_SHIFT_3) and BYTE_MASK)
            out.write((value ushr INT_SHIFT_2) and BYTE_MASK)
            out.write((value ushr INT_SHIFT_1) and BYTE_MASK)
            out.write(value and BYTE_MASK)
        }

        private fun writeLong(value: Long) {
            writeInt((value ushr INT_SHIFT_4).toInt())
            writeInt(value.toInt())
        }

        private companion object {
            const val INITIAL_CAPACITY = 256
            const val INT_SHIFT_1 = 8
            const val INT_SHIFT_2 = 16
            const val INT_SHIFT_3 = 24
            const val INT_SHIFT_4 = 32
            const val BYTE_MASK = 0xFF
        }
    }

    private const val TAG_NULL: Int = 0x00
    private const val TAG_STR: Int = 0x01
    private const val TAG_DBL: Int = 0x02
    private const val TAG_INT: Int = 0x03
    private const val TAG_BOOL: Int = 0x04
}
