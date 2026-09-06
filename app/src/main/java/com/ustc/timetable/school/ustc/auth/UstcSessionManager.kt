package com.ustc.timetable.school.ustc.auth

import com.ustc.timetable.school.ustc.portal.CookieAwareFetcher
import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.PortalDescriptorRules
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.asFailure
import java.time.Clock

class UstcSessionManager(
    private val descriptor: PortalDescriptor,
    private val store: SessionStore,
    private val cookies: CookieRetriever,
    private val fetcher: CookieAwareFetcher,
    private val detector: LoginPageDetector,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        PortalDescriptorRules.validate(descriptor)
    }

    suspend fun hasSession(): Boolean = store.load() != null

    suspend fun clear() {
        store.clear()
        cookies.clearAll()
    }

    suspend fun captureAndVerify(completionUrl: String? = null): SessionBlob {
        if (completionUrl != null) {
            require(
                PortalDescriptorRules.isAutomaticCompletionNavigation(descriptor, completionUrl),
            ) { "Completion URL is outside the verified portal scope" }
        }
        val targets = (descriptor.sessionCookieUrls + listOfNotNull(completionUrl)).distinct()
        val headers = targets.mapNotNull(::captureHeaderFor).distinct().toMutableList()

        if (SessionCookieHeader.pickFor(descriptor.probeUrl, headers) == null) {
            throw SyncError.AuthenticationExpired.asFailure()
        }
        if (completionUrl != null && SessionCookieHeader.pickFor(completionUrl, headers) == null) {
            throw SyncError.AuthenticationExpired.asFailure()
        }

        val page = fetcher.fetch(descriptor.probeUrl, headers) { redirectTarget ->
            if (!descriptor.isDynamicSessionCookieUrl(redirectTarget)) return@fetch null
            captureHeaderFor(redirectTarget)?.also { captured ->
                headers.removeAll { existing -> existing.scope == captured.scope }
                headers += captured
            }
        }
        if (page.html.isBlank()) throw SyncError.NetworkFailed.asFailure()
        if (detector.isLoginPage(page.finalUrl, page.html)) {
            throw SyncError.AuthenticationExpired.asFailure()
        }

        return SessionBlob(headers.distinct(), clock.instant()).also { store.save(it) }
    }

    private fun captureHeaderFor(target: String): SessionCookieHeader? =
        cookies.cookieHeaderFor(target)
            ?.takeUnless(String::isBlank)
            ?.let { cookieHeader ->
                val credentialSafeRequestUrl = SessionCookieHeader.Scope.of(target).requestUrl()
                SessionCookieHeader(credentialSafeRequestUrl, cookieHeader)
            }
}
