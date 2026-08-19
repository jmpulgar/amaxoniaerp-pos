package com.amaxonia.pos.composition

import com.amaxonia.pos.domain.usecase.auth.AuthenticateUserUseCase
import com.amaxonia.pos.domain.usecase.auth.ConfigureLoginCountryUseCase
import com.amaxonia.pos.ui.login.LoginViewModel

/** Grafo del feature login (TASK-051/052): construcción del ViewModel en composition. */
object LoginGraph {
    fun loginViewModel(): LoginViewModel =
        LoginViewModel(
            AuthenticateUserUseCase(DependencyContainer.authRepository, DependencyContainer.localStore),
            ConfigureLoginCountryUseCase(DependencyContainer.serverEnvironment, DependencyContainer.localStore),
        )
}
