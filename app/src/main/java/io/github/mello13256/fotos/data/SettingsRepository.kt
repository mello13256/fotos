package io.github.mello13256.fotos.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** De onde vêm as cores da app. */
enum class ColorSource {
    /** Material You: cores do fundo de ecrã (Android 12+). */
    WALLPAPER,

    /** Cores tiradas de uma fotografia escolhida no visualizador. */
    PHOTO,

    /** Paleta própria da app. */
    DEFAULT,
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = defaultColorSource,
    val seedColor: Int? = null,
    val gridColumns: Int = DEFAULT_COLUMNS,
    val mascotEnabled: Boolean = true,
) {
    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 8
        const val DEFAULT_COLUMNS = 4

        val defaultColorSource: ColorSource
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorSource.WALLPAPER else ColorSource.DEFAULT
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val colorSourceKey = stringPreferencesKey("color_source")
    private val legacyDynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val seedColorKey = intPreferencesKey("seed_color")
    private val gridColumnsKey = intPreferencesKey("grid_columns")
    private val mascotKey = booleanPreferencesKey("mascot")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val colorSource = prefs[colorSourceKey]?.let { name -> ColorSource.entries.firstOrNull { it.name == name } }
            ?: when (prefs[legacyDynamicColorKey]) {
                false -> ColorSource.DEFAULT
                else -> AppSettings.defaultColorSource
            }
        AppSettings(
            theme = prefs[themeKey]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            colorSource = colorSource,
            seedColor = prefs[seedColorKey],
            gridColumns = (prefs[gridColumnsKey] ?: AppSettings.DEFAULT_COLUMNS)
                .coerceIn(AppSettings.MIN_COLUMNS, AppSettings.MAX_COLUMNS),
            mascotEnabled = prefs[mascotKey] ?: true,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setColorSource(source: ColorSource) {
        context.dataStore.edit { it[colorSourceKey] = source.name }
    }

    /** Passa a usar as cores de uma fotografia, a partir da sua cor principal. */
    suspend fun setPhotoColor(seed: Int) {
        context.dataStore.edit {
            it[seedColorKey] = seed
            it[colorSourceKey] = ColorSource.PHOTO.name
        }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit {
            it[gridColumnsKey] = columns.coerceIn(AppSettings.MIN_COLUMNS, AppSettings.MAX_COLUMNS)
        }
    }

    suspend fun setMascotEnabled(enabled: Boolean) {
        context.dataStore.edit { it[mascotKey] = enabled }
    }
}
