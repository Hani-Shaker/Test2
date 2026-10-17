package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("taskflow_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(getAccentColor())
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _fontSize = MutableStateFlow(getFontSize())
    val fontSize: StateFlow<String> = _fontSize.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(isOverlayEnabled())
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _gameModeEnabled = MutableStateFlow(isGameModeEnabled())
    val gameModeEnabled: StateFlow<Boolean> = _gameModeEnabled.asStateFlow()

    private val _excludedPackages = MutableStateFlow(getExcludedPackages())
    val excludedPackages: StateFlow<Set<String>> = _excludedPackages.asStateFlow()

    private val _ongoingNotificationEnabled = MutableStateFlow(isOngoingNotificationEnabled())
    val ongoingNotificationEnabled: StateFlow<Boolean> = _ongoingNotificationEnabled.asStateFlow()

    private val _periodicHours = MutableStateFlow(getPeriodicReminderHours())
    val periodicHours: StateFlow<Int> = _periodicHours.asStateFlow()

    fun getThemeMode(): String = prefs.getString("theme_mode", "DARK") ?: "DARK"
    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun getAccentColor(): String = prefs.getString("accent_color", "TEAL") ?: "TEAL"
    fun setAccentColor(color: String) {
        prefs.edit().putString("accent_color", color).apply()
        _accentColor.value = color
    }

    fun getFontSize(): String = prefs.getString("font_size", "MEDIUM") ?: "MEDIUM"
    fun setFontSize(size: String) {
        prefs.edit().putString("font_size", size).apply()
        _fontSize.value = size
    }

    fun isOverlayEnabled(): Boolean = prefs.getBoolean("overlay_enabled", false)
    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_enabled", enabled).apply()
        _overlayEnabled.value = enabled
    }

    fun getOverlayDockSide(): String = prefs.getString("overlay_dock_side", "RIGHT") ?: "RIGHT"
    fun setOverlayDockSide(side: String) {
        prefs.edit().putString("overlay_dock_side", side).apply()
    }

    fun getOverlayY(): Int = prefs.getInt("overlay_y", 400)
    fun setOverlayY(y: Int) {
        prefs.edit().putInt("overlay_y", y).apply()
    }

    fun isGameModeEnabled(): Boolean = prefs.getBoolean("game_mode_enabled", true)
    fun setGameModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("game_mode_enabled", enabled).apply()
        _gameModeEnabled.value = enabled
    }

    fun getExcludedPackages(): Set<String> = prefs.getStringSet("excluded_packages", setOf(
        "com.google.android.play.games",
        "com.pubg.krmobile",
        "com.dts.freefireth",
        "com.activision.callofduty.shooter",
        "com.mojang.minecraftpe",
        "com.roblox.client"
    )) ?: emptySet()

    fun setExcludedPackages(packages: Set<String>) {
        prefs.edit().putStringSet("excluded_packages", packages).apply()
        _excludedPackages.value = packages
    }

    fun addExcludedPackage(pkg: String) {
        val current = getExcludedPackages().toMutableSet()
        current.add(pkg)
        setExcludedPackages(current)
    }

    fun removeExcludedPackage(pkg: String) {
        val current = getExcludedPackages().toMutableSet()
        current.remove(pkg)
        setExcludedPackages(current)
    }

    fun isOngoingNotificationEnabled(): Boolean = prefs.getBoolean("ongoing_notification_enabled", true)
    fun setOngoingNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ongoing_notification_enabled", enabled).apply()
        _ongoingNotificationEnabled.value = enabled
    }

    fun getPeriodicReminderHours(): Int = prefs.getInt("periodic_reminder_hours", 4)
    fun setPeriodicReminderHours(hours: Int) {
        prefs.edit().putInt("periodic_reminder_hours", hours).apply()
        _periodicHours.value = hours
    }

    // PIN Protection for Diary
    fun isPinSet(): Boolean = prefs.contains("diary_pin_hash")

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString("diary_pin_hash", hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString("diary_pin_hash", null) ?: return true
        return savedHash == hashPin(pin)
    }

    fun clearPin() {
        prefs.edit().remove("diary_pin_hash").apply()
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
