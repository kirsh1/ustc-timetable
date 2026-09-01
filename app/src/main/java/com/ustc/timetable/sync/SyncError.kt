package com.ustc.timetable.sync

sealed interface SyncError {
    data object AuthenticationExpired : SyncError
    data object ParseFailed : SyncError
    data object ValidationFailed : SyncError
    data object NetworkFailed : SyncError
}

class SyncFailure(
    val error: SyncError,
    cause: Throwable? = null,
) : Exception("Sync failed: ${error::class.simpleName}", cause)

fun SyncError.asFailure(cause: Throwable? = null): SyncFailure = SyncFailure(this, cause)

fun Throwable.syncErrorOrNull(): SyncError? = (this as? SyncFailure)?.error
