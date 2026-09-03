package com.ustc.timetable.school.ustc.auth

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {
    private class InMemorySessionStorage : SessionStorage {
        var bytes: ByteArray? = null
        val threads = mutableListOf<String>()

        override fun read(): ByteArray? {
            threads += Thread.currentThread().name
            return bytes?.copyOf()
        }

        override fun write(data: ByteArray?) {
            threads += Thread.currentThread().name
            bytes = data?.copyOf()
        }
    }

    private class FixedKeyProvider(keyBytes: ByteArray) : SecretKeyProvider {
        private val key = SecretKeySpec(keyBytes, "AES")
        val threads = mutableListOf<String>()

        override fun getOrCreateKey(): SecretKey {
            threads += Thread.currentThread().name
            return key
        }
    }

    private val keyA = ByteArray(32) { it.toByte() }
    private val keyB = ByteArray(32) { (it + 1).toByte() }
    private val blob = SessionBlob(
        headers = listOf(
            SessionCookieHeader("https://one.example/course", "A=1; A=2; B=3"),
            SessionCookieHeader("https://two.example/timetable", "SESSION=opaque value"),
        ),
        capturedAt = Instant.parse("2026-09-01T12:34:56.789Z"),
    )

    private fun store(
        storage: InMemorySessionStorage,
        key: ByteArray = keyA,
    ) = SessionStore(FixedKeyProvider(key), storage)

    @Test fun roundtrip_works_with_test_secret_key() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        assertEquals(blob, store.load())
    }

    @Test fun roundtrip_preserves_header_list_order_and_raw_values() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        val loaded = store.load()!!
        assertEquals(blob.headers.map { it.requestUrl }, loaded.headers.map { it.requestUrl })
        assertEquals(listOf("A=1; A=2; B=3", "SESSION=opaque value"), loaded.headers.map { it.cookieHeader })
    }

    @Test fun persisted_session_header_keeps_only_scope_url_without_query_or_fragment() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        val queryBearing = SessionBlob(
            headers = listOf(
                SessionCookieHeader(
                    "https://one.example/callback?ticket=%3Credacted%3E&mode=current#fragment",
                    "A=1",
                ),
            ),
            capturedAt = Instant.EPOCH,
        )

        store.save(queryBearing)

        assertEquals("https://one.example/callback", store.load()!!.headers.single().requestUrl)
    }

    @Test fun same_blob_two_saves_have_different_ciphertext() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        val first = storage.bytes!!.copyOf()
        store.save(blob)
        assertFalse(first.contentEquals(storage.bytes!!))
    }

    @Test fun one_byte_ciphertext_tamper_fails() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        storage.bytes!![storage.bytes!!.lastIndex] = (storage.bytes!!.last().toInt() xor 1).toByte()
        assertCorrupt { store.load() }
    }

    @Test fun one_byte_iv_tamper_fails() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        storage.bytes!![9] = (storage.bytes!![9].toInt() xor 1).toByte()
        assertCorrupt { store.load() }
    }

    @Test fun wrong_key_fails() = runBlocking {
        val storage = InMemorySessionStorage()
        store(storage, keyA).save(blob)
        assertCorrupt { store(storage, keyB).load() }
    }

    @Test fun envelope_has_locked_magic_version_and_twelve_byte_iv() = runBlocking {
        val storage = InMemorySessionStorage()
        store(storage).save(blob)
        val raw = storage.bytes!!
        assertArrayEquals("USTCSES1".toByteArray(StandardCharsets.US_ASCII), raw.copyOfRange(0, 8))
        assertEquals(1, raw[8].toInt())
        assertTrue(raw.size >= 8 + 1 + 12 + 16)
    }

    @Test fun bad_magic_rejected() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        storage.bytes!![0] = 'X'.code.toByte()
        assertCorrupt { store.load() }
    }

    @Test fun unsupported_version_rejected() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        storage.bytes!![8] = 2
        assertCorrupt { store.load() }
    }

    @Test fun truncated_envelope_rejected() = runBlocking {
        val storage = InMemorySessionStorage().apply { bytes = ByteArray(36) }
        assertCorrupt { store(storage).load() }
    }

    @Test fun envelope_header_tamper_fails() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        storage.bytes!![7] = (storage.bytes!![7].toInt() xor 1).toByte()
        assertCorrupt { store.load() }
    }

    @Test fun stored_bytes_do_not_contain_cookie_plaintext() = runBlocking {
        val storage = InMemorySessionStorage()
        store(storage).save(blob)
        val persisted = storage.bytes!!.toString(StandardCharsets.ISO_8859_1)
        assertFalse(persisted.contains("A=1; A=2; B=3"))
        assertFalse(persisted.contains("https://one.example/course"))
        assertFalse(persisted.contains("2026-09-01"))
    }

    @Test fun missing_storage_returns_null() = runBlocking {
        assertNull(store(InMemorySessionStorage()).load())
    }

    @Test fun corrupt_storage_throws_not_null() = runBlocking {
        val storage = InMemorySessionStorage().apply { bytes = "not-an-envelope".toByteArray() }
        assertCorrupt { store(storage).load() }
    }

    @Test fun malformed_decrypted_json_throws_corruption() = runBlocking {
        val storage = InMemorySessionStorage().apply { bytes = encryptRaw("not-json".toByteArray(), keyA) }
        assertCorrupt { store(storage).load() }
    }

    @Test fun invalid_header_url_in_decrypted_json_throws_corruption() = runBlocking {
        val json = """{"headers":[{"requestUrl":"https:/missing-host","cookieHeader":"A=1"}],"capturedAtEpochMilli":0}"""
        val storage = InMemorySessionStorage().apply { bytes = encryptRaw(json.toByteArray(), keyA) }
        assertCorrupt { store(storage).load() }
    }

    @Test fun corruption_exception_chain_does_not_contain_decrypted_plaintext() = runBlocking {
        val secret = "A=private-cookie-value"
        val storage = InMemorySessionStorage().apply {
            bytes = encryptRaw("not-json-$secret".toByteArray(), keyA)
        }
        val error = assertThrows(SessionStoreCorruptedException::class.java) {
            runBlocking { store(storage).load() }
        }
        val messages = generateSequence(error as Throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
        assertFalse(messages.contains(secret))
    }

    @Test fun clear_removes_blob() = runBlocking {
        val storage = InMemorySessionStorage()
        val store = store(storage)
        store.save(blob)
        store.clear()
        assertNull(store.load())
        assertNull(storage.bytes)
    }

    @Test fun save_load_and_clear_run_on_io_dispatcher() {
        val storage = InMemorySessionStorage()
        val keys = FixedKeyProvider(keyA)
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "session-io-test") }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            runBlocking {
                val store = SessionStore(keys, storage, ioDispatcher = dispatcher)
                store.save(blob)
                store.load()
                store.clear()
            }
            assertTrue(storage.threads.isNotEmpty() && storage.threads.all { it.startsWith("session-io-test") })
            assertTrue(keys.threads.isNotEmpty() && keys.threads.all { it.startsWith("session-io-test") })
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private suspend fun assertCorrupt(block: suspend () -> Any?) {
        val error = assertThrows(SessionStoreCorruptedException::class.java) {
            runBlocking { block() }
        }
        assertEquals("Stored session is unreadable", error.message)
        assertFalse(error.message.orEmpty().contains("A=1"))
    }

    private fun encryptRaw(plain: ByteArray, key: ByteArray): ByteArray {
        val magic = "USTCSES1".toByteArray(StandardCharsets.US_ASCII)
        val aad = magic + byteArrayOf(1)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return aad + iv + cipher.doFinal(plain)
    }
}
