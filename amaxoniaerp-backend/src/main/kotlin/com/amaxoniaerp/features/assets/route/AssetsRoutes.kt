package com.amaxoniaerp.features.assets.route

import com.amaxoniaerp.features.assets.data.AssetsRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64

private val PNG_HEADER = listOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
private val JPEG_HEADER = listOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

/**
 * Rutas para servir imágenes de productos (item), clientes, métodos de pago,
 * denominaciones y banners/videos de kiosco.
 *
 * En BD: item.foto = "fotos/1_foto.jpeg"; cliente = "{id_cliente}_foto.jpeg".
 * Físicamente: item en {data}/item/1_foto.jpeg; cliente en {data}/cliente_foto/{id}/{filename}.
 * Banners: {data}/banners/{filename}. Departamentos: departamento.foto = "fotos/21_foto.png" -> {data}/departamento/21_foto.png.
 *
 * Si ASSETS_BASE_URL está configurado, se redirige allí (ej. listoerp.app).
 * Si DATA_BASE_PATH está configurado y el archivo existe, se sirve desde disco.
 */
fun Route.assetsRoutes(
    assetsBaseUrls: Map<String, String>,
    dataBasePath: String?,
    assetsRepository: AssetsRepository = AssetsRepository(),
) {
    val handlers = AssetsHandlers(assetsBaseUrls, dataBasePath, assetsRepository)
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

        /**
         * Banners / videos del attract loop para Kiosco.
         * URL: .../banners/{filename} (ej. .../banners/video_promo.mp4)
         */
        get("/banners/{filename}") { handlers.servirBanner(call) }

        /**
         * Imagen propia de un departamento (categoría del kiosco).
         * Path en BD: "fotos/21_foto.png" -> archivo en departamento/21_foto.png
         */
        get("/departamento/{filename}") { handlers.servirDepartamento(call) }

        /**
         * Imagen de forma de pago.
         */
        get("/forma_pago/{id}") { handlers.servirFormaPago(call) }
        get("/caja_forma_pago/{id}") { handlers.servirFormaPago(call) }

        /**
         * Imagen de grupo de forma de pago.
         */
        get("/forma_pago_grupo/{id}") { handlers.servirFormaPagoGrupo(call) }
        get("/caja_forma_pago_grupo/{id}") { handlers.servirFormaPagoGrupo(call) }

        /**
         * Imagen de denominación de moneda / billete.
         */
        get("/moneda_denominacion/{id}") { handlers.servirMonedaDenominacion(call) }
    }

    /**
     * Compatibilidad con formato de llamada legacy PHP (?action=...).
     */
    route("/api") {
        get { handlers.servirLegacyAction(call) }
    }
    route("/app/api") {
        get { handlers.servirLegacyAction(call) }
    }
}

private data class AssetRequestScope(
    val countryCode: String,
    val companyDb: String,
)

/**
 * Handlers de los endpoints de assets (imágenes de productos, clientes, métodos de pago y monedas).
 */
