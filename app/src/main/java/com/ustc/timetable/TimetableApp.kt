package com.ustc.timetable

import android.app.Application
import androidx.work.Configuration
import com.ustc.timetable.school.ustc.auth.WebViewLoginDependencies
import com.ustc.timetable.school.ustc.auth.WebViewLoginDependenciesProvider
import com.ustc.timetable.settings.WeeklySyncScheduling
import com.ustc.timetable.sync.StartupWeeklySyncReconciler
import com.ustc.timetable.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** open 以允许 debug variant 的 DebugTimetableApp 子类化；release 直接使用本类。 */
open class TimetableApp : Application(), Configuration.Provider, WebViewLoginDependenciesProvider {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    final override val workManagerConfiguration: Configuration by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Configuration.Builder()
            .setWorkerFactory(container.ustcPortalRuntime.workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        StartupWeeklySyncReconciler(
            settings = container.settings,
            scheduling = WeeklySyncScheduling { enabled -> SyncScheduler.setEnabled(this, enabled) },
            scope = appScope,
        ).start()
    }

    final override fun webViewLoginDependencies(): WebViewLoginDependencies =
        WebViewLoginDependencies(
            descriptor = container.ustcPortalRuntime.descriptor,
            sessionManager = container.ustcPortalRuntime.sessionManager,
        )
}
