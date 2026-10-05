package com.amaxonia.erp.ui.shell

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.erp.R
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.ui.components.PosStatusBadge
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isCompactHeight
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosPalette
import kotlinx.coroutines.launch

enum class ErpModule(
    val title: String,
    val icon: ImageVector,
) {
    POS("POS", Icons.Default.PointOfSale),
    CLIENTS("Clientes", Icons.Default.People),
    PRODUCTS("Productos", Icons.Default.ShoppingBag),
    SUCURSALES("Sucursales", Icons.Default.Storefront),
    CAJAS("Cajas", Icons.Default.AccountBalanceWallet),
    SETTINGS("Configuración", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    session: CompanySession,
    onLogout: () -> Unit,
    onChangeCompany: () -> Unit,
    modifier: Modifier = Modifier,
    activeSucursalName: String = "Sucursal Principal",
    activeCajaName: String = "Caja 01",
    isCajaOpen: Boolean = true,
    posContent: @Composable (onNavigateToCajas: () -> Unit) -> Unit,
    clientsContent: @Composable () -> Unit,
    productsContent: @Composable () -> Unit,
    sucursalesContent: @Composable () -> Unit,
    cajasContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentModule by remember { mutableStateOf(ErpModule.POS) }

    val isLandscape = isLandscape()
    val isCompact = isCompactHeight() || isLandscape

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.secondary,
                drawerContentColor = PosPalette.FixedWhite,
                modifier =
                    Modifier
                        .width(if (isLandscape) 280.dp else 300.dp)
                        .fillMaxHeight(),
            ) {
                // Contenido del Drawer con scroll unificado para evitar cualquier corte
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    SidebarHeader(
                        session = session,
                        activeSucursalName = activeSucursalName,
                        activeCajaName = activeCajaName,
                        isCompact = isCompact,
                        onCajaClick = {
                            currentModule = ErpModule.CAJAS
                            scope.launch { drawerState.close() }
                        },
                    )

                    HorizontalDivider(color = PosPalette.FixedWhite.copy(alpha = 0.2f))

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isCompact) 4.dp else 8.dp),
                    ) {
                        ErpModule.entries.forEach { module ->
                            val isSelected = currentModule == module
                            DrawerMenuItem(
                                icon = module.icon,
                                label = module.title,
                                isSelected = isSelected,
                                isCompact = isCompact,
                                onClick = {
                                    currentModule = module
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }

                        // Opción de cambio de empresa integrada en el menú
                        DrawerMenuItem(
                            icon = Icons.Default.SwapHoriz,
                            label = "Cambiar Empresa",
                            isSelected = false,
                            isCompact = isCompact,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onChangeCompany()
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))

                    // Botón blanco de cierre de sesión idéntico a amaxoniaerp-pos
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (isCompact) 14.dp else 24.dp),
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onLogout()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PosPalette.FixedWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (isCompact) 42.dp else 48.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Cerrar Sesión",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompact) 14.sp else 15.sp,
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (currentModule == ErpModule.POS) "Punto de Venta" else currentModule.title,
                                fontWeight = FontWeight.Bold,
                                style =
                                    if (isCompact) {
                                        MaterialTheme.typography.titleSmall
                                    } else {
                                        MaterialTheme.typography.titleMedium
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${session.company.name} • $activeSucursalName",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = if (isCompact) 10.sp else 11.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú principal",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    actions = {
                        // Badge interactivo de Caja Activa en píldora armonizada
                        PosStatusBadge(
                            label = if (isCajaOpen) activeCajaName else "$activeCajaName (Cerrada)",
                            tone = if (isCajaOpen) PosVisualTone.Success else PosVisualTone.Error,
                            modifier =
                                Modifier
                                    .padding(end = 12.dp)
                                    .clickable {
                                        currentModule = ErpModule.CAJAS
                                    },
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                when (currentModule) {
                    ErpModule.POS -> posContent { currentModule = ErpModule.CAJAS }
                    ErpModule.CLIENTS -> clientsContent()
                    ErpModule.PRODUCTS -> productsContent()
                    ErpModule.SUCURSALES -> sucursalesContent()
                    ErpModule.CAJAS -> cajasContent()
                    ErpModule.SETTINGS -> settingsContent()
                }
            }
        }
    }
}

@Composable
private fun SidebarHeader(
    session: CompanySession,
    activeSucursalName: String,
    activeCajaName: String,
    isCompact: Boolean = false,
    onCajaClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 14.dp else 24.dp),
    ) {
        // Fila 1: Logo en cápsula blanca, Nombre de la marca y Badge "Pro+"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(id = R.drawable.brand_mark),
                contentDescription = stringResource(R.string.brand_logo_description),
                modifier =
                    Modifier
                        .size(if (isCompact) 34.dp else 40.dp)
                        .background(PosPalette.FixedWhite, MaterialTheme.shapes.small)
                        .padding(1.dp)
                        .padding(end = 3.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.brand_name),
                fontSize = if (isCompact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = PosPalette.FixedWhite,
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = PosExtraShapes.Pill,
                color = MaterialTheme.colorScheme.tertiary,
            ) {
                Text(
                    text = "Pro+",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = PosPalette.FixedWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 20.dp))

        // Título de la sucursal activa
        Text(
            text = activeSucursalName,
            fontSize = if (isCompact) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color = PosPalette.FixedWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Subtexto Empresa y Usuario
        Text(
            text = "${session.company.name} • ${session.user.username}",
            fontSize = if (isCompact) 11.sp else 12.sp,
            color = PosPalette.FixedWhite.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 10.dp))

        // Selector / Tarjeta de Caja en blanco translúcido (alpha = 0.2f)
        Surface(
            color = PosPalette.FixedWhite.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onCajaClick() },
        ) {
            Row(
                modifier = Modifier.padding(if (isCompact) 10.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activeCajaName,
                    color = PosPalette.FixedWhite,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (isCompact) 13.sp else 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Cambiar caja",
                    tint = PosPalette.FixedWhite,
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    isCompact: Boolean = false,
    onClick: () -> Unit,
) {
    val backgroundModifier =
        if (isSelected) {
            Modifier.background(
                color = MaterialTheme.colorScheme.primary, // #201B82 Índigo Profundo
                shape = RoundedCornerShape(12.dp),
            )
        } else {
            Modifier
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 1.dp else 2.dp)
                .then(backgroundModifier)
                .clickable { onClick() }
                .padding(vertical = if (isCompact) 9.dp else 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PosPalette.FixedWhite,
            modifier = Modifier.size(if (isCompact) 20.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (isCompact) 12.dp else 16.dp))
        Text(
            text = label,
            color = PosPalette.FixedWhite,
            fontSize = if (isCompact) 14.sp else 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
