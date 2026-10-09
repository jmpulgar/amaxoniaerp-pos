package com.amaxonia.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.amaxonia.erp.composition.DependencyContainer
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.ui.caja.CajasOverviewScreen
import com.amaxonia.erp.ui.clients.ClientListScreen
import com.amaxonia.erp.ui.company.CompanySelectionScreen
import com.amaxonia.erp.ui.history.HistoryScreen
import com.amaxonia.erp.ui.login.LoginScreen
import com.amaxonia.erp.ui.pos.PosTerminalScreen
import com.amaxonia.erp.ui.products.ProductListScreen
import com.amaxonia.erp.ui.settings.SettingsScreen
import com.amaxonia.erp.ui.shell.MainShellScreen
import com.amaxonia.erp.ui.sucursales.SucursalesScreen
import com.amaxonia.erp.ui.welcome.WelcomeScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.amaxonia.erp.ui.theme.PosTheme
import com.amaxonia.erp.ui.util.KeyboardHelper
import com.amaxonia.erp.data.sync.SyncScheduler
import com.amaxonia.erp.ui.offlinesettings.OfflineSettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) CrashLogger.setup(this)
        DependencyContainer.initialize(applicationContext)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SyncScheduler.schedulePeriodic(applicationContext)
            SyncScheduler.scheduleWeeklyReconcile(applicationContext)
        }
        enableEdgeToEdge()

        setContent {
            val defaultController = LocalSoftwareKeyboardController.current
            val view = LocalView.current
            val enhancedController = remember(defaultController, view) {
                object : SoftwareKeyboardController {
                    override fun show() {
                        defaultController?.show()
                        KeyboardHelper.forceShow(view)
                    }

                    override fun hide() {
                        defaultController?.hide()
                        KeyboardHelper.hide(view)
                    }
                }
            }

            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides enhancedController,
            ) {
                PosTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        PosAppContent()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DependencyContainer.customerDisplayManager.start(this)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            DependencyContainer.cajaRepository.restoreActiveCajaIfValid()
        }
    }

    override fun onStop() {
        DependencyContainer.customerDisplayManager.stop()
        super.onStop()
    }
}

