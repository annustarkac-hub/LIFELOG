package com.lifelog.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class TimeFormat { SYSTEM, H24, H12 }

data class AppSettings(
    val onboardingAccepted: Boolean = false,
    val trackingEnabled: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val excludedPackages: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore(name = "lifelog_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_accepted")
        val tracking = booleanPreferencesKey("tracking_enabled")
        val theme = stringPreferencesKey("theme")
        val weekStart = stringPreferencesKey("week_start")
        val timeFormat = stringPreferencesKey("time_format")
        val excluded = stringSetPreferencesKey("excluded_packages")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        onboardingAccepted = this[K.onboarding] ?: false,
        trackingEnabled = this[K.tracking] ?: true,
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        weekStart = this[K.weekStart]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: DayOfWeek.MONDAY,
        timeFormat = this[K.timeFormat]?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() } ?: TimeFormat.SYSTEM,
        excludedPackages = this[K.excluded] ?: emptySet(),
    )

    suspend fun setOnboardingAccepted(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setTrackingEnabled(v: Boolean) = context.dataStore.edit { it[K.tracking] = v }
    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setWeekStart(v: DayOfWeek) = context.dataStore.edit { it[K.weekStart] = v.name }
    suspend fun setTimeFormat(v: TimeFormat) = context.dataStore.edit { it[K.timeFormat] = v.name }
    suspend fun setExcluded(pkg: String, excluded: Boolean) = context.dataStore.edit {
        val cur = it[K.excluded] ?: emptySet()
        it[K.excluded] = if (excluded) cur + pkg else cur - pkg
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
