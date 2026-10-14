package com.amaxonia.erp.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

/**
 * Decodifica una cadena Base64 (con o sin prefijo data:image/...) a un [ImageBitmap] de Compose.
 */
fun parseBase64ImageBitmap(raw: String?): ImageBitmap? {
    if (raw.isNullOrBlank()) return null
    return try {
        val base64Data = if (raw.contains(",")) raw.substringAfter(",") else raw
        val decodedBytes = Base64.decode(base64Data.trim(), Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        bitmap?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Composable universal para imágenes en el POS:
 * 1. Si [source] es Base64 (data:image/... o base64 puro), lo decodifica inmediatamente en memoria a [ImageBitmap].
 * 2. Si [source] es una URL HTTP/HTTPS, lo descarga vía Coil [SubcomposeAsyncImage].
 * 3. Si no existe imagen o falla la decodificación/descarga, renderiza el Composable [fallback].
 */
@Composable
fun AdaptiveImage(
    source: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit = {},
) {
    val trimmed = source?.trim().orEmpty()

    val inMemoryBitmap = remember(trimmed) {
        if (trimmed.startsWith("data:image/", ignoreCase = true) ||
            (trimmed.length > 50 && !trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true))
        ) {
            parseBase64ImageBitmap(trimmed)
        } else {
            null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            inMemoryBitmap != null -> {
                Image(
                    bitmap = inMemoryBitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                SubcomposeAsyncImage(
                    model = trimmed,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    error = { fallback() },
                    loading = { fallback() },
                )
            }
            else -> {
                fallback()
            }
        }
    }
}
