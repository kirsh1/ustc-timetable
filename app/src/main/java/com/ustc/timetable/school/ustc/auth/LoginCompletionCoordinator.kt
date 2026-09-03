package com.ustc.timetable.school.ustc.auth

import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.PortalDescriptorRules
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

sealed interface LoginCompletionResult {
    data object Ignored : LoginCompletionResult
    data object Failed : LoginCompletionResult
    data object Verified : LoginCompletionResult
}

class LoginCompletionCoordinator(
    private val descriptor: PortalDescriptor,
    private val captureAndVerify: suspend () -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)

    @Volatile
    private var manualProbeEligible = false

    @Volatile
    var consecutiveAutomaticFailures: Int = 0
        private set

    val isFallbackVisible: Boolean
        get() = consecutiveAutomaticFailures >= FALLBACK_FAILURE_COUNT

    init {
        PortalDescriptorRules.validate(descriptor)
    }

    suspend fun onPageFinished(rawUrl: String): LoginCompletionResult {
        val isEligible = PortalDescriptorRules.isAutomaticCompletionNavigation(descriptor, rawUrl)
        manualProbeEligible = isEligible
        if (!isEligible) {
            return LoginCompletionResult.Ignored
        }
        return probe(isAutomatic = true)
    }

    suspend fun onManualProbe(): LoginCompletionResult {
        if (!manualProbeEligible) return LoginCompletionResult.Ignored
        return probe(isAutomatic = false)
    }

    private suspend fun probe(isAutomatic: Boolean): LoginCompletionResult {
        if (completed.get()) return LoginCompletionResult.Ignored
        if (!running.compareAndSet(false, true)) return LoginCompletionResult.Ignored
        return try {
            captureAndVerify()
            consecutiveAutomaticFailures = 0
            if (completed.compareAndSet(false, true)) {
                LoginCompletionResult.Verified
            } else {
                LoginCompletionResult.Ignored
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isAutomatic) consecutiveAutomaticFailures++
            LoginCompletionResult.Failed
        } finally {
            running.set(false)
        }
    }

    private companion object {
        const val FALLBACK_FAILURE_COUNT = 3
    }
}
