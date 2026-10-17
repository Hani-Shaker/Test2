package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_REMINDERS_ID = "task_reminders_channel"
    const val CHANNEL_ONGOING_ID = "task_ongoing_channel"
    const val CHANNEL_SUMMARY_ID = "task_summary_channel"

    const val ONGOING_NOTIFICATION_ID = 1001
    const val SUMMARY_NOTIFICATION_ID = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Reminders Channel
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS_ID,
                "تنبيهات المواعيد والمهام",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات تنبيهية للمهام المستحقة بمواعيد محددة"
                enableVibration(true)
            }

            // Ongoing Tasks Channel
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                "قائمة المهام الحالية المستمرة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "شريط الإشعارات المستمر لعرض المهام النشطة للوصول السريع"
                setShowBadge(false)
            }

            // Periodic Summary Channel
            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY_ID,
                "ملخص وتذكيرات دورية للمهام",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات دورية تذكرك بالمهام الحالية والمتأخرة"
            }

            notificationManager.createNotificationChannels(listOf(reminderChannel, ongoingChannel, summaryChannel))
        }
    }
}
