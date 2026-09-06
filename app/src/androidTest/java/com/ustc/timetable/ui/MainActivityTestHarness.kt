package com.ustc.timetable.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.MainActivity

internal class MainActivityTestHarness(private val compose: ComposeTestRule) {
    private var scenario: ActivityScenario<MainActivity>? = null

    fun launch() {
        check(scenario == null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    fun close() {
        scenario?.close()
        scenario = null
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    fun recreate() {
        checkNotNull(scenario).recreate()
        compose.waitForIdle()
    }

    fun fontScale(): Float {
        var scale = Float.NaN
        checkNotNull(scenario).onActivity { scale = it.resources.configuration.fontScale }
        return scale
    }
}
