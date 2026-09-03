package com.ustc.timetable

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.ustc.timetable.settings.SettingsViewModel
import com.ustc.timetable.semester.ImportFlowViewModel
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.ui.FirstLaunchViewModel
import com.ustc.timetable.timetable.ui.TimetableViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TimetableApp::class)
class MainActivityLifecycleTest {
    private val app: TimetableApp
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetDatabase() = runBlocking {
        resetRoom()
    }

    @After
    fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun activity_close_clears_activity_owned_viewmodels() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val failIfMissing = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                throw AssertionError("${modelClass.simpleName} is not owned by MainActivity.viewModelStore")
            }
        }
        val provider = ViewModelProvider(activity, failIfMissing)
        assertNotNull(provider[TimetableViewModel::class.java])
        assertNotNull(provider[FirstLaunchViewModel::class.java])
        assertNotNull(provider[SettingsViewModel::class.java])
        assertNotNull(provider[ImportFlowViewModel::class.java])

        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun database_can_be_reset_after_activity_close() = runBlocking {
        seedSemester("lifecycle-reset-a")
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        first.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()

        resetRoom()
        seedSemester("lifecycle-reset-b")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun manual_ui_tests_do_not_share_previous_activity_observers() = runBlocking {
        seedSemester("lifecycle-manual-a")
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        first.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()

        resetRoom()
        seedSemester("lifecycle-manual-b")
        val second = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        second.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun seedSemester(id: String) = withContext(Dispatchers.IO) {
        app.container.semesters.createLocalSemester(
            SemesterDefaults.AUTUMN_2026(
                id = id,
                profileId = "replaced-by-clone",
                now = Instant.parse("2026-09-02T00:00:00Z"),
            ),
            app.container.bundledOfficial,
        )
        app.container.settings.setViewedSemesterId(id)
    }

    private suspend fun resetRoom() = withContext(Dispatchers.IO) {
        app.container.db.clearAllTables()
        app.container.profiles.ensureBundledSeeded()
    }
}
