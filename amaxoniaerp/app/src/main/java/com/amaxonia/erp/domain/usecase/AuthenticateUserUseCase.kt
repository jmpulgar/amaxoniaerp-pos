package com.amaxonia.erp.domain.usecase

import com.amaxonia.erp.domain.model.LoginCredentials
import com.amaxonia.erp.domain.model.LoginError
import com.amaxonia.erp.domain.model.LoginResult
import com.amaxonia.erp.domain.repository.AuthRepository

class AuthenticateUserUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(credentials: LoginCredentials): LoginResult {
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            return LoginResult.Failure(LoginError.MissingCredentials)
        }
        return authRepository
            .login(
                username = credentials.username.trim(),
                password = credentials.password,
                countryCode = credentials.country.code,
            ).fold(
                onSuccess = { session -> LoginResult.Success(session) },
                onFailure = { error ->
                    val loginError =
                        when (error) {
                            is UnauthorizedException -> LoginError.Unauthorized
                            is ConnectivityException -> LoginError.Connectivity
                            else -> LoginError.Unexpected(error.message)
                        }
                    LoginResult.Failure(loginError)
                },
            )
    }
}

class UnauthorizedException(message: String = "Usuario o contraseña incorrectos") : Exception(message)

class ConnectivityException(message: String = "No se pudo conectar con el servidor", cause: Throwable? = null) :
    Exception(message, cause)
