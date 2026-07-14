package com.amaxonia.pos.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLogTest {
    @Test
    fun redactsCredentialsAndPersonalIdentifiers() {
        val sanitized =
            SafeLog.redact(
                "token=secret authorization:Bearer abc.def.ghi gatewayKey=gw customerCI=12345678 " +
                    "commerceRif=J123 rawResponse={payment} password=hunter2",
            )

        listOf("secret", "abc.def.ghi", "gw", "12345678", "J123", "{payment}", "hunter2")
            .forEach { sensitiveValue -> assertFalse(sanitized.contains(sensitiveValue)) }
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun preservesOperationalMessagesWithoutSensitiveValues() {
        val message = "Payment gateway response received; approved=true"

        assertTrue(SafeLog.redact(message) == message)
    }
}
