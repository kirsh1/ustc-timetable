package com.ustc.timetable.school.ustc.auth

import android.webkit.CookieManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface CookieRetriever {
    fun cookieHeaderFor(url: String): String?
    suspend fun clearAll() = Unit
}

class WebViewCookieRetriever : CookieRetriever {
    override fun cookieHeaderFor(url: String): String? =
        CookieManager.getInstance().getCookie(url)

    override suspend fun clearAll() {
        val manager = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            manager.removeAllCookies { continuation.resume(Unit) }
        }
        manager.flush()
    }
}
