package com.amaxonia.pos.ui.history

import com.amaxonia.pos.domain.model.Transaction

data class HistoryState(
    val isLoading: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val error: String? = null
)
