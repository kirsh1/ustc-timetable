package com.ustc.timetable.school.ustc.portal

import java.net.URI
import java.util.Locale

interface PortalDescriptor {
    val loginUrl: String
    val probeUrl: String
    val selectionUrl: String
    val timetableUrl: String
    val sessionHosts: List<String>
    val sessionCookieUrls: List<String>
        get() = listOf(probeUrl, selectionUrl, timetableUrl)

    fun isPostLoginPortalHomeUrl(rawUrl: String): Boolean = false

    fun isDynamicSessionCookieUrl(rawUrl: String): Boolean = false

    fun isCurrentTurnLandingUrl(rawUrl: String): Boolean = false

    fun isCurrentTurnSelectionUrl(rawUrl: String): Boolean = false
}

internal object PortalDescriptorRules {
    fun validate(descriptor: PortalDescriptor) {
        val requiredUrls = listOf(
            descriptor.loginUrl,
            descriptor.probeUrl,
            descriptor.selectionUrl,
            descriptor.timetableUrl,
        )
        requiredUrls.forEach(::validatedUri)
        require(descriptor.sessionCookieUrls.isNotEmpty()) {
            "At least one session cookie URL is required"
        }
        descriptor.sessionCookieUrls.forEach(::validatedUri)
        require(descriptor.probeUrl in descriptor.sessionCookieUrls) {
            "Session cookie URLs must include the probe URL"
        }
        require(descriptor.selectionUrl in descriptor.sessionCookieUrls) {
            "Session cookie URLs must include the selection URL"
        }
        require(descriptor.timetableUrl in descriptor.sessionCookieUrls) {
            "Session cookie URLs must include the timetable URL"
        }
        require(descriptor.sessionHosts.isNotEmpty()) { "At least one session host is required" }
        descriptor.sessionHosts.forEach(::normalizedHostEntry)
    }

    fun isSessionHost(descriptor: PortalDescriptor, rawUrl: String): Boolean {
        val targetHost = try {
            validatedUri(rawUrl).host.lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return descriptor.sessionHosts
            .map(::normalizedHostEntry)
            .any { it == targetHost }
    }

    fun isAutomaticCompletionNavigation(
        descriptor: PortalDescriptor,
        rawUrl: String,
    ): Boolean {
        if (!isSafeAutomaticNavigation(descriptor, rawUrl)) return false
        return try {
            descriptor.isCurrentTurnSelectionUrl(rawUrl)
        } catch (_: Exception) {
            false
        }
    }

    fun isAutomaticModuleBootstrapNavigation(
        descriptor: PortalDescriptor,
        rawUrl: String,
    ): Boolean {
        if (!isSafeAutomaticNavigation(descriptor, rawUrl)) return false
        return try {
            descriptor.isPostLoginPortalHomeUrl(rawUrl)
        } catch (_: Exception) {
            false
        }
    }

    private fun isSafeAutomaticNavigation(
        descriptor: PortalDescriptor,
        rawUrl: String,
    ): Boolean {
        if (!isSessionHost(descriptor, rawUrl)) return false
        val current = try {
            validatedUri(rawUrl)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val login = validatedUri(descriptor.loginUrl)
        if (routeScope(current) == routeScope(login)) return false
        if (queryKeyNames(current).any { it.equals("ticket", ignoreCase = true) }) return false
        return true
    }

    fun validatedUri(rawUrl: String): URI {
        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid portal URL")
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "Unsupported portal URL scheme" }
        require(uri.rawUserInfo == null) { "Portal URL must not contain user info" }
        require(!uri.host.isNullOrBlank()) { "Portal URL must contain a host" }
        return uri
    }

    private fun normalizedHostEntry(rawHost: String): String {
        require(rawHost.isNotBlank() && rawHost == rawHost.trim()) { "Invalid session host" }
        val uri = try {
            URI("http://$rawHost")
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid session host")
        }
        require(
            uri.host?.isNotBlank() == true &&
                uri.rawUserInfo == null &&
                uri.port == -1 &&
                uri.rawPath.isNullOrEmpty() &&
                uri.rawQuery == null &&
                uri.rawFragment == null,
        ) { "Invalid session host" }
        return uri.host.lowercase(Locale.ROOT)
    }

    private data class RouteScope(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
    )

    private fun routeScope(uri: URI): RouteScope = RouteScope(
        scheme = uri.scheme.lowercase(Locale.ROOT),
        host = uri.host.lowercase(Locale.ROOT),
        port = effectivePort(uri),
        path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/",
    )

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> 443
    }

    private fun queryKeyNames(uri: URI): List<String> = uri.rawQuery
        ?.split('&')
        ?.map { field -> field.substringBefore('=') }
        .orEmpty()
}
