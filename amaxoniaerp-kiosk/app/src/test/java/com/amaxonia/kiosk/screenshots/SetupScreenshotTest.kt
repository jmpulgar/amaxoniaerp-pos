package com.amaxonia.kiosk.screenshots

import android.app.Application
import com.amaxonia.kiosk.core.network.KioskCajaDto
import com.amaxonia.kiosk.core.network.KioskCompanyDto
import com.amaxonia.kiosk.ui.accessibility.KioskLanguage
import com.amaxonia.kiosk.ui.attract.AdminUnlockActions
import com.amaxonia.kiosk.ui.attract.AttractContent
import com.amaxonia.kiosk.ui.attract.AttractUiState
import com.amaxonia.kiosk.ui.cajasetup.CajaLoadError
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupActions
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupContent
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupUiState
import com.amaxonia.kiosk.ui.login.LoginActions
import com.amaxonia.kiosk.ui.login.LoginContent
import com.amaxonia.kiosk.ui.login.LoginError
import com.amaxonia.kiosk.ui.login.LoginStep
import com.amaxonia.kiosk.ui.login.LoginUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Operator setup: system login, company choice, caja + prefix, and the 503 out-of-service banner. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = KIOSK_QUALIFIERS, sdk = [34], application = Application::class)
class SetupScreenshotTest {
    @get:Rule
    val shots = KioskScreenshotRule()

    private val server = "https://api.listoerp.app/"

    private val cajas =
        listOf(
            KioskCajaDto(idCaja = "c-1", codCaja = "001", descripcion = "Caja Kiosco Entrada", sucursalNombre = "Casa Matriz"),
            KioskCajaDto(idCaja = "c-2", codCaja = "002", descripcion = "Caja Kiosco Terraza", sucursalNombre = "Casa Matriz"),
            KioskCajaDto(idCaja = "c-3", codCaja = "010", descripcion = "Caja Principal", sucursalNombre = "Albrook Mall"),
        )

    private fun cajaState(
        cajas: List<KioskCajaDto> = this.cajas,
        selected: String? = "c-2",
        loadError: CajaLoadError? = null,
    ) = CajaSetupUiState(
        companyName = "Momi Café",
        username = "cajero1",
        isLoading = false,
        cajas = cajas,
        selectedCajaId = selected,
        prefix = "K2",
        loadError = loadError,
    )

    @Test
    fun login() = login(Variant.NORMAL)

    @Test
    fun loginHighContrast() = login(Variant.HIGH_CONTRAST)

    private fun login(variant: Variant) =
        shots.snap("00_login", variant, orderingScreen = false) {
            LoginContent(uiState = LoginUiState(serverUrl = server, username = "cajero1"), actions = LoginActions.NoOp)
        }

    @Test
    fun loginError() =
        shots.snap("00_login_error", orderingScreen = false) {
            LoginContent(
                uiState =
                    LoginUiState(
                        serverUrl = server,
                        username = "cajero1",
                        password = "secreta",
                        error = LoginError.InvalidCredentials,
                        showAdvanced = true,
                    ),
                actions = LoginActions.NoOp,
            )
        }

    @Test
    fun loginLoading() =
        shots.snap("00_login_loading", orderingScreen = false) {
            LoginContent(
                uiState = LoginUiState(serverUrl = server, username = "cajero1", password = "secreta", isLoading = true),
                actions = LoginActions.NoOp,
            )
        }

    @Test
    fun loginCompany() =
        shots.snap("00_login_company", orderingScreen = false) {
            LoginContent(
                uiState =
                    LoginUiState(
                        step = LoginStep.COMPANY,
                        serverUrl = server,
                        companies =
                            listOf(
                                KioskCompanyDto(id = 1, name = "Momi Café", rif = "155-123-456 DV 12"),
                                KioskCompanyDto(id = 2, name = "Momi Café Albrook", rif = "155-123-789 DV 40"),
                                KioskCompanyDto(id = 3, name = "Compañía Prueba"),
                            ),
                    ),
                actions = LoginActions.NoOp,
            )
        }

    @Test
    fun cajaSetup() = cajaSetup(Variant.NORMAL)

    @Test
    fun cajaSetupHighContrast() = cajaSetup(Variant.HIGH_CONTRAST)

    private fun cajaSetup(variant: Variant) =
        shots.snap("00_caja_setup", variant, orderingScreen = false) {
            CajaSetupContent(uiState = cajaState(), actions = CajaSetupActions.NoOp)
        }

    @Test
    fun cajaSetupInvalid() =
        shots.snap("00_caja_setup_invalid", orderingScreen = false) {
            CajaSetupContent(uiState = cajaState(selected = null).copy(previousCajaInvalid = true), actions = CajaSetupActions.NoOp)
        }

    @Test
    fun cajaSetupEmpty() =
        shots.snap("00_caja_setup_empty", orderingScreen = false) {
            CajaSetupContent(uiState = cajaState(cajas = emptyList(), selected = null), actions = CajaSetupActions.NoOp)
        }

    @Test
    fun attractOutOfService() =
        shots.snap("01_attract_out_of_service", orderingScreen = false) {
            AttractContent(
                uiState =
                    AttractUiState(
                        isLoading = false,
                        isOffline = true,
                        outOfServiceMessage = "El kiosco no está habilitado en esta empresa (falta migración)",
                    ),
                onStartOrder = {},
                onSecretTap = {},
                onMediaFinished = {},
                adminActions = AdminUnlockActions({}, {}, {}),
                language = KioskLanguage.SPANISH,
                onLanguageSelected = {},
            )
        }
}
