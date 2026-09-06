package dev.spatial.android.spatial

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import dev.spatial.android.AppDiscovery
import dev.spatial.android.InstalledApp
import dev.spatial.android.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpatialNotification(
    val id: Long,
    val title: String,
    val text: String,
    val time: Long
)

class TextInputController {
    var activeState by mutableStateOf<androidx.compose.runtime.MutableState<String>?>(null)

    fun insert(s: String) {
        val state = activeState ?: return
        state.value = state.value + s
    }

    fun backspace() {
        val state = activeState ?: return
        if (state.value.isNotEmpty()) {
            state.value = state.value.dropLast(1)
        }
    }
}

fun Context.findActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

class ShellState(
    val context: Context,
    val settings: SettingsStore
) {
    val interaction = InteractionRegistry()
    val controller = SpatialController(settings, interaction)
    val input = TextInputController()
    val windows = WindowManagerState()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())

    val searchQueryState = mutableStateOf("")
    var searchQuery by searchQueryState

    val noteTextState = mutableStateOf(settings.noteText)

    var toast by mutableStateOf<String?>(null)
    var menuOpen by mutableStateOf(false)
    var keyboardVisible by mutableStateOf(false)
    var keyboardOffset by mutableStateOf(Offset.Zero)

    var notifications by mutableStateOf(
        listOf(
            SpatialNotification(
                id = 1L,
                title = "Spatial Android",
                text = "Hand tracking shell ready. Pinch to interact.",
                time = System.currentTimeMillis()
            )
        )
    )

    suspend fun loadApps() {
        installedApps = withContext(Dispatchers.IO) {
            AppDiscovery.discover(context)
        }
    }

    fun openWindow(kind: WindowKind) {
        windows.open(kind)
        recordRecent("internal:${kind.name}")
    }

    fun handleBack() {
        when {
            menuOpen -> menuOpen = false
            keyboardVisible -> keyboardVisible = false
            windows.top() != null -> windows.close(windows.top()!!.id)
            else -> context.findActivity()?.finish()
        }
    }

    fun launchApp(app: InstalledApp) {
        val ok = AppDiscovery.launch(context, app)
        if (ok) {
            recordRecent(app.key)
            showToast("Launched ${app.label}")
        } else {
            showToast("Cannot launch ${app.label}. It will be opened normally if possible.")
        }
    }

    fun activateDockKey(key: String) {
        when (key) {
            "home" -> windows.minimizeAll()
            "apps" -> openWindow(WindowKind.APP_LIBRARY)
            "search" -> openWindow(WindowKind.SEARCH)
            "recents" -> openWindow(WindowKind.RECENTS)
            "notifications" -> openWindow(WindowKind.NOTIFICATIONS)
            "settings" -> openWindow(WindowKind.SETTINGS)
            else -> {
                if (key.startsWith("internal:")) {
                    val kindName = key.substringAfter(":")
                    val kind = WindowKind.entries.find { it.name == kindName }
                    if (kind != null) openWindow(kind)
                } else {
                    installedApps.find { it.key == key }?.let { launchApp(it) }
                }
            }
        }
    }

    fun recordRecent(key: String) {
        val current = recentKeys().filterNot { it == key }.toMutableList()
        current.add(0, key)
        settings.recentApps = current.take(12).joinToString(",")
    }

    fun recentKeys(): List<String> {
        return settings.recentApps.split(',').filter { it.isNotBlank() }
    }

    fun dockItems(): List<String> {
        return settings.dockItems.split(',').filter { it.isNotBlank() }
    }

    fun addToDock(key: String) {
        val items = dockItems().toMutableList()
        if (!items.contains(key)) {
            items.add(key)
            settings.dockItems = items.joinToString(",")
        }
    }

    fun removeFromDock(key: String) {
        settings.dockItems = dockItems().filterNot { it == key }.joinToString(",")
    }

    fun toggleFavorite(app: InstalledApp) {
        val set = settings.favorites.toMutableSet()
        if (!set.add(app.key)) {
            set.remove(app.key)
        }
        settings.favorites = set
    }

    fun toggleMenu() {
        menuOpen = !menuOpen
    }

    fun applyLayout(mode: String) {
        windows.applyLayout(mode)
    }

    fun showToast(message: String) {
        toast = message
    }

    fun uiSound() {
        if (!settings.sounds) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_UI, 40)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            scope.launch {
                delay(180)
                tone.release()
            }
        } catch (_: Exception) {
        }
    }

    fun openSystemSettings(action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            showToast("System setting not available on this device")
        }
    }

    fun saveNote() {
        settings.noteText = noteTextState.value
    }

    fun dismissNotification(id: Long) {
        notifications = notifications.filterNot { it.id == id }
    }

    fun clearNotifications() {
        notifications = emptyList()
    }

    fun addNotification(title: String, text: String) {
        notifications = listOf(
            SpatialNotification(
                id = System.nanoTime(),
                title = title,
                text = text,
                time = System.currentTimeMillis()
            )
        ) + notifications
    }
}
