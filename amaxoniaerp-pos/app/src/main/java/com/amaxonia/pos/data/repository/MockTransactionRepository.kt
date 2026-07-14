package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.repository.TransactionRepository
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

class MockTransactionRepository : TransactionRepository {
    private val mockTransactions = mutableListOf<Transaction>()
    private val failureRate = 0.1

    init {
        generateMockTransactions()
    }

    override suspend fun getAllTransactions(): Result<List<Transaction>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar transacciones desde el servidor"))
        }
        return Result.success(mockTransactions.toList())
    }

    override suspend fun getTransactionById(id: String): Result<Transaction> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al obtener la transacción"))
        }
        val transaction = mockTransactions.find { it.id == id }
        return if (transaction != null) {
            Result.success(transaction)
        } else {
            Result.failure(Exception("Transacción no encontrada"))
        }
    }

    override suspend fun saveTransaction(transaction: Transaction): Result<Unit> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al guardar la transacción"))
        }
        mockTransactions.add(transaction)
        return Result.success(Unit)
    }

    private suspend fun simulateNetworkDelay() {
        delay((300..1000).random().toLong())
    }

    private fun shouldSimulateError(): Boolean = Random.nextFloat() < failureRate

    private fun generateMockTransactions() {
        mockTransactions.clear()
        val transactions =
            listOf(
                createMockTransaction("320.99", "10:00 AM", "Domingo, 02 Agosto 2020"),
                createMockTransaction("520.99", "05:00 PM", "Domingo, 02 Agosto 2020"),
                createMockTransaction("420.99", "09:00 PM", "Domingo, 02 Agosto 2020"),
                createMockTransaction("120.99", "11:00 AM", "Sábado, 03 Agosto 2020"),
                createMockTransaction("520.99", "10:00 AM", "Sábado, 03 Agosto 2020"),
                createMockTransaction("620.99", "08:00 AM", "Sábado, 03 Agosto 2020"),
            )
        mockTransactions.addAll(transactions)
    }

    private fun createMockTransaction(
        amount: String,
        time: String,
        date: String,
    ): Transaction =
        Transaction(
            id = UUID.randomUUID().toString(),
            invoiceNumber = "#TRX${(10000..99999).random()}",
            time = time,
            amount = amount.toDouble(),
            dateHeader = date,
            status = TransactionStatus.PAID,
        )
}
