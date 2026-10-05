package com.amaxonia.erp.ui.login

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.amaxonia.erp.R
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.ui.components.PosGradientButton
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosPalette

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onCompanySessionReady: (CompanySession) -> Unit,
    onRequiresCompanySelection: (AuthSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnCompanySessionReady by rememberUpdatedState(onCompanySessionReady)
    val currentOnRequiresCompanySelection by rememberUpdatedState(onRequiresCompanySelection)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginUiEffect.CompanySessionReady -> currentOnCompanySessionReady(effect.companySession)
                is LoginUiEffect.RequiresCompanySelection -> currentOnRequiresCompanySelection(effect.session)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            LoginForm(
                state = state,
                onAction = viewModel::onAction,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = isLandscape()

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (isLandscape) 12.dp else 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 12.dp))

        // Logo oficial estilizado de marca (compacto en landscape)
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = stringResource(R.string.brand_logo_description),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 48.dp else 88.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

        Text(
            text = stringResource(R.string.login_title),
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (isLandscape) 22.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (isLandscape) 12.sp else 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 20.dp))

        LoginCredentialsCard(state, onAction, isLandscape = isLandscape)
    }
}

@Composable
private fun LoginCredentialsCard(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    isLandscape: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PosExtraShapes.FeaturedCardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 16.dp else 22.dp),
        ) {
            UsernameField(state.username) { onAction(LoginUiAction.UsernameChanged(it)) }

            Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 18.dp))

            PasswordField(
                value = state.password,
                visible = state.isPasswordVisible,
                onValueChange = { onAction(LoginUiAction.PasswordChanged(it)) },
                onToggleVisibility = { onAction(LoginUiAction.TogglePasswordVisibility) },
                onSubmit = { onAction(LoginUiAction.Submit) },
            )

            // Mensaje de Error animado
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                state.errorMessage?.let { errorText ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onAction(LoginUiAction.DismissError) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón con gradiente fluido de marca sin sombras cuadradas
            LoginSubmitButton(state.isLoading) { onAction(LoginUiAction.Submit) }

            if (state.loadingMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.loadingMessage!!,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {},
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_forgot_password),
                    color = MaterialTheme.colorScheme.secondary,
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
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.login_username_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
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
    Spacer(modifier = Modifier.height(8.dp))
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.login_password_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    PosGradientButton(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(18.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = PosPalette.FixedWhite,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.login_submit),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PosPalette.FixedWhite,
            )
        }
    }
}

@Composable
private fun loginFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
    )
