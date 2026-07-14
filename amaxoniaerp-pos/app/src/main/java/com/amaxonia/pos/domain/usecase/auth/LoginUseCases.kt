package com.amaxonia.pos.domain.usecase.auth

import com.amaxonia.pos.domain.error.AuthenticationConnectivityException
import com.amaxonia.pos.domain.error.UnauthorizedException
import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.domain.repository.CountrySelectionStore
import com.amaxonia.pos.domain.repository.ServerEnvironment

data class LoginCredentials(
    val username: String,
    val password: String,
    val country: ServerCountry,
)

sealed interface LoginResult {
    data class Success(
        val session: AuthSession,
    ) : LoginResult

    data class Failure(
        val error: LoginError,
    ) : LoginResult
}

sealed interface LoginError {
    data object MissingCredentials : LoginError

    data object Unauthorized : LoginError

    data object Connectivity : LoginError

    data class Unexpected(
        val message: String?,
    ) : LoginError
}

class AuthenticateUserUseCase(
    private val authRepository: AuthRepository,
    private val countrySelectionStore: CountrySelectionStore,
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
                onSuccess = { session ->
                    countrySelectionStore.saveSelectedCountry(credentials.country)
                    LoginResult.Success(session)
                },
                onFailure = { error -> LoginResult.Failure(error.toLoginError()) },
            )
    }
}

class ConfigureLoginCountryUseCase(
    private val serverEnvironment: ServerEnvironment,
    private val countrySelectionStore: CountrySelectionStore,
) {
    fun select(country: ServerCountry) {
        serverEnvironment.selectCountry(country)
    }

    suspend fun restore(defaultCountry: ServerCountry): ServerCountry {
        val country = countrySelectionStore.readSelectedCountry() ?: defaultCountry
        select(country)
        return country
    }
}

private fun Throwable.toLoginError(): LoginError =
    when (this) {
        is UnauthorizedException -> LoginError.Unauthorized
        is AuthenticationConnectivityException -> LoginError.Connectivity
        else -> LoginError.Unexpected(message)
    }
