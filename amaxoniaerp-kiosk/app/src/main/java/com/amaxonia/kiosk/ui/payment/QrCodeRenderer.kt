package com.amaxonia.kiosk.ui.payment

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quiet zone in modules (QR spec minimum is 4): keeps phones able to lock on against any background. */
const val QR_QUIET_ZONE_MODULES = 4
private const val QR_DARK = 0xFF000000.toInt()
private const val QR_LIGHT = 0xFFFFFFFF.toInt()

/** QR as a square module grid (true = dark), quiet zone included. */
class QrMatrix(
    val size: Int,
    private val modules: BooleanArray,
) {
    operator fun get(
        x: Int,
        y: Int,
    ): Boolean = modules[y * size + x]
}

object QrCodeEncoder {
    /**
     * Encodes [content] at one pixel per module with high (H, ~30 %) error correction, so a
     * scratched or glare-covered screen still scans. Returns null if the content cannot be encoded.
     */
    fun encode(content: String): QrMatrix? =
        runCatching {
            val hints =
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES,
                    EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
                )
            val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
            val size = bits.width
            QrMatrix(size, BooleanArray(size * size) { bits.get(it % size, it / size) })
        }.getOrNull()

    /** One pixel per module; draw it scaled up with nearest-neighbour filtering for crisp edges. */
    fun toBitmap(matrix: QrMatrix): Bitmap {
        val pixels = IntArray(matrix.size * matrix.size) { if (matrix[it % matrix.size, it / matrix.size]) QR_DARK else QR_LIGHT }
        return Bitmap.createBitmap(pixels, matrix.size, matrix.size, Bitmap.Config.ARGB_8888)
    }
}

/** Encodes and rasterizes the QR off the main thread; null while working or if encoding failed. */
@Composable
fun rememberQrBitmap(content: String): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, content) {
        value =
            withContext(Dispatchers.Default) {
                QrCodeEncoder.encode(content)?.let { QrCodeEncoder.toBitmap(it).asImageBitmap() }
            }
    }
