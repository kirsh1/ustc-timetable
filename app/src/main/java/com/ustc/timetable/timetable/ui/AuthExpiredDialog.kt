package com.ustc.timetable.timetable.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun AuthExpiredDialog(
    onCancel: () -> Unit,
    onRelogin: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("登录状态已失效") },
        text = { Text("已有课表不会受到影响") },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
        confirmButton = { TextButton(onClick = onRelogin) { Text("重新登录") } },
    )
}
