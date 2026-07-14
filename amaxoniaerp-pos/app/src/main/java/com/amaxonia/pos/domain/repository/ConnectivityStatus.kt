package com.amaxonia.pos.domain.repository

fun interface ConnectivityStatus {
    fun isOnline(): Boolean
}
