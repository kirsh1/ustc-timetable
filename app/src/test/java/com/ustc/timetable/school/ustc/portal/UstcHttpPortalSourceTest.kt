package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.auth.HeuristicLoginPageDetector
import com.ustc.timetable.school.ustc.auth.CookieRetriever
import com.ustc.timetable.school.ustc.auth.SecretKeyProvider
import com.ustc.timetable.school.ustc.auth.SessionBlob
import com.ustc.timetable.school.ustc.auth.SessionCookieHeader
import com.ustc.timetable.school.ustc.auth.SessionStorage
import com.ustc.timetable.school.ustc.auth.SessionStore
import com.ustc.timetable.school.ustc.auth.UstcSessionManager
import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.parser.UstcCourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.UstcTimetablePageParser
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.syncErrorOrNull
import java.time.Instant
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UstcHttpPortalSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var descriptor: UstcPortalDescriptor

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        descriptor = UstcPortalDescriptor(server.url("/").toString())
    }

    @After fun tearDown() {
        runCatching { server.close() }
    }

    @Test fun fetches_four_evidenced_posts_and_returns_separate_authoritative_pages() = runBlocking {
        enqueueSuccessfulBundle()
        val source = sourceWithSession(scopedHeaders())

        val selection = source.fetchCourseSelectionPage()
        val timetable = source.fetchTimetablePage()

        assertNull(selection.document)
        assertEquals(setOf(UstcEndpointId.SELECTED_LESSONS), selection.xhr.keys)
        assertNull(timetable.document)
        assertEquals(
            setOf(
                UstcEndpointId.TIMETABLE_LAYOUT,
                UstcEndpointId.TIMETABLE_DATUM,
                UstcEndpointId.WEEK_INDICES_DIGEST,
            ),
            timetable.xhr.keys,
        )

        val discovery = server.takeRequest()
        val layout = server.takeRequest()
        val selected = server.takeRequest()
        val datum = server.takeRequest()
        val digest = server.takeRequest()

        assertRequest(discovery, "GET", "/for-std/course-select", "text/html")
        assertRequest(layout, "POST", "/ws/schedule-table/timetable-layout", "application/json")
        assertRequest(selected, "POST", "/ws/for-std/course-select/selected-lessons", "application/json")
        assertRequest(datum, "POST", "/ws/schedule-table/datum", "application/json")
        assertRequest(digest, "POST", "/ws/schedule-table/week-indices-digest", "application/json")

        assertEquals(JsonObject(mapOf("timeTableLayoutId" to JsonPrimitive(1))), jsonObject(layout))
        assertEquals("studentId=101&turnId=202", selected.body?.utf8())
        assertTrue(selected.headers["Content-Type"].orEmpty().startsWith("application/x-www-form-urlencoded"))
        assertEquals(
            JsonObject(
                mapOf(
                    "lessonIds" to JsonArray(listOf(JsonPrimitive(301))),
                    "studentId" to JsonPrimitive(101),
                ),
            ),
            jsonObject(datum),
        )
        assertEquals(
            JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "weekIndicesGroupId" to JsonPrimitive("c1"),
                            "weekIndices" to JsonArray(listOf(JsonPrimitive(2), JsonPrimitive(3))),
                        ),
                    ),
                ),
            ),
            Json.parseToJsonElement(digest.body?.utf8().orEmpty()),
        )
        assertEquals("COOKIE=layout", layout.headers["Cookie"])
        assertEquals("COOKIE=selected", selected.headers["Cookie"])
        assertEquals("COOKIE=datum", datum.headers["Cookie"])
        assertEquals("COOKIE=digest", digest.headers["Cookie"])
        assertTrue(layout.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertTrue(datum.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertTrue(digest.headers["Content-Type"].orEmpty().startsWith("application/json"))

        val selectedResponse = selection.xhr.getValue(UstcEndpointId.SELECTED_LESSONS)
        assertEquals(descriptor.selectedLessonsUrl, selectedResponse.requestUrl)
        assertEquals(descriptor.selectedLessonsUrl, selectedResponse.finalUrl)
        assertEquals("application/json; charset=utf-8", selectedResponse.contentType)
    }

    @Test fun discovery_redirect_reselects_the_exact_target_cookie() = runBlocking {
        val redirectedUrl = server.url("/for-std/course-select/turns/101").toString()
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", redirectedUrl).build(),
        )
        server.enqueue(htmlResponse(discoveryHtml()))
        enqueuePostResponses()
        val headers = scopedHeaders() + SessionCookieHeader(redirectedUrl, "COOKIE=redirected")

        sourceWithSession(headers).fetchCourseSelectionPage()

        assertEquals("COOKIE=discovery", server.takeRequest().headers["Cookie"])
        assertEquals("COOKIE=redirected", server.takeRequest().headers["Cookie"])
    }

    @Test fun real_capture_path_persists_dynamic_landing_scope_for_source_redirect() = runBlocking {
        val redirectedUrl = server.url("/for-std/course-select/turns/101").toString()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", redirectedUrl).build())
        server.enqueue(htmlResponse(discoveryHtml()))
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", redirectedUrl).build())
        server.enqueue(htmlResponse(discoveryHtml()))
        enqueuePostResponses()
        val cookies = RecordingCookies(
            scopedHeaders().associate { it.requestUrl to it.cookieHeader } +
                (redirectedUrl to "COOKIE=dynamic-capture"),
        )
        val store = SessionStore(FixedKeyProvider(), InMemorySessionStorage())
        val detector = HeuristicLoginPageDetector(descriptor.loginUrl)
        val fetcher = CookieAwareFetcher(PortalHttpClientFactory.create())
        val sessionManager = UstcSessionManager(
            descriptor = descriptor,
            store = store,
            cookies = cookies,
            fetcher = fetcher,
            detector = detector,
        )

        val captured = sessionManager.captureAndVerify()
        val source = UstcHttpPortalSource(descriptor, store, detector, fetcher)
        source.fetchCourseSelectionPage()

        assertEquals(descriptor.sessionCookieUrls + redirectedUrl, cookies.urls)
        assertEquals(redirectedUrl, captured.headers.last().requestUrl)
        assertEquals("COOKIE=discovery", server.takeRequest().headers["Cookie"])
        assertEquals("COOKIE=dynamic-capture", server.takeRequest().headers["Cookie"])
        assertEquals("COOKIE=discovery", server.takeRequest().headers["Cookie"])
        assertEquals("COOKIE=dynamic-capture", server.takeRequest().headers["Cookie"])
    }

    @Test fun login_redirect_to_successful_login_html_is_authentication_expired() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/login").build())
        server.enqueue(
            htmlResponse("<a id=\"login-unified-wrapper\" href=\"/cas\">login</a>"),
        )
        val headers = scopedHeaders() + SessionCookieHeader(descriptor.loginUrl, "COOKIE=login")

        assertSyncError(SyncError.AuthenticationExpired) {
            sourceWithSession(headers).fetchCourseSelectionPage()
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun absent_encrypted_session_is_authentication_expired_without_a_request() = runBlocking {
        assertSyncError(SyncError.AuthenticationExpired) {
            sourceWithSession(null).fetchCourseSelectionPage()
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun transport_failure_remains_network_failed() = runBlocking {
        val source = sourceWithSession(scopedHeaders())
        server.close()

        assertSyncError(SyncError.NetworkFailed) { source.fetchCourseSelectionPage() }
    }

    @Test fun malformed_success_is_returned_to_the_phase_one_parser_as_parse_failed() = runBlocking {
        enqueueSuccessfulBundle(selectedBody = "[{\"id\":301}]")
        val selection = sourceWithSession(scopedHeaders()).fetchCourseSelectionPage()

        assertSyncError(SyncError.ParseFailed) {
            UstcCourseSelectionPageParser().parse(selection)
        }
    }

    @Test fun committed_sanitized_responses_cross_the_source_parser_boundary() = runBlocking {
        server.enqueue(htmlResponse(discoveryHtml()))
        server.enqueue(jsonResponse(fixture("response-01-timetable-layout.json")))
        server.enqueue(jsonResponse(fixture("response-02-selected-lessons.json")))
        server.enqueue(jsonResponse(fixture("response-03-datum.json")))
        server.enqueue(jsonResponse(fixture("response-04-week-indices-digest.json")))
        val source = sourceWithSession(scopedHeaders())

        val courses = UstcCourseSelectionPageParser().parse(source.fetchCourseSelectionPage())
        val meetings = UstcTimetablePageParser().parse(source.fetchTimetablePage())

        assertTrue(courses.isNotEmpty())
        assertTrue(meetings.isNotEmpty())
    }

    private suspend fun sourceWithSession(headers: List<SessionCookieHeader>?): UstcHttpPortalSource {
        val store = SessionStore(FixedKeyProvider(), InMemorySessionStorage())
        headers?.let {
            store.save(SessionBlob(it, Instant.parse("2026-09-03T00:00:00Z")))
        }
        return UstcHttpPortalSource(
            descriptor = descriptor,
            session = store,
            detector = HeuristicLoginPageDetector(descriptor.loginUrl),
            fetcher = CookieAwareFetcher(PortalHttpClientFactory.create()),
        )
    }

    private fun scopedHeaders(): List<SessionCookieHeader> = listOf(
        SessionCookieHeader(descriptor.currentTurnDiscoveryUrl, "COOKIE=discovery"),
        SessionCookieHeader(descriptor.timetableLayoutUrl, "COOKIE=layout"),
        SessionCookieHeader(descriptor.selectedLessonsUrl, "COOKIE=selected"),
        SessionCookieHeader(descriptor.timetableDatumUrl, "COOKIE=datum"),
        SessionCookieHeader(descriptor.weekIndicesDigestUrl, "COOKIE=digest"),
    )

    private fun enqueueSuccessfulBundle(selectedBody: String = SELECTED_BODY) {
        server.enqueue(htmlResponse(discoveryHtml()))
        enqueuePostResponses(selectedBody)
    }

    private fun enqueuePostResponses(selectedBody: String = SELECTED_BODY) {
        server.enqueue(jsonResponse(LAYOUT_BODY))
        server.enqueue(jsonResponse(selectedBody))
        server.enqueue(jsonResponse(DATUM_BODY))
        server.enqueue(jsonResponse(DIGEST_BODY))
    }

    private fun discoveryHtml(): String = """
        <div class="col-sm-3 text-center">
          <a class="btn btn-primary"
             href="/for-std/course-select/101/turn/202/select">进入选课</a>
        </div>
    """.trimIndent()

    private fun assertRequest(request: RecordedRequest, method: String, path: String, accept: String) {
        assertEquals(method, request.method)
        assertEquals(path, request.url.encodedPath)
        assertNull(request.url.encodedQuery)
        assertEquals(accept, request.headers["Accept"])
        assertNull(request.headers["Referer"])
        assertNull(request.headers["Origin"])
        assertNull(request.headers["Sec-Fetch-Site"])
    }

    private fun jsonObject(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body?.utf8().orEmpty()) as JsonObject

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader?.getResource("fixtures/ustc/xhr/$name"),
    ).readText()

    private suspend fun assertSyncError(expected: SyncError, block: suspend () -> Unit) {
        val thrown = assertThrows(Exception::class.java) { runBlocking { block() } }
        assertEquals(expected, thrown.syncErrorOrNull())
    }

    private fun htmlResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .body(body)
        .build()

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .body(body)
        .build()

    private class InMemorySessionStorage : SessionStorage {
        private var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(data: ByteArray?) {
            bytes = data?.copyOf()
        }
    }

    private class FixedKeyProvider : SecretKeyProvider {
        private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

        override fun getOrCreateKey(): SecretKey = key
    }

    private class RecordingCookies(private val values: Map<String, String?>) : CookieRetriever {
        val urls = mutableListOf<String>()

        override fun cookieHeaderFor(url: String): String? {
            urls += url
            return values[url]
        }
    }

    private companion object {
        const val SELECTED_BODY = """[{"id":301}]"""
        const val LAYOUT_BODY = """{"result":{"courseUnitList":[]}}"""
        const val DATUM_BODY = """{"result":{"scheduleList":[{"lessonId":301,"scheduleGroupId":401,"startTime":480,"endTime":570,"weekday":1,"weekIndex":2},{"lessonId":301,"scheduleGroupId":401,"startTime":480,"endTime":570,"weekday":1,"weekIndex":3}]}}"""
        const val DIGEST_BODY = """{"result":{"c1":"2~3"}}"""
    }
}
