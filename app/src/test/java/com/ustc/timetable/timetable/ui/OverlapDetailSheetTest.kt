package com.ustc.timetable.timetable.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlapDetailSheetTest {
    @get:Rule val rule = createComposeRule()

    private val item = ManualScheduleItem(
        id = ManualItemId("manual-1"),
        semesterId = SemesterId("semester"),
        title = "组会",
        weekday = 2,
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
        weekPattern = WeekPattern.of(1),
        location = "ROOM",
        note = "note",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    private val profile = ScheduleProfile(
        "profile", "profile", false,
        (1..13).map { PeriodTime(it, LocalTime.of(6 + it, 0), LocalTime.of(6 + it, 45)) },
    )

    private fun content(onDelete: (ManualItemId) -> Unit = {}) {
        rule.setContent {
            OverlapDetailSheet(
                pages = listOf(OverlapDetailPage("manual:${item.id.value}", manual = item)),
                profile = profile,
                onDismiss = {},
                onEdit = {},
                onDelete = onDelete,
            )
        }
        rule.waitForIdle()
    }

    @Test fun manual_detail_places_vector_delete_below_edit() {
        content()
        val edit = rule.onNodeWithTag("overlap_edit_manual").fetchSemanticsNode().boundsInRoot
        val delete = rule.onNodeWithTag("overlap_delete_manual").fetchSemanticsNode().boundsInRoot

        assertTrue("delete $delete must be below edit $edit", delete.top >= edit.bottom)
    }

    @Test fun manual_detail_delete_requires_confirmation_and_cancel_keeps_item() {
        var deleted: ManualItemId? = null
        content { deleted = it }

        rule.onNodeWithTag("overlap_delete_manual").performClick()
        rule.onAllNodesWithTag("detail_delete_confirm").assertCountEquals(1)
        assertEquals(null, deleted)
        rule.onNodeWithTag("detail_delete_cancel").performClick()
        rule.onAllNodesWithTag("detail_delete_confirm").assertCountEquals(0)
        assertEquals(null, deleted)
    }

    @Test fun manual_detail_confirm_deletes_exact_displayed_item() {
        var deleted: ManualItemId? = null
        content { deleted = it }

        rule.onNodeWithTag("overlap_delete_manual").performClick()
        rule.onNodeWithTag("detail_delete_confirm").performClick()

        assertEquals(item.id, deleted)
    }
}
