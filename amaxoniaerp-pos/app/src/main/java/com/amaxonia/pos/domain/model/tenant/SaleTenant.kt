package com.amaxonia.pos.domain.model.tenant

import com.amaxonia.pos.domain.model.CompanySession

/**
 * Durable identity of the tenant that owns a sale.
 *
 * The auditoría (docs/auditoria-produccion-pos-2026-07-20.md, ítem 3)
 * requires every sale attempt, every queued upload and every worker lease
 * to be tied to the tenant that started the operation, so a pending
 * operation of tenant A is NEVER processed with the credentials, token or
 * endpoints of tenant B after a session change.
 *
 * The canonical key is [tenantId] (`t$<companyId>`). It is stored denormalised
 * on every ledger row so workers can resolve ownership without consulting any
 * in-memory or DataStore session: if the row's [tenantId] does not match the
 * session that the worker is running under, the row stays pending.
 *
 * [label], [adminDb], [contableDb], [nominaDb] are informative snapshots
 * persisted for logs, runbooks and reconciliation — they MUST NEVER be used
 * as a key to route or authorise processing. Routing/authorisation is by
 * [tenantId] only.
 */
data class SaleTenant(
    val tenantId: String,
    val companyId: Int,
    val label: String,
    val adminDb: String,
    val contableDb: String,
    val nominaDb: String,
) {
    fun matches(activeTenantId: String?): Boolean =
        activeTenantId != null && activeTenantId == tenantId

    companion object {
        /**
         * Builds the canonical durable tenant id from a session.
         *
         * The `t$` prefix avoids ambiguity with raw numeric ids persisted by
         * previous schema versions and makes a tenant id visually distinct
         * from a user id or invoice id in logs.
         */
        fun idFor(companyId: Int): String = "t$$companyId"

        fun fromSession(session: CompanySession): SaleTenant =
            SaleTenant(
                tenantId = idFor(session.company.id),
                companyId = session.company.id,
                label = session.company.name,
                adminDb = session.company.adminDb,
                contableDb = session.company.accountingDb,
                nominaDb = session.company.payrollDb,
            )

        /**
         * Sentinel used by seed/migration data where no real tenant is known.
         * Workers must NEVER process a row whose tenantId equals this value:
         * it represents a data-integrity gap, not a real tenant.
         */
        const val UNKNOWN_TENANT_ID: String = "t$0"
    }
}
