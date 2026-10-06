package com.amaxonia.erp.data.remote

import java.net.URLEncoder

/**
 * Construye URLs absolutas para imágenes servidas por el backend (productos y clientes).
 *
 * Endpoints de assets en el backend:
 *  GET /api/data/{countryCode}/{companyDb}/item/{path...}
 *  GET /api/data/{countryCode}/{companyDb}/cliente_foto/{idCliente}/{filename}
 */
object ImageUrlHelper {
    private const val UTF8 = "UTF-8"

    private fun encSegment(value: String): String =
        URLEncoder.encode(value, UTF8).replace("+", "%20")

    /** Encodea una ruta preservando los separadores de directorio "/". */
    private fun encPath(path: String): String =
        path
            .split("/")
            .filter { it.isNotBlank() }
            .joinToString("/") { seg -> encSegment(seg) }

    /**
     * Construye la URL completa de la foto del producto.
     * Si [photoPath] ya es una URL completa (http:// o https://), se devuelve sin alterar.
     * Si es una ruta relativa (ej. "fotos/1_foto.jpeg"), se construye la URL del backend.
     */
    fun productImageUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        photoPath: String,
    ): String {
        if (photoPath.isBlank()) return ""
        if (photoPath.startsWith("http://", ignoreCase = true) ||
            photoPath.startsWith("https://", ignoreCase = true)
        ) {
            return photoPath
        }
        if (baseUrl.isBlank() || countryCode.isBlank() || companyDb.isBlank()) {
            return ""
        }
        val base = baseUrl.trimEnd('/')
        val encodedPath = encPath(photoPath)
        return "$base/api/data/$countryCode/$companyDb/item/$encodedPath"
    }

    /**
     * Construye la URL completa de la foto de cliente.
     */
    fun clientPhotoUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        idCliente: String,
        photoFilename: String,
    ): String {
        if (photoFilename.isBlank()) return ""
        if (photoFilename.startsWith("http://", ignoreCase = true) ||
            photoFilename.startsWith("https://", ignoreCase = true)
        ) {
            return photoFilename
        }
        if (baseUrl.isBlank() || countryCode.isBlank() || companyDb.isBlank() || idCliente.isBlank()) {
            return ""
        }
        val base = baseUrl.trimEnd('/')
        val encId = encSegment(idCliente)
        val encFile = encSegment(photoFilename)
        return "$base/api/data/$countryCode/$companyDb/cliente_foto/$encId/$encFile"
    }
}
