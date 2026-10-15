package com.amaxonia.erp.ui.login

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.R
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.ui.components.isCompactHeight
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.components.screenHeightDp
import com.amaxonia.erp.ui.components.screenWidthDp
import com.amaxonia.erp.ui.theme.PosPalette
import com.amaxonia.erp.ui.util.KeyboardHelper
import com.amaxonia.erp.ui.util.forceShowKeyboardOnTouch

// Colores oficiales del nuevo diseño web Amaxonia Administrativo
private val WebCanvasBg = Color(0xFFF4F5F8)
private val WebCardLine = Color(0xFFE4E9F0)
private val WebInputBg = Color(0xFFFAFBFD)
private val WebTextPrimary = Color(0xFF121212)
private val WebTextSecondary = Color(0xFF475569)
private val WebTextMuted = Color(0xFF94A3B8)
private val WebBrand = Color(0xFF2488FA)
private val WebBrandStrong = Color(0xFF201B82)
private val WebAccent = Color(0xFFA37EE3)
private val WebSun = Color(0xFFC9B0F5)
private val WebIceBlue = Color(0xFF8EC5FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onCompanySessionReady: (CompanySession) -> Unit,
    onRequiresCompanySelection: (AuthSession) -> Unit,
    onBack: (() -> Unit)? = null,
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

    val isLandscape = isLandscape()
    val screenWidth = screenWidthDp()
    val screenHeight = screenHeightDp()

    // En pantallas amplias horizontales (tablets o terminales POS), mostramos el layout 50/50 exacto
    val useSplitLayout = isLandscape && screenWidth >= 700.dp && screenHeight >= 460.dp

    if (useSplitLayout) {
        LoginSplitWebLayout(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = modifier,
        )
    } else {
        LoginSingleWebLayout(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

/**
 * Layout Split 50/50 exacto al del login de Amaxonia Administrativo web:
 * - Izquierda: Formulario en tarjeta blanca (con logo arriba y footer de seguridad abajo).
 * - Derecha: Imagen `login_banner.jpg` con degradado oscuro, chip de conexión segura y kicker.
 */
@Composable
private fun LoginSplitWebLayout(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        // PANEL IZQUIERDO: Formulario
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(WebCanvasBg)
                    .imePadding(),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                        tint = WebBrandStrong,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoginFormWithBranding(
                    state = state,
                    onAction = onAction,
                    isCompact = false,
                )
            }
        }

        // PANEL DERECHO: Banner visual con login_banner.jpg
        LoginRightBanner(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
    }
}

/**
 * Layout para teléfonos y tablets en vertical (Portrait):
 * Solo muestra el formulario de login centrado ergonómicamente con su logo y footer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginSingleWebLayout(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isCompact = isCompactHeight()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WebCanvasBg,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back),
                                tint = WebBrandStrong,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = WebCanvasBg),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            LoginFormWithBranding(
                state = state,
                onAction = onAction,
                isCompact = isCompact,
            )
        }
    }
}

/**
 * Bloque completo del panel izquierdo: Logo superior + Tarjeta blanca de 20dp + Footer de seguridad.
 */
@Composable
private fun LoginFormWithBranding(
    state: LoginState,
    onAction: (LoginUiAction) -> Unit,
    isCompact: Boolean,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo de Amaxonia centrado ARRIBA de la tarjeta
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = stringResource(R.string.brand_logo_description),
            modifier =
                Modifier
                    .widthIn(max = 230.dp)
                    .fillMaxWidth(0.65f)
                    .height(if (isCompact) 48.dp else 56.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 26.dp))

        // Tarjeta blanca (login-card-inner del diseño web)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PosPalette.FixedWhite),
            border = BorderStroke(1.dp, WebCardLine),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isCompact) 20.dp else 32.dp,
                            vertical = if (isCompact) 20.dp else 34.dp,
                        ),
            ) {
                // Header dentro de la tarjeta
                Text(
                    text = stringResource(R.string.login_title),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontSize = if (isCompact) 20.sp else 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                        ),
                    color = WebTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = if (isCompact) 12.5.sp else 14.sp,
                            lineHeight = 20.sp,
                        ),
                    color = WebTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(if (isCompact) 18.dp else 26.dp))

                // Campo Usuario
                WebLoginFieldLabel(stringResource(R.string.login_username_label))
                Spacer(modifier = Modifier.height(8.dp))
                val view = LocalView.current
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { onAction(LoginUiAction.UsernameChanged(it)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.login_username_placeholder),
                            color = WebTextMuted,
                            fontSize = 14.sp,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .forceShowKeyboardOnTouch(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = WebTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { KeyboardHelper.forceShow(view) }) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Abrir teclado",
                                tint = WebBrand,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    colors = webFieldColors(),
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 18.dp))

                // Campo Contraseña con fila de label + "¿Olvidó su contraseña?"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WebLoginFieldLabel(stringResource(R.string.login_password_label))

                    Text(
                        text = stringResource(R.string.login_forgot_password),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = WebBrandStrong,
                        modifier = Modifier.clickable { /* No-op o recuperación */ },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onAction(LoginUiAction.PasswordChanged(it)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.login_password_placeholder),
                            color = WebTextMuted,
                            fontSize = 14.sp,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .forceShowKeyboardOnTouch(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = WebTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onAction(LoginUiAction.Submit)
                            },
                        ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { KeyboardHelper.forceShow(view) }) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = "Abrir teclado",
                                    tint = WebBrand,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = { onAction(LoginUiAction.TogglePasswordVisibility) }) {
                                Icon(
                                    imageVector = if (state.isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription =
                                        stringResource(
                                            if (state.isPasswordVisible) R.string.hide_password else R.string.show_password,
                                        ),
                                    tint = WebTextMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    colors = webFieldColors(),
                )

                // Mensaje de Error
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    state.errorMessage?.let { errorText ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFB91C1C),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorText,
                                    color = Color(0xFFB91C1C),
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
                                        tint = Color(0xFFB91C1C),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 18.dp else 26.dp))

                // Botón Ingresar oficial con gradiente #2488FA -> #201B82 y flecha derecha
                Button(
                    onClick = { onAction(LoginUiAction.Submit) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    brush =
                                        Brush.linearGradient(
                                            colors = listOf(WebBrand, WebBrandStrong),
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = PosPalette.FixedWhite,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.login_submit),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        ),
                                    color = PosPalette.FixedWhite,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = PosPalette.FixedWhite,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                if (state.loadingMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.loadingMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = WebBrandStrong,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 20.dp))

        // Footer oficial: candado/escudo + Acceso exclusivo para personal autorizado
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = WebBrand,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.login_authorized_only),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                color = WebTextMuted,
            )
        }
    }
}

