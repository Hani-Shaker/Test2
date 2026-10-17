package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "preferences",
    val userId: String = "",
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val accentColor: String = "TEAL", // TEAL, BLUE, PURPLE, ORANGE, EMERALD
    val fontSize: String = "MEDIUM", // SMALL, MEDIUM, LARGE
    val overlayEnabled: Boolean = true,
    val gameModeEnabled: Boolean = true,
    val excludedPackages: List<String> = emptyList(),
    val periodicReminderIntervalHours: Long = 4L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
