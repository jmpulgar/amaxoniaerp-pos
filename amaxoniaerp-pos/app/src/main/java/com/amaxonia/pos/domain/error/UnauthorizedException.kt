package com.amaxonia.pos.domain.error

sealed class AuthenticationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class UnauthorizedException(
    message: String,
) : AuthenticationException(message)

class AuthenticationConnectivityException(
    cause: Throwable,
) : AuthenticationException("No se pudo conectar al servidor", cause)

class UnexpectedAuthenticationException(
    message: String,
    cause: Throwable,
) : AuthenticationException(message, cause)
