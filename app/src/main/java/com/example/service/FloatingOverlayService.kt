package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class FloatingOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayRootView: FrameLayout? = null
    private var collapsedTab: LinearLayout? = null
    private var expandedWindow: LinearLayout? = null
    private var taskListContainer: LinearLayout? = null
    private var taskCountBadge: TextView? = null

    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var prefsManager: PreferencesManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var taskObserveJob: Job? = null
    private var gameCheckJob: Job? = null

    private var initialY = 0
    private var initialTouchY = 0f
    private var isDragging = false
    private var isExpanded = false
    private var isHiddenByGameMode = false

    companion object {
        const val OVERLAY_NOTIF_ID = 2001
        const val OVERLAY_CHANNEL_ID = "overlay_service_channel"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(this)

        startForegroundServiceNotification()

        if (Settings.canDrawOverlays(this)) {
            initOverlayView()
            observeTasks()
            startGameAppDetector()
        } else {
            stopSelf()
        }
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "خدمة النافذة العائمة للمهام",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("النافذة العائمة قيد التشغيل")
            .setContentText("المستطيل العائم متاح على حافة الشاشة للوصول السريع")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(OVERLAY_NOTIF_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = prefsManager.getOverlayY()
        }

        overlayRootView = FrameLayout(this)

        // 1. Collapsed Docked Tab (Semi-hidden rectangle as requested by user)
        collapsedTab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A")) // Dark Slate with slight opacity
                setStroke(dpToPx(2), Color.parseColor("#14B8A6")) // Glowing teal border
                cornerRadii = floatArrayOf(
                    dpToPx(16).toFloat(), dpToPx(16).toFloat(),
                    0f, 0f,
                    0f, 0f,
                    dpToPx(16).toFloat(), dpToPx(16).toFloat()
                )
            }
            background = bg
            setPadding(dpToPx(6), dpToPx(8), dpToPx(4), dpToPx(8))

            // Handle bar glyph
            val bar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(24)).apply {
                    setMargins(0, 0, dpToPx(4), 0)
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2DD4BF"))
                    cornerRadius = dpToPx(2).toFloat()
                }
            }
            addView(bar)

            // Badge text
            taskCountBadge = TextView(context).apply {
                text = "0"
                textSize = 12f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            addView(taskCountBadge)
        }

        // Drag & Tap Listener for the collapsed tab
        collapsedTab?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = layoutParams.y
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaY) > dpToPx(5)) {
                        isDragging = true
                        layoutParams.y = (initialY + deltaY).coerceIn(100, 2000)
                        windowManager?.updateViewLayout(overlayRootView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        expandOverlayWindow()
                    } else {
                        prefsManager.setOverlayY(layoutParams.y)
                    }
                    true
                }
                else -> false
            }
        }

        // 2. Expanded Window (Tasks sheet)
        expandedWindow = createExpandedView()
        expandedWindow?.visibility = View.GONE

        overlayRootView?.addView(collapsedTab)
        overlayRootView?.addView(expandedWindow)

        windowManager?.addView(overlayRootView, layoutParams)
    }

    private fun createExpandedView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val width = dpToPx(320)
            layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F20F172A")) // Rich dark theme
                setStroke(dpToPx(2), Color.parseColor("#14B8A6"))
                cornerRadius = dpToPx(18).toFloat()
            }
            background = bg
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))

            // Header Bar
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

                val title = TextView(context).apply {
                    text = "📋 مهامي الحالية"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    paint.isFakeBoldText = true
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(title)

                // Open full app button
                val openBtn = Button(context).apply {
                    text = "تطبيق ↗"
                    textSize = 11f
                    setTextColor(Color.parseColor("#2DD4BF"))
                    background = null
                    setPadding(dpToPx(4), 0, dpToPx(4), 0)
                    setOnClickListener {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                        collapseOverlayWindow()
                    }
                }
                addView(openBtn)

                // Close / Minimize button
                val closeBtn = Button(context).apply {
                    text = "✕"
                    textSize = 14f
                    setTextColor(Color.parseColor("#94A3B8"))
                    background = null
                    setPadding(dpToPx(6), 0, dpToPx(6), 0)
                    setOnClickListener {
                        collapseOverlayWindow()
                    }
                }
                addView(closeBtn)
            }
            addView(header)

            // Quick Add Task row
            val addRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(8), 0, dpToPx(8))
                }

                val input = EditText(context).apply {
                    hint = "إضافة مهمة سريعة..."
                    setHintTextColor(Color.parseColor("#64748B"))
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        cornerRadius = dpToPx(8).toFloat()
                    }
                    setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(input)

                val addBtn = Button(context).apply {
                    text = "+"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#0D9488"))
                        cornerRadius = dpToPx(8).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(38)).apply {
                        setMargins(dpToPx(6), 0, 0, 0)
                    }
                    setOnClickListener {
                        val text = input.text.toString().trim()
                        if (text.isNotEmpty()) {
                            addNewTaskQuick(text)
                            input.text.clear()
                        }
                    }
                }
                addView(addBtn)
            }
            addView(addRow)

            // Scrollable task list container
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(240))
                taskListContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                addView(taskListContainer)
            }
            addView(scrollView)

            // Footer fold button
            val foldBtn = Button(context).apply {
                text = "طي النافذة ❯"
                textSize = 12f
                setTextColor(Color.parseColor("#CBD5E1"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#334155"))
                    cornerRadius = dpToPx(6).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(34)).apply {
                    setMargins(0, dpToPx(8), 0, 0)
                }
                setOnClickListener {
                    collapseOverlayWindow()
                }
            }
            addView(foldBtn)
        }
    }

    private fun expandOverlayWindow() {
        isExpanded = true
        collapsedTab?.visibility = View.GONE
        expandedWindow?.visibility = View.VISIBLE

        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutParams.gravity = Gravity.CENTER
        layoutParams.x = 0
        windowManager?.updateViewLayout(overlayRootView, layoutParams)
    }

    private fun collapseOverlayWindow() {
        isExpanded = false
        expandedWindow?.visibility = View.GONE
        collapsedTab?.visibility = View.VISIBLE

        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = 0
        layoutParams.y = prefsManager.getOverlayY()
        windowManager?.updateViewLayout(overlayRootView, layoutParams)
    }

    private fun observeTasks() {
        taskObserveJob?.cancel()
        taskObserveJob = serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            db.taskDao().getPendingTasksFlow().collectLatest { tasks ->
                taskCountBadge?.text = tasks.size.toString()
                updateTaskListUI(tasks)
            }
        }
    }

    private fun updateTaskListUI(tasks: List<TaskEntity>) {
        val container = taskListContainer ?: return
        container.removeAllViews()

        if (tasks.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "رائع! لا توجد مهام نشطة حالياً ✨"
                textSize = 13f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(30), 0, dpToPx(30))
            }
            container.addView(emptyView)
            return
        }

        for (task in tasks) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(4), 0, dpToPx(4))
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(8).toFloat()
                }
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))

                val checkbox = CheckBox(context).apply {
                    isChecked = task.isCompleted
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            markTaskDone(task.id)
                        }
                    }
                }
                addView(checkbox)

                val titleText = TextView(context).apply {
                    text = task.title
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dpToPx(6), 0, dpToPx(6), 0)
                    }
                }
                addView(titleText)

                val priorityBadge = TextView(context).apply {
                    val (pText, pColor) = when (task.priority) {
                        "HIGH" -> "عاجل" to "#EF4444"
                        "MEDIUM" -> "متوسط" to "#F59E0B"
                        else -> "عادي" to "#10B981"
                    }
                    text = pText
                    textSize = 10f
                    setTextColor(Color.parseColor(pColor))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#334155"))
                        cornerRadius = dpToPx(4).toFloat()
                    }
                    setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                }
                addView(priorityBadge)
            }
            container.addView(itemRow)
        }
    }

    private fun addNewTaskQuick(title: String) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val newTask = TaskEntity(
                id = UUID.randomUUID().toString(),
                userId = "local_user",
                title = title,
                category = "عام",
                priority = "MEDIUM",
                isCompleted = false,
                hasReminder = false,
                createdAtEpoch = System.currentTimeMillis(),
                updatedAtEpoch = System.currentTimeMillis()
            )
            db.taskDao().insertOrUpdate(newTask)
        }
    }

    private fun markTaskDone(taskId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            db.taskDao().setCompletion(taskId, true, System.currentTimeMillis(), System.currentTimeMillis())
        }
    }

    // App Exclusion & Game Mode Detector
    private fun startGameAppDetector() {
        gameCheckJob?.cancel()
        gameCheckJob = serviceScope.launch(Dispatchers.Default) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            while (isActive) {
                if (prefsManager.isGameModeEnabled()) {
                    val excluded = prefsManager.getExcludedPackages()
                    val foregroundPackage = getForegroundPackage(usageStatsManager)

                    val shouldHide = foregroundPackage != null && excluded.contains(foregroundPackage)
                    if (shouldHide != isHiddenByGameMode) {
                        isHiddenByGameMode = shouldHide
                        withContext(Dispatchers.Main) {
                            if (isHiddenByGameMode) {
                                overlayRootView?.visibility = View.GONE
                            } else {
                                overlayRootView?.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    private fun getForegroundPackage(manager: UsageStatsManager?): String? {
        if (manager == null) return null
        val time = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        taskObserveJob?.cancel()
        gameCheckJob?.cancel()
        if (overlayRootView != null) {
            windowManager?.removeView(overlayRootView)
        }
    }
}
