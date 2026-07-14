package com.amaxonia.pos.ui.cart

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
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
                    cart,
                    DefaultClientRepository(client),
                    session,
                    clientBranchRepository = {
                        listOf(
                            com.amaxonia.pos.domain.model
                                .ClientBranch(7, "C-1", "Principal"),
                        )
                    },
                    resolveClientImageUrl = ResolveClientImageUrlUseCase(session, FixedImageResolver),
                )

            coordinator.start(backgroundScope, state)
            runCurrent()

            assertEquals(client, state.value.selectedClient)
            assertEquals(listOf(7), state.value.clientSucursales.map { it.sucursalId })
            assertEquals("client://tenant/C-1/ana.jpg", state.value.selectedClientPhotoUrl)
            assertTrue(state.value.isPanama)
        }

    private class DefaultClientRepository(
        private val client: Client,
    ) : ClientRepository {
        override suspend fun getAllClients(
            page: Int,
            pageSize: Int,
        ): Result<List<Client>> = Result.success(listOf(client))

        override suspend fun getClientById(id: String): Result<Client> = Result.success(client)

        override suspend fun getDefaultClient(): Result<Client> = Result.success(client)

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
