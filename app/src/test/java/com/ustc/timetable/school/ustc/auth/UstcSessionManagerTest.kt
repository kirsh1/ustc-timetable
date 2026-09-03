package com.ustc.timetable.school.ustc.auth

import com.ustc.timetable.school.ustc.portal.CookieAwareFetcher
import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.PortalHttpClientFactory
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.syncErrorOrNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UstcSessionManagerTest {
    private class MemoryStorage : SessionStorage {
        var bytes: ByteArray? = null
        var writes = 0
        override fun read(): ByteArray? = bytes?.copyOf()
        override fun write(data: ByteArray?) {
            writes++
            bytes = data?.copyOf()
        }
    }

    private class KeyProvider : SecretKeyProvider {
        override fun getOrCreateKey(): SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    }

    private class RecordingCookies(private val values: Map<String, String?>) : CookieRetriever {
        val urls = mutableListOf<String>()
        override fun cookieHeaderFor(url: String): String? {
            urls += url
            return values[url]
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var storage: MemoryStorage
    private lateinit var store: SessionStore
    private val fixedInstant = Instant.parse("2026-09-01T03:04:05.006Z")

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        storage = MemoryStorage()
        store = SessionStore(KeyProvider(), storage)
    }

    @After fun tearDown() {
        server.close()
    }

    @Test fun capture_reads_every_configured_document_and_xhr_url() = runBlocking {
        server.enqueue(okPage())
        val probe = server.url("/probe").toString()
        val targets = listOf(
            probe,
            server.url("/selection-document").toString(),
            server.url("/timetable-document").toString(),
            server.url("/xhr/layout").toString(),
            server.url("/xhr/selected-lessons").toString(),
            server.url("/xhr/datum").toString(),
            server.url("/xhr/week-digest").toString(),
        )
        val descriptor = descriptor(
            probeUrl = probe,
            selectionUrl = targets[1],
            timetableUrl = targets[2],
            sessionCookieUrls = targets,
        )
        val cookies = RecordingCookies(allHeaders(descriptor))
        manager(descriptor, cookies).captureAndVerify()
        assertEquals(targets, cookies.urls)
    }

    @Test fun capture_strips_query_values_before_persisting_request_scope() = runBlocking {
        server.enqueue(okPage())
        val probeWithQuery = server.url("/probe?ticket=%3Credacted%3E&mode=current").toString()
        val descriptor = descriptor(probeUrl = probeWithQuery)
        val cookies = RecordingCookies(mapOf(probeWithQuery to "SESSION=fixture"))

        val blob = manager(descriptor, cookies).captureAndVerify()

        assertEquals(descriptor.sessionCookieUrls, cookies.urls)
        assertEquals(server.url("/probe").toString(), blob.headers.single().requestUrl)
        assertFalse(blob.headers.single().requestUrl.contains('?'))
        assertFalse(blob.headers.single().requestUrl.contains("ticket", ignoreCase = true))
    }

    @Test fun capture_preserves_cross_host_headers() = runBlocking {
        server.enqueue(okPage())
        val descriptor = descriptor()
        val blob = manager(descriptor, RecordingCookies(allHeaders(descriptor))).captureAndVerify()
        assertEquals(
            listOf(descriptor.probeUrl, descriptor.selectionUrl, descriptor.timetableUrl),
            blob.headers.map { it.requestUrl },
        )
        assertEquals(listOf("PROBE=1", "SELECT=2", "TABLE=3"), blob.headers.map { it.cookieHeader })
    }

    @Test fun capture_dedupes_only_identical_url_header_pairs() = runBlocking {
        server.enqueue(okPage())
        val probe = server.url("/probe").toString()
        val descriptor = descriptor(probeUrl = probe, selectionUrl = probe)
        val blob = manager(descriptor, RecordingCookies(allHeaders(descriptor))).captureAndVerify()
        assertEquals(2, blob.headers.size)
        assertEquals(listOf(probe, descriptor.timetableUrl), blob.headers.map { it.requestUrl })
    }

    @Test fun duplicate_cookie_names_preserved_verbatim() = runBlocking {
        server.enqueue(okPage())
        val descriptor = descriptor()
        val raw = "A=1; A=2; B=3"
        val values = allHeaders(descriptor).toMutableMap().apply { put(descriptor.probeUrl, raw) }
        val blob = manager(descriptor, RecordingCookies(values)).captureAndVerify()
        assertEquals(raw, blob.headers.first().cookieHeader)
        assertEquals(raw, server.takeRequest().headers["Cookie"])
    }

    @Test fun blank_cookie_retrieval_is_ignored() = runBlocking {
        server.enqueue(okPage())
        val descriptor = descriptor()
        val values = allHeaders(descriptor).toMutableMap().apply { put(descriptor.selectionUrl, "  ") }
        val blob = manager(descriptor, RecordingCookies(values)).captureAndVerify()
        assertEquals(listOf(descriptor.probeUrl, descriptor.timetableUrl), blob.headers.map { it.requestUrl })
    }

    @Test fun no_cookie_headers_throws_auth_expired_without_network() = runBlocking {
        val descriptor = descriptor()
        assertAuthExpired { manager(descriptor, RecordingCookies(emptyMap())).captureAndVerify() }
        assertEquals(0, server.requestCount)
        assertEquals(0, storage.writes)
    }

    @Test fun no_probe_scope_header_throws_auth_expired_without_network() = runBlocking {
        val descriptor = descriptor()
        val values = mapOf(descriptor.selectionUrl to "SELECT=2", descriptor.timetableUrl to "TABLE=3")
        assertAuthExpired { manager(descriptor, RecordingCookies(values)).captureAndVerify() }
        assertEquals(0, server.requestCount)
        assertEquals(0, storage.writes)
    }

    @Test fun conflicting_probe_scope_headers_fail_closed_without_network() = runBlocking {
        val base = server.url("/probe").toString()
        val descriptor = descriptor(probeUrl = "$base?a=1", selectionUrl = "$base?a=2")
        val values = mapOf(
            descriptor.probeUrl to "SESSION=one",
            descriptor.selectionUrl to "SESSION=two",
            descriptor.timetableUrl to "TABLE=3",
        )
        assertAuthExpired { manager(descriptor, RecordingCookies(values)).captureAndVerify() }
        assertEquals(0, server.requestCount)
        assertEquals(0, storage.writes)
    }

    @Test fun detector_receives_same_response_final_url_and_html() = runBlocking {
        val finalUrl = server.url("/final").toString()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", finalUrl).build())
        server.enqueue(okPage("<html>same-pair</html>"))
        val descriptor = descriptor()
        val seen = mutableListOf<Pair<String, String>>()
        manager(descriptor, RecordingCookies(allHeaders(descriptor))) { url, html ->
            seen += url to html
            false
        }.captureAndVerify()
        assertEquals(listOf(finalUrl to "<html>same-pair</html>"), seen)
    }

    @Test fun login_page_is_not_saved() = runBlocking {
        server.enqueue(okPage("login"))
        val descriptor = descriptor()
        assertAuthExpired {
            manager(descriptor, RecordingCookies(allHeaders(descriptor))) { _, _ -> true }.captureAndVerify()
        }
        assertNull(storage.bytes)
    }

    @Test fun verified_probe_saves_blob() = runBlocking {
        server.enqueue(okPage("verified"))
        val descriptor = descriptor()
        val expected = manager(descriptor, RecordingCookies(allHeaders(descriptor))).captureAndVerify()
        assertEquals(expected, store.load())
        assertNotNull(storage.bytes)
    }

    @Test fun captured_at_comes_from_injected_clock() = runBlocking {
        server.enqueue(okPage())
        val descriptor = descriptor()
        val blob = manager(descriptor, RecordingCookies(allHeaders(descriptor))).captureAndVerify()
        assertEquals(fixedInstant, blob.capturedAt)
    }

    @Test fun blank_probe_html_is_network_failed_and_not_saved() = runBlocking {
        server.enqueue(okPage(""))
        val descriptor = descriptor()
        val error = assertThrows(Exception::class.java) {
            runBlocking { manager(descriptor, RecordingCookies(allHeaders(descriptor))).captureAndVerify() }
        }
        assertEquals(SyncError.NetworkFailed, error.syncErrorOrNull())
        assertNull(storage.bytes)
    }

    @Test fun detector_login_failure_does_not_overwrite_existing_session() = runBlocking {
        val before = saveOldSession()
        server.enqueue(okPage("login"))
        val descriptor = descriptor()
        assertAuthExpired {
            manager(descriptor, RecordingCookies(allHeaders(descriptor))) { _, _ -> true }.captureAndVerify()
        }
        assertArrayEquals(before, storage.bytes)
    }

    @Test fun network_failure_does_not_overwrite_existing_session() = runBlocking {
        val before = saveOldSession()
        server.enqueue(MockResponse.Builder().code(500).build())
        val descriptor = descriptor()
        assertThrows(Exception::class.java) {
            runBlocking { manager(descriptor, RecordingCookies(allHeaders(descriptor))).captureAndVerify() }
        }
        assertArrayEquals(before, storage.bytes)
    }

    @Test fun missing_cookie_failure_does_not_overwrite_existing_session() = runBlocking {
        val before = saveOldSession()
        val descriptor = descriptor()
        assertAuthExpired { manager(descriptor, RecordingCookies(emptyMap())).captureAndVerify() }
        assertArrayEquals(before, storage.bytes)
    }

    @Test fun has_session_and_clear_use_suspend_store_contract() = runBlocking {
        val descriptor = descriptor()
        val manager = manager(descriptor, RecordingCookies(emptyMap()))
        assertFalse(manager.hasSession())
        store.save(SessionBlob(listOf(SessionCookieHeader(descriptor.probeUrl, "OLD=1")), fixedInstant))
        assertTrue(manager.hasSession())
        manager.clear()
        assertFalse(manager.hasSession())
    }

    private fun manager(
        descriptor: PortalDescriptor,
        cookies: CookieRetriever,
        detector: LoginPageDetector = LoginPageDetector { _, _ -> false },
    ) = UstcSessionManager(
        descriptor = descriptor,
        store = store,
        cookies = cookies,
        fetcher = CookieAwareFetcher(PortalHttpClientFactory.create()),
        detector = detector,
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
    )

    private fun descriptor(
        probeUrl: String = server.url("/probe").toString(),
        selectionUrl: String = "https://selection.fixture.example/course",
        timetableUrl: String = "https://timetable.fixture.example/table",
        sessionCookieUrls: List<String>? = null,
    ): PortalDescriptor = object : PortalDescriptor {
        override val loginUrl = "https://login.fixture.example/login"
        override val probeUrl = probeUrl
        override val selectionUrl = selectionUrl
        override val timetableUrl = timetableUrl
        override val sessionHosts = listOf(server.hostName)
        override val sessionCookieUrls = sessionCookieUrls
            ?: listOf(probeUrl, selectionUrl, timetableUrl)
    }

    private fun allHeaders(descriptor: PortalDescriptor): Map<String, String> =
        descriptor.sessionCookieUrls.associateWith { target ->
            when (target) {
                descriptor.probeUrl -> "PROBE=1"
                descriptor.selectionUrl -> "SELECT=2"
                descriptor.timetableUrl -> "TABLE=3"
                else -> "ENDPOINT=fixture"
            }
        }

    private fun okPage(body: String = "<html>ok</html>") = MockResponse.Builder().code(200).body(body).build()

    private suspend fun saveOldSession(): ByteArray {
        store.save(
            SessionBlob(
                listOf(SessionCookieHeader("https://old.fixture.example/session", "OLD=preserve")),
                Instant.EPOCH,
            ),
        )
        return storage.bytes!!.copyOf()
    }

    private suspend fun assertAuthExpired(block: suspend () -> Unit) {
        val error = assertThrows(Exception::class.java) { runBlocking { block() } }
        assertEquals(SyncError.AuthenticationExpired, error.syncErrorOrNull())
    }
}