/**
 * Panel derecho de marca idéntico a `auth-right` en la web:
 * Utiliza `login_banner.jpg`, degradado en capas, chip superior "Conexión segura", y banner kicker.
 */
@Composable
private fun LoginRightBanner(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(Color(0xFF0F0C3D)),
    ) {
        // Imagen de fondo login_banner.jpg
        Image(
            painter = painterResource(R.drawable.login_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay 1: Gradiente vertical oscuro idéntico al CSS web
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color(0x330F0C3D), // rgba(15, 12, 61, .20)
                                        Color(0x730F0C3D), // rgba(15, 12, 61, .45)
                                        Color(0xF00F0C3D), // rgba(15, 12, 61, .94)
                                    ),
                            ),
                    ),
        )

        // Overlay 2: Gradiente suave diagonal de azul a púrpura
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0x472488FA), // rgba(36, 136, 250, .28)
                                        Color(0x1FA37EE3), // rgba(163, 126, 227, .12)
                                    ),
                            ),
                    ),
        )

        // Floating Chip superior: "Conexión segura" con punto pulsante
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = PosPalette.FixedWhite.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, PosPalette.FixedWhite.copy(alpha = 0.22f)),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFF22C55E), CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.login_secure_connection),
                    color = PosPalette.FixedWhite,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
            }
        }

        // Contenido inferior del banner
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 44.dp, end = 44.dp, bottom = 44.dp)
                    .widthIn(max = 560.dp),
        ) {
            // Kicker en cápsula con punto morado
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PosPalette.FixedWhite.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, PosPalette.FixedWhite.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .background(WebAccent, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_banner_kicker).uppercase(),
                        color = PosPalette.FixedWhite,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Headline con texto degradado celeste/lila
            Text(
                text =
                    buildAnnotatedString {
                        append(stringResource(R.string.login_banner_headline_start))
                        withStyle(
                            SpanStyle(
                                brush =
                                    Brush.linearGradient(
                                        colors = listOf(WebIceBlue, WebSun),
                                    ),
                            ),
                        ) {
                            append(stringResource(R.string.login_banner_headline_accent))
                        }
                    },
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                color = PosPalette.FixedWhite,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Descripción
            Text(
                text = stringResource(R.string.login_banner_description),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    ),
                color = PosPalette.FixedWhite.copy(alpha = 0.78f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature list pills con fondo translúcido
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LoginBannerFeaturePill(
                    icon = Icons.Default.Receipt,
                    text = stringResource(R.string.login_feature_billing),
                )
                LoginBannerFeaturePill(
                    icon = Icons.Default.PointOfSale,
                    text = stringResource(R.string.login_feature_caja),
                )
                LoginBannerFeaturePill(
                    icon = Icons.Default.Inventory2,
                    text = stringResource(R.string.login_feature_inventory),
                )
            }
        }
    }
}

@Composable
private fun LoginBannerFeaturePill(
    icon: ImageVector,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PosPalette.FixedWhite.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, PosPalette.FixedWhite.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WebIceBlue,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = text,
                color = PosPalette.FixedWhite,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    }
}

@Composable
private fun WebLoginFieldLabel(text: String) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        color = WebTextPrimary,
    )
}

@Composable
private fun webFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = WebBrand,
        unfocusedBorderColor = WebCardLine,
        focusedContainerColor = PosPalette.FixedWhite,
        unfocusedContainerColor = WebInputBg,
        focusedTextColor = WebTextPrimary,
        unfocusedTextColor = WebTextPrimary,
        focusedPlaceholderColor = WebTextMuted,
        unfocusedPlaceholderColor = WebTextMuted,
        focusedLeadingIconColor = WebBrand,
        unfocusedLeadingIconColor = WebTextMuted,
        cursorColor = WebBrand,
    )
