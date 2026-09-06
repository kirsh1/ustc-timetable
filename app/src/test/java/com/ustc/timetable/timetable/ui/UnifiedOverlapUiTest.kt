package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import com.ustc.timetable.timetable.domain.*
import com.ustc.timetable.timetable.layout.*
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnifiedOverlapUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun shaped_card_has_one_body_and_marker_does_not_edit_manual() {
        val a = UiManualTimedBlock("manual:a",ManualItemId("a"),1,LocalTime.of(10,0),LocalTime.of(12,0),WeekPattern.of(1),"Lecture","TH-A301",emptyList())
        val b = a.copy(manualItemId=ManualItemId("b"),colorKey="manual:b",weeks=WeekPattern.of(2))
        val entry = OverlapEntry(a,Instant.EPOCH)
        var body=0
        val markers=mutableListOf<OverlapMarkerKind>()
        rule.setContent { MaterialTheme { Box(Modifier.size(350.dp,600.dp)) {
            SegmentedCourseCard(entry,SegmentedOverlapLayout.place(listOf(a)).single(),350.dp,600.dp,
                TimelineAxis(LocalTime.of(7,0),LocalTime.of(22,0)),1,true,0L,
                emptyList(),
                OverlapAttachment(crossWeek=listOf(OverlapEntry(b,Instant.EPOCH))), { body++ }, { markers+=it })
        } } }
        rule.onAllNodesWithText("Lecture").assertCountEquals(1)
        rule.onNodeWithTag("overlap_marker:CROSS_WEEK").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(0,body)
        assertEquals(listOf(OverlapMarkerKind.CROSS_WEEK),markers)
        rule.onNodeWithTag("manual_block:a").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1,body)
    }
}
