package com.campusute.app.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local app preferences (v1.3 settings screen): dark-mode override and in-app
 * notification toggle, persisted in a private SharedPreferences file. Flows are
 * hot so the activity applies the theme change immediately.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("campusute_settings", Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK, false))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, enabled).apply()
        _darkMode.value = enabled
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notifications.value = enabled
    }

    private companion object {
        const val KEY_DARK = "dark_mode"
        const val KEY_NOTIFICATIONS = "notifications"
    }
}
