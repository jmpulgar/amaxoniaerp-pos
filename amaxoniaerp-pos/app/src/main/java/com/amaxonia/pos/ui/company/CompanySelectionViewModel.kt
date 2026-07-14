package com.amaxonia.pos.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.domain.repository.CompanyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CompanySelectionViewModel(
    private val companyRepository: CompanyRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CompanySelectionState())
    val state: StateFlow<CompanySelectionState> = _state.asStateFlow()

    init {
        loadCompanies()
    }

    fun retry() {
        loadCompanies()
    }

    private fun loadCompanies() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            companyRepository.getAllCompanies().fold(
                onSuccess = { companies ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            companies = companies,
                            error = null,
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Error al cargar empresas",
                        )
                    }
                },
            )
        }
    }

    fun selectCompany(
        company: Company,
        onCompanySelected: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val companyId = company.id.toIntOrNull()
            if (companyId == null) {
                _state.update {
                    it.copy(isLoading = false, error = "Id de empresa invalido")
                }
                return@launch
            }
            authRepository.selectCompany(companyId).fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false) }
                    onCompanySelected()
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Error al seleccionar empresa",
                        )
                    }
                },
            )
        }
    }
}
