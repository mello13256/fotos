package io.github.mello13256.fotos.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = DEFAULT_COLUMNS,
) {
    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 8
        const val DEFAULT_COLUMNS = 4
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val gridColumnsKey = intPreferencesKey("grid_columns")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[themeKey]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[dynamicColorKey] ?: true,
            gridColumns = (prefs[gridColumnsKey] ?: AppSettings.DEFAULT_COLUMNS)
                .coerceIn(AppSettings.MIN_COLUMNS, AppSettings.MAX_COLUMNS),
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit {
            it[gridColumnsKey] = columns.coerceIn(AppSettings.MIN_COLUMNS, AppSettings.MAX_COLUMNS)
        }
    }
}
