package dev.spatial.android.spatial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.util.Locale

enum class WindowKind(val title: String) {
    APP_LIBRARY("App Library"),
    SEARCH("Search"),
    NOTES("Notes"),
    CALCULATOR("Calculator"),
    GALLERY("Gallery"),
    FILES("Files"),
    SETTINGS("Settings"),
    MEDIA("Media"),
    CLOCK("Clock"),
    BROWSER("Browser"),
    RECENTS("Recents"),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick Settings"),
    HELP("Help")
}

class WindowManagerState {
    data class WindowState(
        val id: String,
        val kind: WindowKind,
        val title: String,
        val position: Offset,
        val size: Size,
        val zIndex: Int,
        val minimized: Boolean,
        val maximized: Boolean,
        val pinned: Boolean
    )

    var windows by mutableStateOf(emptyList<WindowState>())
        private set

    var screenSize by mutableStateOf(Size(1080f, 1920f))

    private var topZ = 10

    fun open(kind: WindowKind) {
        val id = kind.name
        val existing = windows.find { it.id == id }

        if (existing != null) {
            topZ += 1
            windows = windows.map {
                if (it.id == id) it.copy(minimized = false, zIndex = topZ) else it
            }
            return
        }

        val screen = if (screenSize.width <= 0f || screenSize.height <= 0f) {
            Size(1080f, 1920f)
        } else {
            screenSize
        }

        val width = (screen.width * 0.72f).coerceAtMost(1100f).coerceAtLeast(320f)
        val height = (screen.height * 0.70f).coerceAtMost(900f).coerceAtLeast(240f)
        val pos = Offset(
            ((screen.width - width) / 2f).coerceAtLeast(12f),
            ((screen.height - height) / 2f - 30f).coerceAtLeast(12f)
        )

        topZ += 1

        windows = windows + WindowState(
            id = id,
            kind = kind,
            title = kind.title,
            position = pos,
            size = Size(width, height),
            zIndex = topZ,
            minimized = false,
            maximized = false,
            pinned = false
        )
    }

    fun close(id: String) {
        windows = windows.filterNot { it.id == id }
    }

    fun focus(id: String) {
        topZ += 1
        windows = windows.map { if (it.id == id) it.copy(zIndex = topZ) else it }
    }

    fun minimize(id: String) {
        windows = windows.map { if (it.id == id) it.copy(minimized = true) else it }
    }

    fun restore(id: String) {
        topZ += 1
        windows = windows.map { if (it.id == id) it.copy(minimized = false, zIndex = topZ) else it }
    }

    fun minimizeAll() {
        windows = windows.map { it.copy(minimized = true) }
    }

    fun toggleMaximize(id: String) {
        val window = windows.find { it.id == id } ?: return
        val screen = if (screenSize.width <= 0f) Size(1080f, 1920f) else screenSize

        windows = windows.map {
            if (it.id == id) {
                if (it.maximized) {
                    val width = (screen.width * 0.72f).coerceAtLeast(320f)
                    val height = (screen.height * 0.70f).coerceAtLeast(240f)
                    it.copy(
                        maximized = false,
                        position = Offset((screen.width - width) / 2f, (screen.height - height) / 2f),
                        size = Size(width, height)
                    )
                } else {
                    it.copy(
                        maximized = true,
                        position = Offset.Zero,
                        size = Size(screen.width, screen.height)
                    )
                }
            } else {
                it
            }
        }
    }

    fun togglePin(id: String) {
        windows = windows.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
    }

    fun move(id: String, position: Offset) {
        val window = windows.find { it.id == id } ?: return
        val maxX = (screenSize.width - 80f).coerceAtLeast(0f)
        val maxY = (screenSize.height - 60f).coerceAtLeast(0f)

        val newPos = Offset(
            position.x.coerceIn(-window.size.width * 0.35f, maxX),
            position.y.coerceIn(0f, maxY)
        )

        windows = windows.map { if (it.id == id) it.copy(position = newPos) else it }
    }

    fun moveBy(id: String, delta: Offset) {
        val current = positionOf(id)
        move(id, current + delta)
    }

    fun resizeTo(id: String, size: Size) {
        val width = size.width.coerceIn(300f, screenSize.width.coerceAtLeast(320f))
        val height = size.height.coerceIn(220f, screenSize.height.coerceAtLeast(240f))

        windows = windows.map {
            if (it.id == id) it.copy(size = Size(width, height)) else it
        }
    }

    fun resizeBy(id: String, delta: Size) {
        val current = sizeOf(id)
        resizeTo(id, Size(current.width + delta.width, current.height + delta.height))
    }

    fun positionOf(id: String): Offset {
        return windows.find { it.id == id }?.position ?: Offset.Zero
    }

    fun sizeOf(id: String): Size {
        return windows.find { it.id == id }?.size ?: Size(400f, 300f)
    }

    fun top(): WindowState? {
        return windows.filter { !it.minimized }.maxByOrNull { it.zIndex }
    }

    fun isOpen(kind: WindowKind): Boolean {
        return windows.any { it.kind == kind && !it.minimized }
    }

    fun applyLayout(mode: String) {
        val ids = windows
            .filter { !it.minimized }
            .sortedBy { it.zIndex }
            .map { it.id }

        if (ids.isEmpty()) return

        val w = if (screenSize.width > 0f) screenSize.width else 1080f
        val h = if (screenSize.height > 0f) screenSize.height else 1920f

        windows = windows.map { win ->
            val index = ids.indexOf(win.id)
            if (index < 0) return@map win

            when (mode.uppercase(Locale.US)) {
                "CENTER" -> win.copy(
                    position = Offset((w - win.size.width) / 2f, (h - win.size.height) / 2f),
                    minimized = false
                )

                "LEFT" -> win.copy(
                    position = Offset(0f, 0f),
                    size = Size(w * 0.5f, h),
                    minimized = false
                )

                "RIGHT" -> win.copy(
                    position = Offset(w * 0.5f, 0f),
                    size = Size(w * 0.5f, h),
                    minimized = false
                )

                "GRID" -> {
                    val cols = if (ids.size <= 1) 1 else 2
                    val rows = (ids.size + cols - 1) / cols
                    val cw = w / cols
                    val ch = h / rows

                    win.copy(
                        position = Offset((index % cols) * cw, (index / cols) * ch),
                        size = Size(cw, ch),
                        minimized = false
                    )
                }

                "FAN" -> win.copy(
                    position = Offset(40f + index * 70f, 60f + index * 50f),
                    minimized = false
                )

                "FOCUS" -> {
                    if (index == ids.lastIndex) {
                        win.copy(position = Offset.Zero, size = Size(w, h), minimized = false)
                    } else {
                        win.copy(minimized = true)
                    }
                }

                else -> win
            }
        }
    }
}
