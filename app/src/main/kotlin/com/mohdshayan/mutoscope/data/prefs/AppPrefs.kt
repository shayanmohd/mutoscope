package com.mohdshayan.mutoscope.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mohdshayan.mutoscope.core.stats.DrawingDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class Tool { PENCIL, INK, MARKER, ERASER, FILL, LASSO }

enum class OnionTint { LIGHT_TABLE, ORCHID, GRAPHITE }

enum class ExportFormat { GIF, MP4 }

/** Everything the editor reads from settings, gathered so it arrives as one value. */
data class EditorPrefs(
    val onionBefore: Int = 2,
    val onionAfter: Int = 1,
    val onionTint: OnionTint = OnionTint.LIGHT_TABLE,
    val onionOpacity: Float = 0.45f,
    val lightTable: Boolean = false,
    val streamline: Int = 40,
    val tool: Tool = Tool.PENCIL,
    val brush: Tool = Tool.PENCIL,
    val brushSizes: Map<String, Float> = emptyMap(),
    val brushOpacity: Float = 1f,
    val colour: Int = 0xFF1C1D24.toInt(),
    val recentColours: List<Int> = emptyList(),
    val leftHanded: Boolean = false,
    val fillTolerance: Int = 24,
    val fillCloseGaps: Boolean = true,
) {
    fun sizeOf(tool: Tool): Float = brushSizes[tool.name] ?: defaultSize(tool)

    companion object {
        fun defaultSize(tool: Tool): Float = when (tool) {
            Tool.PENCIL -> 5f
            Tool.INK -> 10f
            Tool.MARKER -> 26f
            Tool.ERASER -> 36f
            else -> 8f
        }
    }
}

/** Local-only, opt-in counts shown in Settings. Nothing here ever leaves the phone. */
data class Counts(
    val optIn: Boolean,
    val successfulExports: Int,
    val drawingDays: List<Long>,
)

class AppPrefs(private val context: Context) {

    private object Keys {
        val SAMPLE_CREATED = booleanPreferencesKey("sampleCreated")
        val SAMPLE_PLAYED = booleanPreferencesKey("samplePlayed")
        val ONION_BEFORE = intPreferencesKey("onionBefore")
        val ONION_AFTER = intPreferencesKey("onionAfter")
        val ONION_TINT = stringPreferencesKey("onionTint")
        val ONION_OPACITY = floatPreferencesKey("onionOpacity")
        val LIGHT_TABLE = booleanPreferencesKey("lightTable")
        val STREAMLINE = intPreferencesKey("streamline")
        val LAST_TOOL = stringPreferencesKey("lastTool")
        val LAST_BRUSH = stringPreferencesKey("lastBrush")
        val BRUSH_SIZES = stringPreferencesKey("brushSizes")
        val BRUSH_OPACITY = floatPreferencesKey("brushOpacity")
        val COLOUR = intPreferencesKey("colour")
        val RECENT_COLOURS = stringPreferencesKey("recentColours")
        val LEFT_HANDED = booleanPreferencesKey("leftHanded")
        val DEFAULT_FPS = intPreferencesKey("defaultFps")
        val EXPORT_FORMAT = stringPreferencesKey("exportFormat")
        val EXPORT_SIZE = intPreferencesKey("exportSize")
        val SUCCESSFUL_EXPORTS = intPreferencesKey("successfulExports")
        val REVIEW_ASKED = booleanPreferencesKey("reviewAsked")
        val COUNTS_OPT_IN = booleanPreferencesKey("countsOptIn")
        val DRAWING_DAYS = stringPreferencesKey("countDrawingDays")
        val FILL_TOLERANCE = intPreferencesKey("fillTolerance")
        val FILL_CLOSE_GAPS = booleanPreferencesKey("fillCloseGaps")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val sizesSerializer = MapSerializer(String.serializer(), Float.serializer())
    private val intsSerializer = ListSerializer(Int.serializer())
    private val longsSerializer = ListSerializer(Long.serializer())

    private val data get() = context.dataStore.data

    val editorPrefs: Flow<EditorPrefs> = data.map { p ->
        EditorPrefs(
            onionBefore = p[Keys.ONION_BEFORE] ?: 2,
            onionAfter = p[Keys.ONION_AFTER] ?: 1,
            onionTint = p[Keys.ONION_TINT]?.let { runCatching { OnionTint.valueOf(it) }.getOrNull() } ?: OnionTint.LIGHT_TABLE,
            onionOpacity = p[Keys.ONION_OPACITY] ?: 0.45f,
            lightTable = p[Keys.LIGHT_TABLE] ?: false,
            streamline = p[Keys.STREAMLINE] ?: 40,
            tool = p[Keys.LAST_TOOL]?.let { runCatching { Tool.valueOf(it) }.getOrNull() } ?: Tool.PENCIL,
            brush = p[Keys.LAST_BRUSH]?.let { runCatching { Tool.valueOf(it) }.getOrNull() } ?: Tool.PENCIL,
            brushSizes = p[Keys.BRUSH_SIZES]?.let { runCatching { json.decodeFromString(sizesSerializer, it) }.getOrNull() } ?: emptyMap(),
            brushOpacity = p[Keys.BRUSH_OPACITY] ?: 1f,
            colour = p[Keys.COLOUR] ?: 0xFF1C1D24.toInt(),
            recentColours = p[Keys.RECENT_COLOURS]?.let { runCatching { json.decodeFromString(intsSerializer, it) }.getOrNull() } ?: emptyList(),
            leftHanded = p[Keys.LEFT_HANDED] ?: false,
            fillTolerance = p[Keys.FILL_TOLERANCE] ?: 24,
            fillCloseGaps = p[Keys.FILL_CLOSE_GAPS] ?: true,
        )
    }

