package com.amaxonia.pos.ui.drafts

import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.repository.DraftInvoiceRestorer
import com.amaxonia.pos.domain.usecase.drafts.RestoreDraftInvoiceUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DraftInvoicesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val draft =
        DraftInvoice(
            id = "draft-1",
            itemsJson = "[]",
            total = 25.0,
            itemCount = 1,
            createdAt = 0L,
        )

    @Test
    fun `al iniciar expone los borradores persistidos`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeDraftRepository(mutableListOf(draft))
            val viewModel = DraftInvoicesViewModel(repo, restoring(Result.success(Unit)))

            advanceUntilIdle()

            assertEquals(listOf(draft), viewModel.drafts.value)
            assertFalse(viewModel.isLoading.value)
            assertEquals(1, repo.allCalls)
        }

    @Test
    fun `deleteDraft elimina el borrador y recarga la lista`() =
        runTest(mainDispatcherRule.dispatcher) {
            val other = draft.copy(id = "draft-2")
            val repo = FakeDraftRepository(mutableListOf(draft, other))
            val viewModel = DraftInvoicesViewModel(repo, restoring(Result.success(Unit)))
            advanceUntilIdle()

            viewModel.deleteDraft("draft-1")
            advanceUntilIdle()

            assertEquals(listOf("draft-1"), repo.deleted)
            assertEquals(listOf(other), viewModel.drafts.value)
        }

    @Test
    fun `loadDraftIntoCart exitoso restaura y borra el borrador consumido`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeDraftRepository(mutableListOf(draft))
            val restorer = RecordingRestorer(Result.success(Unit))
            val viewModel = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase(restorer))

            val loaded = viewModel.loadDraftIntoCart(draft)

            advanceUntilIdle()
            assertTrue(loaded)
            assertEquals(listOf(draft), restorer.restored)
            assertEquals(listOf("draft-1"), repo.deleted)
            assertTrue(viewModel.drafts.value.isEmpty())
        }

    @Test
    fun `loadDraftIntoCart fallido devuelve false y conserva el borrador`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeDraftRepository(mutableListOf(draft))
            val viewModel = DraftInvoicesViewModel(repo, restoring(Result.failure(IllegalStateException("json corrupto"))))

            val loaded = viewModel.loadDraftIntoCart(draft)

            advanceUntilIdle()
            assertFalse(loaded)
            assertTrue(repo.deleted.isEmpty())
            assertEquals(listOf(draft), viewModel.drafts.value)
        }

    private fun restoring(result: Result<Unit>) = RestoreDraftInvoiceUseCase { result }

    private class RecordingRestorer(
        private val result: Result<Unit>,
    ) : DraftInvoiceRestorer {
        val restored = mutableListOf<DraftInvoice>()

        override fun restore(draft: DraftInvoice): Result<Unit> {
            restored += draft
            return result
        }
    }

    private class FakeDraftRepository(
        drafts: MutableList<DraftInvoice>,
    ) : DraftInvoiceRepository {
        private val stored = drafts
        val deleted = mutableListOf<String>()
        var allCalls = 0

        override suspend fun all(): List<DraftInvoice> {
            allCalls++
            return stored.toList()
        }

        override suspend fun save(draft: DraftInvoice) {
            stored += draft
        }

        override suspend fun delete(id: String) {
            deleted += id
            stored.removeAll { it.id == id }
        }
    }
}
