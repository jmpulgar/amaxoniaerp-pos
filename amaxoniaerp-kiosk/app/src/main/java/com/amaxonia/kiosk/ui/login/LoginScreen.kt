package com.amaxonia.kiosk.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.BuildConfig
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.network.KioskCompanyDto
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.kioskPressable

private const val CHEVRON_OPEN_DEGREES = 180f

/** Callbacks of the stateless [LoginContent]; every one defaults to a no-op (screenshot tests use [NoOp]). */
interface LoginActions {
    fun onUsernameChanged(value: String) = Unit

    fun onPasswordChanged(value: String) = Unit

    fun onTogglePasswordVisibility() = Unit

    fun onCountrySelected(code: String) = Unit

    fun onServerUrlChanged(value: String) = Unit

    fun onToggleAdvanced() = Unit

    fun onSubmit() = Unit

    fun onCompanySelected(company: KioskCompanyDto) = Unit

    fun onBackToCredentials() = Unit

    companion object {
        val NoOp = object : LoginActions {}
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.LoggedIn -> onLoggedIn()
            }
        }
    }
    LoginContent(
        uiState = uiState,
        actions =
            object : LoginActions {
                override fun onUsernameChanged(value: String) = viewModel.onUsernameChanged(value)

                override fun onPasswordChanged(value: String) = viewModel.onPasswordChanged(value)

                override fun onTogglePasswordVisibility() = viewModel.onTogglePasswordVisibility()

                override fun onCountrySelected(code: String) = viewModel.onCountrySelected(code)

                override fun onServerUrlChanged(value: String) = viewModel.onServerUrlChanged(value)

                override fun onToggleAdvanced() = viewModel.onToggleAdvanced()

                override fun onSubmit() {
                    focusManager.clearFocus()
                    viewModel.submit()
                }

                override fun onCompanySelected(company: KioskCompanyDto) {
                    viewModel.selectCompany(company)
                }

                override fun onBackToCredentials() = viewModel.onBackToCredentials()
            },
        modifier = modifier,
    )
}

