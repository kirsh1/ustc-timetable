package com.ustc.timetable.school.ustc.auth

enum class WebViewDisplayMode { MOBILE, DESKTOP }

data class WebViewSettingsSnapshot(
    val userAgent: String,
    val useWideViewPort: Boolean,
    val loadWithOverviewMode: Boolean,
)

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36"

fun webViewSettingsFor(mode: WebViewDisplayMode, capturedMobile: WebViewSettingsSnapshot): WebViewSettingsSnapshot =
    when (mode) {
        WebViewDisplayMode.MOBILE -> capturedMobile
        WebViewDisplayMode.DESKTOP -> WebViewSettingsSnapshot(DESKTOP_USER_AGENT, true, true)
    }
