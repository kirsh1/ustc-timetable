package com.ustc.timetable.school.ustc.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewDisplayModeTest {
    private val mobile = WebViewSettingsSnapshot("mobile-agent", false, false)

    @Test fun desktop_projection_changes_ua_and_enables_wide_overview() {
        val desktop = webViewSettingsFor(WebViewDisplayMode.DESKTOP, mobile)
        assertTrue(desktop.userAgent.contains("X11"))
        assertTrue(desktop.useWideViewPort)
        assertTrue(desktop.loadWithOverviewMode)
    }

    @Test fun mobile_projection_restores_captured_defaults() {
        assertEquals(mobile, webViewSettingsFor(WebViewDisplayMode.MOBILE, mobile))
        assertFalse(webViewSettingsFor(WebViewDisplayMode.MOBILE, mobile).useWideViewPort)
    }
}
