package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.ui.theme.PriorityHigh
import com.example.ui.theme.PriorityLow
import com.example.ui.theme.PriorityMedium
import com.example.util.AlarmScheduler
import com.example.widget.TaskAppWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    taskRepository: TaskRepository? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val tasks by db.taskDao().getAllTasksFlow().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") } // الكل, اليوم, القادمة, المتأخرة, المكتملة
    var selectedCategory by remember { mutableStateOf("الكل") }

    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }

    val categories = listOf("الكل", "عمل", "شخصي", "دراسة", "صحة", "عام")
    val filters = listOf("الكل", "اليوم", "القادمة", "المتأخرة", "المكتملة")

    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    val endOfToday = calendar.timeInMillis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startOfToday = calendar.timeInMillis

    val filteredTasks = tasks.filter { task ->
        // Search
        val matchQuery = searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true) || task.description.contains(searchQuery, ignoreCase = true)
        // Category
        val matchCategory = selectedCategory == "الكل" || task.category == selectedCategory
        // Status filter
        val matchFilter = when (selectedFilter) {
            "المكتملة" -> task.isCompleted
            "المتأخرة" -> !task.isCompleted && task.dueDateTime != null && task.dueDateTime < now
            "اليوم" -> !task.isCompleted && task.dueDateTime != null && task.dueDateTime in startOfToday..endOfToday
            "القادمة" -> !task.isCompleted && (task.dueDateTime == null || task.dueDateTime > endOfToday)
            else -> true
        }
        matchQuery && matchCategory && matchFilter
    }

    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    taskToEdit = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مهمة جديدة")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header stats banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "إنجاز مهام اليوم 🎯",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (totalCount == 0) "أضف مهامك الأولى لتبدأ يومك بحماس!" else "أنجزت $completedCount من $totalCount مهمة",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val percent = if (totalCount > 0) (completedCount * 100 / totalCount) else 0
                        Text(
                            text = "$percent%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث في المهام...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Task List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لا توجد مهام تطابق هذا التصنيف 🌿",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "اضغط زر + بالأسفل لإضافة مهمة جديدة",
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
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onToggleComplete = {
                                val newStatus = !task.isCompleted
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.taskDao().setCompletion(
                                        task.id,
                                        newStatus,
                                        if (newStatus) System.currentTimeMillis() else null,
                                        System.currentTimeMillis()
                                    )
                                    TaskAppWidgetProvider.notifyDataChanged(context)

                                    // Cloud sync if signed in
                                    if (Firebase.auth.currentUser != null && taskRepository != null) {
                                        taskRepository.updateTaskCompletion(task.id, newStatus)
                                    }
                                }
                            },
                            onEdit = {
                                taskToEdit = task
                                showAddDialog = true
                            },
                            onDelete = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.taskDao().deleteById(task.id)
                                    AlarmScheduler.cancelTaskAlarm(context, task.id)
                                    TaskAppWidgetProvider.notifyDataChanged(context)

                                    if (Firebase.auth.currentUser != null && taskRepository != null) {
                                        taskRepository.deleteTask(task.id)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Task Dialog
    if (showAddDialog) {
        TaskAddOrEditDialog(
            initialTask = taskToEdit,
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, cat, priority, dueTime, hasAlarm ->
                coroutineScope.launch(Dispatchers.IO) {
                    val taskId = taskToEdit?.id ?: UUID.randomUUID().toString()
                    val newTask = TaskEntity(
                        id = taskId,
                        userId = Firebase.auth.currentUser?.uid ?: "local_user",
                        title = title,
                        description = desc,
                        category = cat,
                        priority = priority,
                        dueDateTime = dueTime,
                        isCompleted = taskToEdit?.isCompleted ?: false,
                        completedAt = taskToEdit?.completedAt,
                        hasReminder = hasAlarm,
                        createdAtEpoch = taskToEdit?.createdAtEpoch ?: System.currentTimeMillis(),
                        updatedAtEpoch = System.currentTimeMillis()
                    )
                    db.taskDao().insertOrUpdate(newTask)
                    TaskAppWidgetProvider.notifyDataChanged(context)

                    // Alarm scheduling
                    if (hasAlarm && dueTime != null) {
                        AlarmScheduler.scheduleTaskAlarm(context, taskId, title, cat, dueTime)
                    } else {
                        AlarmScheduler.cancelTaskAlarm(context, taskId)
                    }

                    // Cloud sync
                    if (Firebase.auth.currentUser != null && taskRepository != null) {
                        taskRepository.createTask(title, desc, cat, priority, dueTime, hasAlarm)
                    }
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TaskItemCard(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (task.priority) {
        "HIGH" -> PriorityHigh
        "MEDIUM" -> PriorityMedium
        else -> PriorityLow
    }
    val priorityText = when (task.priority) {
        "HIGH" -> "عاجل"
        "MEDIUM" -> "متوسط"
        else -> "عادي"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "تبديل حالة المهمة",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title & Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        fontSize = 12.sp,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(task.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Priority
                    Box(
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(priorityText, fontSize = 10.sp, color = priorityColor, fontWeight = FontWeight.Bold)
                    }

                    // Due date & Alarm indicator
                    if (task.dueDateTime != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val isOverdue = !task.isCompleted && task.dueDateTime < System.currentTimeMillis()
                        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        val dateText = sdf.format(Date(task.dueDateTime))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (task.hasReminder) Icons.Default.Alarm else Icons.Default.CalendarToday,
                                contentDescription = "موعد الاستحقاق",
                                tint = if (isOverdue) PriorityHigh else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = dateText,
                                fontSize = 10.sp,
                                color = if (isOverdue) PriorityHigh else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Edit & Delete actions
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "تعديل",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف",
                    tint = Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TaskAddOrEditDialog(
    initialTask: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, category: String, priority: String, dueTime: Long?, hasAlarm: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var category by remember { mutableStateOf(initialTask?.category ?: "عام") }
    var priority by remember { mutableStateOf(initialTask?.priority ?: "MEDIUM") }
    var dueDateTime by remember { mutableStateOf<Long?>(initialTask?.dueDateTime) }
    var hasAlarm by remember { mutableStateOf(initialTask?.hasReminder ?: false) }

    val categories = listOf("عمل", "شخصي", "دراسة", "صحة", "عام")
    val priorities = listOf("LOW" to "عادي", "MEDIUM" to "متوسط", "HIGH" to "عاجل")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialTask == null) "إضافة مهمة جديدة ✨" else "تعديل المهمة")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المهمة *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("تفاصيل إضافية (اختياري)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("التصنيف:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text("درجة الأهمية:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    priorities.forEach { (key, label) ->
                        FilterChip(
                            selected = priority == key,
                            onClick = { priority = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date & Time Picker trigger
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val currentCal = Calendar.getInstance().apply {
                    if (dueDateTime != null) timeInMillis = dueDateTime!!
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الموعد المحدد:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (dueDateTime == null) "لم يحدد موعد" else sdf.format(Date(dueDateTime!!)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            val nowCal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    nowCal.set(Calendar.YEAR, y)
                                    nowCal.set(Calendar.MONTH, m)
                                    nowCal.set(Calendar.DAY_OF_MONTH, d)

                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            nowCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            nowCal.set(Calendar.MINUTE, minute)
                                            nowCal.set(Calendar.SECOND, 0)
                                            dueDateTime = nowCal.timeInMillis
                                        },
                                        currentCal.get(Calendar.HOUR_OF_DAY),
                                        currentCal.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                currentCal.get(Calendar.YEAR),
                                currentCal.get(Calendar.MONTH),
                                currentCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) {
                        Text(if (dueDateTime == null) "تحديد الموعد" else "تغيير")
                    }
                }

                if (dueDateTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تنبيه ومنبه في الموعد ⏰", fontSize = 13.sp)
                        Switch(
                            checked = hasAlarm,
                            onCheckedChange = { hasAlarm = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), description.trim(), category, priority, dueDateTime, hasAlarm)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("حفظ المهمة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
