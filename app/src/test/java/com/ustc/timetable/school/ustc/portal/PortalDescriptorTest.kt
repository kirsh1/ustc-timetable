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

    private fun descriptor(
        loginUrl: String = "https://fixture.example/login",
        probeUrl: String = "https://fixture.example/probe",
        selectionUrl: String = "https://fixture.example/selection",
        timetableUrl: String = "https://fixture.example/timetable",
        sessionHosts: List<String> = listOf("fixture.example"),
    ): PortalDescriptor = object : PortalDescriptor {
        override val loginUrl = loginUrl
        override val probeUrl = probeUrl
        override val selectionUrl = selectionUrl
        override val timetableUrl = timetableUrl
        override val sessionHosts = sessionHosts
    }
}
