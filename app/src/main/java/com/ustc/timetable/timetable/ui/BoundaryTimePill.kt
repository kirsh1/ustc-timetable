package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ustc.timetable.scheduleprofile.PeriodTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BoundaryTimePills(val start: String?, val end: String?)

private val boundaryTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun boundaryTimePills(start: LocalTime, end: LocalTime, periods: List<PeriodTime>): BoundaryTimePills {
    val standard = periods.flatMap { listOf(it.start, it.end) }.toSet()
    return BoundaryTimePills(
        start = start.takeUnless { it in standard }?.format(boundaryTimeFormatter),
        end = end.takeUnless { it in standard }?.format(boundaryTimeFormatter),
    )
}

@Composable
fun BoundaryTimePill(label: String, tag: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag(tag).clearAndSetSemantics { },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 1.dp,
    ) {
        Text(label, fontSize = 8.sp, lineHeight = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
    }
}
