package com.amaxonia.pos.core.result

/**
 * Converts the same [Exception] boundary previously handled by repeated try/catch
 * blocks while allowing fatal JVM errors to propagate.
 */
suspend fun <T> catchingResult(block: suspend () -> Result<T>): Result<T> {
    val attempt = runCatching { block() }
    return attempt.fold(
        onSuccess = { it },
        onFailure = { failure ->
            if (failure is Exception) Result.failure(failure) else throw failure
        },
    )
}
