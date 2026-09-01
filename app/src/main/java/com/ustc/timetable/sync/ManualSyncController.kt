package com.ustc.timetable.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ManualSyncState {
    data object Idle : ManualSyncState
    data object Syncing : ManualSyncState
    data object AwaitingReauth : ManualSyncState
}

sealed interface ManualSyncEvent {
    data class Updated(val changeCount: Int) : ManualSyncEvent
    data class FailedOther(val error: SyncError) : ManualSyncEvent
}

fun interface ManualSyncRunner {
    suspend fun sync(): SyncResult
}

class ManualSyncController(
    private val runner: ManualSyncRunner,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<ManualSyncState>(ManualSyncState.Idle)
    val state: StateFlow<ManualSyncState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<ManualSyncEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ManualSyncEvent> = mutableEvents.asSharedFlow()

    fun start() {
        if (!mutableState.compareAndSet(ManualSyncState.Idle, ManualSyncState.Syncing)) return
        scope.launch {
            try {
                when (val result = runner.sync()) {
                    SyncResult.NoChange -> mutableState.value = ManualSyncState.Idle
                    is SyncResult.Success -> {
                        mutableState.value = ManualSyncState.Idle
                        if (result.changes.isNotEmpty()) {
                            mutableEvents.emit(ManualSyncEvent.Updated(result.changes.size))
                        }
                    }
                    is SyncResult.Failed -> when (result.error) {
                        SyncError.AuthenticationExpired -> mutableState.value = ManualSyncState.AwaitingReauth
                        SyncError.NetworkFailed,
                        SyncError.ParseFailed,
                        SyncError.ValidationFailed,
                        -> {
                            mutableState.value = ManualSyncState.Idle
                            mutableEvents.emit(ManualSyncEvent.FailedOther(result.error))
                        }
                    }
                }
            } catch (failure: Throwable) {
                mutableState.value = ManualSyncState.Idle
                throw failure
            }
        }
    }

    fun onReloginSuccess() {
        if (!mutableState.compareAndSet(ManualSyncState.AwaitingReauth, ManualSyncState.Idle)) return
        start()
    }

    fun onReloginCanceled() {
        // Canceled WebView login deliberately keeps AwaitingReauth and its dialog visible.
    }

    fun onCancelAuthExpired() {
        mutableState.compareAndSet(ManualSyncState.AwaitingReauth, ManualSyncState.Idle)
    }
}
