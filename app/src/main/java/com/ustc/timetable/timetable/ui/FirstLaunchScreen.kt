package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun FirstLaunchRoute(viewModel: FirstLaunchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FirstLaunchEvent.ShowSnackbar -> snackbar.showSnackbar(event.message)
            }
        }
    }
    FirstLaunchScreen(
        state = state,
        onLoginAndImport = viewModel::onLoginAndImport,
        onSkipManualCreation = viewModel::onSkipManualCreation,
        snackbarHostState = snackbar,
    )
}

@Composable
fun FirstLaunchScreen(
    state: FirstLaunchUiState,
    onLoginAndImport: () -> Unit,
    onSkipManualCreation: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("first_launch_screen"),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("课表", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(18.dp))
            Text("从学校教务系统导入课表", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "登录仅用于读取你的课表。\nApp 不保存学校账户密码，\n课表数据保存在本机。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onLoginAndImport,
                enabled = state.actionsEnabled,
                modifier = Modifier.fillMaxWidth().testTag("first_launch_import"),
            ) { Text("登录并导入") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSkipManualCreation,
                enabled = state.actionsEnabled,
                modifier = Modifier.fillMaxWidth().testTag("first_launch_manual"),
            ) {
                if (state.isCreatingManualSemester) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).testTag("first_launch_manual_progress"),
                        strokeWidth = 2.dp,
                    )
                } else Text("稍后手动创建")
            }
        }
    }
}
