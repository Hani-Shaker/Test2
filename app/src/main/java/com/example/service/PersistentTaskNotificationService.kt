package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PersistentTaskNotificationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var observeJob: Job? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PersistentTaskNotificationService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background start restrictions fallback
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PersistentTaskNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        startForeground(NotificationHelper.ONGOING_NOTIFICATION_ID, buildNotification(emptyList()))
        observeTasks()
    }

    private fun observeTasks() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            db.taskDao().getPendingTasksFlow().collectLatest { pendingList ->
                val notification = buildNotification(pendingList.map { it.title })
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.ONGOING_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(tasks: List<String>): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val count = tasks.size
        val title = if (count == 0) "لا توجد مهام معلقة 🎉" else "لديك $count مهام نشطة"
        val summaryText = if (tasks.isEmpty()) {
            "يومك منظم وجميل! اضغط لإضافة مهام جديدة."
        } else {
            tasks.take(3).joinToString("\n") { "• $it" } + if (tasks.size > 3) "\n... والمزيد (${tasks.size - 3})" else ""
        }

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(if (tasks.isNotEmpty()) tasks.first() else "اضغط لفتح التطبيق")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
    }
}
