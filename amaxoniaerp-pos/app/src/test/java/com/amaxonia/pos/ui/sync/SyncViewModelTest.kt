package com.amaxonia.pos.ui.sync

import com.amaxonia.pos.domain.repository.CatalogSynchronization
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `sync inicial ya completada no lanza carga y avisa onCompleted`() =
        runTest(mainDispatcherRule.dispatcher) {
            var completions = 0
            val syncer = FakeCatalogSyncer(initialCompleted = true)
            val viewModel = SyncViewModel(syncer)

            viewModel.startSyncIfNeeded { completions++ }
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isCompleted)
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(0, syncer.syncAllCalls)
            assertEquals(1, completions)
        }

    @Test
    fun `sync exitosa marca completado y avisa onCompleted una sola vez`() =
        runTest(mainDispatcherRule.dispatcher) {
            var completions = 0
            val syncer = FakeCatalogSyncer(initialCompleted = false, syncResult = Result.success(Unit))
            val viewModel = SyncViewModel(syncer)

            viewModel.startSyncIfNeeded { completions++ }
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isCompleted)
            assertFalse(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.error)
            assertEquals(1, syncer.syncAllCalls)
            assertEquals(1, completions)
        }

    @Test
    fun `fallo de sync expone el error del repositorio y no marca completado`() =
        runTest(mainDispatcherRule.dispatcher) {
            val syncer =
                FakeCatalogSyncer(initialCompleted = false, syncResult = Result.failure(IllegalStateException("red caída")))
            val viewModel = SyncViewModel(syncer)

            viewModel.startSyncIfNeeded { }
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("red caída", state.error)
            assertFalse(state.isCompleted)
            assertFalse(state.isLoading)
        }

    @Test
    fun `fallo sin mensaje usa el mensaje fallback y retry reintenta la sincronizacion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val syncer = MutableCatalogSyncer()
            syncer.result = Result.failure(IllegalStateException())
            val viewModel = SyncViewModel(syncer)

            viewModel.startSyncIfNeeded { }
            advanceUntilIdle()
            assertEquals("Error al sincronizar", viewModel.state.value.error)

            syncer.result = Result.success(Unit)
            var completed = false
            viewModel.retry { completed = true }
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isCompleted)
            assertNull(viewModel.state.value.error)
            assertTrue(completed)
            assertEquals(2, syncer.syncAllCalls)
        }

    private open class MutableCatalogSyncer : CatalogSynchronization {
        var result: Result<Unit> = Result.success(Unit)
        var syncAllCalls = 0

        override suspend fun syncAll(pageSize: Int): Result<Unit> {
            syncAllCalls++
            return result
        }

        override suspend fun isInitialSyncCompleted() = false
    }

    private class FakeCatalogSyncer(
        private val initialCompleted: Boolean,
        private val syncResult: Result<Unit> = Result.success(Unit),
    ) : CatalogSynchronization {
        var syncAllCalls = 0

        override suspend fun syncAll(pageSize: Int): Result<Unit> {
            syncAllCalls++
            return syncResult
        }

        override suspend fun isInitialSyncCompleted() = initialCompleted
    }
}
