package com.ustc.timetable.school.ustc.auth

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionStoreCorruptedException(
    cause: Throwable? = null,
) : Exception("Stored session is unreadable", cause)

class SessionStore(
    private val keys: SecretKeyProvider,
    private val storage: SessionStorage,
    private val random: SecureRandom = SecureRandom(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun save(blob: SessionBlob): Unit = withContext(ioDispatcher) {
        val plain = Json.encodeToString(SessionBlobDto.from(blob)).toByteArray(StandardCharsets.UTF_8)
        val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            keys.getOrCreateKey(),
            GCMParameterSpec(TAG_SIZE_BITS, iv),
        )
        cipher.updateAAD(AAD)
        storage.write(AAD + iv + cipher.doFinal(plain))
    }

    suspend fun load(): SessionBlob? = withContext(ioDispatcher) {
        val raw = storage.read() ?: return@withContext null
        try {
            requireReadableEnvelope(raw)
            val ivStart = AAD.size
            val ciphertextStart = ivStart + IV_SIZE_BYTES
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keys.getOrCreateKey(),
                GCMParameterSpec(TAG_SIZE_BITS, raw.copyOfRange(ivStart, ciphertextStart)),
            )
            cipher.updateAAD(AAD)
            val plain = cipher.doFinal(raw.copyOfRange(ciphertextStart, raw.size))
            Json.decodeFromString<SessionBlobDto>(plain.toString(StandardCharsets.UTF_8)).toDomain()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (corrupted: SessionStoreCorruptedException) {
            throw corrupted
        } catch (_: Exception) {
            throw SessionStoreCorruptedException()
        }
    }

    suspend fun clear(): Unit = withContext(ioDispatcher) {
        storage.write(null)
    }

    private fun requireReadableEnvelope(raw: ByteArray) {
        if (raw.size < MIN_ENVELOPE_SIZE) throw SessionStoreCorruptedException()
        if (!raw.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw SessionStoreCorruptedException()
        }
        if (raw[MAGIC.size] != FORMAT_VERSION) throw SessionStoreCorruptedException()
    }

    private companion object {
        val MAGIC: ByteArray = "USTCSES1".toByteArray(StandardCharsets.US_ASCII)
        const val FORMAT_VERSION_NUMBER: Int = 1
        val FORMAT_VERSION: Byte = FORMAT_VERSION_NUMBER.toByte()
        val AAD: ByteArray = MAGIC + byteArrayOf(FORMAT_VERSION)
        const val IV_SIZE_BYTES: Int = 12
        const val TAG_SIZE_BITS: Int = 128
        const val TAG_SIZE_BYTES: Int = TAG_SIZE_BITS / 8
        const val MIN_ENVELOPE_SIZE: Int = 8 + 1 + IV_SIZE_BYTES + TAG_SIZE_BYTES
        const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    }
}

@Serializable
private data class SessionBlobDto(
    val headers: List<HeaderDto>,
    val capturedAtEpochMilli: Long,
) {
    fun toDomain(): SessionBlob = SessionBlob(
        headers = headers.map(HeaderDto::toDomain),
        capturedAt = Instant.ofEpochMilli(capturedAtEpochMilli),
    )

    companion object {
        fun from(blob: SessionBlob): SessionBlobDto = SessionBlobDto(
            headers = blob.headers.map(HeaderDto::from),
            capturedAtEpochMilli = blob.capturedAt.toEpochMilli(),
        )
    }
}

@Serializable
private data class HeaderDto(
    val requestUrl: String,
    val cookieHeader: String,
) {
    fun toDomain(): SessionCookieHeader = SessionCookieHeader(requestUrl, cookieHeader)

    companion object {
        fun from(header: SessionCookieHeader): HeaderDto = HeaderDto(
            requestUrl = header.requestUrl,
            cookieHeader = header.cookieHeader,
        )
    }
}
