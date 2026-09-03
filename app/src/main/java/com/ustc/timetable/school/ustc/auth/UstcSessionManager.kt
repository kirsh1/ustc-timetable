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

    suspend fun clear() = store.clear()

    suspend fun captureAndVerify(): SessionBlob {
        val targets = descriptor.sessionCookieUrls.distinct()
        val headers = targets.mapNotNull { target ->
            cookies.cookieHeaderFor(target)
                ?.takeUnless(String::isBlank)
                ?.let { cookieHeader ->
                    val credentialSafeRequestUrl = SessionCookieHeader.Scope.of(target).requestUrl()
                    SessionCookieHeader(credentialSafeRequestUrl, cookieHeader)
                }
        }.distinct()

        if (SessionCookieHeader.pickFor(descriptor.probeUrl, headers) == null) {
            throw SyncError.AuthenticationExpired.asFailure()
        }

        val page = fetcher.fetch(descriptor.probeUrl, headers)
        if (page.html.isBlank()) throw SyncError.NetworkFailed.asFailure()
        if (detector.isLoginPage(page.finalUrl, page.html)) {
            throw SyncError.AuthenticationExpired.asFailure()
        }

        return SessionBlob(headers, clock.instant()).also { store.save(it) }
    }
}
