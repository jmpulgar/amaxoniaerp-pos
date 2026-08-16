package com.amaxoniaerp.features.assets.route

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.http.path
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.path
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Rutas para servir imágenes de productos (item) y clientes.
 *
 * En BD: item.foto = "fotos/1_foto.jpeg"; cliente = "{id_cliente}_foto.jpeg".
 * Físicamente: item en {data}/item/1_foto.jpeg; cliente en {data}/cliente_foto/{id}/{filename}.
 *
 * Si ASSETS_BASE_URL está configurado, se redirige allí (ej. listoerp.app).
 * Si DATA_BASE_PATH está configurado y el archivo existe, se sirve desde disco.
 */
fun Route.assetsRoutes(
    assetsBaseUrls: Map<String, String>,
    dataBasePath: String?,
) {
    val log = LoggerFactory.getLogger("AssetsRoutes")
    route("/api/data/{countryCode}/{companyDb}") {
        /**
         * Imagen de producto.
         * Path en BD: "fotos/1_foto.jpeg" -> archivo en item/1_foto.jpeg
         */
        get("/item/{path...}") {
            val countryCode =
                call.parameters["countryCode"]?.takeIf { it.length == 2 }?.uppercase()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "countryCode inválido")
            val companyDb =
                call.parameters["companyDb"]?.takeIf { it.isNotBlank() && !it.contains("..") }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "companyDb inválido")
            val pathSegments = call.parameters.getAll("path") ?: emptyList()
            val path = pathSegments.joinToString("/")
            if (path.isBlank() || path.contains("..")) {
                return@get call.respond(HttpStatusCode.BadRequest, "path inválido")
            }
            val filename = path.substringAfterLast('/').ifBlank { path }
            if (filename.isBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, "filename inválido")
            }

            if (!dataBasePath.isNullOrBlank()) {
                val file = File(dataBasePath, "$companyDb/item/$filename")
                if (file.exists() && file.isFile) {
                    call.respondBytes(file.readBytes(), contentTypeForFilename(filename))
                    return@get
                }
            }

            val base =
                assetsBaseUrls[countryCode]
                    ?: assetsBaseUrls.entries.firstOrNull()?.value
                    ?: return@get call.respond(
                        HttpStatusCode.NotImplemented,
                        "Configure ASSETS_BASE_URL o DATA_BASE_PATH para servir imágenes",
                    )
            val redirectUrl = "$base/$companyDb/item/$filename"
            call.respondRedirect(redirectUrl, permanent = false)
        }

        /**
         * Imagen de cliente.
         * URL: .../cliente_foto/{idCliente}/{filename} (ej. .../46726248-.../46726248-..._foto.jpeg)
         */
        get("/cliente_foto/{idCliente}/{filename}") {
            val countryCode =
                call.parameters["countryCode"]?.takeIf { it.length == 2 }?.uppercase()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "countryCode inválido")
            val companyDb =
                call.parameters["companyDb"]?.takeIf { it.isNotBlank() && !it.contains("..") }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "companyDb inválido")
            val idCliente =
                call.parameters["idCliente"]?.takeIf { it.isNotBlank() && !it.contains("..") }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "idCliente inválido")
            val filename =
                call.parameters["filename"]?.takeIf { it.isNotBlank() && !it.contains("..") }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "filename inválido")

            if (!dataBasePath.isNullOrBlank()) {
                val file = File(dataBasePath, "$companyDb/cliente_foto/$idCliente/$filename")
                log.info(
                    "[CLIENTE_FOTO] dataBasePath=$dataBasePath path=${file.absolutePath} exists=${file.exists()} " +
                        "isFile=${file.isFile}",
                )
                if (file.exists() && file.isFile) {
                    call.respondBytes(file.readBytes(), contentTypeForFilename(filename))
                    return@get
                }
            }

            val base =
                assetsBaseUrls[countryCode]
                    ?: assetsBaseUrls.entries.firstOrNull()?.value
                    ?: return@get call.respond(
                        HttpStatusCode.NotImplemented,
                        "Configure ASSETS_BASE_URL o DATA_BASE_PATH para servir imágenes",
                    )
            val redirectUrl = "$base/$companyDb/cliente_foto/$idCliente/$filename"
            log.info("[CLIENTE_FOTO] redirect idCliente=$idCliente filename=$filename redirectUrl=$redirectUrl")
            call.respondRedirect(redirectUrl, permanent = false)
        }
    }
}

private fun contentTypeForFilename(filename: String): ContentType {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "gif" -> ContentType.Image.GIF
        "webp" -> ContentType.parse("image/webp")
        else -> ContentType.Image.Any
    }
}
