package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Company

interface CompanyRepository {
    suspend fun getAllCompanies(): Result<List<Company>>

    suspend fun getCompanyById(id: String): Result<Company>
}
