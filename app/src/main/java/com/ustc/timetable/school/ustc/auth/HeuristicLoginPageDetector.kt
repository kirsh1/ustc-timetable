package com.ustc.timetable.school.ustc.auth

import org.jsoup.Jsoup

/**
 * Detects the evidenced portal login bridge from its configured URL scope and
 * the unified-login control present in the sanitized response fixture.
 */
class HeuristicLoginPageDetector(
    portalLoginUrl: String,
) : LoginPageDetector {
    private val loginScope = SessionCookieHeader.Scope.of(portalLoginUrl)

    override fun isLoginPage(url: String, html: String): Boolean {
        val responseScope = runCatching { SessionCookieHeader.Scope.of(url) }.getOrNull()
            ?: return false
        if (responseScope != loginScope) return false
        if (html.isBlank()) return false

        return runCatching {
            Jsoup.parse(html, url)
                .selectFirst("a#login-unified-wrapper[href]")
                ?.attr("href")
                ?.isNotBlank() == true
        }.getOrDefault(false)
    }
}
