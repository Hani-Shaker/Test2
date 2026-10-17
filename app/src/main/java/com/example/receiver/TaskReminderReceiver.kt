package com.example.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MARK_DONE = "com.example.ACTION_MARK_DONE"
        const val EXTRA_NOTIFICATION_ID = "extra_notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            AlarmScheduler.ACTION_TASK_REMINDER -> {
                val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
                val title = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "مهمة مستحقة"
                val category = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_CATEGORY) ?: "عام"
                val notifId = taskId.hashCode()

                // Intent to open app
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Intent to mark done directly
                val markDoneIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                    this.action = ACTION_MARK_DONE
                    putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_NOTIFICATION_ID, notifId)
                }
                val markDonePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notifId + 1,
                    markDoneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("⏰ تذكير بموعد المهمة: $title")
                    .setContentText("التصنيف: $category - حان وقت إنجاز هذه المهمة!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(openPendingIntent)
                    .addAction(android.R.drawable.checkbox_on_background, "تم الإنجاز ✔️", markDonePendingIntent)
                    .build()

                notificationManager.notify(notifId, notification)
            }

            ACTION_MARK_DONE -> {
                val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notifId != 0) {
                    notificationManager.cancel(notifId)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(context)
                    db.taskDao().setCompletion(taskId, true, System.currentTimeMillis(), System.currentTimeMillis())
                }
            }

            AlarmScheduler.ACTION_PERIODIC_CHECK -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(context)
                    val pendingTasks = db.taskDao().getPendingTasks()
                    val overdueTasks = db.taskDao().getOverdueTasks(System.currentTimeMillis())

                    if (pendingTasks.isNotEmpty()) {
                        val openIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val pendingOpen = PendingIntent.getActivity(
                            context,
                            NotificationHelper.SUMMARY_NOTIFICATION_ID,
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val overdueText = if (overdueTasks.isNotEmpty()) " (منها ${overdueTasks.size} متأخرة ⚠️)" else ""
                        val contentText = "لديك ${pendingTasks.size} مهام نشطة بانتظارك$overdueText. اضغط للاطلاع والإنجاز!"

                        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_SUMMARY_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("📋 ملخص المهام الدوري")
                            .setContentText(contentText)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .setContentIntent(pendingOpen)
                            .build()

                        notificationManager.notify(NotificationHelper.SUMMARY_NOTIFICATION_ID, notification)
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule periodic and upcoming alarms
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(context)
                    val pendingTasks = db.taskDao().getPendingTasks()
                    val now = System.currentTimeMillis()
                    for (task in pendingTasks) {
                        if (task.hasReminder && task.dueDateTime != null && task.dueDateTime > now) {
                            AlarmScheduler.scheduleTaskAlarm(
                                context,
                                task.id,
                                task.title,
                                task.category,
                                task.dueDateTime
                            )
                        }
                    }
                    AlarmScheduler.schedulePeriodicCheck(context, 4)
                }
            }
        }
    }
}
