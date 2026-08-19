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

    private fun kotlinFiles(): List<File> =
        KOTLIN_ROOT
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private companion object {
        val KOTLIN_ROOT: File = File("src/main/java").absoluteFile
        const val CONTAINER_IMPORT = "import com.amaxonia.pos.composition.DependencyContainer"
        const val DATA_IMPORT_PREFIX = "import com.amaxonia.pos.data."
    }
}
