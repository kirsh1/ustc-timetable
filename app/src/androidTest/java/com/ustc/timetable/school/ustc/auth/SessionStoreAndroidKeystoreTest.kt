package com.ustc.timetable.school.ustc.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreAndroidKeystoreTest {
    @Test
    fun save_and_load_roundtrip_with_android_keystore_key() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = SessionStore(AndroidKeystoreKeyProvider(), storage)
        val expected = SessionBlob(
            headers = listOf(
                SessionCookieHeader(
                    requestUrl = "https://example.invalid/session-scope",
                    cookieHeader = "test_cookie=non_secret_fixture",
                ),
            ),
            capturedAt = Instant.parse("2026-09-03T00:00:00Z"),
        )

        store.save(expected)

        assertEquals(expected, store.load())
    }

    private class InMemorySessionStorage : SessionStorage {
        private var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(data: ByteArray?) {
            bytes = data?.copyOf()
        }
    }
}