    val defaultFps: Flow<Int> = data.map { it[Keys.DEFAULT_FPS] ?: 12 }
    val samplePlayed: Flow<Boolean> = data.map { it[Keys.SAMPLE_PLAYED] ?: false }
    val exportFormat: Flow<ExportFormat> = data.map { p ->
        p[Keys.EXPORT_FORMAT]?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() } ?: ExportFormat.GIF
    }
    val exportSize: Flow<Int> = data.map { it[Keys.EXPORT_SIZE] ?: 720 }

    val counts: Flow<Counts> = data.map { p ->
        Counts(
            optIn = p[Keys.COUNTS_OPT_IN] ?: false,
            successfulExports = p[Keys.SUCCESSFUL_EXPORTS] ?: 0,
            drawingDays = p[Keys.DRAWING_DAYS]?.let { runCatching { json.decodeFromString(longsSerializer, it) }.getOrNull() } ?: emptyList(),
        )
    }

    suspend fun sampleCreated(): Boolean = data.first()[Keys.SAMPLE_CREATED] ?: false
    suspend fun setSampleCreated() = context.dataStore.edit { it[Keys.SAMPLE_CREATED] = true }
    suspend fun setSamplePlayed() = context.dataStore.edit { it[Keys.SAMPLE_PLAYED] = true }

    suspend fun setOnion(before: Int, after: Int, tint: OnionTint, opacity: Float, lightTable: Boolean) =
        context.dataStore.edit {
            it[Keys.ONION_BEFORE] = before.coerceIn(0, 5)
            it[Keys.ONION_AFTER] = after.coerceIn(0, 5)
            it[Keys.ONION_TINT] = tint.name
            it[Keys.ONION_OPACITY] = opacity.coerceIn(0.1f, 1f)
            it[Keys.LIGHT_TABLE] = lightTable
        }

    suspend fun setStreamline(value: Int) = context.dataStore.edit { it[Keys.STREAMLINE] = value.coerceIn(0, 100) }

    suspend fun setTool(tool: Tool) = context.dataStore.edit {
        it[Keys.LAST_TOOL] = tool.name
        if (tool == Tool.PENCIL || tool == Tool.INK || tool == Tool.MARKER) it[Keys.LAST_BRUSH] = tool.name
    }

    suspend fun setBrushSize(tool: Tool, size: Float) = context.dataStore.edit { p ->
        val current = p[Keys.BRUSH_SIZES]?.let { runCatching { json.decodeFromString(sizesSerializer, it) }.getOrNull() } ?: emptyMap()
        p[Keys.BRUSH_SIZES] = json.encodeToString(sizesSerializer, current + (tool.name to size))
    }

    suspend fun setBrushOpacity(value: Float) = context.dataStore.edit { it[Keys.BRUSH_OPACITY] = value.coerceIn(0.05f, 1f) }

    /** Sets the drawing colour and moves it to the front of the last eight. */
    suspend fun setColour(argb: Int) = context.dataStore.edit { p ->
        val recent = p[Keys.RECENT_COLOURS]?.let { runCatching { json.decodeFromString(intsSerializer, it) }.getOrNull() } ?: emptyList()
        p[Keys.COLOUR] = argb
        p[Keys.RECENT_COLOURS] = json.encodeToString(intsSerializer, (listOf(argb) + recent.filter { it != argb }).take(8))
    }

    suspend fun setLeftHanded(value: Boolean) = context.dataStore.edit { it[Keys.LEFT_HANDED] = value }
    suspend fun setDefaultFps(value: Int) = context.dataStore.edit { it[Keys.DEFAULT_FPS] = value.coerceIn(4, 30) }
    suspend fun setExportFormat(value: ExportFormat) = context.dataStore.edit { it[Keys.EXPORT_FORMAT] = value.name }
    suspend fun setExportSize(value: Int) = context.dataStore.edit { it[Keys.EXPORT_SIZE] = value }
    suspend fun setFill(tolerance: Int, closeGaps: Boolean) = context.dataStore.edit {
        it[Keys.FILL_TOLERANCE] = tolerance.coerceIn(0, 128)
        it[Keys.FILL_CLOSE_GAPS] = closeGaps
    }

    /** Counts a successful export and returns the new total. */
    suspend fun recordExport(): Int {
        var total = 0
        context.dataStore.edit { p ->
            total = (p[Keys.SUCCESSFUL_EXPORTS] ?: 0) + 1
            p[Keys.SUCCESSFUL_EXPORTS] = total
        }
        return total
    }

    suspend fun reviewAsked(): Boolean = data.first()[Keys.REVIEW_ASKED] ?: false
    suspend fun setReviewAsked() = context.dataStore.edit { it[Keys.REVIEW_ASKED] = true }

    suspend fun setCountsOptIn(value: Boolean) = context.dataStore.edit {
        it[Keys.COUNTS_OPT_IN] = value
        if (!value) it.remove(Keys.DRAWING_DAYS)
    }

    /** Records today as a drawing day, only when counts are on. Keeps the last 30 days. */
    suspend fun recordDrawingDay(today: LocalDate = LocalDate.now()) = context.dataStore.edit { p ->
        if (p[Keys.COUNTS_OPT_IN] != true) return@edit
        val day = today.toEpochDay()
        val days = p[Keys.DRAWING_DAYS]?.let { runCatching { json.decodeFromString(longsSerializer, it) }.getOrNull() } ?: emptyList()
        val next = DrawingDays.record(days, day)
        if (next == days) return@edit
        p[Keys.DRAWING_DAYS] = json.encodeToString(longsSerializer, next)
    }
}
