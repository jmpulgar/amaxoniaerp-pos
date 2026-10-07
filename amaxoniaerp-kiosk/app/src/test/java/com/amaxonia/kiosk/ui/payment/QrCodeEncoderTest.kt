package com.amaxonia.kiosk.ui.payment

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class QrCodeEncoderTest {
    private val content = "yappy-qr-hash-0123456789abcdef"

    @Test
    fun `encodes a square matrix with a white quiet zone`() {
        val matrix = QrCodeEncoder.encode(content)
        assertNotNull(matrix)
        matrix!!
        for (i in 0 until matrix.size) {
            for (q in 0 until QR_QUIET_ZONE_MODULES) {
                assertFalse(matrix[i, q])
                assertFalse(matrix[q, i])
                assertFalse(matrix[i, matrix.size - 1 - q])
                assertFalse(matrix[matrix.size - 1 - q, i])
            }
        }
    }

    @Test
    fun `encoded QR decodes back to the Yappy hash`() {
        val matrix = QrCodeEncoder.encode(content)!!
        val scale = 8
        val side = matrix.size * scale
        val pixels = ByteArray(side * side) { idx -> if (matrix[(idx % side) / scale, (idx / side) / scale]) 0 else -1 }
        val source =
            object : LuminanceSource(side, side) {
                override fun getRow(
                    y: Int,
                    row: ByteArray?,
                ): ByteArray = pixels.copyOfRange(y * side, (y + 1) * side)

                override fun getMatrix(): ByteArray = pixels
            }

        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to true))

        assertEquals(content, result.text)
    }
}
