package dev.spatial.android.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Size as AndroidSize
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.spatial.android.SettingsStore
import dev.spatial.android.hand.HandLandmarkAnalyzer
import dev.spatial.android.hand.TrackingState
import dev.spatial.android.spatial.DragPhase
import dev.spatial.android.spatial.LocalInteractionRegistry
import dev.spatial.android.spatial.ShellState
import dev.spatial.android.spatial.SpatialInteractive
import dev.spatial.android.spatial.WindowKind
import dev.spatial.android.spatial.WindowManagerState
import dev.spatial.android.spatial.findActivity
import dev.spatial.android.ui.apps.AppContent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    5 to 9, 9 to 13, 13 to 17
)

@Composable
fun SpatialShell(
    shell: ShellState,
    settings: SettingsStore,
    cameraPermission: Boolean
) {
    BackHandler { shell.handleBack() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        shell.controller.setScreenSize(screenSize)
        shell.windows.screenSize = screenSize

        val useCamera = cameraPermission && settings.trackingEnabled

        if (useCamera) {
            CameraPane(
                modifier = Modifier.matchParentSize(),
                controller = shell.controller,
                settings = settings
            )
        }

        if (!settings.cameraBackground || !useCamera) {
            SpatialBackground(Modifier.matchParentSize(), settings)
        }

        shell.windows.windows
            .sortedBy { it.zIndex }
            .forEach { window ->
                if (!window.minimized) {
                    SpatialWindowFrame(shell = shell, window = window)
                }
            }

        NotificationPop(shell)
        HudBar(shell, settings)
        MinimizedBar(shell)
        DockBar(shell)

        if (shell.menuOpen) {
            UniversalMenu(shell)
        }

        if (shell.keyboardVisible) {
            SpatialKeyboard(shell)
        }

        CursorOverlay(shell.controller, settings)

        if (settings.debugOverlay) {
            DebugOverlay(shell.controller)
        }
    }
}

@Composable
private fun CameraPane(
    modifier: Modifier,
    controller: dev.spatial.android.spatial.SpatialController,
    settings: SettingsStore
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, settings.useFrontCamera, settings.trackingEnabled) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)

        val listener = Runnable {
            try {
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolution = when (settings.graphicsQuality) {
                    0 -> AndroidSize(320, 240)
                    2 -> AndroidSize(960, 720)
                    else -> AndroidSize(640, 480)
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(executor, HandLandmarkAnalyzer(context, settings, controller))

                val selector = if (settings.useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                provider.unbindAll()

                try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (_: Exception) {
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            } catch (e: Exception) {
                controller.onCameraError(e.message ?: "Camera failed")
            }
        }

        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.graphicsLayer {
            alpha = if (settings.cameraBackground) 1f else 0f
            scaleX = if (settings.useFrontCamera) -1f else 1f
        }
    )
}

@Composable
private fun SpatialBackground(modifier: Modifier, settings: SettingsStore) {
    val transition = androidx.compose.animation.rememberInfiniteTransition(label = "bg")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(120000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "bg_time"
    )

    val particleCount = when (settings.graphicsQuality) {
        0 -> 18
        1 -> 42
        else -> 78
    }

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF04060D), Color(0xFF091124), Color(0xFF04060D))
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x4466AAFF), Color.Transparent),
                center = Offset(size.width * 0.30f, size.height * 0.28f),
                radius = size.width * 0.65f
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x338A7CFF), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.72f),
                radius = size.width * 0.55f
            )
        )

        for (i in 0 until particleCount) {
            val seed = i * 13.37f
            val x = (sin(time * 0.01f + seed) * 0.5f + 0.5f) * size.width
            val y = (cos(time * 0.008f + seed * 1.7f) * 0.5f + 0.5f) * size.height
            val radius = 1f + (i % 5) * 0.7f
            val alpha = 0.04f + (i % 10) * 0.007f
            drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = Offset(x, y))
        }
    }
}

@Composable
private fun currentTime(): String {
    val calendar = remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            delaySafe()
            calendar.value = Calendar.getInstance()
        }
    }

    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.value.time)
}

private suspend fun delaySafe() {
    kotlinx.coroutines.delay(10000)
}

@Composable
private fun batteryLevel(): Float {
    val context = LocalContext.current

    val level = produceState(0.5f) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val l = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val s = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (l >= 0) {
                    value = l.toFloat() / s.coerceAtLeast(1)
                }
            }
        }

        val initial = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        initial?.let { receiver.onReceive(context, it) }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    return level.value
}

