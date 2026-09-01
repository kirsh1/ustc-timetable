package com.ustc.timetable.school.ustc.portal

import java.net.URI
import java.util.Locale

interface PortalDescriptor {
    val loginUrl: String
    val probeUrl: String
    val selectionUrl: String
    val timetableUrl: String
    val sessionHosts: List<String>
}

internal object PortalDescriptorRules {
    fun validate(descriptor: PortalDescriptor) {
        listOf(
            descriptor.loginUrl,
            descriptor.probeUrl,
            descriptor.selectionUrl,
            descriptor.timetableUrl,
        ).forEach(::validatedUri)
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
}
