package com.ustc.timetable

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.ustc.timetable.school.ustc.auth.WebViewLoginDependenciesProvider
import com.ustc.timetable.sync.WeeklySyncWorker
import com.ustc.timetable.sync.WeeklySyncWorkerFactory
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TimetableApp::class, sdk = [36])
class RuntimeCompositionTest {
    private val app: TimetableApp = ApplicationProvider.getApplicationContext()

    @Test fun release_application_provides_real_worker_factory() {
        assertTrue(Configuration.Provider::class.java.isAssignableFrom(app.javaClass))
        assertTrue(app.workManagerConfiguration.workerFactory is WeeklySyncWorkerFactory)
        assertSame(app.container.ustcPortalRuntime.workerFactory, app.workManagerConfiguration.workerFactory)
    }

    @Test fun factory_constructs_worker_with_real_runtime() {
        val worker = TestListenableWorkerBuilder.from(app, WeeklySyncWorker::class.java)
            .setWorkerFactory(app.container.ustcPortalRuntime.workerFactory)
            .build()

        assertSame(WeeklySyncWorker::class.java, worker.javaClass)
    }

    @Test fun login_activity_receives_the_same_real_runtime() {
        val dependencies = (app as WebViewLoginDependenciesProvider).webViewLoginDependencies()

        assertSame(app.container.ustcPortalRuntime.descriptor, dependencies?.descriptor)
        assertSame(app.container.ustcPortalRuntime.sessionManager, dependencies?.sessionManager)
    }

    @Test fun no_duplicate_workmanager_initialization() {
        assertSame(WorkManager.getInstance(app), WorkManager.getInstance(app))
    }

    @Test fun default_initializer_removed_exactly_once() {
        val provider = app.packageManager.getProviderInfo(
            ComponentName(app.packageName, "androidx.startup.InitializationProvider"),
            PackageManager.GET_META_DATA,
        )

        assertFalse(provider.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true)
    }

    @Test fun debug_application_inherits_configuration() {
        assertTrue(Configuration.Provider::class.java.isAssignableFrom(DebugTimetableApp::class.java))
        assertTrue(WebViewLoginDependenciesProvider::class.java.isAssignableFrom(DebugTimetableApp::class.java))
    }
}
