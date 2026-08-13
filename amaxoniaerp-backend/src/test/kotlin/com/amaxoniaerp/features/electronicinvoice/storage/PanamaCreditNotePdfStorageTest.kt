package com.amaxoniaerp.features.electronicinvoice.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

class PanamaCreditNotePdfStorageTest {

    private val roots = mutableListOf<Path>()

    @After
    fun tearDown() {
        roots.forEach { root ->
            if (Files.exists(root)) {
                Files.walk(root).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
    }

    @Test
    fun `stores PDF bytes and creates missing directories`() {
        val root = Files.createTempDirectory("credit-note-pdf-").also(roots::add)
        val storage = FileSystemPanamaCreditNotePdfStorage(root.toString())
        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)

        val target = storage.store("company_pa", "note-1", "0000000001", bytes).getOrThrow()

        assertTrue(Files.exists(target))
        assertContentEquals(bytes, target.readBytes())
        assertTrue(target.toString().contains("documentos_fiscales"))
    }

    @Test
    fun `retry replaces the previous PDF atomically`() {
        val root = Files.createTempDirectory("credit-note-pdf-retry-").also(roots::add)
        val storage = FileSystemPanamaCreditNotePdfStorage(root.toString())

        val first = storage.store("company_pa", "note-2", "0000000002", byteArrayOf(1, 2)).getOrThrow()
        val second = storage.store("company_pa", "note-2", "0000000002", byteArrayOf(3, 4, 5)).getOrThrow()

        assertTrue(first == second)
        assertContentEquals(byteArrayOf(3, 4, 5), second.readBytes())
    }

    @Test
    fun `rejects path traversal segments`() {
        val root = Files.createTempDirectory("credit-note-pdf-safe-").also(roots::add)
        val storage = FileSystemPanamaCreditNotePdfStorage(root.toString())

        val result = storage.store("../outside", "note-3", "0000000003", byteArrayOf(1))

        assertTrue(result.isFailure)
        assertFalse(Files.exists(root.parent.resolve("outside")))
    }
}
