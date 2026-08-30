package com.ustc.timetable

import android.app.Application

/** open 以允许 debug variant 的 DebugTimetableApp 子类化；release 直接使用本类。 */
open class TimetableApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
