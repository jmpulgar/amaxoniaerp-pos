package com.amaxonia.pos.composition

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.WorkInfo
import com.amaxonia.pos.data.sync.SyncScheduler
import com.amaxonia.pos.ui.sync.SyncViewModel

/**
 * Grafo del feature sincronización de catálogos (TASK-051/052/065). También
 * fachada de la programación WorkManager (`SyncScheduler`): las pantallas no
 * importan `data.sync` directamente.
 */
object SyncGraph {
    fun syncViewModel(): SyncViewModel = SyncViewModel(DependencyContainer.catalogSyncer)

    fun schedulePeriodic(context: Context) = SyncScheduler.schedulePeriodic(context)

    fun enqueueManual(context: Context) = SyncScheduler.enqueueManual(context)

    fun enqueuePendingInvoices(context: Context) = SyncScheduler.enqueuePendingInvoices(context)

    fun manualSyncWorkInfos(context: Context): LiveData<List<WorkInfo>> = SyncScheduler.getManualSyncWorkInfos(context)
}
