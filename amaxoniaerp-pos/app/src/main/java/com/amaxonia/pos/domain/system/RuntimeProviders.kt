package com.amaxonia.pos.domain.system

import java.time.Clock
import java.time.Instant
import java.util.UUID

fun interface AppClock {
    fun now(): Instant
}

fun interface IdGenerator {
    fun nextId(): String
}

class SystemAppClock(
    private val clock: Clock = Clock.systemUTC(),
) : AppClock {
    override fun now(): Instant = clock.instant()
}

object UuidGenerator : IdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
