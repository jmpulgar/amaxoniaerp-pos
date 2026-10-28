package com.amaxonia.pos.ui.cart

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.usecase.cart.ResolveClientImageUrlUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartStateCoordinatorTest {
    @Test
    fun `default Panama client publishes branch and photo state`() =
        runTest {
            val client = Client(id = "C-1", firstName = "Ana", photoFilename = "ana.jpg")
            val cart = CartRepository()
            val session = FixedSessionReader(ServerCountries.fromCode("PA"), "tenant")
            val state = MutableStateFlow(CartState())
            val coordinator =
                CartStateCoordinator(
                    CartStateDependencies(
                        cartRepository = cart,
                        clientRepository = DefaultClientRepository(client),
                        sessionReader = session,
                        clientBranchRepository = {
                            listOf(
                                com.amaxonia.pos.domain.model
                                    .ClientBranch(7, "C-1", "Principal"),
                            )
                        },
                        resolveClientImageUrl = ResolveClientImageUrlUseCase(session, FixedImageResolver),
                    ),
                )

            coordinator.start(backgroundScope, state)
            runCurrent()

            assertEquals(client, state.value.selectedClient)
            assertEquals(listOf(7), state.value.clientSucursales.map { it.sucursalId })
            assertEquals("client://tenant/C-1/ana.jpg", state.value.selectedClientPhotoUrl)
            assertTrue(state.value.isPanama)
        }

    @Test
    fun `ensureDefaultClient passes idSucursal from active caja to client repository`() =
        runTest {
            val client = Client(id = "C-1", firstName = "Ana")
            val cart = CartRepository()
            val session = FixedSessionReader(ServerCountries.fromCode("PA"), "tenant")
            val state = MutableStateFlow(CartState())
            val fakeActiveCaja =
                MutableStateFlow<Caja?>(
                    Caja(
                        idCaja = "caja-1",
                        codCaja = "01",
                        descripcion = "Caja 1",
                        estatus = 1,
                        idSucursal = 42,
                        serieCaja = "C01",
                    ),
                )
            var requestedSucursalId: Int? = null
            val repo =
                object : ClientRepository {
                    override suspend fun getClientById(id: String) = Result.success(client)

                    override suspend fun getDefaultClient(sucursalId: Int?): Result<Client> {
                        requestedSucursalId = sucursalId
                        return Result.success(client)
                    }

                    override suspend fun searchClients(query: String) = Result.success(listOf(client))

                    override suspend fun saveClient(client: Client) = Result.success(Unit)

                    override suspend fun deleteClient(id: String) = Result.success(Unit)
                }
            val activeCajaReader =
                object : ActiveCajaReader {
                    override val activeCaja = fakeActiveCaja
                }
            val coordinator =
                CartStateCoordinator(
                    CartStateDependencies(
                        cartRepository = cart,
                        clientRepository = repo,
                        sessionReader = session,
                        clientBranchRepository = { emptyList() },
                        resolveClientImageUrl = ResolveClientImageUrlUseCase(session, FixedImageResolver),
                        activeCajaReader = activeCajaReader,
                    ),
                )

            coordinator.start(backgroundScope, state)
            runCurrent()

            assertEquals(42, requestedSucursalId)
            assertEquals(client, state.value.selectedClient)
        }

    @Test
    fun `ensureDefaultClient leaves cart without client when no default client configured`() =
        runTest {
            val cart = CartRepository()
            val session = FixedSessionReader(ServerCountries.fromCode("PA"), "tenant")
            val state = MutableStateFlow(CartState())
            val repo =
                object : ClientRepository {
                    override suspend fun getClientById(id: String) = Result.failure<Client>(NoSuchElementException())

                    override suspend fun getDefaultClient(sucursalId: Int?) =
                        Result.failure<Client>(NoSuchElementException("Default client not configured"))

                    override suspend fun searchClients(query: String) = Result.success(emptyList<Client>())

                    override suspend fun saveClient(client: Client) = Result.success(Unit)

                    override suspend fun deleteClient(id: String) = Result.success(Unit)
                }
            val coordinator =
                CartStateCoordinator(
                    CartStateDependencies(
                        cartRepository = cart,
                        clientRepository = repo,
                        sessionReader = session,
                        clientBranchRepository = { emptyList() },
                        resolveClientImageUrl = ResolveClientImageUrlUseCase(session, FixedImageResolver),
                    ),
                )

            coordinator.start(backgroundScope, state)
            runCurrent()

            assertNull(state.value.selectedClient)
        }

    private class DefaultClientRepository(
        private val client: Client,
    ) : ClientRepository {
        override suspend fun getAllClients(
            page: Int,
            pageSize: Int,
        ): Result<List<Client>> = Result.success(listOf(client))

        override suspend fun getClientById(id: String): Result<Client> = Result.success(client)

        override suspend fun getDefaultClient(sucursalId: Int?): Result<Client> = Result.success(client)

        override suspend fun searchClients(query: String): Result<List<Client>> = Result.success(listOf(client))

        override suspend fun saveClient(client: Client): Result<Unit> = Result.success(Unit)

        override suspend fun deleteClient(id: String): Result<Unit> = Result.success(Unit)
    }

    private class FixedSessionReader(
        private val country: ServerCountry?,
        private val database: String,
    ) : DashboardSessionReader {
        override suspend fun currentAdminDatabase(): String = database

        override suspend fun currentCountry(): ServerCountry? = country
    }

    private object FixedImageResolver : ImageUrlResolver {
        override fun product(
            companyDatabase: String,
            photoPath: String,
        ): String = error("not used")

        override fun client(
            companyDatabase: String,
            clientId: String,
            filename: String,
        ): String = "client://$companyDatabase/$clientId/$filename"
    }
}
