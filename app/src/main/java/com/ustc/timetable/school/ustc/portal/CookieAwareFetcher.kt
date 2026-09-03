package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.auth.SessionCookieHeader
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcPortalResponse
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncFailure
import com.ustc.timetable.sync.asFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resumeWithException

class CookieAwareFetcher(
    private val client: OkHttpClient,
    private val maxRedirects: Int = 5,
) {
    init {
        require(maxRedirects >= 0) { "maxRedirects must be non-negative" }
        require(!client.followRedirects && !client.followSslRedirects) {
            "Portal client must disable automatic redirects"
        }
        require(client.cookieJar === okhttp3.CookieJar.NO_COOKIES) {
            "Portal client must not keep cookies"
        }
    }

    suspend fun fetch(
        url: String,
        headers: List<SessionCookieHeader>,
        redirectCookieHeaderFor: ((String) -> SessionCookieHeader?)? = null,
    ): UstcPortalPage = UstcPortalPage(
        document = fetch(
            Request.Builder().url(validatedHttpUrl(url)).build(),
            headers,
            redirectCookieHeaderFor,
        ),
    )

    suspend fun fetch(
        initialRequest: Request,
        headers: List<SessionCookieHeader>,
        redirectCookieHeaderFor: ((String) -> SessionCookieHeader?)? = null,
    ): UstcPortalResponse {
        validatedHttpUrl(initialRequest.url.toString())
        var currentRequest = initialRequest
        val availableHeaders = headers.toMutableList()
        val requestUrl = initialRequest.url.toString()
        var redirectCount = 0
        while (true) {
            val request = currentRequest.newBuilder().removeHeader("Cookie").apply {
                SessionCookieHeader.pickFor(currentRequest.url.toString(), availableHeaders)?.let {
                    header("Cookie", it.cookieHeader)
                }
            }.build()
            val response = try {
                client.newCall(request).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SyncFailure) {
                throw failure
            } catch (io: IOException) {
                throw SyncError.NetworkFailed.asFailure(io)
            } catch (failure: Exception) {
                throw SyncError.NetworkFailed.asFailure(failure)
            }
            response.use {
                if (it.code in REDIRECT_CODES) {
                    if (redirectCount >= maxRedirects) throw SyncError.NetworkFailed.asFailure()
                    val location = it.header("Location") ?: throw SyncError.NetworkFailed.asFailure()
                    val target = resolveRedirect(request.url, location)
                    redirectCookieHeaderFor?.invoke(target.toString())?.let { captured ->
                        if (captured.scope != SessionCookieHeader.Scope.of(target.toString())) {
                            throw SyncError.NetworkFailed.asFailure()
                        }
                        availableHeaders.removeAll { existing -> existing.scope == captured.scope }
                        availableHeaders += captured
                    }
                    currentRequest = redirectRequest(request, target, it.code)
                    redirectCount++
                    continue
                }
                if (!it.isSuccessful) throw SyncError.NetworkFailed.asFailure()
                val html = try {
                    it.body.string()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (io: IOException) {
                    throw SyncError.NetworkFailed.asFailure(io)
                }
                return UstcPortalResponse(
                    requestUrl = requestUrl,
                    finalUrl = request.url.toString(),
                    contentType = it.header("Content-Type"),
                    body = html,
                )
            }
        }
    }

    private fun redirectRequest(request: Request, target: HttpUrl, statusCode: Int): Request {
        val preserveMethod = statusCode in setOf(307, 308)
        return request.newBuilder()
            .url(target)
            .removeHeader("Cookie")
            .apply {
                if (!preserveMethod && request.method != "GET" && request.method != "HEAD") {
                    method("GET", null)
                    removeHeader("Content-Type")
                    removeHeader("Content-Length")
                    removeHeader("Transfer-Encoding")
                }
            }
            .build()
    }

    private fun resolveRedirect(current: HttpUrl, location: String): HttpUrl {
        val resolved = try {
            current.resolve(location)
        } catch (_: IllegalArgumentException) {
            null
        } ?: throw SyncError.NetworkFailed.asFailure()
        return validatedHttpUrl(resolved.toString())
    }

    private fun validatedHttpUrl(rawUrl: String): HttpUrl {
        try {
            PortalDescriptorRules.validatedUri(rawUrl)
            return rawUrl.toHttpUrl()
        } catch (failure: IllegalArgumentException) {
            throw SyncError.NetworkFailed.asFailure(failure)
        }
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, cancelledResponse, _ ->
                    cancelledResponse.close()
                }
            }
        },
    )
}
