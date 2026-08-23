package com.amaxoniaerp.composition

import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.application.OpenCajaUseCase
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.clients.data.ClientTypesRepository
import com.amaxoniaerp.features.clients.data.ClientsRepository
import com.amaxoniaerp.features.companies.domain.CompanyService
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.promotions.data.PromotionsRepository
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase

/**
 * Composition root del backend: constructor DI manual como estrategia canónica.
 * `Routing.kt` solo registra endpoints a partir de este grafo.
 */
class AppDependencies(
    val auth: AuthDependencies,
    val repositories: Repositories,
    val caja: CajaDependencies,
    val mesas: MesasDependencies,
    val fiscal: FiscalDependencies,
    val routingConfig: RoutingConfig,
)

/** Servicios de autenticación/empresa (two-tier login + selección de empresa). */
class AuthDependencies(
    val authService: AuthService,
    val companyService: CompanyService,
)

class Repositories {
    val itemsRepository = ItemsRepository()
    val clientsRepository = ClientsRepository()
    val clientTypesRepository = ClientTypesRepository()
    val facturasRepository = FacturasRepository()
    val geographyRepository = GeographyRepository()
    val formasPagoRepository = FormasPagoRepository()
    val promotionsRepository = PromotionsRepository()
    val mesasRepository = MesasRepository()
}

/** Caja: workflow de apertura (transición) y sesión de caja profunda sobre el repositorio de queries. */
class CajaDependencies(
    val cajaRepository: CajaRepository,
    val openCajaUseCase: OpenCajaUseCase,
    val cajaSession: CajaSessionWorkflow,
)

/** Grafo fiscal: FE (PAC/HKA) y su consumidor de notas de crédito. */
class FiscalDependencies(
    val feDependencies: FeDependencies,
    val creditNoteDependencies: CreditNoteDependencies,
)

/**
 * Cuenta/mesas y la venta POS comparten repositorios: SesionMesaRepository usa
 * PedidoMesaRepository como lookup de operaciones y ProcessSaleUseCase escribe
 * cuentas de mesa al procesar la venta.
 */
class MesasDependencies(
    val pedidoMesaRepository: PedidoMesaRepository,
    val sesionMesaRepository: SesionMesaRepository,
    val cuentaMesaRepository: CuentaMesaRepository,
    val processSaleUseCase: ProcessSaleUseCase,
)

class RoutingConfig(
    val dataBasePath: String?,
    val assetsBaseUrls: MutableMap<String, String>,
)
