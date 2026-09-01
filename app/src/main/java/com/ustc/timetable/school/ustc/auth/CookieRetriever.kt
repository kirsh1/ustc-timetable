package com.ustc.timetable.school.ustc.auth

import android.webkit.CookieManager

fun interface CookieRetriever {
    fun cookieHeaderFor(url: String): String?
}

class WebViewCookieRetriever : CookieRetriever {
    override fun cookieHeaderFor(url: String): String? =
        CookieManager.getInstance().getCookie(url)
}
