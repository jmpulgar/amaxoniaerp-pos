package com.amaxonia.pos.ui.login

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.R
import com.amaxonia.pos.domain.usecase.auth.AuthenticateUserUseCase
import com.amaxonia.pos.domain.usecase.auth.ConfigureLoginCountryUseCase
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.login.components.CountrySelector
import com.amaxonia.pos.ui.theme.PosPalette

@Composable
fun LoginScreen(
    viewModel: LoginViewModel =
        injectedViewModel {
            LoginViewModel(
                AuthenticateUserUseCase(DependencyContainer.authRepository, DependencyContainer.localStore),
                ConfigureLoginCountryUseCase(DependencyContainer.serverEnvironment, DependencyContainer.localStore),
            )
        },
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)

    LaunchedEffect(viewModel) {
        viewModel.onAction(LoginUiAction.LoadSavedCountry)
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginUiEffect.LoginSucceeded -> currentOnLoginSuccess()
            }
        }
    }

    LoginContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginContent(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LoginTopBar(onBack) },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            LoginForm(
                state = state,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 22.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun LoginForm(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Spacer(modifier = Modifier.height(8.dp))
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = stringResource(R.string.brand_logo_description),
            modifier = Modifier.fillMaxWidth().height(128.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.login_country_region),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        CountrySelector(
            selectedCountry = state.selectedCountry,
            onCountrySelected = { onAction(LoginUiAction.CountryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        )
        Spacer(modifier = Modifier.height(18.dp))
        LoginCredentialsCard(state, onAction)
    }
}

@Composable
private fun LoginCredentialsCard(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            UsernameField(state.username) { onAction(LoginUiAction.UsernameChanged(it)) }
            Spacer(modifier = Modifier.height(18.dp))
            PasswordField(
                value = state.password,
                visible = state.isPasswordVisible,
                onValueChange = { onAction(LoginUiAction.PasswordChanged(it)) },
                onToggleVisibility = { onAction(LoginUiAction.TogglePasswordVisibility) },
                onSubmit = { onAction(LoginUiAction.Submit) },
            )
            state.errorMessage?.let { errorMessage ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(18.dp))
            LoginSubmitButton(state.isLoading) { onAction(LoginUiAction.Submit) }
            TextButton(
                onClick = {},
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_forgot_password),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    LoginFieldLabel(R.string.login_username_label)
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.login_username_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        colors = loginFieldColors(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    LoginFieldLabel(R.string.login_password_label)
    Spacer(modifier = Modifier.height(10.dp))
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.login_password_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            ),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription =
                        stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        colors = loginFieldColors(),
    )
}

@Composable
private fun LoginFieldLabel(
    @StringRes resourceId: Int,
) {
    Text(
        text = stringResource(resourceId),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LoginSubmitButton(
    loading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        enabled = !loading,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.login_submit),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun loginFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PosPalette.Transparent,
        unfocusedBorderColor = PosPalette.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        cursorColor = MaterialTheme.colorScheme.primary,
    )
