package com.ustc.timetable.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OverviewGestureArbitratorTest {
    @Test fun click_wins_when_pointer_releases_within_slop() {
        listOf(GridPressTarget.EMPTY, GridPressTarget.SCHOOL_CARD, GridPressTarget.MANUAL_CARD).forEach { target ->
            val subject = OverviewGestureArbitrator(10f)
            subject.onDown(target)
            assertEquals(GridGestureDecision.None, subject.onMove(6f, 7f))
            assertEquals(GridGestureDecision.Click, subject.onUp())
            assertEquals(GridGestureOwner.IDLE, subject.owner)
        }
    }

    @Test fun long_press_wins_after_timeout() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.EMPTY)
        assertEquals(GridGestureDecision.LongPress, subject.onLongPressTimeout())
        assertEquals(GridGestureOwner.LONG_PRESS_OWNED, subject.owner)
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun card_long_press_is_consumed_without_click() {
        listOf(GridPressTarget.SCHOOL_CARD, GridPressTarget.MANUAL_CARD).forEach { target ->
            val subject = OverviewGestureArbitrator(10f)
            subject.onDown(target)
            assertEquals(GridGestureDecision.ConsumeCardLongPress, subject.onLongPressTimeout())
            assertEquals(GridGestureOwner.LONG_PRESS_OWNED, subject.owner)
            assertEquals(GridGestureDecision.None, subject.onUp())
        }
    }

    @Test fun diagonal_motion_beyond_slop_does_not_click_on_release() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.SCHOOL_CARD)
        assertEquals(GridGestureDecision.None, subject.onMove(8f, 8f))
        assertEquals(GridGestureOwner.DIRECTION_PENDING, subject.owner)
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun motion_beyond_slop_disables_pending_long_press() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.EMPTY)
        subject.onMove(8f, 8f)
        assertEquals(GridGestureDecision.None, subject.onLongPressTimeout())
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun horizontal_drag_before_timeout_yields_to_pager() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.EMPTY)
        assertEquals(GridGestureDecision.YieldToHorizontalPager, subject.onMove(12f, 2f))
        assertEquals(GridGestureOwner.HORIZONTAL_OWNED, subject.owner)
        assertEquals(GridGestureDecision.YieldToHorizontalPager, subject.onMove(20f, 3f))
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun vertical_drag_before_timeout_cancels_click_and_longpress() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.MANUAL_CARD)
        assertEquals(
            GridGestureDecision.ChangeOverview(VerticalOverviewAction.EXPAND),
            subject.onMove(1f, 12f),
        )
        assertEquals(GridGestureDecision.None, subject.onLongPressTimeout())
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun one_vertical_drag_changes_overview_at_most_once() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.EMPTY)
        assertEquals(
            GridGestureDecision.ChangeOverview(VerticalOverviewAction.COLLAPSE),
            subject.onMove(0f, -12f),
        )
        assertEquals(GridGestureDecision.None, subject.onMove(0f, -30f))
        assertEquals(GridGestureOwner.VERTICAL_OWNED, subject.owner)
    }

    @Test fun direction_pending_can_resolve_but_never_restore_click() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.SCHOOL_CARD)
        assertEquals(GridGestureDecision.None, subject.onMove(8f, 8f))
        assertEquals(
            GridGestureDecision.ChangeOverview(VerticalOverviewAction.EXPAND),
            subject.onMove(9f, 14f),
        )
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun cancellation_resets_owner_without_action() {
        val subject = OverviewGestureArbitrator(10f)
        subject.onDown(GridPressTarget.EMPTY)
        subject.onCancel()
        assertEquals(GridGestureOwner.IDLE, subject.owner)
        assertEquals(GridGestureDecision.None, subject.onUp())
    }

    @Test fun nonpositive_touch_slop_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) { OverviewGestureArbitrator(0f) }
        assertThrows(IllegalArgumentException::class.java) { OverviewGestureArbitrator(-1f) }
    }
}
