package com.ustc.timetable.settings

import com.ustc.timetable.timetable.data.DEFAULT_COURSE_PALETTE_SEED

data class PaletteCandidateState(val persistedSeed: Long, val candidateSeed: Long)

object PaletteCandidateEditor {
    fun open(persistedSeed: Long): PaletteCandidateState = PaletteCandidateState(persistedSeed, persistedSeed)
    fun nextDistinctCandidate(state: PaletteCandidateState, source: PaletteSeedSource): PaletteCandidateState =
        state.copy(candidateSeed = PaletteSeedGenerator.nextDistinct(state.candidateSeed, source))
    fun restoreDefault(state: PaletteCandidateState): PaletteCandidateState = state.copy(candidateSeed = DEFAULT_COURSE_PALETTE_SEED)
    fun appliedSeed(state: PaletteCandidateState): Long = state.candidateSeed
    fun cancel(state: PaletteCandidateState): PaletteCandidateState = state.copy(candidateSeed = state.persistedSeed)
}
