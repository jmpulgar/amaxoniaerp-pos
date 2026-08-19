package com.amaxoniaerp

import com.amaxoniaerp.core.tenant.tenantLogContext
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import org.slf4j.event.Level
import java.util.UUID

private const val CALL_ID_MDC_KEY = "call-id"
private const val COUNTRY_MDC_KEY = "country"

fun Application.configureMonitoring() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { "req-${UUID.randomUUID()}" }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        mdc(CALL_ID_MDC_KEY) { call -> call.callId }
        mdc(COUNTRY_MDC_KEY) { call -> call.tenantLogContext()[COUNTRY_MDC_KEY] }
    }
}
