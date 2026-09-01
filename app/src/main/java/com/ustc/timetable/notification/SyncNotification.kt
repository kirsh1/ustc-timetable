package com.ustc.timetable.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ustc.timetable.R
import com.ustc.timetable.timetable.domain.ChangeFormatter
import com.ustc.timetable.timetable.domain.ScheduleChange

object SyncNotification {
    const val CHANNEL_SYNC = "sync_updates"
    internal const val ID_CHANGES = 1001
    internal const val ID_REAUTH = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                "课表更新",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun postChanges(context: Context, changes: List<ScheduleChange>) {
        if (changes.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val lines = changes.flatMap(ChangeFormatter::notificationLines)
        if (lines.isEmpty()) return

        ensureChannel(context)
        val expandedText = lines.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification_timetable)
            .setContentTitle("课表已更新")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_CHANGES, notification)
    }

    @SuppressLint("MissingPermission")
    fun postReauthNeeded(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(context)
        val message = "学校登录状态已失效，已有课表不会受到影响"
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification_timetable)
            .setContentTitle("请重新登录")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_REAUTH, notification)
    }
}
