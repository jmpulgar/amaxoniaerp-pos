package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Transaction

interface PrinterRepository {
    suspend fun printReceipt(transaction: Transaction): Result<Boolean>
}
