package com.amaxonia.pos.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.usecase.drafts.RestoreDraftInvoiceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DraftInvoicesViewModel(
    private val draftInvoiceRepository: DraftInvoiceRepository,
    private val restoreDraftInvoice: RestoreDraftInvoiceUseCase,
) : ViewModel() {
    private val _drafts = MutableStateFlow<List<DraftInvoice>>(emptyList())
    val drafts: StateFlow<List<DraftInvoice>> = _drafts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        viewModelScope.launch {
            _isLoading.value = true
            _drafts.value = draftInvoiceRepository.all()
            _isLoading.value = false
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            draftInvoiceRepository.delete(id)
            loadDrafts()
        }
    }

    /** Restores a persisted draft without exposing JSON parsing to Compose. */
    fun loadDraftIntoCart(draft: DraftInvoice): Boolean =
        restoreDraftInvoice(draft)
            .onSuccess {
                viewModelScope.launch {
                    draftInvoiceRepository.delete(draft.id)
                    loadDrafts()
                }
            }.onFailure { error ->
                SafeLog.e(TAG, "Unable to restore persisted draft", error)
            }.isSuccess

    private companion object {
        const val TAG = "DraftInvoicesVM"
    }
}
