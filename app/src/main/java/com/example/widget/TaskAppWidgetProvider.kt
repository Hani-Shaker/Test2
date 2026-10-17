package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TaskAppWidgetProvider::class.java)
            val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
            updateWidgets(context, appWidgetManager, allIds)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"

        fun notifyDataChanged(context: Context) {
            val intent = Intent(context, TaskAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            context.sendBroadcast(intent)
        }

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val pendingTasks = db.taskDao().getPendingTasks()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_tasks)

                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    val count = pendingTasks.size
                    views.setTextViewText(R.id.widget_count_badge, "$count مهام")

                    if (pendingTasks.isEmpty()) {
                        views.setTextViewText(R.id.widget_task_1, "🎉 لا توجد مهام نشطة حالياً")
                        views.setViewVisibility(R.id.widget_task_2, View.GONE)
                        views.setViewVisibility(R.id.widget_task_3, View.GONE)
                    } else {
                        views.setTextViewText(R.id.widget_task_1, "• " + pendingTasks[0].title)
                        views.setViewVisibility(R.id.widget_task_1, View.VISIBLE)

                        if (pendingTasks.size > 1) {
                            views.setTextViewText(R.id.widget_task_2, "• " + pendingTasks[1].title)
                            views.setViewVisibility(R.id.widget_task_2, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_task_2, View.GONE)
                        }

                        if (pendingTasks.size > 2) {
                            views.setTextViewText(R.id.widget_task_3, "• " + pendingTasks[2].title + if (pendingTasks.size > 3) " (+${pendingTasks.size - 3})" else "")
                            views.setViewVisibility(R.id.widget_task_3, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_task_3, View.GONE)
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
