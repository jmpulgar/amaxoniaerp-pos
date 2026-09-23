package com.amaxoniaerp.features.items.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.facturas.data.getBestSellerItemQuantities
import com.amaxoniaerp.features.items.data.ItemsListQuery
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.data.listBrands
import com.amaxoniaerp.features.items.data.listDepartments
import com.amaxoniaerp.features.items.data.listFamilies
import com.amaxoniaerp.features.items.data.listLines
import com.amaxoniaerp.features.items.data.listSections
import com.amaxoniaerp.features.items.data.listSubFamilies
import com.amaxoniaerp.features.items.domain.BestSellerItemResponse
import com.amaxoniaerp.features.items.domain.BestSellersApiResponse
import com.amaxoniaerp.features.items.domain.CreateProductRequest
import com.amaxoniaerp.features.items.domain.DepartmentItemResponse
import com.amaxoniaerp.features.items.domain.DepartmentsApiResponse
import com.amaxoniaerp.features.items.domain.ProductsListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000
private const val MIN_BEST_SELLERS_LIMIT = 1
private const val MAX_BEST_SELLERS_LIMIT = 50
private const val DEFAULT_BEST_SELLERS_LIMIT = 20

private const val ERR_INVALID_PRODUCT_ID = "ID de producto inválido"
private const val ERR_PRODUCT_NOT_FOUND = "Producto no encontrado"

/**
 * Rutas de items Multi-Tenant con Safe Parsing.
 * Utiliza claims del JWT para routing dinámico a la BD correcta.
 */
fun Route.itemsRoutes(itemsRepository: ItemsRepository) {
    val handlers = ItemsHandlers(itemsRepository)
    val taxonomyHandlers = ItemsTaxonomyHandlers(itemsRepository)

    authenticate {
        route("/items") {
            /**
             * GET /items - Lista productos con paginación.
             *
             * Requiere: JWT Company Token con claims country_code y admin_db
             */
            get { handlers.listar(call) }

            /** GET /items/departments - Lista departamentos con productos. */
            get("departments") { taxonomyHandlers.departamentos(call) }

            get("sections") { taxonomyHandlers.secciones(call) }
            get("families") { taxonomyHandlers.familias(call) }
            get("subfamilies") { taxonomyHandlers.subfamilias(call) }
            get("brands") { taxonomyHandlers.marcas(call) }
            get("lines") { taxonomyHandlers.lineas(call) }

            /**
             * GET /items/best-sellers - Productos más vendidos desde factura_detalle.
             */
            get("best-sellers") { handlers.masVendidos(call) }

            /**
             * POST /items - Crea un nuevo producto.
             */
            post { handlers.crear(call) }

            /**
             * PUT /items/{id} - Actualiza un producto.
             */
            put("/{id}") { handlers.actualizar(call) }

            /**
             * GET /items/{id} - Obtiene un producto por ID.
             */
            get("/{id}") { handlers.detalle(call) }

            /**
             * GET /items/{id}/lots - Lotes disponibles para un producto (FEFO).
             */
            get("/{id}/lots") { handlers.lotes(call) }

            /**
             * GET /items/{id}/stock - Inventario por almacen con precompromisos.
             */
            get("/{id}/stock") { handlers.stock(call) }
        }
    }
}

/**
 * Handlers de endpoints de taxonomía de items (jerarquía departamento ->
 * sección -> familia -> subfamilia y catálogos marca/línea).
 */
internal class ItemsTaxonomyHandlers(
    private val itemsRepository: ItemsRepository,
) {
    suspend fun departamentos(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val list = itemsRepository.listDepartments(database = database)
            call.respondDepartments(list)
        }

    suspend fun secciones(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val departmentId = call.requireIntQuery("departmentId", "departmentId inválido") ?: return@run
            val list = itemsRepository.listSections(database = database, departmentId = departmentId)
            call.respondDepartments(list)
        }

    suspend fun familias(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val sectionId = call.requireIntQuery("sectionId", "sectionId inválido") ?: return@run
            val list = itemsRepository.listFamilies(database = database, sectionId = sectionId)
            call.respondDepartments(list)
        }

    suspend fun subfamilias(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val familyId = call.requireIntQuery("familyId", "familyId inválido") ?: return@run
            val list = itemsRepository.listSubFamilies(database = database, familyId = familyId)
            call.respondDepartments(list)
        }

    suspend fun marcas(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val list = itemsRepository.listBrands(database = database)
            call.respondDepartments(list)
        }

    suspend fun lineas(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val brandId = call.requireIntQuery("brandId", "brandId inválido") ?: return@run
            val list = itemsRepository.listLines(database = database, brandId = brandId)
            call.respondDepartments(list)
        }
}

/**
 * Handlers de los endpoints de items. Resuelven tenant por el seam canónico,
 * delegan en el repositorio y mapean los resultados a HTTP.
 */
