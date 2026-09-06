package dev.spatial.android.ui.apps

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.spatial.android.ExpressionEvaluator
import dev.spatial.android.InstalledApp
import dev.spatial.android.spatial.ShellState
import dev.spatial.android.spatial.SpatialInteractive
import dev.spatial.android.spatial.WindowKind
import dev.spatial.android.spatial.findActivity
import dev.spatial.android.ui.AppIcon
import dev.spatial.android.ui.GlassSurface
import dev.spatial.android.ui.SpatialButton
import dev.spatial.android.ui.SpatialSwitch
import dev.spatial.android.ui.SpatialTextField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppContent(
    shell: ShellState,
    kind: WindowKind,
    baseZ: Int
) {
    when (kind) {
        WindowKind.APP_LIBRARY -> AppLibraryContent(shell, baseZ)
        WindowKind.SEARCH -> SearchContent(shell, baseZ)
        WindowKind.NOTES -> NotesContent(shell, baseZ)
        WindowKind.CALCULATOR -> CalculatorContent(baseZ)
        WindowKind.GALLERY -> GalleryContent(baseZ)
        WindowKind.FILES -> FilesContent(shell)
        WindowKind.SETTINGS -> SettingsContent(shell, baseZ)
        WindowKind.MEDIA -> MediaContent(shell)
        WindowKind.CLOCK -> ClockContent()
        WindowKind.BROWSER -> BrowserContent(shell, baseZ)
        WindowKind.RECENTS -> RecentsContent(shell, baseZ)
        WindowKind.NOTIFICATIONS -> NotificationsContent(shell, baseZ)
        WindowKind.QUICK_SETTINGS -> QuickSettingsContent(shell, baseZ)
        WindowKind.HELP -> HelpContent()
    }
}

@Composable
private fun AppLibraryContent(shell: ShellState, baseZ: Int) {
    var tab by remember { mutableStateOf(1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Favorites", onClick = { tab = 0 }, zIndex = baseZ + 1, small = true, id = "apps_tab_fav")
            SpatialButton("All", onClick = { tab = 1 }, zIndex = baseZ + 1, small = true, id = "apps_tab_all")
            SpatialButton("Recent", onClick = { tab = 2 }, zIndex = baseZ + 1, small = true, id = "apps_tab_recent")
        }

        Spacer(Modifier.height(10.dp))

        val apps = when (tab) {
            0 -> shell.installedApps.filter { shell.settings.favorites.contains(it.key) }
            1 -> shell.installedApps
            else -> shell.recentKeys().mapNotNull { key -> shell.installedApps.find { it.key == key } }
        }

        if (apps.isEmpty()) {
            Text("No apps found.", color = Color.White.copy(alpha = 0.7f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.key }) { app ->
                    AppTile(shell, app, baseZ)
                }
            }
        }
    }
}

