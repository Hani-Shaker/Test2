package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.DiaryEntity
import com.example.data.local.PreferencesManager
import com.example.data.repository.DiaryRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun DiaryScreen(
    diaryRepository: DiaryRepository? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val diaries by db.diaryDao().getAllDiariesFlow().collectAsState(initial = emptyList())

    val isPinConfigured = prefs.isPinSet()
    var isUnlocked by remember { mutableStateOf(!isPinConfigured) }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<DiaryEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMoodFilter by remember { mutableStateOf("الكل") }

    val moods = listOf("الكل", "هادئ 😊", "منجز 🚀", "سعيد 🌟", "ممتن 🙏", "متأمل 💭")

    if (!isUnlocked) {
        // Locked Screen Gate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "قفل اليوميات",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "اليوميات والملاحظات الخاصة 🔒",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "أدخل رمز PIN المكون من 4 أرقام لفتح هذا القسم السري",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN indicator circles
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val filled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                }

                if (pinError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = pinError!!,
                        color = Color.Red,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("مسح", "0", "مسح الكل")
                    )

                    for (row in rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            when (key) {
                                                "مسح" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                        pinError = null
                                                    }
                                                }
                                                "مسح الكل" -> {
                                                    enteredPin = ""
                                                    pinError = null
                                                }
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        enteredPin += key
                                                        pinError = null
                                                        if (enteredPin.length == 4) {
                                                            if (prefs.verifyPin(enteredPin)) {
                                                                isUnlocked = true
                                                                enteredPin = ""
                                                            } else {
                                                                pinError = "رمز PIN غير صحيح! حاول ثانية."
                                                                enteredPin = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = if (key.length > 2) 11.sp else 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Unlocked Diary Screen
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    entryToEdit = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_diary_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "كتابة تدوينة جديدة")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "يومياتي وملاحظاتي الخاصة 📖",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isPinConfigured) "محمية برمز PIN سري" else "لم يتم تعيين رمز حماية بعد",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = { showPinSetupDialog = true }) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "إعدادات الأمان والقفل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isPinConfigured) {
                        IconButton(onClick = { isUnlocked = false }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "قفل الآن",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث في اليوميات والملاحظات...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mood filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(moods) { mood ->
                    FilterChip(
                        selected = selectedMoodFilter == mood,
                        onClick = { selectedMoodFilter = mood },
                        label = { Text(mood, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredDiaries = diaries.filter { entry ->
                val matchSearch = searchQuery.isBlank() || entry.title.contains(searchQuery, ignoreCase = true) || entry.content.contains(searchQuery, ignoreCase = true)
                val matchMood = selectedMoodFilter == "الكل" || entry.mood.contains(selectedMoodFilter.take(4))
                matchSearch && matchMood
            }

            if (filteredDiaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لا توجد خواطر أو ملاحظات مسجلة هنا ✍️",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "اضغط زر + بالأسفل لتدوين فكرة أو يوميات جديدة",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(filteredDiaries, key = { it.id }) { entry ->
                        DiaryEntryCard(
                            entry = entry,
                            onEdit = {
                                entryToEdit = entry
                                showAddDialog = true
                            },
                            onDelete = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.diaryDao().deleteById(entry.id)
                                    if (Firebase.auth.currentUser != null && diaryRepository != null) {
                                        diaryRepository.deleteEntry(entry.id)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Entry Dialog
    if (showAddDialog) {
        DiaryAddOrEditDialog(
            initialEntry = entryToEdit,
            onDismiss = { showAddDialog = false },
            onSave = { title, content, mood ->
                coroutineScope.launch(Dispatchers.IO) {
                    val entryId = entryToEdit?.id ?: UUID.randomUUID().toString()
                    val newEntry = DiaryEntity(
                        id = entryId,
                        userId = Firebase.auth.currentUser?.uid ?: "local_user",
                        title = title,
                        content = content,
                        mood = mood,
                        createdAtEpoch = entryToEdit?.createdAtEpoch ?: System.currentTimeMillis(),
                        updatedAtEpoch = System.currentTimeMillis()
                    )
                    db.diaryDao().insertOrUpdate(newEntry)

                    if (Firebase.auth.currentUser != null && diaryRepository != null) {
                        diaryRepository.createEntry(title, content, mood)
                    }
                }
                showAddDialog = false
            }
        )
    }

    // PIN Setup / Change Dialog
    if (showPinSetupDialog) {
        PinSetupDialog(
            isCurrentlySet = isPinConfigured,
            onDismiss = { showPinSetupDialog = false },
            onSetPin = { newPin ->
                prefs.setPin(newPin)
                showPinSetupDialog = false
            },
            onClearPin = {
                prefs.clearPin()
                showPinSetupDialog = false
            }
        )
    }
}

@Composable
fun DiaryEntryCard(
    entry: DiaryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    val dateText = sdf.format(Date(entry.createdAtEpoch))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Mood badge
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = entry.mood,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "تعديل التدوينة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف التدوينة",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiaryAddOrEditDialog(
    initialEntry: DiaryEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, mood: String) -> Unit
) {
    var title by remember { mutableStateOf(initialEntry?.title ?: "") }
    var content by remember { mutableStateOf(initialEntry?.content ?: "") }
    var mood by remember { mutableStateOf(initialEntry?.mood ?: "هادئ 😊") }

    val moods = listOf("هادئ 😊", "منجز 🚀", "سعيد 🌟", "ممتن 🙏", "متأمل 💭")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialEntry == null) "تدوين خاطرة أو ملاحظة سرية ✍️" else "تعديل الملاحظة")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان التدوينة *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("الحالة المزاجية:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(moods) { m ->
                        FilterChip(
                            selected = mood == m,
                            onClick = { mood = m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("المحتوى والخواطر *") },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title.trim(), content.trim(), mood)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("حفظ التدوينة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun PinSetupDialog(
    isCurrentlySet: Boolean,
    onDismiss: () -> Unit,
    onSetPin: (newPin: String) -> Unit,
    onClearPin: () -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCurrentlySet) "تغيير رمز PIN السري 🔑" else "تعيين رمز PIN لليوميات 🔐") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "اختر رمزاً مكوناً من 4 أرقام لحماية يومياتك وملاحظاتك الشخصية.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("الرمز الجديد (4 أرقام)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("تأكيد الرمز") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMessage!!, color = Color.Red, fontSize = 12.sp)
                }

                if (isCurrentlySet) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onClearPin,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إلغاء القفل برمز PIN نهائياً")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length != 4) {
                        errorMessage = "يجب أن يتكون الرمز من 4 أرقام بالضبط"
                    } else if (newPin != confirmPin) {
                        errorMessage = "الرمزان غير متطابقين!"
                    } else {
                        onSetPin(newPin)
                    }
                },
                enabled = newPin.length == 4 && confirmPin.length == 4
            ) {
                Text("حفظ الرمز")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
