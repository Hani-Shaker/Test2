package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.DiaryRepository
import com.example.data.repository.TaskRepository
import com.example.service.FloatingOverlayService
import com.example.service.PersistentTaskNotificationService
import com.example.ui.auth.GoogleAuthManager
import com.example.ui.screens.DiaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.TaskFlowTheme
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import com.example.widget.TaskAppWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Notification channels setup
        NotificationHelper.createNotificationChannels(this)

        val prefs = PreferencesManager(this)

        // Start ongoing notification if enabled
        if (prefs.isOngoingNotificationEnabled()) {
            PersistentTaskNotificationService.start(this)
        }

        // Start floating overlay if enabled and permitted
        if (prefs.isOverlayEnabled() && Settings.canDrawOverlays(this)) {
            FloatingOverlayService.start(this)
        }

        // Initial periodic check schedule
        AlarmScheduler.schedulePeriodicCheck(this, prefs.getPeriodicReminderHours())

        // Initial widget update
        TaskAppWidgetProvider.notifyDataChanged(this)

        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            val accentColor by prefs.accentColor.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemDark
            }

            var themeKey by remember { mutableIntStateOf(0) }

            TaskFlowTheme(
                darkTheme = isDark,
                accentColorName = accentColor
            ) {
                MainAppScreen(
                    prefs = prefs,
                    onThemeChanged = { themeKey++ }
                )
            }
        }
    }
}

@Composable
fun MainAppScreen(
    prefs: PreferencesManager,
    onThemeChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Attempt silent Google Sign-In for cloud sync
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = coroutineScope
        )
    }

    // Repositories initialization
    val dbId = remember { context.getString(R.string.firestore_database_id) }
    val firestore = remember { FirebaseFirestore.getInstance(dbId) }
    val taskRepo = remember { TaskRepository(firestore) }
    val diaryRepo = remember { DiaryRepository(firestore) }

    var selectedTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        Triple("المهام", Icons.Default.Checklist, "tasks_nav_item"),
        Triple("اليوميات", Icons.Default.Book, "diary_nav_item"),
        Triple("الإحصائيات", Icons.Default.BarChart, "stats_nav_item"),
        Triple("الإعدادات", Icons.Default.Settings, "settings_nav_item")
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp
            ) {
                navItems.forEachIndexed { index, (label, icon, testTag) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.testTag(testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TasksScreen(taskRepository = taskRepo)
                1 -> DiaryScreen(diaryRepository = diaryRepo)
                2 -> StatsScreen()
                3 -> SettingsScreen(prefs = prefs, onThemeChanged = onThemeChanged)
            }
        }
    }
}
