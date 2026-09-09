package com.amaxoniaerp.features.sync.domain

/**
 * Implementación pura de XXH64 (spec xxHash, variante 64-bit, seed 0).
 * Sin dependencias externas para que el mismo algoritmo exacto pueda
 * duplicarse verbatim en el cliente Android (POS) sin arrastrar librerías.
 *
 * Todas las operaciones son aritmética modular de 64 bits sobre Long.
 */
internal object XxHash64 {
    // Constantes primas del algoritmo. Los literales hex que superan
    // Long.MAX_VALUE no son válidos con sufijo L en Kotlin: se declaran como
    // ULong y se convierten a Long (mismos 64 bits, interpretación con signo).
    private val P1: Long = 0x9E3779B185EBCA87uL.toLong()
    private val P2: Long = 0xC2B2AE3D27D4EB4FuL.toLong()
    private val P3: Long = 0x165667B19E3779F9uL.toLong()
    private val P4: Long = 0x85EBCA77C2B2AE63uL.toLong()
    private val P5: Long = 0x27D4EB2F165667C5uL.toLong()

    // Tamaños y máscaras de bits del pipeline.
    private const val STRIPE_BYTES = 32
    private const val LANE_BYTES = 8
    private const val SECOND_LANE_OFFSET = LANE_BYTES * 2
    private const val THIRD_LANE_OFFSET = LANE_BYTES * 3
    private const val INT_BYTES = 4
    private const val LONG_BITS = 64
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFFL
    private const val INT_MASK = 0xFF
    private const val INT_MASK_LONG = 0xFFFFFFFFL

    // Rotaciones y corrimientos definidos por la especificación de xxHash.
    private const val ROTATE_CONVERGENCE_1 = 1
    private const val ROTATE_CONVERGENCE_2 = 7
    private const val ROTATE_CONVERGENCE_3 = 12
    private const val ROTATE_CONVERGENCE_4 = 18
    private const val ROTATE_ROUND = 31
    private const val ROTATE_TAIL_LONG = 27
    private const val ROTATE_TAIL_INT = 23
    private const val ROTATE_TAIL_BYTE = 11
    private const val AVALANCHE_SHIFT_1 = 33
    private const val AVALANCHE_SHIFT_2 = 29
    private const val AVALANCHE_SHIFT_3 = 32

    fun hash(
        data: ByteArray,
        seed: Long = 0L,
    ): Long {
        var offset = 0
        val length = data.size
        var h: Long

        if (length >= STRIPE_BYTES) {
            var v1 = seed + P1 + P2
            var v2 = seed + P2
            var v3 = seed
            var v4 = seed - P1
            val limit = length - STRIPE_BYTES
            do {
                v1 = round(v1, readLongLe(data, offset))
                v2 = round(v2, readLongLe(data, offset + LANE_BYTES))
                v3 = round(v3, readLongLe(data, offset + SECOND_LANE_OFFSET))
                v4 = round(v4, readLongLe(data, offset + THIRD_LANE_OFFSET))
                offset += STRIPE_BYTES
            } while (offset <= limit)
            h = rotateLeft(v1, ROTATE_CONVERGENCE_1) +
                rotateLeft(v2, ROTATE_CONVERGENCE_2) +
                rotateLeft(v3, ROTATE_CONVERGENCE_3) +
                rotateLeft(v4, ROTATE_CONVERGENCE_4)
            h = mergeRound(h, v1)
            h = mergeRound(h, v2)
            h = mergeRound(h, v3)
            h = mergeRound(h, v4)
        } else {
            h = seed + P5
        }

        h += length.toLong()

        var remaining = length - offset
        while (remaining >= LANE_BYTES) {
            h = h xor round(0L, readLongLe(data, offset))
            h = rotateLeft(h, ROTATE_TAIL_LONG) * P1 + P4
            offset += LANE_BYTES
            remaining -= LANE_BYTES
        }
        if (remaining >= INT_BYTES) {
            h = h xor (readIntLe(data, offset).toLong() and INT_MASK_LONG) * P1
            h = rotateLeft(h, ROTATE_TAIL_INT) * P2 + P3
            offset += INT_BYTES
            remaining -= INT_BYTES
        }
        while (remaining > 0) {
            h = h xor (data[offset].toLong() and BYTE_MASK) * P5
            h = rotateLeft(h, ROTATE_TAIL_BYTE) * P1
            offset++
            remaining--
        }

        h = h xor (h ushr AVALANCHE_SHIFT_1)
        h *= P2
        h = h xor (h ushr AVALANCHE_SHIFT_2)
        h *= P3
        h = h xor (h ushr AVALANCHE_SHIFT_3)
        return h
    }

    private fun round(
        acc: Long,
        input: Long,
    ): Long = rotateLeft(acc + input * P2, ROTATE_ROUND) * P1

    private fun mergeRound(
        h: Long,
        v: Long,
    ): Long {
        val acc = h xor round(0L, v)
        return acc * P1 + P4
    }

    private fun rotateLeft(
        value: Long,
        bits: Int,
    ): Long = (value shl bits) or (value ushr (LONG_BITS - bits))

    private fun readLongLe(
        data: ByteArray,
        offset: Int,
    ): Long {
        var result = 0L
        for (i in LANE_BYTES - 1 downTo 0) {
            result = (result shl BITS_PER_BYTE) or (data[offset + i].toLong() and BYTE_MASK)
        }
        return result
    }

    private fun readIntLe(
        data: ByteArray,
        offset: Int,
    ): Int {
        var result = 0
        for (i in INT_BYTES - 1 downTo 0) {
            result = (result shl BITS_PER_BYTE) or (data[offset + i].toInt() and INT_MASK)
        }
        return result
    }
}
