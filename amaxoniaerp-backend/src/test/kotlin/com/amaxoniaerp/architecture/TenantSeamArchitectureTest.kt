package com.amaxoniaerp.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Congela el seam canónico de tenant (`CompanyRequestContext`).
 *
 * Regla: la resolución de tenant (claims `token_type`, `admin_db`, `country_code`,
 * `schema_type`, header `Company-DB` y acceso a `JWTPrincipal`) vive únicamente en
 * `core/tenant`. La única excepción es `features/auth`, que emite/lee los tokens de
 * identidad antes de existir cualquier contexto de empresa.
 *
 * Cualquier feature que duplique esa resolución rompe este test.
 */
class TenantSeamArchitectureTest {
    @Test
    fun `tenant claims y header se resuelven solo en core tenant o auth`() {
        val violations =
            mainKotlinFiles().flatMap { file ->
                val relative = file.toRelativeString(KOTLIN_ROOT)
                val text = file.readText()
                if (isSeamOrAuthFile(relative)) {
                    emptyList()
                } else {
                    findViolations(text).map { "$relative: $it" }
                }
            }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Resolución de tenant fuera de core/tenant y features/auth:")
                violations.forEach { appendLine("  - $it") }
            },
        )
    }

    @Test
    fun `getAdminDb getCountryCode y getSchemaType no se importan desde features`() {
        val violations =
            mainKotlinFiles()
                .map { it.toRelativeString(KOTLIN_ROOT) }
                .filter { it.startsWith("com${File.separator}amaxoniaerp${File.separator}features${File.separator}") }
                .filterNot { isSeamOrAuthFile(it) }
                .flatMap { relative ->
                    val text = File(KOTLIN_ROOT, relative).readText()
                    listOf(
                        "getAdminDb()" to "core.tenant.getAdminDb",
                        "getCountryCode()" to "core.tenant.getCountryCode",
                        "getSchemaType()" to "core.tenant.getSchemaType",
                    ).filter { (symbol, _) -> text.contains(symbol) }
                        .map { (_, seamRef) -> "$relative importa $seamRef" }
                }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Features accediendo a claims de tenant por la extensión del seam:")
                violations.forEach { appendLine("  - $it") }
            },
        )
    }

    private fun findViolations(text: String): List<String> =
        listOf(
            "principal<JWTPrincipal>()" to "acceso directo a JWTPrincipal",
            """payload.getClaim("token_type")""" to "claim token_type",
            """payload.getClaim("admin_db")""" to "claim admin_db",
            """payload.getClaim("country_code")""" to "claim country_code",
            """payload.getClaim("schema_type")""" to "claim schema_type",
            """headers["Company-DB"]""" to "header Company-DB",
        ).filter { (pattern, _) -> text.contains(pattern) }
            .map { (_, label) -> label }

    private fun isSeamOrAuthFile(relative: String): Boolean {
        val sep = File.separator
        return relative.startsWith("com${sep}amaxoniaerp${sep}core${sep}tenant") ||
            relative.startsWith("com${sep}amaxoniaerp${sep}features${sep}auth")
    }

    private fun mainKotlinFiles(): List<File> =
        KOTLIN_ROOT
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private companion object {
        val KOTLIN_ROOT: File = File("src/main/kotlin").absoluteFile
    }
}