/** Stateless login: credentials form, or the company list when the user has several companies. */
@Composable
fun LoginContent(
    uiState: LoginUiState,
    actions: LoginActions,
    modifier: Modifier = Modifier,
) {
    val isCompanyStep = uiState.step == LoginStep.COMPANY
    SetupScaffold(
        title = stringResource(if (isCompanyStep) R.string.login_company_title else R.string.login_hero_title),
        subtitle = stringResource(if (isCompanyStep) R.string.login_company_subtitle else R.string.login_hero_subtitle),
        modifier = modifier,
        footer = {
            Text(
                text = stringResource(R.string.login_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "login_step",
        ) { step ->
            Column {
                when (step) {
                    LoginStep.CREDENTIALS -> CredentialsForm(uiState, actions)
                    LoginStep.COMPANY -> CompanyList(uiState, actions)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CredentialsForm(
    uiState: LoginUiState,
    actions: LoginActions,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Public,
            contentDescription = stringResource(R.string.login_country),
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))
        SetupPillToggle(
            options =
                listOf(
                    "PA" to stringResource(R.string.login_country_pa),
                    "VE" to stringResource(R.string.login_country_ve),
                ).filter { (code, _) -> code in LOGIN_COUNTRIES },
            selected = uiState.countryCode,
            onSelect = actions::onCountrySelected,
            enabled = !uiState.isLoading,
        )
    }
    Spacer(Modifier.height(40.dp))

    SetupErrorBanner(message = uiState.error?.let { loginErrorText(it) })

    SetupTextField(
        value = uiState.username,
        onValueChange = actions::onUsernameChanged,
        label = stringResource(R.string.login_username),
        leadingIcon = Icons.Rounded.Person,
        enabled = !uiState.isLoading,
        isError = uiState.error.isCredentialError(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrect = false),
    )
    Spacer(Modifier.height(24.dp))
    SetupTextField(
        value = uiState.password,
        onValueChange = actions::onPasswordChanged,
        label = stringResource(R.string.login_password),
        leadingIcon = Icons.Rounded.Lock,
        enabled = !uiState.isLoading,
        isError = uiState.error.isCredentialError(),
        visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
        trailingIcon = {
            IconButton(onClick = actions::onTogglePasswordVisibility, modifier = Modifier.size(KioskTouchTarget)) {
                Icon(
                    imageVector = if (uiState.isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription =
                        stringResource(if (uiState.isPasswordVisible) R.string.login_hide_password else R.string.login_show_password),
                    modifier = Modifier.size(36.dp),
                )
            }
        },
    )
    Spacer(Modifier.height(40.dp))

    if (uiState.isLoading) {
        LoadingPill(text = stringResource(R.string.login_loading))
    } else {
        KioskButton(
            text = stringResource(R.string.login_submit),
            onClick = actions::onSubmit,
            icon = Icons.AutoMirrored.Rounded.Login,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(28.dp))
    AdvancedOptions(uiState, actions)
}

@Composable
private fun AdvancedOptions(
    uiState: LoginUiState,
    actions: LoginActions,
) {
    val colors = MaterialTheme.colorScheme
    val chevron by animateFloatAsState(if (uiState.showAdvanced) CHEVRON_OPEN_DEGREES else 0f, label = "advanced_chevron")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = KioskTouchTarget)
                .kioskPressable(pressedScale = 0.98f, onClick = actions::onToggleAdvanced),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = stringResource(R.string.login_advanced), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(36.dp).rotate(chevron),
        )
    }
    AnimatedVisibility(visible = uiState.showAdvanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            SetupTextField(
                value = uiState.serverUrl,
                onValueChange = actions::onServerUrlChanged,
                label = stringResource(R.string.login_server_url),
                leadingIcon = Icons.Rounded.Dns,
                enabled = !uiState.isLoading,
                isError = uiState.error == LoginError.MissingServerUrl,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrect = false),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            )
            Text(
                text = stringResource(R.string.login_server_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.CompanyList(
    uiState: LoginUiState,
    actions: LoginActions,
) {
    SetupErrorBanner(message = uiState.error?.let { loginErrorText(it) })
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        uiState.companies.forEach { company ->
            CompanyCard(
                company = company,
                loading = uiState.selectingCompanyId == company.id,
                enabled = uiState.selectingCompanyId == null,
                onClick = { actions.onCompanySelected(company) },
            )
        }
    }
    Spacer(Modifier.height(36.dp))
    KioskButton(
        text = stringResource(R.string.login_company_back),
        onClick = actions::onBackToCredentials,
        style = KioskButtonStyle.Secondary,
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        height = KioskTouchTarget,
        enabled = uiState.selectingCompanyId == null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CompanyCard(
    company: KioskCompanyDto,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    KioskCard(
        modifier = Modifier.fillMaxWidth(),
        selected = loading,
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Apartment, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.width(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!company.rif.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.login_company_rif, company.rif),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 6.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

/** Disabled-looking CTA with a spinner while a request runs. */
@Composable
fun LoadingPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(colors.primaryContainer, RoundedCornerShape(percent = 50)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(52.dp), strokeWidth = 6.dp, color = colors.primary)
        Spacer(Modifier.width(24.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
    }
}

private fun LoginError?.isCredentialError(): Boolean = this == LoginError.MissingCredentials || this == LoginError.InvalidCredentials

@Composable
private fun loginErrorText(error: LoginError): String =
    when (error) {
        LoginError.MissingCredentials -> stringResource(R.string.login_error_missing)
        LoginError.MissingServerUrl -> stringResource(R.string.login_error_server_url)
        LoginError.InvalidCredentials -> stringResource(R.string.login_error_invalid)
        LoginError.Connectivity -> stringResource(R.string.login_error_connectivity)
        LoginError.NoCompanies -> stringResource(R.string.login_error_no_companies)
        is LoginError.Unexpected -> error.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.login_error_unexpected)
    }
