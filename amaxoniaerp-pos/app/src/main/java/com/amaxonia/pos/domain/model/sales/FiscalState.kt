package com.amaxonia.pos.domain.model.sales

import androidx.room.TypeConverter

/**
 * Explicit fiscal-document lifecycle for a sale.
 *
 * Introduced by the auditoría (docs/auditoria-produccion-pos-2026-07-20.md,
 * ítem 5 / riesgo FIS-001, FIS-002). The document must NOT be reissued
 * automatically by a retry: once the printer has produced a voucher, the
 * terminal persists `PRINTED_PENDING_CONFIRM` and only the backend
 * confirmation can move it to `CONFIRMED`. A crash between print and
 * confirm re-enters at `PRINTED_PENDING_CONFIRM` and replays the backend
 * confirmation, never the printer.
 *
 * Migration v13 → v14 maps the legacy `fiscalConfirmationStatus` column to
 * these values (see [fromLegacyConfirmationStatus]); the legacy column is
 * kept readable during the ítem-8 money migration and removed in a later
 * schema bump.
 */
enum class FiscalState {
    /**
     * Country/flavour has no fiscal printer (e.g. `amaxonia` tasting in a
     * jurisdiction that does not require a fiscal voucher). No fiscal
     * lifecycle is tracked for this sale.
     */
    NOT_APPLICABLE,

    /**
     * Sale is PAID and confirmed with the backend, but the fiscal voucher
     * has not yet been produced. The printer will be invoked next; on
     * success the row moves to [PRINTED_PENDING_CONFIRM].
     */
    PENDING_PRINT,

    /**
     * Printer returned a voucher (we have `fiscalNumber` + `printerSerial`)
     * but the backend PATCH `/facturas/{id}/confirmacion-fiscal` has NOT
     * yet acknowledged. A crash/retry at this point replays the PATCH only,
     * never the printer.
     */
    PRINTED_PENDING_CONFIRM,

    /** Backend acknowledged the fiscal voucher. Terminal state. */
    CONFIRMED,

    /**
     * Fiscal pipeline failed terminally (printer hardware gone, voucher
     * rejected, MAX_RETRIES exhausted). Requires manual reconciliation.
     */
    FAILED;

    companion object {
        /**
         * Backfill mapping used by migration v13 → v14. Any unknown legacy
         * value defaults to [NOT_APPLICABLE] (the row predates the explicit
         * fiscal lifecycle and its country/flavour must opt in again).
         */
        fun fromLegacyConfirmationStatus(raw: String?): FiscalState =
            when (raw?.trim()?.uppercase()) {
                null, "", "IGNORED" -> NOT_APPLICABLE
                "PENDING", "RETRYABLE_PENDING", "IN_FLIGHT" -> PRINTED_PENDING_CONFIRM
                "CONFIRMED" -> CONFIRMED
                "TERMINAL_FAILED" -> FAILED
                else -> NOT_APPLICABLE
            }
    }
}

/** Room TypeConverter for [FiscalState]. Stores the enum name; rejects unknowns as [FiscalState.NOT_APPLICABLE]. */
class FiscalStateConverter {
    @TypeConverter
    fun toString(state: FiscalState): String = state.name

    @TypeConverter
    fun fromString(raw: String?): FiscalState =
        raw?.let { name -> FiscalState.entries.firstOrNull { it.name == name } } ?: FiscalState.NOT_APPLICABLE
}
