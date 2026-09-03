package com.ustc.timetable.school.ustc.auth

import java.net.URI
import java.util.Locale

data class SessionCookieHeader(
    val requestUrl: String,
    val cookieHeader: String,
) {
    init {
        require(cookieHeader.isNotBlank()) { "cookieHeader must not be blank" }
    }

    val scope: Scope = Scope.of(requestUrl)

    data class Scope(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
    ) {
        fun requestUrl(): String {
            val authorityHost = if (':' in host) "[$host]" else host
            val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            val portSuffix = if (defaultPort) "" else ":$port"
            return "$scheme://$authorityHost$portSuffix$path"
        }

        companion object {
            fun of(raw: String): Scope {
                val uri = try {
                    URI(raw)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Invalid session URL")
                }
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                require(scheme == "http" || scheme == "https") { "Unsupported session URL scheme" }
                require(uri.rawUserInfo == null) { "Session URL must not contain user info" }
                val host = uri.host?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
                    ?: throw IllegalArgumentException("Session URL must contain a host")
                val port = when {
                    uri.port != -1 -> uri.port
                    scheme == "http" -> 80
                    else -> 443
                }
                val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
                return Scope(scheme, host, port, path)
            }
        }
    }

    override fun toString(): String =
        "SessionCookieHeader(scope=$scope, cookieHeader=<redacted>)"

    companion object {
        fun pickFor(
            requestUrl: String,
            headers: List<SessionCookieHeader>,
        ): SessionCookieHeader? {
            val requestedScope = Scope.of(requestUrl)
            val matches = headers.filter { it.scope == requestedScope }
            if (matches.isEmpty()) return null
            val first = matches.first()
            return first.takeIf { candidate ->
                matches.all { it.cookieHeader == candidate.cookieHeader }
            }
        }
    }
}
