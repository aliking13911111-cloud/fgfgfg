package dev.spatial.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("spatial_settings", Context.MODE_PRIVATE)

    private class BoolPref(
        private val prefs: SharedPreferences,
        private val key: String,
        private val def: Boolean
    ) : ReadWriteProperty<Any?, Boolean> {
        private val state = mutableStateOf(prefs.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    private class FloatPref(
        private val prefs: SharedPreferences,
        private val key: String,
        private val def: Float
    ) : ReadWriteProperty<Any?, Float> {
        private val state = mutableStateOf(prefs.getFloat(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            state.value = value
            prefs.edit().putFloat(key, value).apply()
        }
    }

    private class IntPref(
        private val prefs: SharedPreferences,
        private val key: String,
        private val def: Int
    ) : ReadWriteProperty<Any?, Int> {
        private val state = mutableStateOf(prefs.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private class StringPref(
        private val prefs: SharedPreferences,
        private val key: String,
        private val def: String
    ) : ReadWriteProperty<Any?, String> {
        private val state = mutableStateOf(prefs.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            prefs.edit().putString(key, value).apply()
        }
    }

    private class StringSetPref(
        private val prefs: SharedPreferences,
        private val key: String,
        private val def: Set<String>
    ) : ReadWriteProperty<Any?, Set<String>> {
        private val state = mutableStateOf<Set<String>>(prefs.getStringSet(key, def)?.toSet() ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>) {
            state.value = value
            prefs.edit().putStringSet(key, value).apply()
        }
    }

    var cameraBackground by BoolPref(prefs, "camera_background", true)
    var trackingEnabled by BoolPref(prefs, "tracking_enabled", true)
    var useFrontCamera by BoolPref(prefs, "use_front_camera", true)
    var menuGestureEnabled by BoolPref(prefs, "menu_gesture", true)

    var cursorSpeed by FloatPref(prefs, "cursor_speed", 1.25f)
    var cursorSmoothing by FloatPref(prefs, "cursor_smoothing", 0.55f)
    var sensitivity by FloatPref(prefs, "sensitivity", 1.0f)
    var invertX by BoolPref(prefs, "invert_x", true)
    var invertY by BoolPref(prefs, "invert_y", false)

    var pinchStart by FloatPref(prefs, "pinch_start", 0.22f)
    var pinchRelease by FloatPref(prefs, "pinch_release", 0.34f)
    var confirmMs by IntPref(prefs, "confirm_ms", 90)
    var dragDelayMs by IntPref(prefs, "drag_delay_ms", 240)

    var sounds by BoolPref(prefs, "sounds", false)
    var haptics by BoolPref(prefs, "haptics", true)
    var debugOverlay by BoolPref(prefs, "debug_overlay", false)
    var handSkeleton by BoolPref(prefs, "hand_skeleton", false)

    var graphicsQuality by IntPref(prefs, "graphics_quality", 1)
    var theme by IntPref(prefs, "theme", 0)
    var keyboardMode by IntPref(prefs, "keyboard_mode", 0)

    var dockItems by StringPref(prefs, "dock_items", "home,apps,search,recents,notifications,settings")
    var favorites by StringSetPref(prefs, "favorites", emptySet())
    var recentApps by StringPref(prefs, "recent_apps", "")
    var recentSearches by StringPref(prefs, "recent_searches", "")
    var noteText by StringPref(prefs, "note_text", "Welcome to Spatial Android")
}
