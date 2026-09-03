package com.ustc.timetable.school.ustc.portal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalDescriptorTest {
    @Test fun descriptor_rejects_non_http_urls() {
        assertThrows(IllegalArgumentException::class.java) {
            PortalDescriptorRules.validate(descriptor(loginUrl = "file:///login"))
        }
    }

    @Test fun descriptor_rejects_missing_host() {
        assertThrows(IllegalArgumentException::class.java) {
            PortalDescriptorRules.validate(descriptor(probeUrl = "https:/probe"))
        }
    }

    @Test fun descriptor_rejects_userinfo() {
        assertThrows(IllegalArgumentException::class.java) {
            PortalDescriptorRules.validate(descriptor(selectionUrl = "https://student@fixture.example/select"))
        }
    }

    @Test fun session_host_match_is_case_insensitive_exact() {
        val descriptor = descriptor(sessionHosts = listOf("FiXtUrE.ExAmPlE"))
        PortalDescriptorRules.validate(descriptor)
        assertTrue(PortalDescriptorRules.isSessionHost(descriptor, "https://FIXTURE.example/done"))
    }

    @Test fun session_host_does_not_suffix_match() {
        val descriptor = descriptor(sessionHosts = listOf("fixture.example"))
        assertFalse(PortalDescriptorRules.isSessionHost(descriptor, "https://fixture.example.evil.com/done"))
        assertFalse(PortalDescriptorRules.isSessionHost(descriptor, "https://sub.fixture.example/done"))
    }

    @Test fun invalid_session_host_entry_rejected() {
        listOf("https://fixture.example", "*.fixture.example", "fixture.example/path", "student@fixture.example", " ")
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) {
                    PortalDescriptorRules.validate(descriptor(sessionHosts = listOf(invalid)))
                }
            }
    }

    @Test fun descriptor_rejects_invalid_session_cookie_url() {
        assertThrows(IllegalArgumentException::class.java) {
            PortalDescriptorRules.validate(descriptor(sessionCookieUrls = listOf("file:///session")))
        }
    }

    @Test fun descriptor_requires_probe_in_session_cookie_urls() {
        assertThrows(IllegalArgumentException::class.java) {
            PortalDescriptorRules.validate(
                descriptor(sessionCookieUrls = listOf("https://fixture.example/selection")),
            )
        }
    }

    @Test fun login_route_on_session_host_is_not_an_automatic_completion_navigation() {
        val descriptor = descriptor()
        assertFalse(
            PortalDescriptorRules.isAutomaticCompletionNavigation(
                descriptor,
                "https://fixture.example/login?refer=%2Ffixture",
            ),
        )
    }

    @Test fun generic_descriptor_denies_automatic_navigation_by_default() {
        val descriptor = descriptor()
        assertFalse(
            PortalDescriptorRules.isAutomaticCompletionNavigation(
                descriptor,
                "https://fixture.example/home",
            ),
        )
        assertFalse(
            PortalDescriptorRules.isAutomaticModuleBootstrapNavigation(
                descriptor,
                "https://fixture.example/home",
            ),
        )
    }

    @Test fun service_ticket_callback_is_not_an_automatic_completion_navigation() {
        val descriptor = descriptor()
        assertFalse(
            PortalDescriptorRules.isAutomaticCompletionNavigation(
                descriptor,
                "https://fixture.example/ucas-sso/login?ticket=%3Credacted%3E",
            ),
        )
    }

    private fun descriptor(
        loginUrl: String = "https://fixture.example/login",
        probeUrl: String = "https://fixture.example/probe",
        selectionUrl: String = "https://fixture.example/selection",
        timetableUrl: String = "https://fixture.example/timetable",
        sessionHosts: List<String> = listOf("fixture.example"),
        sessionCookieUrls: List<String>? = null,
    ): PortalDescriptor = object : PortalDescriptor {
        override val loginUrl = loginUrl
        override val probeUrl = probeUrl
        override val selectionUrl = selectionUrl
        override val timetableUrl = timetableUrl
        override val sessionHosts = sessionHosts
        override val sessionCookieUrls = sessionCookieUrls
            ?: listOf(probeUrl, selectionUrl, timetableUrl)
    }
}
