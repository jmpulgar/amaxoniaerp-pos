package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.repository.ClientBranchRepository
import com.amaxonia.pos.domain.repository.DashboardSessionReader

class ResolveClientBranchesUseCase(
    private val sessionReader: DashboardSessionReader,
    private val branchRepository: ClientBranchRepository,
) {
    suspend operator fun invoke(client: Client): List<ClientBranch> =
        if (sessionReader.currentCountry()?.code.equals(PANAMA_COUNTRY_CODE, ignoreCase = true)) {
            branchRepository.findFor(client)
        } else {
            emptyList()
        }

    private companion object {
        const val PANAMA_COUNTRY_CODE = "PA"
    }
}
