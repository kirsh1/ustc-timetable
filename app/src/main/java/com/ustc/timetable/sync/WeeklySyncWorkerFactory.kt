package com.ustc.timetable.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ustc.timetable.timetable.data.SettingsStore

class WeeklySyncWorkerFactory(
    private val runner: BackgroundSyncRunner,
    private val settings: SettingsStore,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == WeeklySyncWorker::class.java.name) {
        WeeklySyncWorker(appContext, workerParameters, runner, settings)
    } else {
        null
    }
}
