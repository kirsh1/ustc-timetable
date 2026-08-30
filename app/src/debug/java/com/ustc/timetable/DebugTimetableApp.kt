package com.ustc.timetable

import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug variant Application：异步 seed（application-lifetime scope + IO），不 runBlocking。
 * main release variant 仍是 TimetableApp（不 seed、不网络、不同步）。
 */
class DebugTimetableApp : TimetableApp() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val bundled = OfficialProfileLoader.load(this@DebugTimetableApp)
            DebugSeed.seedIfEmpty(
                semesters = container.semesters,
                manual = container.manual,
                db = container.db,
                bundledProfile = bundled,
            )
        }
    }
}
