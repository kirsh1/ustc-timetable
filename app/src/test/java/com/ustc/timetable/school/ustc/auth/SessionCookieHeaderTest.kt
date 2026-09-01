package com.ustc.timetable.school.ustc.auth

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieHeaderTest {

    @Test fun scope_normalizes_scheme_and_host_case() {
        assertEquals(
            SessionCookieHeader.Scope("https", "example.com", 443, "/Course"),
            SessionCookieHeader.Scope.of("HTTPS://Example.COM/Course"),
        )
    }

    @Test fun default_https_port_equals_explicit_443() {
        assertEquals(
            SessionCookieHeader.Scope.of("https://example.com/course"),
            SessionCookieHeader.Scope.of("https://example.com:443/course"),
        )
    }

    @Test fun default_http_port_equals_explicit_80() {
        assertEquals(
            SessionCookieHeader.Scope.of("http://example.com/course"),
            SessionCookieHeader.Scope.of("http://example.com:80/course"),
        )
    }

    @Test fun empty_path_normalizes_to_slash() {
        assertEquals("/", SessionCookieHeader.Scope.of("https://example.com").path)
    }

    @Test fun query_does_not_affect_scope() {
        assertEquals(
            SessionCookieHeader.Scope.of("https://example.com/course?a=1"),
            SessionCookieHeader.Scope.of("https://example.com/course?a=2"),
        )
    }

    @Test fun fragment_does_not_affect_scope() {
        assertEquals(
            SessionCookieHeader.Scope.of("https://example.com/course#one"),
            SessionCookieHeader.Scope.of("https://example.com/course#two"),
        )
    }

    @Test fun trailing_slash_is_distinct() {
        assertTrue(
            SessionCookieHeader.Scope.of("https://example.com/course") !=
                SessionCookieHeader.Scope.of("https://example.com/course/"),
        )
    }

    @Test fun encoded_path_is_not_decoded() {
        assertEquals("/%7Ealice", SessionCookieHeader.Scope.of("https://example.com/%7Ealice").path)
        assertTrue(
            SessionCookieHeader.Scope.of("https://example.com/%7Ealice") !=
                SessionCookieHeader.Scope.of("https://example.com/~alice"),
        )
    }

    @Test fun unsupported_scheme_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCookieHeader.Scope.of("ftp://example.com/course")
        }
    }

    @Test fun missing_host_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCookieHeader.Scope.of("https:/course")
        }
    }

    @Test fun userinfo_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCookieHeader.Scope.of("https://student@example.com/course")
        }
    }

    @Test fun duplicate_cookie_names_are_preserved_verbatim() {
        val raw = "A=1; A=2; B=3"
        assertEquals(raw, SessionCookieHeader("https://example.com/course", raw).cookieHeader)
    }

    @Test fun cookie_header_order_is_preserved() {
        val raw = "Z=9; A=1; M=5"
        assertEquals(raw, SessionCookieHeader("https://example.com/course", raw).cookieHeader)
    }

    @Test fun blank_cookie_header_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCookieHeader("https://example.com/course", "  ")
        }
    }

    @Test fun pick_exact_scope_match() {
        val wanted = SessionCookieHeader("https://example.com/course", "A=1")
        val sibling = SessionCookieHeader("https://example.com/other", "B=2")
        assertSame(wanted, SessionCookieHeader.pickFor("https://example.com/course", listOf(sibling, wanted)))
    }

    @Test fun sibling_path_not_reused() {
        val header = SessionCookieHeader("https://example.com/course", "A=1")
        assertNull(SessionCookieHeader.pickFor("https://example.com/courses", listOf(header)))
    }

    @Test fun different_host_not_reused() {
        val header = SessionCookieHeader("https://one.example/course", "A=1")
        assertNull(SessionCookieHeader.pickFor("https://two.example/course", listOf(header)))
    }

    @Test fun different_port_not_reused() {
        val header = SessionCookieHeader("https://example.com:8443/course", "A=1")
        assertNull(SessionCookieHeader.pickFor("https://example.com/course", listOf(header)))
    }

    @Test fun different_scheme_not_reused() {
        val header = SessionCookieHeader("http://example.com/course", "A=1")
        assertNull(SessionCookieHeader.pickFor("https://example.com/course", listOf(header)))
    }

    @Test fun same_path_different_query_matches() {
        val header = SessionCookieHeader("https://example.com/course?a=1", "A=1")
        assertSame(header, SessionCookieHeader.pickFor("https://example.com/course?a=2", listOf(header)))
    }

    @Test fun duplicate_same_scope_same_header_is_safe() {
        val first = SessionCookieHeader("https://example.com/course?a=1", "A=1; B=2")
        val second = SessionCookieHeader("https://example.com/course?a=2", "A=1; B=2")
        assertSame(first, SessionCookieHeader.pickFor("https://example.com/course?a=3", listOf(first, second)))
    }

    @Test fun conflicting_same_scope_headers_fail_closed() {
        val first = SessionCookieHeader("https://example.com/course?a=1", "A=1")
        val second = SessionCookieHeader("https://example.com/course?a=2", "A=2")
        assertNull(SessionCookieHeader.pickFor("https://example.com/course?a=3", listOf(first, second)))
    }

    @Test fun header_toString_redacts_cookie_value() {
        val text = SessionCookieHeader("https://example.com/course", "secret-cookie-value").toString()
        assertFalse(text.contains("secret-cookie-value"))
        assertTrue(text.contains("<redacted>"))
    }

    @Test fun blob_toString_does_not_contain_cookie_value() {
        val blob = SessionBlob(
            listOf(SessionCookieHeader("https://example.com/course", "secret-cookie-value")),
            Instant.parse("2026-09-01T00:00:00Z"),
        )
        assertFalse(blob.toString().contains("secret-cookie-value"))
    }
}
