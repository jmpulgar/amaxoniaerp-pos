package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch

fun interface ClientBranchRepository {
    suspend fun findFor(client: Client): List<ClientBranch>
}