@Composable
private fun HudBar(shell: ShellState, settings: SettingsStore) {
    val context = LocalContext.current
    val time = currentTime()
    val battery = batteryLevel()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassSurface(highlighted = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text("${(battery * 100).toInt()}%", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    shell.controller.trackingState.name.take(10),
                    color = when (shell.controller.trackingState) {
                        TrackingState.UNAVAILABLE, TrackingState.TRACKING_LOST, TrackingState.NO_HAND -> Color(0xFFFF8A80)
                        else -> Color(0xFF80FFEA)
                    },
                    fontSize = 11.sp
                )
                if (shell.notifications.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("•", color = Color(0xFFFFD180))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        SpatialButton("Menu", onClick = { shell.toggleMenu() }, zIndex = 20000, small = true, id = "hud_menu")
        SpatialButton(
            if (shell.keyboardVisible) "Hide KB" else "Keyboard",
            onClick = { shell.keyboardVisible = !shell.keyboardVisible },
            zIndex = 20000,
            small = true,
            id = "hud_keyboard"
        )
        SpatialButton("Exit", onClick = { context.findActivity()?.finish() }, zIndex = 20000, small = true, id = "hud_exit")
    }
}

@Composable
private fun DockBar(shell: ShellState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        GlassSurface {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shell.dockItems().forEach { key ->
                    DockItem(shell, key)
                }
            }
        }
    }
}

@Composable
private fun DockItem(shell: ShellState, key: String) {
    val app = shell.installedApps.find { it.key == key }

    if (app != null) {
        SpatialInteractive(
            id = "dock/$key",
            zIndex = 15000,
            onClick = { shell.launchApp(app) },
            modifier = Modifier.size(58.dp)
        ) { hovered ->
            GlassSurface(highlighted = hovered) {
                AppIcon(app = app, modifier = Modifier.size(36.dp))
            }
        }
        return
    }

    val label = when (key) {
        "home" -> "Home"
        "apps" -> "Apps"
        "search" -> "Search"
        "recents" -> "Recents"
        "notifications" -> "Alerts"
        "settings" -> "Settings"
        else -> {
            if (key.startsWith("internal:")) {
                val kindName = key.substringAfter(":")
                WindowKind.entries.find { it.name == kindName }?.title ?: "App"
            } else {
                "Item"
            }
        }
    }

    SpatialButton(label, onClick = { shell.activateDockKey(key) }, zIndex = 15000, small = true, id = "dock/$key")
}

