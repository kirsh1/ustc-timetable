package com.ustc.timetable.sync

import com.ustc.timetable.timetable.data.SettingsStore

fun interface SyncExecutionSource {
    suspend fun execute(): SyncExecutionReport
}

/** Single timestamp producer shared by foreground and background sync adapters. */
class SyncExecutionRecorder(
    private val source: SyncExecutionSource,
    private val settings: SettingsStore,
) : ManualSyncRunner, BackgroundSyncRunner {
    override suspend fun sync(): SyncResult = executeAndRecord()

    override suspend fun run(): SyncResult = executeAndRecord()

    private suspend fun executeAndRecord(): SyncResult {
        val report = source.execute()
        report.finishedAt?.let { settings.setLastSyncFinishedAt(it.toEpochMilli()) }
        return report.result
    }
}