@Composable
private fun PosAppContent() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var isLoadingSession by remember { mutableStateOf(true) }
    var currentCompanySession by remember { mutableStateOf<CompanySession?>(null) }
    var pendingSelectionSession by remember { mutableStateOf<AuthSession?>(null) }

    LaunchedEffect(currentCompanySession) {
        currentCompanySession?.let { session ->
            DependencyContainer.customerDisplayManager.updateCompanyInfo(
                companyName = session.company.name,
                countryCode = session.company.countryCode.orEmpty(),
                branchName = "",
            )
        }
    }

    LaunchedEffect(Unit) {
        val savedCompanySession = DependencyContainer.authRepository.getActiveCompanySession()
        if (savedCompanySession != null) {
            currentCompanySession = savedCompanySession
            DependencyContainer.cajaRepository.restoreActiveCajaIfValid()
        } else {
            val savedAuthSession = DependencyContainer.authRepository.getActiveSession()
            if (savedAuthSession != null) {
                if (savedAuthSession.companies.size == 1) {
                    val singleCompany = savedAuthSession.companies.first()
                    DependencyContainer.authRepository.selectCompany(singleCompany.id).fold(
                        onSuccess = { companySession ->
                            currentCompanySession = companySession
                            DependencyContainer.cajaRepository.restoreActiveCajaIfValid()
                        },
                        onFailure = {
                            pendingSelectionSession = savedAuthSession
                        },
                    )
                } else if (savedAuthSession.companies.size > 1) {
                    pendingSelectionSession = savedAuthSession
                }
            }
        }
        isLoadingSession = false
    }

    when {
        isLoadingSession -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        currentCompanySession != null -> {
            val currentSession = currentCompanySession!!
            val posViewModel = remember(currentSession) {
                DependencyContainer.createPosTerminalViewModel()
            }
            val clientListViewModel = remember(currentSession) {
                DependencyContainer.createClientListViewModel()
            }
            val productListViewModel = remember(currentSession) {
                DependencyContainer.createProductListViewModel()
            }
            val sucursalesViewModel = remember(currentSession) {
                DependencyContainer.createSucursalesViewModel()
            }
            val cajasOverviewViewModel = remember(currentSession) {
                DependencyContainer.createCajasOverviewViewModel()
            }
            val settingsViewModel = remember(currentSession) {
                DependencyContainer.createSettingsViewModel()
            }
            val offlineSettingsViewModel = remember(currentSession) {
                DependencyContainer.createOfflineSettingsViewModel()
            }
            val historyViewModel = remember(currentSession) {
                DependencyContainer.createHistoryViewModel(currentSession)
            }

            val isOnline by DependencyContainer.networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(
                initialValue = DependencyContainer.networkMonitor.isOnline(),
            )
            val activeCaja by DependencyContainer.cajaRepository.activeCaja.collectAsStateWithLifecycle()
            val activeCajaName by DependencyContainer.cajaRepository.activeCajaName.collectAsStateWithLifecycle()
            val activeCajaSecuencia by DependencyContainer.cajaRepository.activeCajaSecuencia.collectAsStateWithLifecycle()

            val sucursalNombre = activeCaja?.sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal Principal"
            val rawFecha = activeCajaSecuencia?.fechaApertura
            val formattedFecha = rawFecha?.let(com.amaxonia.erp.domain.util.CajaDateParser::formatDisplayDate)
            val isDiaAnterior = activeCajaSecuencia != null && com.amaxonia.erp.domain.util.CajaDateParser.isFromPreviousDay(rawFecha)
            val usuarioApertura = activeCajaSecuencia?.usuarioApertura

            MainShellScreen(
                session = currentSession,
                activeSucursalName = sucursalNombre,
                activeCajaName = activeCajaName,
                isCajaOpen = activeCajaSecuencia != null,
                isCajaDiaAnterior = isDiaAnterior,
                cajaFechaApertura = formattedFecha,
                usuarioApertura = usuarioApertura,
                isOnline = isOnline,
                onSyncManual = {
                    SyncScheduler.enqueueManual(context)
                },
                onLogout = {
                    scope.launch {
                        DependencyContainer.authRepository.logout()
                        currentCompanySession = null
                        pendingSelectionSession = null
                    }
                },
                onChangeCompany = {
                    scope.launch {
                        val auth = DependencyContainer.authRepository.getActiveSession()
                        DependencyContainer.localStore.clearCompanySession()
                        currentCompanySession = null
                        pendingSelectionSession = auth
                    }
                },
                posContent = { onNavigateToCajas, onOpenDrawer ->
                    PosTerminalScreen(
                        viewModel = posViewModel,
                        onNavigateToCajas = onNavigateToCajas,
                        onOpenDrawer = onOpenDrawer,
                    )
                },
                historyContent = {
                    HistoryScreen(viewModel = historyViewModel)
                },
                clientsContent = {
                    ClientListScreen(viewModel = clientListViewModel)
                },
                productsContent = {
                    ProductListScreen(viewModel = productListViewModel)
                },
                sucursalesContent = {
                    SucursalesScreen(viewModel = sucursalesViewModel)
                },
                cajasContent = {
                    CajasOverviewScreen(viewModel = cajasOverviewViewModel)
                },
                settingsContent = { onNavigateToVisibility ->
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToVisibilitySettings = onNavigateToVisibility,
                    )
                },
                offlineSettingsContent = { onBack ->
                    OfflineSettingsScreen(
                        viewModel = offlineSettingsViewModel,
                        onBack = onBack,
                    )
                },
            )
        }

        pendingSelectionSession != null -> {
            val companySelectionViewModel = remember(pendingSelectionSession) {
                DependencyContainer.createCompanySelectionViewModel(pendingSelectionSession!!)
            }
            CompanySelectionScreen(
                viewModel = companySelectionViewModel,
                onCompanySelected = { companySession ->
                    currentCompanySession = companySession
                    pendingSelectionSession = null
                },
                onBack = {
                    scope.launch {
                        DependencyContainer.authRepository.logout()
                        pendingSelectionSession = null
                    }
                },
            )
        }

        else -> {
            var showLoginScreen by remember { mutableStateOf(false) }
            val loginViewModel = remember { DependencyContainer.createLoginViewModel() }

            if (!showLoginScreen) {
                WelcomeScreen(
                    onLoginClick = { showLoginScreen = true },
                )
            } else {
                LoginScreen(
                    viewModel = loginViewModel,
                    onCompanySessionReady = { companySession ->
                        currentCompanySession = companySession
                        pendingSelectionSession = null
                    },
                    onRequiresCompanySelection = { authSession ->
                        pendingSelectionSession = authSession
                    },
                    onBack = { showLoginScreen = false },
                )
            }
        }
    }
}