@Composable
private fun MinimizedBar(shell: ShellState) {
    val minimized = shell.windows.windows.filter { it.minimized }
    if (minimized.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 86.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        GlassSurface {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                minimized.forEach { window ->
                    SpatialButton(
                        window.title,
                        onClick = { shell.windows.restore(window.id) },
                        zIndex = 14000,
                        small = true,
                        id = "minimized/${window.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun UniversalMenu(shell: ShellState) {
    val screenSize = shell.windows.screenSize

    val position = remember(shell.menuOpen, shell.controller.cursor) {
        val raw = if (shell.controller.cursor == Offset.Zero) {
            Offset(screenSize.width / 2f, screenSize.height / 2f)
        } else {
            shell.controller.cursor
        }

        Offset(
            raw.x.coerceIn(20f, (screenSize.width - 260f).coerceAtLeast(20f)),
            raw.y.coerceIn(80f, (screenSize.height - 420f).coerceAtLeast(80f))
        )
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(230.dp)
    ) {
        GlassSurface {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                menuItem(shell, "Home") { shell.windows.minimizeAll() }
                menuItem(shell, "Apps") { shell.openWindow(WindowKind.APP_LIBRARY) }
                menuItem(shell, "Search") { shell.openWindow(WindowKind.SEARCH) }
                menuItem(shell, "Notifications") { shell.openWindow(WindowKind.NOTIFICATIONS) }
                menuItem(shell, "Quick Settings") { shell.openWindow(WindowKind.QUICK_SETTINGS) }
                menuItem(shell, "Keyboard") { shell.keyboardVisible = true }
                menuItem(shell, "Recent Apps") { shell.openWindow(WindowKind.RECENTS) }
                menuItem(shell, "Settings") { shell.openWindow(WindowKind.SETTINGS) }
                menuItem(shell, "Layout Left") { shell.applyLayout("LEFT") }
                menuItem(shell, "Layout Right") { shell.applyLayout("RIGHT") }
                menuItem(shell, "Layout Grid") { shell.applyLayout("GRID") }
                menuItem(shell, "Exit Spatial Mode") {
                    shell.context.findActivity()?.finish()
                }
            }
        }
    }
}

@Composable
private fun menuItem(shell: ShellState, label: String, action: () -> Unit) {
    SpatialButton(
        label = label,
        onClick = {
            shell.menuOpen = false
            action()
        },
        zIndex = 25000,
        small = true,
        id = "menu/$label"
    )
}

@Composable
private fun SpatialKeyboard(shell: ShellState) {
    val settings = shell.settings

    LaunchedEffect(shell.keyboardVisible) {
        if (shell.keyboardVisible && shell.keyboardOffset == Offset.Zero) {
            val w = shell.windows.screenSize.width
            val h = shell.windows.screenSize.height
            shell.keyboardOffset = Offset((w - 760f).coerceAtLeast(16f), (h - 430f).coerceAtLeast(90f))
        }
    }

    var shift by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(settings.keyboardMode) }

    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }

    val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val qwerty = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    val symbolRows = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
        listOf("-", "=", "_", "+", "{", "}", "[", "]", "\\", "|"),
        listOf(";", "'", ":", "\"", ",", ".", "<", ">", "/", "?")
    )

    val numeric = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf("0", ".", "=")
    )

    val rows = when {
        mode == 2 -> numeric
        symbols -> symbolRows
        mode == 1 -> qwerty
        else -> listOf(numberRow) + qwerty
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(shell.keyboardOffset.x.roundToInt(), shell.keyboardOffset.y.roundToInt()) }
            .widthIn(max = 780.dp)
    ) {
        GlassSurface {
            Column {
                SpatialInteractive(
                    id = "keyboard/title",
                    zIndex = 26050,
                    onClick = {},
                    drag = { phase, p ->
                        when (phase) {
                            DragPhase.START -> {
                                dragPointer = p
                                dragStart = shell.keyboardOffset
                            }

                            DragPhase.MOVE -> {
                                shell.keyboardOffset = dragStart + (p - dragPointer)
                            }

                            DragPhase.END -> {}
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .pointerInput("kb_touch_drag") {
                            detectDragGestures { _, drag ->
                                shell.keyboardOffset += drag
                            }
                        }
                ) { hovered ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spatial Keyboard", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        SpatialButton(
                            when (mode) {
                                0 -> "Full"
                                1 -> "Compact"
                                else -> "Numeric"
                            },
                            onClick = {
                                mode = (mode + 1) % 3
                                settings.keyboardMode = mode
                            },
                            zIndex = 26051,
                            small = true,
                            id = "kb_mode"
                        )
                        SpatialButton(
                            "Close",
                            onClick = { shell.keyboardVisible = false },
                            zIndex = 26052,
                            small = true,
                            id = "kb_close"
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (rowIndex == 2 && !symbols && mode != 2) {
                            keyButton("Shift", 26010) { shift = !shift }
                        }

                        row.forEach { key ->
                            val label = if (shift && !symbols && key.length == 1) key.uppercase() else key
                            keyButton(label, 26020) {
                                shell.input.insert(label)
                                if (shift && !symbols) shift = false
                            }
                        }

                        if (rowIndex == 2 && !symbols && mode != 2) {
                            keyButton("⌫", 26030) { shell.input.backspace() }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    keyButton(if (symbols) "ABC" else "?!#", 26040) { symbols = !symbols }
                    keyButton("Space", 26041) { shell.input.insert(" ") }
                    keyButton("Enter", 26042) { shell.input.insert("\n") }
                    keyButton("Mic", 26043) { shell.showToast("Optional speech input can be added with system recognizer") }
                }
            }
        }
    }
}

@Composable
private fun keyButton(label: String, zIndex: Int, onClick: () -> Unit) {
    SpatialButton(label, onClick = onClick, zIndex = zIndex, small = true, id = "key/$label/$zIndex")
}

@Composable
private fun SpatialWindowFrame(
    shell: ShellState,
    window: WindowManagerState.WindowState
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseZ = window.zIndex * 10

    var titleDragPointer by remember { mutableStateOf(Offset.Zero) }
    var titleDragStart by remember { mutableStateOf(Offset.Zero) }
    var resizePointer by remember { mutableStateOf(Offset.Zero) }
    var resizeStart by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset(window.position.x.roundToInt(), window.position.y.roundToInt()) }
            .size(
                with(density) { window.size.width.toDp() },
                with(density) { window.size.height.toDp() }
            )
            .graphicsLayer {
                zIndex = window.zIndex.toFloat()
                shadowElevation = 18f
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xCC0A0F1E))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SpatialInteractive(
                id = "${window.id}/title",
                zIndex = baseZ + 5,
                onClick = { shell.windows.focus(window.id) },
                drag = { phase, p ->
                    when (phase) {
                        DragPhase.START -> {
                            titleDragPointer = p
                            titleDragStart = shell.windows.positionOf(window.id)
                        }

                        DragPhase.MOVE -> {
                            shell.windows.move(window.id, titleDragStart + (p - titleDragPointer))
                        }

                        DragPhase.END -> {}
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput("window_touch_drag_${window.id}") {
                        detectDragGestures { _, drag ->
                            shell.windows.moveBy(window.id, drag)
                        }
                    }
            ) { hovered ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp)
                ) {
                    Text(window.title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))

                    SpatialButton("—", onClick = { shell.windows.minimize(window.id) }, zIndex = baseZ + 6, small = true, id = "${window.id}/min")
                    SpatialButton("▢", onClick = { shell.windows.toggleMaximize(window.id) }, zIndex = baseZ + 6, small = true, id = "${window.id}/max")
                    SpatialButton("P", onClick = { shell.windows.togglePin(window.id) }, zIndex = baseZ + 6, small = true, id = "${window.id}/pin")
                    SpatialButton("✕", onClick = { shell.windows.close(window.id) }, zIndex = baseZ + 6, small = true, id = "${window.id}/close")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                AppContent(shell = shell, kind = window.kind, baseZ = baseZ)
            }
        }

        SpatialInteractive(
            id = "${window.id}/resize",
            zIndex = baseZ + 8,
            onClick = {},
            drag = { phase, p ->
                when (phase) {
                    DragPhase.START -> {
                        resizePointer = p
                        resizeStart = shell.windows.sizeOf(window.id)
                    }

                    DragPhase.MOVE -> {
                        shell.windows.resizeTo(
                            window.id,
                            Size(
                                resizeStart.width + (p.x - resizePointer.x),
                                resizeStart.height + (p.y - resizePointer.y)
                            )
                        )
                    }

                    DragPhase.END -> {}
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(42.dp)
                .pointerInput("window_touch_resize_${window.id}") {
                    detectDragGestures { _, drag ->
                        shell.windows.resizeBy(window.id, Size(drag.x, drag.y))
                    }
                }
        ) { hovered ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hovered) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
            )
        }
    }
}

