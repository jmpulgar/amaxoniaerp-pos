package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Transaction

interface TransactionRepository {
    suspend fun getAllTransactions(): Result<List<Transaction>>

    suspend fun getTransactionById(id: String): Result<Transaction>

    suspend fun saveTransaction(transaction: Transaction): Result<Unit>
}
