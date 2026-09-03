package com.ustc.timetable.school.ustc.auth

import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.UstcPortalDescriptor
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.asFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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

    @Test fun dynamic_student_selection_landing_does_not_probe() = runBlocking {
        val captured = mutableListOf<String>()
        val coordinator = coordinator { captured += it }
        assertEquals(LoginCompletionResult.Ignored, coordinator.onPageFinished(landing()))
        assertTrue(captured.isEmpty())
    }

    @Test fun verified_current_turn_selection_probes_with_exact_url() = runBlocking {
        val captured = mutableListOf<String>()
        val coordinator = coordinator { captured += it }
        val target = selection()
        assertEquals(LoginCompletionResult.Verified, coordinator.onPageFinished(target))
        assertEquals(listOf(target), captured)
    }

    @Test fun arbitrary_same_host_page_does_not_probe() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        assertEquals(
            LoginCompletionResult.Ignored,
            coordinator.onPageFinished("https://session.fixture.example/dashboard"),
        )
        assertEquals(0, calls)
    }

    @Test fun invalid_dynamic_student_selection_id_does_not_probe() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        listOf("0", "-1", "not-an-id").forEach { id ->
            assertEquals(LoginCompletionResult.Ignored, coordinator.onPageFinished(
                "https://session.fixture.example/for-std/course-select/$id/turn/202/select",
            ))
        }
        assertEquals(0, calls)
    }

    @Test fun login_route_on_session_host_does_not_probe() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        assertEquals(
            LoginCompletionResult.Ignored,
            coordinator.onPageFinished("https://session.fixture.example/login?refer=%2Ffixture"),
        )
        assertEquals(0, calls)
    }

    @Test fun concurrent_page_finished_is_single_flight() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator { _ ->
            calls++
            entered.complete(Unit)
            release.await()
        }
        val first = async { coordinator.onPageFinished(selection()) }
        entered.await()
        val second = async { coordinator.onPageFinished(selection(turnId = 303)) }
        yield()
        assertEquals(LoginCompletionResult.Ignored, second.await())
        assertEquals(1, calls)
        release.complete(Unit)
        assertEquals(LoginCompletionResult.Verified, first.await())
    }

    @Test fun ignored_event_does_not_increment_failure_count() = runBlocking {
        val coordinator = coordinator { _ -> throw SyncError.AuthenticationExpired.asFailure() }
        coordinator.onPageFinished("https://evil.example/done")
        assertEquals(0, coordinator.consecutiveAutomaticFailures)
        assertFalse(coordinator.isFallbackVisible)
    }

    @Test fun three_actual_failures_enable_fallback() = runBlocking {
        val coordinator = coordinator { _ -> throw SyncError.AuthenticationExpired.asFailure() }
        repeat(2) {
            assertEquals(LoginCompletionResult.Failed, coordinator.onPageFinished(selection()))
            assertFalse(coordinator.isFallbackVisible)
        }
        assertEquals(LoginCompletionResult.Failed, coordinator.onPageFinished(selection()))
        assertEquals(3, coordinator.consecutiveAutomaticFailures)
        assertTrue(coordinator.isFallbackVisible)
    }

    @Test fun success_returns_verified_and_resets_failure_count() = runBlocking {
        var fail = true
        val coordinator = coordinator { _ ->
            if (fail) throw SyncError.AuthenticationExpired.asFailure()
        }
        coordinator.onPageFinished(selection())
        fail = false
        assertEquals(LoginCompletionResult.Verified, coordinator.onPageFinished(selection()))
        assertEquals(0, coordinator.consecutiveAutomaticFailures)
        assertFalse(coordinator.isFallbackVisible)
    }

    @Test fun successful_completion_is_reported_only_once() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++ }
        assertEquals(
            LoginCompletionResult.Verified,
            coordinator.onPageFinished(selection()),
        )
        assertEquals(
            LoginCompletionResult.Ignored,
            coordinator.onPageFinished(selection(turnId = 303)),
        )
        assertEquals(LoginCompletionResult.Ignored, coordinator.onManualProbe())
        assertEquals(1, calls)
    }

    @Test fun cancellation_remains_cancellation_and_does_not_count_as_failure() = runBlocking {
        val coordinator = coordinator { _ -> throw CancellationException("cancel-login") }
        val thrown = runCatching {
            coordinator.onPageFinished(selection())
        }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(0, coordinator.consecutiveAutomaticFailures)
        assertFalse(coordinator.isFallbackVisible)
    }

    @Test fun manual_probe_requires_verified_current_turn_selection() = runBlocking {
        val captured = mutableListOf<String>()
        var fail = true
        val coordinator = coordinator { url ->
            captured += url
            if (fail) throw SyncError.AuthenticationExpired.asFailure()
        }
        assertEquals(
            LoginCompletionResult.Ignored,
            coordinator.onPageFinished("https://session.fixture.example/home"),
        )
        assertTrue(captured.isEmpty())
        assertEquals(LoginCompletionResult.Ignored, coordinator.onManualProbe())
        assertTrue(captured.isEmpty())
        val target = selection()
        assertEquals(LoginCompletionResult.Failed, coordinator.onPageFinished(target))
        fail = false
        assertEquals(LoginCompletionResult.Verified, coordinator.onManualProbe())
        assertEquals(listOf(target, target), captured)
    }

    private fun coordinator(capture: suspend (String) -> Unit): LoginCompletionCoordinator =
        LoginCompletionCoordinator(descriptor(), capture)

    private fun descriptor(): PortalDescriptor = UstcPortalDescriptor("https://session.fixture.example/")

    private fun landing(id: Long = 101): String =
        "https://session.fixture.example/for-std/course-select/turns/$id"

    private fun selection(studentId: Long = 101, turnId: Long = 202): String =
        "https://session.fixture.example/for-std/course-select/$studentId/turn/$turnId/select"
}
