package com.amaxonia.pos.domain.system

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.util.UUID

fun interface AppClock {
    fun now(): Instant
}

fun interface IdGenerator {
    fun nextId(): String
}

interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class SystemAppClock(
    private val clock: Clock = Clock.systemUTC(),
) : AppClock {
    override fun now(): Instant = clock.instant()
}

object UuidGenerator : IdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}

class DefaultDispatcherProvider(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DispatcherProvider {
    override val io: CoroutineDispatcher get() = ioDispatcher
    override val default: CoroutineDispatcher get() = defaultDispatcher
}
