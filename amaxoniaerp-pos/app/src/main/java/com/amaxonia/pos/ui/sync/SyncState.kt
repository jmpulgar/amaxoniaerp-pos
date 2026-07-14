package com.amaxonia.pos.ui.sync

data class SyncState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCompleted: Boolean = false,
)
