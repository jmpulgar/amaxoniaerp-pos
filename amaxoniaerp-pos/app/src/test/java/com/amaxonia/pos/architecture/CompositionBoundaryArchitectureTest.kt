package com.amaxonia.pos.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Congela el boundary de composición (TASK-050/054).
 *
 * Regla: `DependencyContainer` es el service locator histórico del grafo de la
 * aplicación; sólo puede consumirse desde el boundary de composición
 * (`composition`) y los entrypoints de plataforma (`MainActivity`,
 * instrumented tests). Ninguna pantalla ni ViewModel puede importarlo: las
 * dependencias llegan explícitamente desde `AppGraph`/feature graphs.
 */
class CompositionBoundaryArchitectureTest {
    @Test
    fun `screens y viewmodels no importan DependencyContainer`() {
        val violations =
            kotlinFiles()
                .filter { file ->
                    val name = file.nameWithoutExtension
                    name.endsWith("Screen") || name.endsWith("ViewModel")
                }.filter { it.readText().contains(CONTAINER_IMPORT) }
                .map { it.toRelativeString(KOTLIN_ROOT) }

        assertTrue(
            buildString {
                appendLine("Pantallas/ViewModels que acceden DependencyContainer (usar AppGraph/composition):")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty(),
        )
    }

    @Test
    fun `el resto del paquete ui tampoco consume el service locator`() {
        val violations =
            kotlinFiles()
                .filter { file ->
                    file.parentFile
                        ?.toRelativeString(
                            KOTLIN_ROOT,
                        )?.startsWith("com${File.separator}amaxonia${File.separator}pos${File.separator}ui") ==
                        true
                }.filter { it.readText().contains(CONTAINER_IMPORT) }
                .map { it.toRelativeString(KOTLIN_ROOT) }

        assertTrue(
            buildString {
                appendLine("Archivos bajo ui/ que importan DependencyContainer (sólo composition/entrypoints pueden):")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty(),
        )
    }

    @Test
    fun `ui no importa implementaciones de data`() {
        val violations =
            kotlinFiles()
                .filter { file ->
                    file.parentFile
                        ?.toRelativeString(
                            KOTLIN_ROOT,
                        )?.startsWith("com${File.separator}amaxonia${File.separator}pos${File.separator}ui") ==
                        true
                }.flatMap { file ->
                    file
                        .readText()
                        .lines()
                        .filter { it.startsWith(DATA_IMPORT_PREFIX) }
                        .map { "${file.toRelativeString(KOTLIN_ROOT)}: ${it.trim()}" }
                }

        assertTrue(
            buildString {
                appendLine("Archivos bajo ui/ que importan data/ (la dirección canónica es ui -> domain):")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty(),
        )
    }

    /**
     * FASE 11 — domain !-> data: el dominio no conoce la capa de datos.
     *
     * Excepciones allow-listadas (deuda documentada, NO precedentes): los
     * use cases de la cola durable fiscal/gateway consumen el seam
     * [com.amaxonia.pos.data.local.db.TransactionLogDao] por diseño
     * (TASK-082/083); migrarlos exige una TASK funcional.
     */
    @Test
    fun `domain no importa data salvo la cola durable allow-listada`() {
        val violations =
            kotlinFiles()
                .filter { isUnderDomain(it) }
                .flatMap { file ->
                    val rel = relPath(file)
                    file
                        .readText()
                        .lines()
                        .filter { it.startsWith(DATA_IMPORT_PREFIX) }
                        .map { "$rel: ${it.trim()}" }
                }.filterNot { line ->
                    DOMAIN_DATA_ALLOW_LIST.any { allowed -> line.startsWith(allowed) }
                }

        assertTrue(
            buildString {
                appendLine("Archivos bajo domain/ que importan data/ sin estar allow-listados:")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty(),
        )
    }

    /** FASE 11 — domain !-> ui: regla limpia, sin excepciones. */
    @Test
    fun `domain no importa ui`() {
        val violations =
            kotlinFiles()
                .filter { isUnderDomain(it) }
                .flatMap { file ->
                    file
                        .readText()
                        .lines()
                        .filter { it.startsWith(UI_IMPORT_PREFIX) }
                        .map { "${relPath(file)}: ${it.trim()}" }
                }

        assertTrue(
            buildString {
                appendLine("Archivos bajo domain/ que importan ui/:")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty(),
        )
    }

    /**
     * FASE 11 — las implementaciones de repositorio viven en data/
     * (la dirección canónica es data -> domain; un *RepositoryImpl fuera de
     * data/ crea una segunda capa de implementación huérfana).
     */
    @Test
    fun `las implementaciones de repositorio viven en data`() {
        val violations =
            kotlinFiles()
                .filter { REPOSITORY_IMPL.containsMatchIn(it.readText()) }
                .filterNot { file -> relPath(file).contains("/data/") }
                .map { relPath(it) }

        assertTrue(
            buildString {
                appendLine("*RepositoryImpl fuera de la capa data/:")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty(),
        )
    }

    private fun relPath(file: File): String = file.toRelativeString(KOTLIN_ROOT).replace(File.separatorChar, '/')

    private fun isUnderDomain(file: File): Boolean = relPath(file).startsWith("com/amaxonia/pos/domain/")

    private fun kotlinFiles(): List<File> =
        KOTLIN_ROOT
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private companion object {
        val KOTLIN_ROOT: File = File("src/main/java").absoluteFile
        const val CONTAINER_IMPORT = "import com.amaxonia.pos.composition.DependencyContainer"
        const val DATA_IMPORT_PREFIX = "import com.amaxonia.pos.data."
        const val UI_IMPORT_PREFIX = "import com.amaxonia.pos.ui."
        val REPOSITORY_IMPL = Regex("""class\s+\w*RepositoryImpl\b""")

        /** Cola durable fiscal/gateway: seam DAO permitido en dominio (TASK-082/083). */
        val DOMAIN_DATA_ALLOW_LIST =
            listOf(
                "com/amaxonia/pos/domain/usecase/payment/AdvanceFiscalStateUseCase.kt",
                "com/amaxonia/pos/domain/usecase/payment/QueueFiscalConfirmationUseCase.kt",
                "com/amaxonia/pos/domain/usecase/payment/QueueGatewayCallbackUseCase.kt",
                "com/amaxonia/pos/domain/usecase/payment/StartTransactionUseCase.kt",
            )
    }
}
