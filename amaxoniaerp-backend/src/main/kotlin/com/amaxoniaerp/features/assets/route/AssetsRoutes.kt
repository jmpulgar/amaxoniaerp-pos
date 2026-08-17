package com.amaxoniaerp.features.assets.route

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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
    val handlers = AssetsHandlers(assetsBaseUrls, dataBasePath)
    route("/api/data/{countryCode}/{companyDb}") {
        /**
         * Imagen de producto.
         * Path en BD: "fotos/1_foto.jpeg" -> archivo en item/1_foto.jpeg
         */
        get("/item/{path...}") { handlers.servirItem(call) }

        /**
         * Imagen de cliente.
         * URL: .../cliente_foto/{idCliente}/{filename} (ej. .../46726248-.../46726248-..._foto.jpeg)
         */
        get("/cliente_foto/{idCliente}/{filename}") { handlers.servirClienteFoto(call) }
    }
}

private data class AssetRequestScope(
    val countryCode: String,
    val companyDb: String,
)

/**
 * Handlers de los endpoints de assets (imágenes de productos y clientes).
 */
internal class AssetsHandlers(
    private val assetsBaseUrls: Map<String, String>,
    private val dataBasePath: String?,
) {
    private val log = LoggerFactory.getLogger("AssetsRoutes")

    suspend fun servirItem(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val pathSegments = call.parameters.getAll("path") ?: emptyList()
            val path = pathSegments.joinToString("/")
            if (path.isBlank() || path.contains("..")) {
                call.respond(HttpStatusCode.BadRequest, "path inválido")
                return@run
            }
            val filename = path.substringAfterLast('/').ifBlank { path }
            if (filename.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "filename inválido")
                return@run
            }

            val localFile = localFile(File(dataBasePath ?: "", "${scope.companyDb}/item/$filename"))
            if (localFile != null) {
                call.respondBytes(localFile.readBytes(), contentTypeForFilename(filename))
                return@run
            }

            redirectToAssetsBase(call, scope, "item/$filename")
        }

    suspend fun servirClienteFoto(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val idCliente =
                call.parameters["idCliente"]?.takeIf { it.isNotBlank() && !it.contains("..") }
            if (idCliente == null) {
                call.respond(HttpStatusCode.BadRequest, "idCliente inválido")
                return@run
            }
            val filename =
                call.parameters["filename"]?.takeIf { it.isNotBlank() && !it.contains("..") }
            if (filename == null) {
                call.respond(HttpStatusCode.BadRequest, "filename inválido")
                return@run
            }

            val localFile =
                localFile(File(dataBasePath ?: "", "${scope.companyDb}/cliente_foto/$idCliente/$filename"))
            if (localFile != null) {
                call.respondBytes(localFile.readBytes(), contentTypeForFilename(filename))
                return@run
            }

            redirectToAssetsBase(call, scope, "cliente_foto/$idCliente/$filename")
        }

    private fun localFile(file: File): File? {
        if (dataBasePath.isNullOrBlank()) return null
        log.info(
            "[ASSETS] dataBasePath=$dataBasePath path=${file.absolutePath} " +
                "exists=${file.exists()} isFile=${file.isFile}",
        )
        return file.takeIf { it.exists() && it.isFile }
    }

    private suspend fun redirectToAssetsBase(
        call: ApplicationCall,
        scope: AssetRequestScope,
        relativePath: String,
    ) {
        val base =
            assetsBaseUrls[scope.countryCode]
                ?: assetsBaseUrls.entries.firstOrNull()?.value
        if (base == null) {
            call.respond(
                HttpStatusCode.NotImplemented,
                "Configure ASSETS_BASE_URL o DATA_BASE_PATH para servir imágenes",
            )
            return
        }
        val redirectUrl = "$base/${scope.companyDb}/$relativePath"
        log.info("[ASSETS] redirect path=$relativePath redirectUrl=$redirectUrl")
        call.respondRedirect(redirectUrl, permanent = false)
    }

    private suspend fun ApplicationCall.resolveAssetScope(): AssetRequestScope? =
        run {
            val countryCode = parameters["countryCode"]?.takeIf { it.length == 2 }?.uppercase()
            if (countryCode == null) {
                respond(HttpStatusCode.BadRequest, "countryCode inválido")
                return@run null
            }
            val companyDb = parameters["companyDb"]?.takeIf { it.isNotBlank() && !it.contains("..") }
            if (companyDb == null) {
                respond(HttpStatusCode.BadRequest, "companyDb inválido")
                return@run null
            }
            AssetRequestScope(countryCode = countryCode, companyDb = companyDb)
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
