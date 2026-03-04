package com.amaxoniaerp.features.sales.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import org.jetbrains.exposed.sql.Database

class ProcessSaleUseCase(
    private val repository: ProcessSaleTransactionalRepository,
) {
    suspend fun execute(database: Database, request: ProcessSaleRequest): ProcessSaleResponse {
        if (request.items.isEmpty()) {
            throw InvalidSaleRequestException("La factura debe contener al menos un item")
        }

        return dbQuery(database) {
            repository.process(request)
        }
    }
}
