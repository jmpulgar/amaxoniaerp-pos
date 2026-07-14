package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SyncSchedulerInstrumentedTest {
    @Test
    fun pendingInvoiceRequestRequiresNetworkAndUsesExplicitExponentialBackoff() {
        val workSpec = SyncScheduler.pendingInvoiceRequest().workSpec

        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, workSpec.backoffDelayDuration)
    }

    @Test
    fun duplicatePendingInvoiceSchedulingKeepsOnlyOneActiveJob() {
        SyncScheduler.enqueuePendingInvoices(context)
        SyncScheduler.enqueuePendingInvoices(context)

        val work =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(SyncScheduler.PENDING_INVOICES_WORK_NAME)
                .get(5, TimeUnit.SECONDS)

        assertEquals(1, work.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })
        assertTrue(work.none { it.state == WorkInfo.State.FAILED })
    }

    companion object {
        private lateinit var context: Context

        @JvmStatic
        @BeforeClass
        fun initializeWorkManager() {
            context = ApplicationProvider.getApplicationContext()
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration
                    .Builder()
                    .setExecutor(SynchronousExecutor())
                    .build(),
            )
        }
    }
}
