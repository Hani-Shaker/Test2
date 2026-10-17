package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.local.PreferencesManager
import com.example.service.FloatingOverlayService
import com.example.service.PersistentTaskNotificationService
import com.example.ui.auth.GoogleAuthManager
import com.example.ui.auth.GoogleSignInButton
import com.example.util.AlarmScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    onThemeChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val currentTheme by prefs.themeMode.collectAsState()
    val currentAccent by prefs.accentColor.collectAsState()
    val currentFontSize by prefs.fontSize.collectAsState()
    val isOverlayActive by prefs.overlayEnabled.collectAsState()
    val isGameModeActive by prefs.gameModeEnabled.collectAsState()
    val isOngoingActive by prefs.ongoingNotificationEnabled.collectAsState()
    val periodicHours by prefs.periodicHours.collectAsState()
    val excludedPkgs by prefs.excludedPackages.collectAsState()

    var showAddPkgDialog by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    val hasOverlayPermission = Settings.canDrawOverlays(context)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Text(
                text = "الإعدادات والتخصيص الشامل ⚙️",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "خصص النافذة العائمة، وضع الألعاب، المظهر، والمزامنة السحابية",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Floating Overlay Window (النافذة العائمة)
        item {
            SettingsSectionCard(title = "النافذة العائمة المصغرة فوق التطبيقات 🪟", icon = Icons.Default.Layers) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تفعيل المستطيل العائم الجانبي",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "مستطيل شبه مخفي على حافة الشاشة يتوسع بلمسة واحدة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isOverlayActive,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (Settings.canDrawOverlays(context)) {
                                    prefs.setOverlayEnabled(true)
                                    FloatingOverlayService.start(context)
                                } else {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    Toast.makeText(context, "يرجى منح إذن الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                prefs.setOverlayEnabled(false)
                                FloatingOverlayService.stop(context)
                            }
                        },
                        modifier = Modifier.testTag("floating_overlay_switch")
                    )
                }

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("منح إذن الظهور فوق التطبيقات 🔓", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 2: Game Mode & Blacklist (وضع الألعاب والتطبيقات المستبعدة)
        item {
            SettingsSectionCard(title = "وضع الألعاب واستثناء التطبيقات 🎮", icon = Icons.Default.Games) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "إخفاء المستطيل العائم تلقائياً أثناء اللعب",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "يمنع ظهور النافذة العائمة أثناء تشغيل الألعاب المستبعدة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isGameModeActive,
                        onCheckedChange = { prefs.setGameModeEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "حزم التطبيقات والألعاب المستبعدة حالياً (${excludedPkgs.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                excludedPkgs.forEach { pkg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• $pkg",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { prefs.removeExcludedPackage(pkg) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف الاستثناء",
                                tint = Color.Red.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { showAddPkgDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة لعبة أو تطبيق للاستثناء", fontSize = 12.sp)
                }
            }
        }

        // Section 3: Notification Shade & Periodic Alarms (الإشعارات المستمرة والتذكير الدوري)
        item {
            SettingsSectionCard(title = "الإشعارات والتذكيرات الدورية 🔔", icon = Icons.Default.Notifications) {
                // Ongoing Notification toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "شريط الإشعارات المستمر للمهام",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "عرض المهام النشطة في شريط الإشعارات للوصول السريع",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isOngoingActive,
                        onCheckedChange = { enable ->
                            prefs.setOngoingNotificationEnabled(enable)
                            if (enable) {
                                PersistentTaskNotificationService.start(context)
                            } else {
                                PersistentTaskNotificationService.stop(context)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Periodic summary reminder
                Text(
                    text = "تكرار إشعار الملخص الدوري للمهام المتأخرة والحالية:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                val intervals = listOf(1 to "كل ساعة", 2 to "كل ساعتين", 4 to "كل 4 ساعات", 8 to "كل 8 ساعات", 24 to "يومياً")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervals.forEach { (hours, label) ->
                        FilterChip(
                            selected = periodicHours == hours,
                            onClick = {
                                prefs.setPeriodicReminderHours(hours)
                                AlarmScheduler.schedulePeriodicCheck(context, hours)
                                Toast.makeText(context, "تم ضبط التذكير الدوري: $label", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Section 4: Themes & Visual Customization (المظهر المخصص والوضع الليلي)
        item {
            SettingsSectionCard(title = "المظهر وتخصيص الواجهة بالكامل 🎨", icon = Icons.Default.Palette) {
                Text("وضع الإضاءة:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themeModes = listOf("DARK" to "ليلي مريح 🌙", "LIGHT" to "نهاري ☀️", "SYSTEM" to "تلقائي النظام ⚙️")
                    themeModes.forEach { (mode, label) ->
                        FilterChip(
                            selected = currentTheme == mode,
                            onClick = {
                                prefs.setThemeMode(mode)
                                onThemeChanged()
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("لون التمييز والأزرار المفضل:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val accents = listOf(
                        "TEAL" to ("فيروزي" to Color(0xFF14B8A6)),
                        "BLUE" to ("أزرق" to Color(0xFF3B82F6)),
                        "PURPLE" to ("بنفسجي" to Color(0xFFA855F7)),
                        "ORANGE" to ("برتقالي" to Color(0xFFF97316)),
                        "EMERALD" to ("زمردي" to Color(0xFF10B981))
                    )
                    accents.forEach { (key, pair) ->
                        val (name, color) = pair
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    prefs.setAccentColor(key)
                                    onThemeChanged()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentAccent == key) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("حجم الخط:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fontSizes = listOf("SMALL" to "صغير", "MEDIUM" to "متوسط", "LARGE" to "كبير")
                    fontSizes.forEach { (size, label) ->
                        FilterChip(
                            selected = currentFontSize == size,
                            onClick = { prefs.setFontSize(size) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Section 5: Cloud Sync (المزامنة السحابية)
        item {
            SettingsSectionCard(title = "المزامنة السحابية وحفظ البيانات ☁️", icon = Icons.Default.CloudDone) {
                if (currentUser != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المزامنة مفعلة بحساب Google ✔️",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentUser?.email ?: "حساب مسجل",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                GoogleAuthManager.signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {
                                        currentUser = null
                                    },
                                    scope = coroutineScope
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("تسجيل الخروج", fontSize = 11.sp)
                        }
                    }
                } else {
                    Text(
                        text = "قم بتسجيل الدخول لضمان حفظ كافة مهامك وتدويناتك على السحابة ومزامنتها بين كافة أجهزتك بأمان.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GoogleSignInButton(
                        onAuthSuccess = {
                            currentUser = Firebase.auth.currentUser
                            Toast.makeText(context, "تم تسجيل الدخول وتفعيل المزامنة السحابية بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        onAuthError = { errorMsg ->
                            Toast.makeText(context, "فشل تسجيل الدخول: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Add Excluded Package Dialog
    if (showAddPkgDialog) {
        var pkgInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPkgDialog = false },
            title = { Text("استثناء لعبة أو تطبيق 🎮") },
            text = {
                Column {
                    Text(
                        text = "أدخل اسم حزمة التطبيق (Package Name) مثل: com.supercell.clashofclans وسيتم إخفاء المستطيل العائم تلقائياً بمجرد فتحها.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pkgInput,
                        onValueChange = { pkgInput = it },
                        label = { Text("اسم الحزمة (مثلاً: com.dts.freefireth)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pkgInput.isNotBlank()) {
                            prefs.addExcludedPackage(pkgInput.trim())
                            showAddPkgDialog = false
                        }
                    },
                    enabled = pkgInput.isNotBlank()
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPkgDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
