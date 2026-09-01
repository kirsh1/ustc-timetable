package com.ustc.timetable.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ustc.timetable.timetable.domain.ScheduleChange
import com.ustc.timetable.timetable.domain.WeekPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SyncNotificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadowManager get() = shadowOf(manager)

    @Test fun empty_changes_posts_nothing() {
        SyncNotification.postChanges(context, emptyList())

        assertEquals(0, shadowManager.size())
        assertNull(manager.getNotificationChannel(SyncNotification.CHANNEL_SYNC))
    }

    @Test fun changes_disabled_posts_nothing() {
        shadowManager.setNotificationsEnabled(false)

        SyncNotification.postChanges(context, listOf(locationChange()))

        assertEquals(0, shadowManager.size())
        assertNull(manager.getNotificationChannel(SyncNotification.CHANNEL_SYNC))
    }

    @Test fun reauth_disabled_posts_nothing() {
        shadowManager.setNotificationsEnabled(false)

        SyncNotification.postReauthNeeded(context)

        assertEquals(0, shadowManager.size())
        assertNull(manager.getNotificationChannel(SyncNotification.CHANNEL_SYNC))
    }

    @Test fun ensure_channel_creates_sync_updates_channel() {
        SyncNotification.ensureChannel(context)

        val channel = manager.getNotificationChannel(SyncNotification.CHANNEL_SYNC)
        assertNotNull(channel)
        assertEquals("课表更新", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test fun ensure_channel_is_idempotent() {
        SyncNotification.ensureChannel(context)
        SyncNotification.ensureChannel(context)

        assertEquals(
            1,
            shadowManager.notificationChannels.count { it.id == SyncNotification.CHANNEL_SYNC },
        )
    }

    @Test fun location_change_posts_formatted_notification() {
        SyncNotification.postChanges(context, listOf(locationChange()))

        val notification = shadowManager.getNotification(SyncNotification.ID_CHANGES)
        assertNotNull(notification)
        assertEquals("课表已更新", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("高等无机化学", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(
            "高等无机化学\n第10周教室：TH-B301 → TH-C204",
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
    }

    @Test fun multiple_changes_preserve_formatter_line_order() {
        SyncNotification.postChanges(
            context,
            listOf(
                ScheduleChange.CourseAdded("先输入"),
                locationChange(courseName = "后输入"),
            ),
        )

        val text = shadowManager.getNotification(SyncNotification.ID_CHANGES)
            .extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertEquals("先输入\n新增课程\n后输入\n第10周教室：TH-B301 → TH-C204", text)
    }

    @Test fun reauth_posts_please_login_notification() {
        SyncNotification.postReauthNeeded(context)

        val notification = shadowManager.getNotification(SyncNotification.ID_REAUTH)
        assertNotNull(notification)
        assertEquals("请重新登录", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "学校登录状态已失效，已有课表不会受到影响",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test fun change_and_reauth_use_distinct_ids() {
        SyncNotification.postChanges(context, listOf(locationChange()))
        SyncNotification.postReauthNeeded(context)

        assertNotNull(shadowManager.getNotification(SyncNotification.ID_CHANGES))
        assertNotNull(shadowManager.getNotification(SyncNotification.ID_REAUTH))
        assertEquals(2, shadowManager.size())
    }

    @Test fun repeated_change_notification_replaces_previous_change() {
        SyncNotification.postChanges(context, listOf(ScheduleChange.CourseAdded("旧通知")))
        SyncNotification.postChanges(context, listOf(ScheduleChange.CourseAdded("新通知")))

        assertEquals(1, shadowManager.size())
        assertEquals(
            "新通知",
            shadowManager.getNotification(SyncNotification.ID_CHANGES)
                .extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    private fun locationChange(courseName: String = "高等无机化学") =
        ScheduleChange.LocationChanged(
            courseName = courseName,
            weeks = WeekPattern.of(10),
            old = "TH-B301",
            new = "TH-C204",
        )
}
