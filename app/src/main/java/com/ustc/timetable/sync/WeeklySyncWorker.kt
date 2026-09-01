package com.ustc.timetable.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ustc.timetable.notification.SyncNotification
import com.ustc.timetable.timetable.data.SettingsStore
import kotlinx.coroutines.flow.first

fun interface BackgroundSyncRunner {
    suspend fun run(): SyncResult
}

class SyncEngineBackgroundRunner(
    private val engine: SyncEngine,
) : BackgroundSyncRunner {
    override suspend fun run(): SyncResult = engine.syncCurrentAcademicSemester()
}

class WeeklySyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val runner: BackgroundSyncRunner,
    private val settings: SettingsStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!settings.weeklySyncEnabled.first()) return Result.success()

        return when (val result = runner.run()) {
            SyncResult.NoChange -> Result.success()
            is SyncResult.Success -> {
                if (result.changes.isNotEmpty()) {
                    SyncNotification.postChanges(applicationContext, result.changes)
                }
                Result.success()
            }
            is SyncResult.Failed -> when (result.error) {
                SyncError.AuthenticationExpired -> {
                    settings.setNeedReauth(true)
                    SyncNotification.postReauthNeeded(applicationContext)
                    Result.success()
                }
                SyncError.NetworkFailed,
                SyncError.ParseFailed,
                SyncError.ValidationFailed,
                -> Result.success()
            }
        }
    }
}
