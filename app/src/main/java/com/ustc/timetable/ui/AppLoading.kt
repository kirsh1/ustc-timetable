package com.ustc.timetable.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ustc.timetable.R

/** Shared branding for both startup gates; readiness, not a timer, removes it. */
@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_app_icon), contentDescription = "课表正在加载",
            modifier = Modifier.size(144.dp).testTag("app_loading_logo"))
    }
}
