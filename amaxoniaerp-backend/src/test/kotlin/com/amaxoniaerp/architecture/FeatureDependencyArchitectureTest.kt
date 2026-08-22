package com.amaxoniaerp.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * FASE 11 — Reglas de dependencias entre capas y features (escaneo de fuentes).
 *
 * Congela las direcciones canónicas del PLAN (§FASE 11):
 *  - `domain` no depende de Ktor, Exposed ni clientes PAC.
 *  - Las rutas no tocan [com.amaxoniaerp.core.database.DatabaseManager] ni
 *    construyen HttpClient.
 *  - `application`/`domain` de una feature no importan la implementación data
 *    de otra feature.
 *
 * Las excepciones existentes están allow-listadas EXPLÍCITAMENTE por archivo
 * con su motivo; cualquier archivo NUEVO que rompa una regla hace fallar el
 * test. Nunca se usan @Suppress para pasar estas reglas.
 */
class FeatureDependencyArchitectureTest {
    @Test
    fun `domain no importa ktor exposed ni clientes pac`() {
        val violations =
            mainKotlinFiles()
                .filter { relPath(it).contains("/domain/") }
                .flatMap { file ->
                    forbiddenDomainImports(file.readText()).map { "${relPath(file)}: ${it.trim()}" }
                }.filterNot { line ->
                    DOMAIN_INFRA_ALLOW_LIST.any { allowed -> line.startsWith(allowed) }
                }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Imports de infraestructura (Ktor/Exposed/PAC) en domain/:")
                violations.forEach { appendLine("  - $it") }
            },
        )
    }

    @Test
    fun `solo las rutas legacy allow-listadas usan DatabaseManager`() {
        val violations =
            routeFiles()
                .filter { it.readText().contains(DATABASE_MANAGER) }
                .map(::relPath)
                .filterNot { rel -> ROUTES_DATABASE_MANAGER_ALLOW_LIST.contains(rel) }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Rutas usando DatabaseManager fuera del allow-list legacy:")
                violations.forEach { appendLine("  - $it") }
            },
        )
    }

    @Test
    fun `las rutas no construyen HttpClient`() {
        val violations =
            routeFiles()
                .filter { it.readText().contains(HTTP_CLIENT_CTOR) }
                .map(::relPath)

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Rutas construyendo HttpClient (la creación vive en composition/adapters):")
                violations.forEach { appendLine("  - $it") }
            },
        )
    }

    @Test
    fun `application y domain no importan data de otra feature`() {
        val violations =
            mainKotlinFiles()
                .mapNotNull { file -> crossFeatureDataImport(file) }
                .filterNot { line ->
                    CROSS_FEATURE_DATA_ALLOW_LIST.any { allowed -> line.startsWith(allowed) }
                }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Features importando implementación data de otra feature desde application/domain:")
                violations.forEach { appendLine("  - $it") }
            },
        )
    }

    /** Imports prohibidos en dominio: Ktor, Exposed y paquetes PAC. */
    private fun forbiddenDomainImports(text: String): List<String> =
        text.lines().filter { line ->
            line.startsWith(KTOR_IMPORT_PREFIX) ||
                line.startsWith(EXPOSED_IMPORT_PREFIX) ||
                line.startsWith(PAC_IMPORT_INFIX) &&
                line.startsWith(IMPORT_PREFIX)
        }

    /**
     * Devuelve la línea violante si este archivo de application/domain importa
     * `features.<otra>.data.*`; null en caso contrario o si es data→data
     * dentro de la MISMA feature (reutilización legítima del esquema).
     */
    private fun crossFeatureDataImport(file: File): String? {
        val rel = relPath(file)
        val match = FEATURE_LAYER.matchEntire(rel)
        val feature = match?.groupValues?.get(1)
        val layer = match?.groupValues?.get(2)
        if (feature == null || (layer != "application" && layer != "domain")) return null
        return file
            .readLines()
            .firstOrNull { line ->
                CROSS_FEATURE_DATA.find(line)?.let { m -> m.groupValues[1] != feature } == true
            }?.let { "$rel: ${it.trim()}" }
    }

    private fun routeFiles(): List<File> = mainKotlinFiles().filter { relPath(it).contains("/route/") }

    private fun mainKotlinFiles(): List<File> =
        KOTLIN_ROOT
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun relPath(file: File): String = file.toRelativeString(KOTLIN_ROOT).replace(File.separatorChar, '/')

    private companion object {
        val KOTLIN_ROOT: File = File("src/main/kotlin").absoluteFile

        const val IMPORT_PREFIX = "import "
        const val KTOR_IMPORT_PREFIX = "import io.ktor."
        const val EXPOSED_IMPORT_PREFIX = "import org.jetbrains.exposed"
        const val PAC_IMPORT_INFIX = "import com.amaxoniaerp.features.electronicinvoice.pac."
        const val DATABASE_MANAGER = "DatabaseManager"
        const val HTTP_CLIENT_CTOR = "HttpClient("
        val FEATURE_LAYER = Regex("""com/amaxoniaerp/features/(\w+)/(application|domain)/.*""")
        val CROSS_FEATURE_DATA = Regex("""^import com\.amaxoniaerp\.features\.(\w+)\.data\.""")

        /**
         * Deuda documentada FE (estrategias VE viven bajo domain/ por decisión
         * previa del PLAN): migrarlas a application exige TASK funcional.
         */
        val DOMAIN_INFRA_ALLOW_LIST =
            listOf(
                "com/amaxoniaerp/features/electronicinvoice/domain/ElectronicInvoiceStrategy.kt",
                "com/amaxoniaerp/features/electronicinvoice/domain/VenezuelaEmissionEvaluation.kt",
                "com/amaxoniaerp/features/electronicinvoice/domain/VenezuelaInvoiceStrategy.kt",
            )

        /**
         * Seam legacy: estas rutas resuelven su DB vía DatabaseManager y están
         * congeladas por tests de integración (TenantSeam/SalesRoutes/
         * CreditNoteRoutes). Una ruta NUEVA no puede sumarse a esta lista sin
         * decisión explícita.
         */
        val ROUTES_DATABASE_MANAGER_ALLOW_LIST =
            listOf(
                "com/amaxoniaerp/features/clients/route/ClientsRoute.kt",
                "com/amaxoniaerp/features/clients/route/ClientTypesRoute.kt",
                "com/amaxoniaerp/features/creditnotes/route/CreditNoteRoutes.kt",
                "com/amaxoniaerp/features/electronicinvoice/route/ElectronicInvoiceRoutes.kt",
                "com/amaxoniaerp/features/facturas/route/FacturasRoutes.kt",
                "com/amaxoniaerp/features/geography/route/GeographyRoutes.kt",
                "com/amaxoniaerp/features/items/route/ItemsRoutes.kt",
                "com/amaxoniaerp/features/promotions/route/PromotionsRoutes.kt",
                "com/amaxoniaerp/features/sales/route/SalesRoutes.kt",
            )

        /**
         * Cruces heredados application/domain → data ajena, caracterizados:
         * auth emite tokens antes de existir tenant; companies lee auth para
         * login; creditnotes reusa el repositorio FE abierto de PA.
         */
        val CROSS_FEATURE_DATA_ALLOW_LIST =
            listOf(
                "com/amaxoniaerp/features/auth/domain/AuthService.kt",
                "com/amaxoniaerp/features/companies/domain/CompanyService.kt",
                "com/amaxoniaerp/features/creditnotes/application/PanamaCreditNoteProcessor.kt",
            )
    }
}
