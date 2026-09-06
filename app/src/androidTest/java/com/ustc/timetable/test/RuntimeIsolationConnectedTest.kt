package com.ustc.timetable.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.TimetableApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeIsolationConnectedTest {
    @Test
    fun target_application_is_base_timetable_app() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals(TimetableApp::class.java, app::class.java)
    }
}