@Composable
private fun NotificationPop(shell: ShellState) {
    val latest = shell.notifications.firstOrNull()
    if (latest == null) return
    if (shell.windows.isOpen(WindowKind.NOTIFICATIONS)) return

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 70.dp, end = 14.dp)
            .width(280.dp)
    ) {
        GlassSurface(highlighted = true) {
            Column {
                Text(latest.title, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(latest.text, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpatialButton(
                        "Open",
                        onClick = { shell.openWindow(WindowKind.NOTIFICATIONS) },
                        zIndex = 18000,
                        small = true,
                        id = "notif_open"
                    )
                    SpatialButton(
                        "Dismiss",
                        onClick = { shell.dismissNotification(latest.id) },
                        zIndex = 18000,
                        small = true,
                        id = "notif_dismiss"
                    )
                }
            }
        }
    }
}

@Composable
private fun CursorOverlay(
    controller: dev.spatial.android.spatial.SpatialController,
    settings: SettingsStore
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (settings.handSkeleton) {
            val points = controller.landmarksScreen
            if (points.size >= 21) {
                HAND_CONNECTIONS.forEach { (a, b) ->
                    drawLine(Color(0xFF00E5FF), points[a], points[b], strokeWidth = 2f)
                }

                points.forEachIndexed { index, p ->
                    val color = when (index) {
                        0 -> Color(0xFFFF4081)
                        4, 8 -> Color.Yellow
                        else -> Color(0xFF7C4DFF)
                    }
                    drawCircle(color, if (index == 0) 6f else 4f, center = p)
                }
            }
        }

        val alpha = controller.cursorAlpha
        if (alpha > 0f) {
            val p = controller.cursor
            val stateColor = when (controller.trackingState) {
                TrackingState.DRAGGING -> Color(0xFFB388FF)
                TrackingState.PINCH_ACTIVE, TrackingState.PINCH_START -> Color(0xFF80FFEA)
                TrackingState.TRACKING_LOST -> Color.Gray
                else -> Color(0xFF77C9FF)
            }

            drawCircle(stateColor.copy(alpha = 0.16f * alpha), radius = 26f, center = p)
            drawCircle(stateColor.copy(alpha = 0.85f * alpha), radius = 9f, center = p)
            drawCircle(Color.White.copy(alpha = 0.95f * alpha), radius = 3.5f, center = p)
        }
    }
}

@Composable
private fun DebugOverlay(controller: dev.spatial.android.spatial.SpatialController) {
    val debug = controller.debug

    Column(
        modifier = Modifier
            .padding(top = 70.dp, start = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(10.dp)
    ) {
        Text("State: ${debug.state}", color = Color.White, fontSize = 11.sp)
        Text("Tracking FPS: %.1f".format(debug.fps), color = Color.White, fontSize = 11.sp)
        Text("Process ms: ${debug.processMs}", color = Color.White, fontSize = 11.sp)
        Text("Hand: ${debug.handDetected}", color = Color.White, fontSize = 11.sp)
        Text("Pinch: %.3f".format(debug.pinchDistance), color = Color.White, fontSize = 11.sp)
        Text("Cursor: %.0f, %.0f".format(debug.cursorX, debug.cursorY), color = Color.White, fontSize = 11.sp)
        Text("Memory: ${debug.memoryMb} MB", color = Color.White, fontSize = 11.sp)
    }
}
