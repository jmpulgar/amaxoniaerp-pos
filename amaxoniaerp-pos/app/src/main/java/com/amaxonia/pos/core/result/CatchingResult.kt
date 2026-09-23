package com.amaxonia.pos.core.result

import kotlinx.coroutines.CancellationException

/**
 * Converts the same [Exception] boundary previously handled by repeated try/catch
 * blocks while allowing fatal JVM errors to propagate.
 */
suspend fun <T> catchingResult(block: suspend () -> Result<T>): Result<T> {
    val attempt = runCatching { block() }
    return attempt.fold(
        onSuccess = { it },
        onFailure = { failure ->
            if (failure is CancellationException) throw failure
            if (failure is Exception) Result.failure(failure) else throw failure
        },
    )
}
