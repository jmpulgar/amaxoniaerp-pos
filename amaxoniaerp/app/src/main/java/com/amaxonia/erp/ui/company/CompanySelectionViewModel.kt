package com.amaxonia.erp.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.CompanySummary
import com.amaxonia.erp.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanySelectionState(
    val companies: List<CompanySummary> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCompanyId: Int? = null,
    val error: String? = null,
)

sealed interface CompanySelectionEffect {
    data class CompanySelected(val session: CompanySession) : CompanySelectionEffect
}

class CompanySelectionViewModel(
    private val authRepository: AuthRepository,
    session: AuthSession,
) : ViewModel() {
    private val _state = MutableStateFlow(CompanySelectionState(companies = session.companies))
    val state: StateFlow<CompanySelectionState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CompanySelectionEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<CompanySelectionEffect> = _effects.asSharedFlow()

    fun selectCompany(company: CompanySummary) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, selectedCompanyId = company.id, error = null) }
            authRepository.selectCompany(company.id).fold(
                onSuccess = { companySession ->
                    _state.update { it.copy(isLoading = false) }
                    _effects.emit(CompanySelectionEffect.CompanySelected(companySession))
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedCompanyId = null,
                            error = error.message ?: "No se pudo seleccionar la empresa",
                        )
                    }
                },
            )
        }
    }
}
