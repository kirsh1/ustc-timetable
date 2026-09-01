package com.ustc.timetable.school.ustc.portal

import okhttp3.CookieJar
import okhttp3.OkHttpClient

object PortalHttpClientFactory {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()
}
