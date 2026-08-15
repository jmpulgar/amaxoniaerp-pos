package com.amaxoniaerp.features.electronicinvoice.storage

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

interface PanamaCreditNotePdfStorage {
    fun store(
        companyDb: String,
        creditNoteId: String,
        numeroDocumentoFiscal: String,
        bytes: ByteArray,
    ): Result<Path>
}

class FileSystemPanamaCreditNotePdfStorage(
    dataBasePath: String,
) : PanamaCreditNotePdfStorage {
    private val root: Path = Path.of(dataBasePath).toAbsolutePath().normalize()

    override fun store(
        companyDb: String,
        creditNoteId: String,
        numeroDocumentoFiscal: String,
        bytes: ByteArray,
    ): Result<Path> =
        runCatching {
            val safeCompanyDb = safeSegment(companyDb, "companyDb")
            val safeCreditNoteId = safeSegment(creditNoteId, "creditNoteId")
            val safeFiscalNumber = safeSegment(numeroDocumentoFiscal, "numeroDocumentoFiscal")
            val directory =
                root
                    .resolve(safeCompanyDb)
                    .resolve("documentos_fiscales")
                    .resolve("notas_credito")
                    .resolve(safeCreditNoteId)
                    .normalize()
            require(directory.startsWith(root)) { "Ruta de documento fiscal inválida" }

            Files.createDirectories(directory)
            val target = directory.resolve("$safeFiscalNumber.pdf").normalize()
            require(target.startsWith(root)) { "Ruta de documento fiscal inválida" }

            val temporary = directory.resolve(".${target.fileName}.${UUID.randomUUID()}.tmp")
            try {
                Files.write(temporary, bytes)
                try {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            target
        }

    private fun safeSegment(
        value: String,
        field: String,
    ): String {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "$field requerido" }
        require(normalized.matches(SAFE_SEGMENT)) { "$field inválido" }
        return normalized
    }

    private companion object {
        val SAFE_SEGMENT = Regex("[A-Za-z0-9_-]+")
    }
}
