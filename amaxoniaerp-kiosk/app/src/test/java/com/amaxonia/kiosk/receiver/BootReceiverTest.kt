package com.amaxonia.kiosk.receiver

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class BootReceiverTest {
    @Test
    fun onReceive_bootCompleted_startsMainActivityWithNewTaskFlag() {
        val receiver = BootReceiver()
        val context = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)

        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
        every { context.startActivity(any()) } returns Unit

        receiver.onReceive(context, intent)

        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun onReceive_unrelatedAction_doesNotStartActivity() {
        val receiver = BootReceiver()
        val context = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)

        every { intent.action } returns Intent.ACTION_AIRPLANE_MODE_CHANGED

        receiver.onReceive(context, intent)

        verify(exactly = 0) { context.startActivity(any()) }
    }
}