internal class ItemsHandlers(
    private val itemsRepository: ItemsRepository,
) {
    suspend fun listar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            val search = call.request.queryParameters["search"]
            val includeTotal = call.request.queryParameters["includeTotal"]?.toBooleanStrictOrNull() ?: true
            val departmentIdParam = call.request.queryParameters["departmentId"]?.toIntOrNull()
            val departmentIdsParam =
                (call.request.queryParameters["departmentIds"] ?: call.request.queryParameters["deptIds"])
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.filter { it > 0 }
            val itemTypeParam = call.request.queryParameters["itemType"] ?: call.request.queryParameters["type"]

            if (limit <= 0 || limit > MAX_PAGE_LIMIT || offset < 0) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Parámetros de paginación inválidos"))
                return@run
            }

            // Conectar a BD de empresa usando Two-Tier routing
            val companyDb = ctx.connectDatabase()

            val (items, total) =
                itemsRepository.listItems(
                    database = companyDb,
                    countryCode = ctx.countryCode,
                    query =
                        ItemsListQuery(
                            limit = limit,
                            offset = offset,
                            search = search,
                            includeTotal = includeTotal,
                            departmentId = departmentIdParam,
                            departmentIds = departmentIdsParam,
                            itemType = itemTypeParam,
                        ),
                )

            call.respond(ProductsListResponse(data = items, total = total))
        }

    suspend fun masVendidos(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val limit =
                call.request.queryParameters["limit"]
                    ?.toIntOrNull()
                    ?.coerceIn(MIN_BEST_SELLERS_LIMIT, MAX_BEST_SELLERS_LIMIT) ?: DEFAULT_BEST_SELLERS_LIMIT
            val companyDb = ctx.connectDatabase()
            val quantities = getBestSellerItemQuantities(companyDb, limit)
            val ids = quantities.map { it.first }
            val products = itemsRepository.getItemsByIds(companyDb, ctx.countryCode, ids)
            val productMap = products.associateBy { it.id.toIntOrNull() ?: 0 }
            val data =
                quantities.mapNotNull { (id, salesCount) ->
                    productMap[id]?.let { p ->
                        BestSellerItemResponse(
                            id = p.id,
                            name = p.description,
                            price = p.prices.firstOrNull()?.pricePlusTax ?: 0.0,
                            salesCount = salesCount.toInt(),
                            photoUrl = p.photoUrl,
                        )
                    }
                }
            call.respond(BestSellersApiResponse(data = data))
        }

    suspend fun crear(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val request = call.receive<CreateProductRequest>()
            val companyDb = ctx.connectDatabase()

            val product =
                itemsRepository.createItem(
                    database = companyDb,
                    countryCode = ctx.countryCode,
                    request = request,
                )

            call.respond(HttpStatusCode.Created, product)
        }

    suspend fun actualizar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val id = call.requireIntParam("id", ERR_INVALID_PRODUCT_ID) ?: return@run

            val request = call.receive<CreateProductRequest>()
            val companyDb = ctx.connectDatabase()

            val product =
                itemsRepository.updateItem(
                    database = companyDb,
                    countryCode = ctx.countryCode,
                    id = id,
                    request = request,
                )
            if (product == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_PRODUCT_NOT_FOUND))
                return@run
            }

            call.respond(product)
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val id = call.requireIntParam("id", ERR_INVALID_PRODUCT_ID) ?: return@run

            val companyDb = ctx.connectDatabase()

            val product =
                itemsRepository.getItemById(
                    database = companyDb,
                    countryCode = ctx.countryCode,
                    id = id,
                )
            if (product == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_PRODUCT_NOT_FOUND))
                return@run
            }

            call.respond(product)
        }

    suspend fun lotes(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val id = call.requireIntParam("id", ERR_INVALID_PRODUCT_ID) ?: return@run

            val companyDb = ctx.connectDatabase()
            val lots = itemsRepository.getItemLots(companyDb, id)
            call.respond(lots)
        }

    suspend fun stock(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val id = call.requireIntParam("id", ERR_INVALID_PRODUCT_ID) ?: return@run

            val companyDb = ctx.connectDatabase()
            val stock = itemsRepository.getItemStockByWarehouse(companyDb, id)
            call.respond(stock)
        }
}

private suspend fun ApplicationCall.resolveDatabase(): org.jetbrains.exposed.sql.Database? =
    run {
        val ctx = resolveCompanyRequestContext() ?: return@run null
        ctx.connectDatabase()
    }

private suspend fun ApplicationCall.respondDepartments(list: List<Pair<Int, String>>) {
    val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
    respond(DepartmentsApiResponse(data = data))
}

private suspend fun ApplicationCall.requireIntQuery(
    name: String,
    errorMessage: String,
): Int? =
    run {
        val value = request.queryParameters[name]?.toIntOrNull()
        if (value == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
            return@run null
        }
        value
    }

private suspend fun ApplicationCall.requireIntParam(
    name: String,
    errorMessage: String,
): Int? =
    run {
        val value = parameters[name]?.toIntOrNull()
        if (value == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
            return@run null
        }
        value
    }
