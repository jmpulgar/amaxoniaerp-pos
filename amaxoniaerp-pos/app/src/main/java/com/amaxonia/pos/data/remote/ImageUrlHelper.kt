package com.amaxonia.pos.data.remote

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Construye URLs para imágenes servidas por el backend (productos y clientes).
 *
 * Backend: GET /api/data/{countryCode}/{companyDb}/item/{path}
 *          GET /api/data/{countryCode}/{companyDb}/cliente_foto/{idCliente}/{filename}
 */
object ImageUrlHelper {

    /**
     * URL de foto de producto.
     * @param baseUrl URL base del backend (ej. http://10.0.2.2:8080), sin barra final
     * @param countryCode VE | PA
     * @param companyDb nombre de BD administrativa (admin_db)
     * @param photoPath valor de item.foto (ej. "fotos/1_foto.jpeg")
     */
    fun productImageUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        photoPath: String
    ): String {
        if (photoPath.isBlank()) return ""
        val base = baseUrl.trimEnd('/')
        val encoded = URLEncoder.encode(photoPath, StandardCharsets.UTF_8).replace("+", "%20")
        return "$base/api/data/$countryCode/$companyDb/item/$encoded"
    }

    /**
     * URL de foto de cliente.
     * @param baseUrl URL base del backend
     * @param countryCode VE | PA
     * @param companyDb admin_db
     * @param idCliente clientes.id_cliente (ej. UUID)
     * @param photoFilename valor en BD (ej. "46726248-84e3-11f0-bdd8-76ef9644317f_foto.jpeg")
     */
    fun clientPhotoUrl(
        baseUrl: String,
        countryCode: String,
        companyDb: String,
        idCliente: String,
        photoFilename: String
    ): String {
        if (idCliente.isBlank() || photoFilename.isBlank()) return ""
        val base = baseUrl.trimEnd('/')
        val encId = URLEncoder.encode(idCliente, StandardCharsets.UTF_8).replace("+", "%20")
        val encFile = URLEncoder.encode(photoFilename, StandardCharsets.UTF_8).replace("+", "%20")
        return "$base/api/data/$countryCode/$companyDb/cliente_foto/$encId/$encFile"
    }

    /**
     * Si en BD solo tienes id_cliente, el filename suele ser "{id_cliente}_foto.jpeg".
     */
    fun clientPhotoFilename(idCliente: String, extension: String = "jpeg"): String =
        "${idCliente}_foto.$extension"
}
