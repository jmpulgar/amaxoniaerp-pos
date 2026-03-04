package com.amaxoniaerp.core.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun <T> dbQuery(database: Database, block: () -> T): T =
    newSuspendedTransaction(Dispatchers.IO, database) { block() }
