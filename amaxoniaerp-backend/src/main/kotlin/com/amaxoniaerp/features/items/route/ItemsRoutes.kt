package com.amaxoniaerp.features.items.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.domain.BestSellerItemResponse
import com.amaxoniaerp.features.items.domain.BestSellersApiResponse
import com.amaxoniaerp.features.items.domain.CreateProductRequest
import com.amaxoniaerp.features.items.domain.DepartmentItemResponse
import com.amaxoniaerp.features.items.domain.DepartmentsApiResponse
import com.amaxoniaerp.features.items.domain.ProductsListResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Rutas de items Multi-Tenant con Safe Parsing.
 * Utiliza claims del JWT para routing dinámico a la BD correcta.
 */
fun Route.itemsRoutes(itemsRepository: ItemsRepository) {
    authenticate {
        route("/items") {
            /**
             * GET /items - Lista productos con paginación.
             *
             * Requiere: JWT Company Token con claims country_code y admin_db
             */
            get {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val limitParam = call.request.queryParameters["limit"]?.toIntOrNull()
                val offsetParam = call.request.queryParameters["offset"]?.toLongOrNull()
                val limit = limitParam ?: 100
                val offset = offsetParam ?: 0L
                val search = call.request.queryParameters["search"]
                val includeTotalParam = call.request.queryParameters["includeTotal"]
                val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true
                val departmentIdParam = call.request.queryParameters["departmentId"]?.toIntOrNull()

                if (limit <= 0 || limit > 1000 || offset < 0) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Parámetros de paginación inválidos"),
                    )
                }

                // Conectar a BD de empresa usando Two-Tier routing
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)

                val (items, total) =
                    itemsRepository.listItems(
                        database = companyDb,
                        countryCode = countryCode,
                        limit = limit,
                        offset = offset,
                        search = search,
                        includeTotal = includeTotal,
                        departmentId = departmentIdParam,
                    )

                call.respond(ProductsListResponse(data = items, total = total))
            }

            /**
             * GET /items/departments - Lista departamentos con productos.
             */
            get("departments") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val list = itemsRepository.listDepartments(database = companyDb, countryCode = countryCode)
                val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
                call.respond(DepartmentsApiResponse(data = data))
            }

            get("sections") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                val departmentId =
                    call.request.queryParameters["departmentId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "departmentId inválido"))

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val list = itemsRepository.listSections(database = companyDb, departmentId = departmentId)
                val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
                call.respond(DepartmentsApiResponse(data = data))
            }

            get("families") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                val sectionId =
                    call.request.queryParameters["sectionId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sectionId inválido"))

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val list = itemsRepository.listFamilies(database = companyDb, sectionId = sectionId)
                val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
                call.respond(DepartmentsApiResponse(data = data))
            }

            get("subfamilies") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                val familyId =
                    call.request.queryParameters["familyId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "familyId inválido"))

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val list = itemsRepository.listSubFamilies(database = companyDb, familyId = familyId)
                val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
                call.respond(DepartmentsApiResponse(data = data))
            }

            get("brands") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val list = itemsRepository.listBrands(database = companyDb)
                val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
                call.respond(DepartmentsApiResponse(data = data))
            }

            get("lines") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                val brandId =
                    call.request.queryParameters["brandId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "brandId inválido"))

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val list = itemsRepository.listLines(database = companyDb, brandId = brandId)
                val data = list.map { (id, name) -> DepartmentItemResponse(id = id, name = name) }
                call.respond(DepartmentsApiResponse(data = data))
            }

            /**
             * GET /items/best-sellers - Productos más vendidos desde factura_detalle.
             */
            get("best-sellers") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                val limit =
                    call.request.queryParameters["limit"]
                        ?.toIntOrNull()
                        ?.coerceIn(1, 50) ?: 20
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val quantities =
                    com.amaxoniaerp.features.facturas.data
                        .getBestSellerItemQuantities(companyDb, limit)
                val ids = quantities.map { it.first }
                val products = itemsRepository.getItemsByIds(companyDb, countryCode, ids)
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

            /**
             * POST /items - Crea un nuevo producto.
             */
            post {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val request = call.receive<CreateProductRequest>()
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)

                val product =
                    itemsRepository.createItem(
                        database = companyDb,
                        countryCode = countryCode,
                        request = request,
                    )

                call.respond(HttpStatusCode.Created, product)
            }

            /**
             * PUT /items/{id} - Actualiza un producto.
             */
            put("/{id}") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@put call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val id =
                    call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "ID de producto inválido"),
                        )

                val request = call.receive<CreateProductRequest>()
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)

                val product =
                    itemsRepository.updateItem(
                        database = companyDb,
                        countryCode = countryCode,
                        id = id,
                        request = request,
                    ) ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Producto no encontrado"),
                    )

                call.respond(product)
            }

            /**
             * GET /items/{id} - Obtiene un producto por ID.
             */
            get("/{id}") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val id =
                    call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "ID de producto inválido"),
                        )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)

                val product =
                    itemsRepository.getItemById(
                        database = companyDb,
                        countryCode = countryCode,
                        id = id,
                    ) ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Producto no encontrado"),
                    )

                call.respond(product)
            }

            /**
             * GET /items/{id}/lots - Lotes disponibles para un producto (FEFO).
             */
            get("/{id}/lots") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token invalido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val id =
                    call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "ID de producto invalido"),
                        )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val lots = itemsRepository.getItemLots(companyDb, id)
                call.respond(lots)
            }

            /**
             * GET /items/{id}/stock - Inventario por almacen con precompromisos.
             */
            get("/{id}/stock") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val id =
                    call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "ID de producto inválido"),
                        )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val stock = itemsRepository.getItemStockByWarehouse(companyDb, id)
                call.respond(stock)
            }
        }
    }
}
