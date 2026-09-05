package com.ustc.timetable.settings

import com.ustc.timetable.timetable.ui.CoursePalette
import org.junit.Assert.*
import org.junit.Test

class PaletteCandidateEditorTest {
    @Test fun candidate_seed_is_not_persisted_before_apply() {
        val state = PaletteCandidateEditor.nextDistinctCandidate(PaletteCandidateEditor.open(0L), PaletteSeedSource { 7L })
        assertEquals(0L, state.persistedSeed)
        assertEquals(7L, PaletteCandidateEditor.appliedSeed(state))
        assertEquals(0L, PaletteCandidateEditor.cancel(state).candidateSeed)
    }

    @Test fun restore_default_requires_apply() {
        val state = PaletteCandidateEditor.restoreDefault(PaletteCandidateEditor.open(7L))
        assertEquals(7L, state.persistedSeed)
        assertEquals(0L, state.candidateSeed)
        assertEquals(7L, PaletteCandidateEditor.cancel(state).candidateSeed)
    }

    @Test fun applied_seed_persists_across_recreation() {
        val state = PaletteCandidateEditor.nextDistinctCandidate(PaletteCandidateEditor.open(0L), PaletteSeedSource { Long.MIN_VALUE })
        assertEquals(state.candidateSeed, PaletteCandidateEditor.open(PaletteCandidateEditor.appliedSeed(state)).persistedSeed)
    }

    @Test fun new_candidate_is_distinct_from_current_preview() {
        var state = PaletteCandidateEditor.open(0L)
        var calls = 0
        repeat(50) {
            val previous = state
            state = PaletteCandidateEditor.nextDistinctCandidate(state, PaletteSeedSource { calls++; 7L })
            assertNotEquals(CoursePalette.previewIndices(previous.candidateSeed), CoursePalette.previewIndices(state.candidateSeed))
        }
        assertEquals(50, calls)
    }

    @Test fun colliding_seed_source_uses_deterministic_fallback() {
        for (seed in listOf(0L, 23L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var calls = 0
            val next = PaletteSeedGenerator.nextDistinct(seed, PaletteSeedSource { calls++; seed })
            assertEquals(((CoursePalette.variantFor(seed) + 1) % 24).toLong(), next)
            assertEquals(1, calls)
        }
    }
}