@Composable
private fun AppTile(shell: ShellState, app: InstalledApp, baseZ: Int) {
    val favorite = shell.settings.favorites.contains(app.key)

    SpatialInteractive(
        id = "app/${app.key}",
        zIndex = baseZ + 1,
        onClick = { shell.launchApp(app) },
        modifier = Modifier.size(112.dp)
    ) { hovered ->
        GlassSurface(highlighted = hovered) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppIcon(app = app, modifier = Modifier.size(42.dp))
                Text(
                    app.label,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpatialButton(
                        if (favorite) "★" else "☆",
                        onClick = { shell.toggleFavorite(app) },
                        zIndex = baseZ + 2,
                        small = true,
                        id = "fav/${app.key}"
                    )
                    SpatialButton(
                        "Dock",
                        onClick = { shell.addToDock(app.key) },
                        zIndex = baseZ + 2,
                        small = true,
                        id = "dockadd/${app.key}"
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchContent(shell: ShellState, baseZ: Int) {
    val query = shell.searchQuery.trim()

    Column(modifier = Modifier.fillMaxSize()) {
        SpatialTextField(
            id = "search_field",
            state = shell.searchQueryState,
            input = shell.input,
            hint = "Search apps, notes, settings",
            baseZ = baseZ + 1,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (query.isNotEmpty()) {
                val appResults = shell.installedApps.filter { it.label.contains(query, true) }
                val internalResults = WindowKind.entries.filter { it.title.contains(query, true) }
                val noteMatch = shell.noteTextState.value.contains(query, true)

                item {
                    Text("Apps", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }

                items(appResults) { app ->
                    SpatialButton(app.label, onClick = { shell.launchApp(app) }, zIndex = baseZ + 2, id = "search_app/${app.key}")
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Internal", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }

                items(internalResults) { kind ->
                    SpatialButton(kind.title, onClick = { shell.openWindow(kind) }, zIndex = baseZ + 2, id = "search_internal/${kind.name}")
                }

                if (noteMatch) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Notes", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        SpatialButton("Open Notes", onClick = { shell.openWindow(WindowKind.NOTES) }, zIndex = baseZ + 2, id = "search_notes")
                    }
                }
            } else {
                item {
                    Text("Type using the spatial keyboard or touch.", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun NotesContent(shell: ShellState, baseZ: Int) {
    Column(modifier = Modifier.fillMaxSize()) {
        SpatialTextField(
            id = "notes_field",
            state = shell.noteTextState,
            input = shell.input,
            hint = "Tap here then use keyboard",
            baseZ = baseZ + 1,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )

        Spacer(Modifier.height(10.dp))

        SpatialInteractive(
            id = "notes_body",
            zIndex = baseZ + 1,
            onClick = { shell.input.activeState = shell.noteTextState },
            modifier = Modifier.fillMaxSize()
        ) { hovered ->
            GlassSurface(highlighted = hovered, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = shell.noteTextState.value.ifEmpty { "Write a note..." },
                    color = if (shell.noteTextState.value.isEmpty()) Color.White.copy(alpha = 0.45f) else Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun CalculatorContent(baseZ: Int) {
    var expression by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassSurface(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(
                text = expression.ifEmpty { "0" },
                color = Color.White,
                fontSize = 26.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        val rows = listOf(
            listOf("C", "(", ")", "/"),
            listOf("7", "8", "9", "*"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=", "")
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.width(64.dp))
                        } else {
                            SpatialButton(
                                label = key,
                                onClick = {
                                    when (key) {
                                        "C" -> expression = ""
                                        "=" -> expression = ExpressionEvaluator.eval(expression)
                                        else -> expression += key
                                    }
                                },
                                zIndex = baseZ + 1,
                                id = "calc/$key"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryContent(baseZ: Int) {
    val gradients = listOf(
        Brush.linearGradient(listOf(Color(0xFF283E51), Color(0xFF485563))),
        Brush.linearGradient(listOf(Color(0xFF42275A), Color(0xFF734B6D))),
        Brush.linearGradient(listOf(Color(0xFF141E30), Color(0xFF243B55))),
        Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364))),
        Brush.linearGradient(listOf(Color(0xFF232526), Color(0xFF414345))),
        Brush.linearGradient(listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)))
    )

    var selected by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(gradients[selected])
        )

        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(gradients.size) { index ->
                SpatialInteractive(
                    id = "gallery/$index",
                    zIndex = baseZ + 1,
                    onClick = { selected = index },
                    modifier = Modifier.size(72.dp)
                ) { hovered ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(gradients[index])
                            .padding(2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesContent(shell: ShellState) {
    val context = LocalContext.current
    val files = remember { context.filesDir.listFiles()?.toList() ?: emptyList() }

    if (files.isEmpty()) {
        Text("No internal files yet.", color = Color.White.copy(alpha = 0.7f))
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { file ->
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, maxLines = 1)
                            Text("${file.length()} bytes", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(shell: ShellState, baseZ: Int) {
    val settings = shell.settings
    val context = LocalContext.current

    var openDistance by remember { mutableStateOf<Float?>(null) }
    var pinchDistance by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Privacy", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Camera frames are processed locally by MediaPipe. No images or gesture data are uploaded.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )

        SpatialSwitch("Camera Background", settings.cameraBackground, { settings.cameraBackground = !settings.cameraBackground }, baseZ + 1, "set_camera_bg")
        SpatialSwitch("Hand Tracking", settings.trackingEnabled, { settings.trackingEnabled = !settings.trackingEnabled }, baseZ + 1, "set_tracking")
        SpatialSwitch("Front Camera", settings.useFrontCamera, { settings.useFrontCamera = !settings.useFrontCamera }, baseZ + 1, "set_front")
        SpatialSwitch("Menu Gesture", settings.menuGestureEnabled, { settings.menuGestureEnabled = !settings.menuGestureEnabled }, baseZ + 1, "set_menu_gesture")
        SpatialSwitch("Sounds", settings.sounds, { settings.sounds = !settings.sounds }, baseZ + 1, "set_sounds")
        SpatialSwitch("Haptics", settings.haptics, { settings.haptics = !settings.haptics }, baseZ + 1, "set_haptics")
        SpatialSwitch("Debug Overlay", settings.debugOverlay, { settings.debugOverlay = !settings.debugOverlay }, baseZ + 1, "set_debug")
        SpatialSwitch("Hand Skeleton", settings.handSkeleton, { settings.handSkeleton = !settings.handSkeleton }, baseZ + 1, "set_skeleton")

        Spacer(Modifier.height(8.dp))
        Text("Cursor", color = Color.White, fontWeight = FontWeight.Bold)

        SettingsFloat("Speed", settings.cursorSpeed, 0.1f, { settings.cursorSpeed = (settings.cursorSpeed - it).coerceIn(0.4f, 3f) }, { settings.cursorSpeed = (settings.cursorSpeed + it).coerceIn(0.4f, 3f) }, baseZ, "set_speed")
        SettingsFloat("Smoothing", settings.cursorSmoothing, 0.05f, { settings.cursorSmoothing = (settings.cursorSmoothing - it).coerceIn(0f, 0.95f) }, { settings.cursorSmoothing = (settings.cursorSmoothing + it).coerceIn(0f, 0.95f) }, baseZ, "set_smooth")
        SettingsFloat("Sensitivity", settings.sensitivity, 0.1f, { settings.sensitivity = (settings.sensitivity - it).coerceIn(0.4f, 3f) }, { settings.sensitivity = (settings.sensitivity + it).coerceIn(0.4f, 3f) }, baseZ, "set_sens")
        SpatialSwitch("Invert X", settings.invertX, { settings.invertX = !settings.invertX }, baseZ + 1, "set_invert_x")
        SpatialSwitch("Invert Y", settings.invertY, { settings.invertY = !settings.invertY }, baseZ + 1, "set_invert_y")

        Spacer(Modifier.height(8.dp))
        Text("Pinch and Drag", color = Color.White, fontWeight = FontWeight.Bold)

        SettingsFloat("Pinch Start", settings.pinchStart, 0.01f, { settings.pinchStart = (settings.pinchStart - it).coerceIn(0.08f, 0.7f) }, { settings.pinchStart = (settings.pinchStart + it).coerceIn(0.08f, 0.7f) }, baseZ, "set_pinch_start")
        SettingsFloat("Pinch Release", settings.pinchRelease, 0.01f, { settings.pinchRelease = (settings.pinchRelease - it).coerceIn(0.1f, 0.9f) }, { settings.pinchRelease = (settings.pinchRelease + it).coerceIn(0.1f, 0.9f) }, baseZ, "set_pinch_release")
        SettingsInt("Confirm ms", settings.confirmMs, 10, { settings.confirmMs = (settings.confirmMs - it).coerceIn(0, 800) }, { settings.confirmMs = (settings.confirmMs + it).coerceIn(0, 800) }, baseZ, "set_confirm")
        SettingsInt("Drag Delay ms", settings.dragDelayMs, 20, { settings.dragDelayMs = (settings.dragDelayMs - it).coerceIn(0, 1200) }, { settings.dragDelayMs = (settings.dragDelayMs + it).coerceIn(0, 1200) }, baseZ, "set_drag_delay")

        Spacer(Modifier.height(8.dp))
        Text("Calibration", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Live pinch distance: %.3f".format(shell.controller.latestPinchDistance), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Capture Open", onClick = { openDistance = shell.controller.latestPinchDistance }, zIndex = baseZ + 2, small = true, id = "calib_open")
            SpatialButton("Capture Pinch", onClick = { pinchDistance = shell.controller.latestPinchDistance }, zIndex = baseZ + 2, small = true, id = "calib_pinch")
            SpatialButton("Apply", onClick = {
                val open = openDistance
                val pinch = pinchDistance
                if (open != null && pinch != null && open > pinch) {
                    val range = (open - pinch).coerceAtLeast(0.05f)
                    settings.pinchStart = pinch + range * 0.35f
                    settings.pinchRelease = pinch + range * 0.60f
                    shell.showToast("Calibration saved")
                } else {
                    shell.showToast("Capture open hand and pinch first")
                }
            }, zIndex = baseZ + 2, small = true, id = "calib_apply")
        }

        Spacer(Modifier.height(8.dp))
        Text("Graphics", color = Color.White, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Quality: ${qualityName(settings.graphicsQuality)}", onClick = { settings.graphicsQuality = (settings.graphicsQuality + 1) % 3 }, zIndex = baseZ + 2, small = true, id = "set_quality")
            SpatialButton("Theme: ${themeName(settings.theme)}", onClick = { settings.theme = (settings.theme + 1) % 4 }, zIndex = baseZ + 2, small = true, id = "set_theme")
        }

        Spacer(Modifier.height(8.dp))
        Text("Dock", color = Color.White, fontWeight = FontWeight.Bold)

        shell.dockItems().forEach { key ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(key, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f), maxLines = 1)
                SpatialButton("Remove", onClick = { shell.removeFromDock(key) }, zIndex = baseZ + 2, small = true, id = "dock_remove/$key")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Help", color = Color.White, fontWeight = FontWeight.Bold)

        SpatialButton("Open Help", onClick = { shell.openWindow(WindowKind.HELP) }, zIndex = baseZ + 2, small = true, id = "open_help")
        SpatialButton("Exit Spatial Android", onClick = { context.findActivity()?.finish() }, zIndex = baseZ + 2, small = true, id = "exit_from_settings")
    }
}

private fun qualityName(q: Int): String = when (q) {
    0 -> "Low"
    1 -> "Medium"
    else -> "High"
}

private fun themeName(t: Int): String = when (t) {
    0 -> "Midnight Glass"
    1 -> "Aurora"
    2 -> "Minimal Dark"
    else -> "Frosted Light"
}

@Composable
private fun SettingsFloat(
    label: String,
    value: Float,
    step: Float,
    onMinus: (Float) -> Unit,
    onPlus: (Float) -> Unit,
    baseZ: Int,
    id: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        SpatialButton("-", onClick = { onMinus(step) }, zIndex = baseZ + 2, small = true, id = "${id}_minus")
        Text(String.format(Locale.US, "%.2f", value), color = Color.White, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
        SpatialButton("+", onClick = { onPlus(step) }, zIndex = baseZ + 2, small = true, id = "${id}_plus")
    }
}

@Composable
private fun SettingsInt(
    label: String,
    value: Int,
    step: Int,
    onMinus: (Int) -> Unit,
    onPlus: (Int) -> Unit,
    baseZ: Int,
    id: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        SpatialButton("-", onClick = { onMinus(step) }, zIndex = baseZ + 2, small = true, id = "${id}_minus")
        Text("$value", color = Color.White, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
        SpatialButton("+", onClick = { onPlus(step) }, zIndex = baseZ + 2, small = true, id = "${id}_plus")
    }
}

@Composable
private fun MediaContent(shell: ShellState) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Spatial Media", color = Color.White, fontSize = 18.sp)
        Text("Plays a local system notification sound as a safe demo media source.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton(
                if (playing) "Stop" else "Play",
                onClick = {
                    if (playing) {
                        player?.stop()
                        player?.release()
                        player = null
                        playing = false
                    } else {
                        try {
                            val uri = Settings.System.DEFAULT_NOTIFICATION_URI
                            val mp = MediaPlayer.create(context, uri)
                            if (mp == null) {
                                shell.showToast("Media unavailable")
                            } else {
                                mp.setOnCompletionListener {
                                    playing = false
                                }
                                mp.start()
                                player = mp
                                playing = true
                            }
                        } catch (_: Exception) {
                            shell.showToast("Media unavailable")
                        }
                    }
                },
                zIndex = 100,
                id = "media_toggle"
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF123456),
                            if (playing) Color(0xFF00C853) else Color(0xFF37474F)
                        )
                    )
                )
        )
    }
}

@Composable
private fun ClockContent() {
    val time = remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            time.value = Date()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(time.value),
            color = Color.White,
            fontSize = 42.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(time.value),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun BrowserContent(shell: ShellState, baseZ: Int) {
    val context = LocalContext.current
    val urlState = remember { mutableStateOf("https://example.com") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Browser-style panel", color = Color.White, fontSize = 16.sp)
        Text("This demo intentionally avoids embedding WebView as the main UI. It opens real browsers through Android intents.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)

        SpatialTextField(
            id = "browser_url",
            state = urlState,
            input = shell.input,
            hint = "Enter URL",
            baseZ = baseZ + 1,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Open", onClick = {
                try {
                    val url = if (urlState.value.startsWith("http")) urlState.value else "https://${urlState.value}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    shell.showToast("No browser available")
                }
            }, zIndex = baseZ + 2, id = "browser_open")
        }
    }
}

@Composable
private fun RecentsContent(shell: ShellState, baseZ: Int) {
    val recents = shell.recentKeys()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Clear All", onClick = { shell.settings.recentApps = "" }, zIndex = baseZ + 1, small = true, id = "recents_clear")
        }

        Spacer(Modifier.height(10.dp))

        if (recents.isEmpty()) {
            Text("No recent items.", color = Color.White.copy(alpha = 0.7f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recents) { key ->
                    val external = shell.installedApps.find { it.key == key }
                    val internalKind = if (key.startsWith("internal:")) {
                        WindowKind.entries.find { it.name == key.substringAfter(":") }
                    } else {
                        null
                    }

                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (external != null) {
                                AppIcon(external, Modifier.size(34.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(external.label, color = Color.White, maxLines = 1)
                                    Text(external.packageName, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
                                }
                                SpatialButton("Open", onClick = { shell.launchApp(external) }, zIndex = baseZ + 2, small = true, id = "recent_open/${external.key}")
                            } else if (internalKind != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(internalKind.title, color = Color.White, maxLines = 1)
                                    Text("Internal app", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                                }
                                SpatialButton("Open", onClick = { shell.openWindow(internalKind) }, zIndex = baseZ + 2, small = true, id = "recent_open/${internalKind.name}")
                            } else {
                                Text(key, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsContent(shell: ShellState, baseZ: Int) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Clear All", onClick = { shell.clearNotifications() }, zIndex = baseZ + 1, small = true, id = "notifications_clear")
            SpatialButton("System Notification Access", onClick = { shell.openSystemSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }, zIndex = baseZ + 1, small = true, id = "notifications_system")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Real notification mirroring requires optional notification-listener permission in Android settings. Until enabled, this is an internal notification center.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shell.notifications) { notification ->
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(notification.title, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(notification.text, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        SpatialButton("Dismiss", onClick = { shell.dismissNotification(notification.id) }, zIndex = baseZ + 2, small = true, id = "notification_dismiss/${notification.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsContent(shell: ShellState, baseZ: Int) {
    val settings = shell.settings

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SpatialSwitch("Camera Background", settings.cameraBackground, { settings.cameraBackground = !settings.cameraBackground }, baseZ + 1, "qs_camera_bg")
        SpatialSwitch("Hand Tracking", settings.trackingEnabled, { settings.trackingEnabled = !settings.trackingEnabled }, baseZ + 1, "qs_tracking")
        SpatialSwitch("Sounds", settings.sounds, { settings.sounds = !settings.sounds }, baseZ + 1, "qs_sounds")
        SpatialSwitch("Haptics", settings.haptics, { settings.haptics = !settings.haptics }, baseZ + 1, "qs_haptics")
        SpatialSwitch("Debug Overlay", settings.debugOverlay, { settings.debugOverlay = !settings.debugOverlay }, baseZ + 1, "qs_debug")

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Wi-Fi", onClick = { shell.openSystemSettings(Settings.ACTION_WIFI_SETTINGS) }, zIndex = baseZ + 2, small = true, id = "qs_wifi")
            SpatialButton("Bluetooth", onClick = { shell.openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }, zIndex = baseZ + 2, small = true, id = "qs_bluetooth")
            SpatialButton("Airplane", onClick = { shell.openSystemSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }, zIndex = baseZ + 2, small = true, id = "qs_airplane")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialButton("Display", onClick = { shell.openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS) }, zIndex = baseZ + 2, small = true, id = "qs_display")
            SpatialButton("Sound", onClick = { shell.openSystemSettings(Settings.ACTION_SOUND_SETTINGS) }, zIndex = baseZ + 2, small = true, id = "qs_sound")
            SpatialButton("Battery", onClick = { shell.openSystemSettings(Settings.ACTION_POWER_USAGE_SUMMARY) }, zIndex = baseZ + 2, small = true, id = "qs_battery")
        }

        Text(
            "Some toggles open the matching Android settings screen because direct control is restricted by Android.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun HelpContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        helpSection("Point", "Move your index finger. The fingertip controls the cursor.")
        helpSection("Pinch", "Bring thumb and index together. A stable pinch confirms a click.")
        helpSection("Drag", "Pinch on a window title bar, hold briefly, then move your hand.")
        helpSection("Menu", "Use the HUD Menu button, or enable thumb-middle hold gesture in Settings.")
        helpSection("Keyboard", "Open Keyboard from HUD, tap a text field, then pinch keys.")
        helpSection("Apps", "Open Apps from dock. Pinch an app tile to launch the real Android app.")
        helpSection("Calibration", "Open Settings and use Capture Open, Capture Pinch, Apply.")
        helpSection("Troubleshooting", "Improve lighting, keep your hand inside camera view, reduce sensitivity if cursor jitters.")
    }
}

@Composable
private fun helpSection(title: String, body: String) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
}
