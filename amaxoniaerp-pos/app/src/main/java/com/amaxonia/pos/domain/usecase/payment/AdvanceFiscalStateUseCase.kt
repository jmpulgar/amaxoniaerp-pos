package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.domain.model.sales.FiscalState
import com.amaxonia.pos.domain.system.AppClock

/**
 * Authoritative driver of the explicit fiscal lifecycle required by auditoría
 * ítem 5 / FIS-001. Every PAID sale starts at [FiscalState.PENDING_PRINT]; from
 * there it can only advance forward through a small set of guarded
 * transitions:
 *
 *  - `markPrinted`           PENDING_PRINT     -> PRINTED_PENDING_CONFIRM
 *    Called by the print-feedback path after the fiscal printer returns OK.
 *  - `markConfirmed`         PRINTED_PENDING_CONFIRM | PENDING_PRINT -> CONFIRMED
 *    Called after PATCH /facturas/{id}/confirmacion-fiscal returns 2xx OR
 *    when no fiscal document is required (NOT_APPLICABLE -> CONFIRMED).
 *  - `markFailed`            any non-terminal -> FAILED
 *    Records a terminal failure so the worker/UI stops retrying.
 *
 * Transitions are atomic conditional UPDATEs: if two paths race (eg. the
 * worker and a manual retry) only one advances the row; the loser sees
 * affectedRows == 0 and treats the operation as already done. This is what
 * makes the lifecycle robust under concurrent dispatchers without locks.
 *
 * Why `dao.transitionFiscalState` returns Int instead of being silent: a
 * 0-affected-rows result is NOT an error (someone else already moved the row);
 * a returned Int lets the caller log it without having to swallow exceptions.
 */
class AdvanceFiscalStateUseCase(
    private val dao: TransactionLogDao,
    private val clock: AppClock,
) {
    /** Transition PENDING_PRINT -> PRINTED_PENDING_CONFIRM (printer OK). */
    suspend fun markPrinted(clientCorrelationId: String): Int =
        dao.transitionFiscalState(
            id = clientCorrelationId,
            newState = FiscalState.PRINTED_PENDING_CONFIRM,
            expectedFrom = listOf(FiscalState.PENDING_PRINT),
            updatedAt = clock.now().toEpochMilli(),
        )

    /**
     * Transition {PRINTED_PENDING_CONFIRM, PENDING_PRINT, FAILED} -> CONFIRMED.
     * `PENDING_PRINT` is allowed because some flows confirm directly without a
     * printer-OK signal (eg. central invoices that skip the fiscal printer).
     */
    suspend fun markConfirmed(clientCorrelationId: String): Int =
        dao.transitionFiscalState(
            id = clientCorrelationId,
            newState = FiscalState.CONFIRMED,
            expectedFrom =
                listOf(
                    FiscalState.PENDING_PRINT,
                    FiscalState.PRINTED_PENDING_CONFIRM,
                ),
            updatedAt = clock.now().toEpochMilli(),
        )

    /** Mark the sale as NOT_APPLICABLE when the fiscal printer is not required. */
    suspend fun markNotApplicable(clientCorrelationId: String): Int =
        dao.transitionFiscalState(
            id = clientCorrelationId,
            newState = FiscalState.NOT_APPLICABLE,
            expectedFrom = listOf(FiscalState.PENDING_PRINT),
            updatedAt = clock.now().toEpochMilli(),
        )

    /** Transition any non-terminal state -> FAILED (terminal). */
    suspend fun markFailed(clientCorrelationId: String): Int {
        val now = clock.now().toEpochMilli()
        // Move out of any non-terminal state. CONFIRMED and NOT_APPLICABLE are
        // terminal-success and must never be overwritten with FAILED.
        val nonTerminal =
            listOf(
                FiscalState.PENDING_PRINT,
                FiscalState.PRINTED_PENDING_CONFIRM,
            )
        return dao.transitionFiscalState(
            id = clientCorrelationId,
            newState = FiscalState.FAILED,
            expectedFrom = nonTerminal,
            updatedAt = now,
        )
    }
}
