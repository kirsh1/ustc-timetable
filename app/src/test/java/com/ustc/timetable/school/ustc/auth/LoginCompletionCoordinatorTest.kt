package com.ustc.timetable.school.ustc.auth

import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.asFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginCompletionCoordinatorTest {
    @Test fun non_session_host_does_not_probe() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        assertEquals(LoginCompletionResult.Ignored, coordinator.onPageFinished("https://evil.example/done"))
        assertEquals(0, calls)
    }

    @Test fun session_host_exact_match_probes() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        assertEquals(LoginCompletionResult.Verified, coordinator.onPageFinished("https://SESSION.fixture.example/done"))
        assertEquals(1, calls)
    }

    @Test fun concurrent_page_finished_is_single_flight() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator {
            calls++
            entered.complete(Unit)
            release.await()
        }
        val first = async { coordinator.onPageFinished("https://session.fixture.example/done") }
        entered.await()
        val second = async { coordinator.onPageFinished("https://session.fixture.example/next") }
        yield()
        assertEquals(LoginCompletionResult.Ignored, second.await())
        assertEquals(1, calls)
        release.complete(Unit)
        assertEquals(LoginCompletionResult.Verified, first.await())
    }

    @Test fun ignored_event_does_not_increment_failure_count() = runBlocking {
        val coordinator = coordinator { throw SyncError.AuthenticationExpired.asFailure() }
        coordinator.onPageFinished("https://evil.example/done")
        assertEquals(0, coordinator.consecutiveAutomaticFailures)
        assertFalse(coordinator.isFallbackVisible)
    }

    @Test fun three_actual_failures_enable_fallback() = runBlocking {
        val coordinator = coordinator { throw SyncError.AuthenticationExpired.asFailure() }
        repeat(2) {
            assertEquals(LoginCompletionResult.Failed, coordinator.onPageFinished("https://session.fixture.example/done"))
            assertFalse(coordinator.isFallbackVisible)
        }
        assertEquals(LoginCompletionResult.Failed, coordinator.onPageFinished("https://session.fixture.example/done"))
        assertEquals(3, coordinator.consecutiveAutomaticFailures)
        assertTrue(coordinator.isFallbackVisible)
    }

    @Test fun success_returns_verified_and_resets_failure_count() = runBlocking {
        var fail = true
        val coordinator = coordinator {
            if (fail) throw SyncError.AuthenticationExpired.asFailure()
        }
        coordinator.onPageFinished("https://session.fixture.example/done")
        fail = false
        assertEquals(LoginCompletionResult.Verified, coordinator.onPageFinished("https://session.fixture.example/done"))
        assertEquals(0, coordinator.consecutiveAutomaticFailures)
        assertFalse(coordinator.isFallbackVisible)
    }

    @Test fun manual_probe_does_not_require_session_host_event() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        assertEquals(LoginCompletionResult.Verified, coordinator.onManualProbe())
        assertEquals(1, calls)
    }

    private fun coordinator(capture: suspend () -> Unit): LoginCompletionCoordinator =
        LoginCompletionCoordinator(descriptor(), capture)

    private fun descriptor(): PortalDescriptor = object : PortalDescriptor {
        override val loginUrl = "https://login.fixture.example/login"
        override val probeUrl = "https://session.fixture.example/probe"
        override val selectionUrl = "https://session.fixture.example/selection"
        override val timetableUrl = "https://session.fixture.example/timetable"
        override val sessionHosts = listOf("session.fixture.example")
    }
}
