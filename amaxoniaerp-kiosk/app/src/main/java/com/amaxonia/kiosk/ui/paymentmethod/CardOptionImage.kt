package com.amaxonia.kiosk.ui.paymentmethod

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/**
 * Decodes the ERP `caja_forma_pago.imagen` data URI (`data:image/png;base64,...`). Coil 2 does not
 * load data URIs reliably, and these logos are a few KB, so they are decoded directly.
 */
internal fun decodeDataUriImage(dataUri: String?): ImageBitmap? {
    val payload = dataUri?.substringAfter("base64,", missingDelimiterValue = "")?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** Logo of a card payment method, or a card icon when the ERP has no (valid) image for it. */
@Composable
internal fun CardOptionLogo(
    dataUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(dataUri) { decodeDataUriImage(dataUri) }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
    } else {
        Icon(
            imageVector = Icons.Rounded.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(size),
        )
    }
}
