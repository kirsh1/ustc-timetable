package com.ustc.timetable.school.ustc.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UstcFixtureLoaderTest {
    @Test fun fixture_loader_reads_minimal_sample() {
        val html = UstcFixtureLoader.load("sample_minimal.html")

        assertTrue(html.contains("<!doctype html>"))
        assertTrue(html.contains("<html>"))
    }

    @Test fun fixture_loader_missing_file_throws() {
        val error = assertThrows(IllegalStateException::class.java) {
            UstcFixtureLoader.load("does_not_exist.html")
        }

        assertEquals("missing fixture does_not_exist.html", error.message)
    }

    @Test fun fixture_loader_rejects_parent_traversal() {
        listOf("..", "../sample_minimal.html", "..\\sample_minimal.html").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { UstcFixtureLoader.load(name) }
        }
    }

    @Test fun fixture_loader_rejects_nested_path() {
        listOf("", " ", "nested/sample.html", "nested\\sample.html").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { UstcFixtureLoader.load(name) }
        }
    }

    @Test fun sample_fixture_is_explicitly_mechanism_only() {
        val html = UstcFixtureLoader.load("sample_minimal.html")

        assertTrue(html.contains("data-fixture=\"mechanism-only\""))
        assertFalse(html.contains("<form", ignoreCase = true))
        assertFalse(html.contains("<table", ignoreCase = true))
    }

    @Test fun sample_fixture_contains_no_secret_or_identity_material() {
        val html = UstcFixtureLoader.load("sample_minimal.html")

        assertFalse(html.contains("http://", ignoreCase = true))
        assertFalse(html.contains("https://", ignoreCase = true))
        assertFalse(html.contains("password=", ignoreCase = true))
        assertFalse(html.contains("Cookie:", ignoreCase = true))
        assertFalse(Regex("""\bPB\d{8,}\b""", RegexOption.IGNORE_CASE).containsMatchIn(html))
    }
}
