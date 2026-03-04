package com.amaxoniaerp.features.auth.domain

open class AuthenticationException(message: String) : RuntimeException(message)

open class AuthorizationException(message: String) : RuntimeException(message)

open class NotFoundException(message: String) : RuntimeException(message)
