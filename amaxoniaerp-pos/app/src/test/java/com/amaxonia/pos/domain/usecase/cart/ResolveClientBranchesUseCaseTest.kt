package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveClientBranchesUseCaseTest {
    @Test
    fun `Panama resolves client branches`() =
        runTest {
            var repositoryCalled = false
            val expected = ClientBranch(7, "C-1", "Principal")
            val useCase =
                ResolveClientBranchesUseCase(SessionReader(ServerCountries.fromCode("PA"))) {
                    repositoryCalled = true
                    listOf(expected)
                }

            assertEquals(listOf(expected), useCase(Client(id = "client")))
            assertTrue(repositoryCalled)
        }

    @Test
    fun `non Panama country does not query branch repository`() =
        runTest {
            var repositoryCalled = false
            val useCase =
                ResolveClientBranchesUseCase(SessionReader(ServerCountries.fromCode("VE"))) {
                    repositoryCalled = true
                    emptyList()
                }

            assertTrue(useCase(Client(id = "client")).isEmpty())
            assertFalse(repositoryCalled)
        }

    private class SessionReader(
        private val country: ServerCountry?,
    ) : DashboardSessionReader {
        override suspend fun currentAdminDatabase(): String = ""

        override suspend fun currentCountry(): ServerCountry? = country
    }
}
