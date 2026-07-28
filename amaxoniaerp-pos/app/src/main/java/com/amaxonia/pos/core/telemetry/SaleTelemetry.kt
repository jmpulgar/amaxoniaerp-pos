package com.amaxonia.pos.core.telemetry

import com.amaxonia.pos.BuildConfig
import java.util.concurrent.atomic.AtomicLong

/**
 * Structured sale telemetry (auditoría ítem 10 / OBS-001).
 *
 * `SafeLog` only emits in DEBUG which is correct for PII-rich developer
 * logs but leaves release builds silent on the events a pilot must observe:
 * ambiguous sales, duplicate invoices, retries, fiscal transitions and HKA
 * callbacks. [SaleTelemetry] fills that gap with one deterministic,
 * structured channel that works in BOTH release and debug.
 *
 * Every event is keyed by the canonical `idFactura` so an operator can
 * follow a single sale across retries, process death, fiscal confirmation
 * and gateway callback. The payload is intentionally machine-parseable
 * (`event=<NAME> idFactura=<id> <kv>...`) and **never** carries card data,
 * encrypted commands, tokens or RIFs — the values recorded are sale
 * identities, codes, attempt counts and timing only.
 *
 * The [sink] is the single extensibility point: structured logging
 * libraries, crashlytics breadcrumbs or an HTTP collector can plug in by
 * implementing [TelemetrySink]. The default [LogcatSink] emits via Android
 * `Log.*` in both variants; [alerting] decides which severities escalate
 * to a separate WARN/ERROR line so an operator-facing dashboard can be
 * wired up without changing any call site.
 */
object SaleTelemetry {
    internal const val TAG = "SaleTelemetry"

    @Volatile
    var sink: TelemetrySink = LogcatSink

    @Volatile
    var alerting: AlertPolicy = DefaultAlertPolicy

    /**
     * Records an event. Public surface is intentionally narrow: callers
     * pass the event name plus a vararg of `key=value` attributes. This
     * forces every event into the same correlation contract.
     */
    fun record(
        event: SaleEvent,
        idFactura: String,
        vararg attributes: Pair<String, Any?>,
    ) {
        val sequence = nextSequence()
        val payload =
            buildString {
                append("seq=$sequence event=${event.code} idFactura=$idFactura")
                for ((key, value) in attributes) {
                    val safe = value?.toString()?.take(MAX_ATTRIBUTE_LEN).orEmpty()
                    append(" $key=$safe")
                }
            }
        // Telemetry is best-effort: every sink we ship (`LogcatSink`, future
        // Crashlytics / HTTP collectors) can fail with RuntimeException-class
        // errors only (RuntimeException is the documented base for the
        // android.jar stubs on the JVM unit-test host and for platform-level
        // failures such as a dead binder when shelling to logd). We MUST NOT
        // swallow Error subclasses (OutOfMemoryError, stack overflow, linking
        // failures) because those indicate a corrupt process that should
        // surface and crash rather than continue silently on the money path.
        try {
            sink.emit(event, payload)
            if (alerting.shouldAlert(event)) {
                // Escalated line so an external collector can pick up only alerts.
                sink.emitAlert(event, payload)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") re: RuntimeException,
        ) {
            // Intentionally not rethrown: telemetry must never break a sale.
            // Swallow silently; sinks must be self-diagnosing via their own
            // channel. We deliberately avoid SafeLog here — it may be the
            // current source of failure and could recurse.
            noop()
        }
    }

    private fun nextSequence(): Long = sequenceCounter.incrementAndGet()

    private val sequenceCounter = AtomicLong(0L)

    private const val MAX_ATTRIBUTE_LEN = 120

    /** Empty body so the swallow-catch above is not flagged as dead code. */
    @Suppress("EmptyFunctionBlock")
    private fun noop() {
        // best-effort sink: intentionally empty.
    }
}

/**
 * Canonical sale events for the pilot. Every entry maps to one of the
 * risk categories in §10 of the audit (ventas ambiguas, duplicados,
 * retries, fiscalización, HKA). New events must be added here so they
 * flow through [SaleTelemetry.record] and the [AlertPolicy] — never
 * invent free-form strings at call sites.
 */
enum class SaleEvent(
    val code: String,
) {
    SALE_STARTED("sale.started"),
    SALE_CONFIRMED("sale.confirmed"),
    SALE_REJECTED_BACKEND("sale.rejected.backend"),
    SALE_DUPLICATE("sale.duplicate"),
    SALE_AMBIGUOUS("sale.ambiguous"),
    RETRY_SCHEDULED("retry.scheduled"),
    RETRY_EXHAUSTED("retry.exhausted"),
    FISCAL_PRINTED("fiscal.printed"),
    FISCAL_CONFIRMED("fiscal.confirmed"),
    FISCAL_FAILED("fiscal.failed"),
    GATEWAY_AWAITING("gateway.awaiting"),
    GATEWAY_RESOLVED("gateway.resolved"),
    GATEWAY_TERMINAL("gateway.terminal"),
    GATEWAY_LATE_CALLBACK("gateway.late_callback"),
    GATEWAY_DUPLICATE_CALLBACK("gateway.duplicate_callback"),
}

/** Severity used by [AlertPolicy.shouldAlert] and sink routing. */
enum class TelemetrySeverity {
    INFO,
    WARN,
    ALERT,
}

/** Plug-in point for a structured sink (e.g. Crashlytics, HTTP collector). */
fun interface TelemetrySink {
    fun emit(
        event: SaleEvent,
        payload: String,
    )

    fun emitAlert(
        event: SaleEvent,
        payload: String,
    ) {
        // Default: same as emit. Concrete sinks override to route alerts
        // to a separate channel (e.g. Crashlytics recordException).
        emit(event, payload)
    }
}

/**
 * Decides which events escalate to operator-facing alerts. The default
 * policy matches §10 of the audit: only money / fiscal / HKA critical
 * events generate an alert; routine confirmations are info-only.
 */
fun interface AlertPolicy {
    fun shouldAlert(event: SaleEvent): Boolean
}

object DefaultAlertPolicy : AlertPolicy {
    private val critical =
        setOf(
            SaleEvent.SALE_DUPLICATE,
            SaleEvent.SALE_AMBIGUOUS,
            SaleEvent.RETRY_EXHAUSTED,
            SaleEvent.FISCAL_FAILED,
            SaleEvent.GATEWAY_TERMINAL,
            SaleEvent.GATEWAY_LATE_CALLBACK,
            SaleEvent.GATEWAY_DUPLICATE_CALLBACK,
        )

    override fun shouldAlert(event: SaleEvent): Boolean = event in critical
}

/**
 * Default sink: emits via Android `Log` in BOTH debug and release so the
 * pilot's `adb logcat` / log shipper captures correlated events without
 * sensitive payloads. Severity is derived from [DefaultAlertPolicy].
 */
private object LogcatSink : TelemetrySink {
    override fun emit(
        event: SaleEvent,
        payload: String,
    ) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(SaleTelemetry.TAG, payload)
        } else {
            // Release: still emit INFO events so the pilot can correlate by
            // sale id; the payload never contains PII or card data.
            android.util.Log.i(SaleTelemetry.TAG, payload)
        }
    }

    override fun emitAlert(
        event: SaleEvent,
        payload: String,
    ) {
        android.util.Log.w("[ALERT] ${SaleTelemetry.TAG}", payload)
    }
}
