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

    /**
     * Determina si un valor de imagen es una fuente directa (URL completa o datos Base64).
     */
    fun isDirectImage(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("data:image/", ignoreCase = true)
        ) {
            return true
        }
        val lower = trimmed.lowercase()
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".image") ||
            trimmed.contains("..")
        ) {
            return false
        }
        return trimmed.length > 50
    }

    /**
     * Construye la URL para la imagen de un método de pago.
     * Si [imageValue] es Base64 (data:image/... o base64 puro) o una URL completa, se devuelve tal cual.
     * Si no, construye la URL del endpoint del backend:
     *   GET /api/data/{countryCode}/{companyDb}/forma_pago/{idFormaPago}
     */
    fun paymentMethodImageUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        idFormaPago: Int,
        imageValue: String? = null,
    ): String {
        if (imageValue.isNullOrBlank()) {
            return ""
        }
        if (isDirectImage(imageValue)) {
            return imageValue.trim()
        }
        if (baseUrl.isBlank() || countryCode.isBlank() || companyDb.isBlank() || idFormaPago <= 0) {
            return ""
        }
        val base = baseUrl.trimEnd('/')
        return "$base/api/data/$countryCode/$companyDb/forma_pago/$idFormaPago"
    }

    /**
     * Construye la URL para la imagen de un grupo de método de pago.
     */
    fun paymentGroupImageUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        idGrupo: Int,
        imageValue: String? = null,
    ): String {
        if (isDirectImage(imageValue)) {
            return imageValue!!.trim()
        }
        if (baseUrl.isBlank() || countryCode.isBlank() || companyDb.isBlank() || idGrupo <= 0) {
            return ""
        }
        val base = baseUrl.trimEnd('/')
        return "$base/api/data/$countryCode/$companyDb/forma_pago_grupo/$idGrupo"
    }

    /**
     * Construye la URL para la imagen de un billete o moneda (denominación).
     */
    fun denominationImageUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        idDenominacion: Int,
        imageValue: String? = null,
    ): String {
        if (isDirectImage(imageValue)) {
            return imageValue!!.trim()
        }
        if (baseUrl.isBlank() || countryCode.isBlank() || companyDb.isBlank() || idDenominacion <= 0) {
            return ""
        }
        val base = baseUrl.trimEnd('/')
        return "$base/api/data/$countryCode/$companyDb/moneda_denominacion/$idDenominacion"
    }
}
