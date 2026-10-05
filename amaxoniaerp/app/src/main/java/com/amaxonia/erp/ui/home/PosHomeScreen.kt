package com.amaxonia.erp.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.erp.R
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.CompanySummary
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.theme.PosExtraShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosHomeScreen(
    session: AuthSession,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideLandscape = isLandscape && configuration.screenWidthDp >= 600
    val isNarrow = configuration.screenWidthDp < 420

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.brand_mark),
                            contentDescription = null,
                            modifier = Modifier.size(if (isNarrow) 30.dp else 36.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(modifier = Modifier.width(if (isNarrow) 8.dp else 12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isNarrow) 14.sp else 16.sp,
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(7.dp)
                                            .background(Color(0xFF22C55E), CircleShape),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.terminal_active),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = onLogout,
                        shape = PosExtraShapes.InputRadius,
                        contentPadding =
                            if (isNarrow) {
                                PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            } else {
                                ButtonDefaults.ContentPadding
                            },
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = if (isNarrow) 8.dp else 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.logout),
                            modifier = Modifier.size(18.dp),
                        )
                        if (!isNarrow) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.logout), fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { paddingValues ->
        if (isWideLandscape) {
            // Diseño Horizontal
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(if (configuration.screenHeightDp < 500) 12.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (configuration.screenHeightDp < 500) 14.dp else 24.dp),
            ) {
                SessionStatusCard(
                    session = session,
                    isScrollable = true,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )

                CompaniesCard(
                    companies = session.companies,
                    isScrollableList = true,
                    modifier =
                        Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                )
            }
        } else {
            // Diseño Vertical
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SessionStatusCard(
                        session = session,
                        isScrollable = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    CompaniesCard(
                        companies = session.companies,
                        isScrollableList = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatusCard(
    session: AuthSession,
    isScrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = PosExtraShapes.FeaturedCardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val contentModifier =
            if (isScrollable) {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            }

        Column(
            modifier = contentModifier,
            verticalArrangement = if (isScrollable) Arrangement.SpaceBetween else Arrangement.Top,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.welcome_user, session.user.username),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Rol: ${session.user.role.uppercase()} • ID: ${session.user.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                PosFeedbackCard(
                    title = "Autenticación Exitosa con Backend ListoERP",
                    message = "El token de sesión ha sido emitido y guardado de manera segura en DataStore.",
                    tone = PosVisualTone.Success,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Módulo de Punto de Venta (POS)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Esta aplicación está configurada para terminales de venta. Próximos pasos a integrar: Apertura de Caja, Catálogo de Productos y Facturación Rápida.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Token: ${session.token.take(24)}...",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun CompaniesCard(
    companies: List<CompanySummary>,
    isScrollableList: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = PosExtraShapes.FeaturedCardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Empresas Habilitadas (${companies.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (companies.isEmpty()) {
                Text(
                    text = "No se encontraron empresas adicionales asociadas a esta cuenta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (isScrollableList) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(companies) { company ->
                        CompanyItem(company)
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    companies.forEach { company ->
                        CompanyItem(company)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyItem(company: CompanySummary) {
    Surface(
        shape = PosExtraShapes.CardRadius,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = company.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            company.rif?.let { rif ->
                Text(
                    text = "RIF / RUC: $rif",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
