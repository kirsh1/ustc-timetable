package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.auth.SessionCookieHeader
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.syncErrorOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CookieAwareFetcherTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.close()
    }

    @Test fun portal_client_disables_automatic_redirects() {
        val client = PortalHttpClientFactory.create()
        assertTrue(!client.followRedirects)
        assertTrue(!client.followSslRedirects)
    }

    @Test fun portal_client_has_no_cookie_jar() {
        assertSame(CookieJar.NO_COOKIES, PortalHttpClientFactory.create().cookieJar)
    }

    @Test fun successful_final_response_returns_same_url_and_html() = runBlocking {
        server.enqueue(response(body = "<html>verified</html>"))
        val url = server.url("/probe").toString()
        val page = fetcher().fetch(url, emptyList())
        assertEquals(url, page.finalUrl)
        assertEquals("<html>verified</html>", page.html)
    }

    @Test fun redirect_same_exact_scope_reuses_header() = runBlocking {
        server.enqueue(redirect("/probe"))
        server.enqueue(response())
        val initial = server.url("/probe?step=1").toString()
        val header = SessionCookieHeader(initial, "SESSION=same")
        fetcher().fetch(initial, listOf(header))
        assertEquals("SESSION=same", server.takeRequest().headers["Cookie"])
        assertEquals("SESSION=same", server.takeRequest().headers["Cookie"])
    }

    @Test fun redirect_sibling_path_does_not_reuse_header() = runBlocking {
        server.enqueue(redirect("/sibling"))
        server.enqueue(response())
        val initial = server.url("/probe").toString()
        fetcher().fetch(initial, listOf(SessionCookieHeader(initial, "SESSION=first")))
        assertEquals("SESSION=first", server.takeRequest().headers["Cookie"])
        assertNull(server.takeRequest().headers["Cookie"])
    }

    @Test fun redirect_cross_origin_without_target_header_sends_no_cookie() = runBlocking {
        val second = MockWebServer().apply { start(); enqueue(response()) }
        try {
            server.enqueue(redirect(second.url("/target").toString()))
            val initial = server.url("/probe").toString()
            fetcher().fetch(initial, listOf(SessionCookieHeader(initial, "FIRST=secret")))
            assertNull(second.takeRequest().headers["Cookie"])
        } finally {
            second.close()
        }
    }

    @Test fun redirect_cross_origin_with_explicit_target_header_uses_target_header() = runBlocking {
        val second = MockWebServer().apply { start(); enqueue(response()) }
        try {
            val target = second.url("/target").toString()
            server.enqueue(redirect(target))
            val initial = server.url("/probe").toString()
            fetcher().fetch(
                initial,
                listOf(
                    SessionCookieHeader(initial, "FIRST=secret"),
                    SessionCookieHeader(target, "SECOND=own"),
                ),
            )
            assertEquals("SECOND=own", second.takeRequest().headers["Cookie"])
        } finally {
            second.close()
        }
    }

    @Test fun first_origin_cookie_never_leaks_to_second_origin() = runBlocking {
        val second = MockWebServer().apply { start(); enqueue(response()) }
        try {
            server.enqueue(redirect(second.url("/target").toString()))
            val initial = server.url("/probe").toString()
            fetcher().fetch(initial, listOf(SessionCookieHeader(initial, "FIRST=must-not-leak")))
            val secondCookie = second.takeRequest().headers["Cookie"].orEmpty()
            assertTrue(!secondCookie.contains("must-not-leak"))
        } finally {
            second.close()
        }
    }

    @Test fun five_redirects_allowed() = runBlocking {
        repeat(5) { index -> server.enqueue(redirect("/r${index + 1}")) }
        server.enqueue(response(body = "done"))
        val page = fetcher().fetch(server.url("/start").toString(), emptyList())
        assertEquals("done", page.html)
        assertEquals(6, server.requestCount)
    }

    @Test fun sixth_redirect_rejected() = runBlocking {
        repeat(6) { index -> server.enqueue(redirect("/r${index + 1}")) }
        assertNetworkFailure { fetcher().fetch(server.url("/start").toString(), emptyList()) }
        assertEquals(6, server.requestCount)
    }

    @Test fun redirect_without_location_fails() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).build())
        assertNetworkFailure { fetcher().fetch(server.url("/start").toString(), emptyList()) }
    }

    @Test fun malformed_location_fails() = runBlocking {
        server.enqueue(redirect("http://[invalid"))
        assertNetworkFailure { fetcher().fetch(server.url("/start").toString(), emptyList()) }
    }

    @Test fun redirect_to_non_http_scheme_fails() = runBlocking {
        server.enqueue(redirect("file:///private/session"))
        assertNetworkFailure { fetcher().fetch(server.url("/start").toString(), emptyList()) }
    }

    @Test fun http_500_maps_to_network_failed() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("error page").build())
        assertNetworkFailure { fetcher().fetch(server.url("/probe").toString(), emptyList()) }
    }

    @Test fun io_failure_maps_to_network_failed() = runBlocking {
        val url = server.url("/probe").toString()
        server.close()
        assertNetworkFailure { fetcher().fetch(url, emptyList()) }
        server = MockWebServer().apply { start() }
    }

    @Test fun cancellation_is_not_remapped() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .headersDelay(2, TimeUnit.SECONDS)
                .body("late")
                .build(),
        )
        val fetch = async(Dispatchers.Default) { fetcher().fetch(server.url("/slow").toString(), emptyList()) }
        assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
        fetch.cancel(CancellationException("cancel-probe"))
        val thrown = runCatching { fetch.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertNull(thrown?.syncErrorOrNull())
    }

    private fun fetcher() = CookieAwareFetcher(PortalHttpClientFactory.create())

    private fun response(body: String = "ok") = MockResponse.Builder().code(200).body(body).build()

    private fun redirect(location: String) = MockResponse.Builder()
        .code(302)
        .addHeader("Location", location)
        .build()

    private suspend fun assertNetworkFailure(block: suspend () -> Unit) {
        val error = assertThrows(Exception::class.java) { runBlocking { block() } }
        assertEquals(SyncError.NetworkFailed, error.syncErrorOrNull())
        assertTrue(error.message.orEmpty().contains("NetworkFailed"))
        assertTrue(!error.message.orEmpty().contains("Cookie"))
    }
}
