package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Company
import kotlinx.coroutines.flow.Flow

interface CompanyRepository {
    suspend fun getAllCompanies(): Result<List<Company>>
    suspend fun getCompanyById(id: String): Result<Company>
}