internal class AssetsHandlers(
    private val assetsBaseUrls: Map<String, String>,
    private val dataBasePath: String?,
    private val assetsRepository: AssetsRepository,
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

    suspend fun servirBanner(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val filename =
                call.parameters["filename"]?.takeIf { it.isNotBlank() && !it.contains("..") }
            if (filename == null) {
                call.respond(HttpStatusCode.BadRequest, "filename inválido")
                return@run
            }

            val localFile =
                localFile(File(dataBasePath ?: "", "${scope.companyDb}/banners/$filename"))
            if (localFile != null) {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                call.respondFile(localFile)
                return@run
            }

            redirectToAssetsBase(call, scope, "banners/$filename")
        }

    suspend fun servirDepartamento(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val filename =
                call.parameters["filename"]?.takeIf { it.isNotBlank() && !it.contains("..") }
            if (filename == null) {
                call.respond(HttpStatusCode.BadRequest, "filename inválido")
                return@run
            }

            val localFile =
                localFile(File(dataBasePath ?: "", "${scope.companyDb}/departamento/$filename"))
            if (localFile != null) {
                call.respondBytes(localFile.readBytes(), contentTypeForFilename(filename))
                return@run
            }

            redirectToAssetsBase(call, scope, "departamento/$filename")
        }

    suspend fun servirFormaPago(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "id inválido")
                return@run
            }
            val dbImage = assetsRepository.findFormaPagoImage(scope.countryCode, scope.companyDb, id)
            respondResolvedAsset(call, scope, dbImage, "administrativo/caja/forma_pago/$id.image")
        }

    suspend fun servirFormaPagoGrupo(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "id inválido")
                return@run
            }
            val dbImage = assetsRepository.findFormaPagoGrupoImage(scope.countryCode, scope.companyDb, id)
            respondResolvedAsset(call, scope, dbImage, "administrativo/caja/forma_pago/$id.image_grupo")
        }

    suspend fun servirMonedaDenominacion(call: ApplicationCall) =
        run {
            val scope = call.resolveAssetScope() ?: return@run
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "id inválido")
                return@run
            }
            val dbImage = assetsRepository.findMonedaDenominacionImage(scope.countryCode, scope.companyDb, id)
            respondResolvedAsset(call, scope, dbImage, "administrativo/moneda/denominacion/$id.image")
        }

    suspend fun servirLegacyAction(call: ApplicationCall) =
        run {
            val action =
                call.request.queryParameters["action"]
                    .orEmpty()
                    .trim()
                    .lowercase()
            val id = call.request.queryParameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "id inválido")
                return@run
            }
            val scope = call.resolveLegacyScope()
            when {
                action.startsWith("item/image") -> serveLegacyItem(call, scope, id)
                action.startsWith("caja_forma_pago_grupo/imagen") -> {
                    val dbImage = assetsRepository.findFormaPagoGrupoImage(scope.countryCode, scope.companyDb, id)
                    respondResolvedAsset(call, scope, dbImage, "administrativo/caja/forma_pago/$id.image_grupo")
                }
                action.startsWith("caja_forma_pago/imagen") -> {
                    val dbImage = assetsRepository.findFormaPagoImage(scope.countryCode, scope.companyDb, id)
                    respondResolvedAsset(call, scope, dbImage, "administrativo/caja/forma_pago/$id.image")
                }
                action.startsWith("moneda/denominacion") -> {
                    val dbImage = assetsRepository.findMonedaDenominacionImage(scope.countryCode, scope.companyDb, id)
                    respondResolvedAsset(call, scope, dbImage, "administrativo/moneda/denominacion/$id.image")
                }
                else -> call.respond(HttpStatusCode.NotFound, "Acción desconocida")
            }
        }

    private suspend fun serveLegacyItem(
        call: ApplicationCall,
        scope: AssetRequestScope,
        id: Int,
    ) {
        val filename = assetsRepository.findItemPhoto(scope.countryCode, scope.companyDb, id)
        val targetFile = filename?.substringAfterLast('/')?.ifBlank { filename } ?: "${id}_foto.jpg"
        val localFile = localFile(File(dataBasePath ?: "", "${scope.companyDb}/item/$targetFile"))
        if (localFile != null) {
            call.respondBytes(localFile.readBytes(), contentTypeForFilename(targetFile))
            return
        }
        redirectToAssetsBase(call, scope, "item/$targetFile")
    }

    private suspend fun respondResolvedAsset(
        call: ApplicationCall,
        scope: AssetRequestScope,
        dbImage: String?,
        relativePath: String,
    ) {
        if (!dbImage.isNullOrBlank()) {
            val decoded = parseDataUriOrBase64(dbImage)
            if (decoded != null) {
                call.respondBytes(decoded.bytes, decoded.contentType)
                return
            }
        }

        val localFile = localFile(File(dataBasePath ?: "", "${scope.companyDb}/$relativePath"))
        if (localFile != null) {
            val decoded = decodeImageBytes(localFile.readBytes())
            if (decoded != null) {
                call.respondBytes(decoded.bytes, decoded.contentType)
                return
            }
        }

        redirectToAssetsBase(call, scope, relativePath)
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

    private fun ApplicationCall.resolveLegacyScope(): AssetRequestScope {
        val countryCode =
            request.queryParameters["countryCode"]?.takeIf { it.length == 2 }?.uppercase()
                ?: "PA"
        val companyDb =
            request.queryParameters["companyDb"]?.takeIf { it.isNotBlank() && !it.contains("..") }
                ?: request.queryParameters["database"]?.takeIf { it.isNotBlank() && !it.contains("..") }
                ?: "default"
        return AssetRequestScope(countryCode = countryCode, companyDb = companyDb)
    }
}

private fun contentTypeForFilename(filename: String): ContentType {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "gif" -> ContentType.Image.GIF
        "webp" -> ContentType.parse("image/webp")
        "mp4" -> ContentType.parse("video/mp4")
        "webm" -> ContentType.parse("video/webm")
        else -> ContentType.Image.Any
    }
}

private data class DecodedImage(
    val bytes: ByteArray,
    val contentType: ContentType,
)

private fun parseDataUriOrBase64(raw: String): DecodedImage? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return try {
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val mimePart = trimmed.substringAfter("data:").substringBefore(";")
            val base64Part = trimmed.substringAfter("base64,")
            val bytes = Base64.getDecoder().decode(base64Part.trim())
            val contentType = ContentType.parse(mimePart)
            DecodedImage(bytes, contentType)
        } else if (trimmed.contains(";base64,")) {
            val mimePart = trimmed.substringBefore(";base64,")
            val base64Part = trimmed.substringAfter(";base64,")
            val bytes = Base64.getDecoder().decode(base64Part.trim())
            val contentType = ContentType.parse(mimePart)
            DecodedImage(bytes, contentType)
        } else {
            val bytes = Base64.getDecoder().decode(trimmed)
            DecodedImage(bytes, ContentType.Image.PNG)
        }
    } catch (_: Exception) {
        null
    }
}

private fun decodeImageBytes(bytes: ByteArray): DecodedImage? {
    if (bytes.size >= PNG_HEADER.size && bytes.take(PNG_HEADER.size) == PNG_HEADER) {
        return DecodedImage(bytes, ContentType.Image.PNG)
    }
    if (bytes.size >= JPEG_HEADER.size && bytes.take(JPEG_HEADER.size) == JPEG_HEADER) {
        return DecodedImage(bytes, ContentType.Image.JPEG)
    }
    return parseDataUriOrBase64(bytes.toString(Charsets.UTF_8))
}
