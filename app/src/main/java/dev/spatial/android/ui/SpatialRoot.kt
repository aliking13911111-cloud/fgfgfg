package dev.spatial.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import dev.spatial.android.SettingsStore
import dev.spatial.android.spatial.LocalInteractionRegistry
import dev.spatial.android.spatial.ShellState
import dev.spatial.android.spatial.findActivity
import kotlinx.coroutines.delay

@Composable
fun SpatialRoot() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val shell = remember { ShellState(context, settings) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var onboardingDone by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            onboardingDone = true
            shell.showToast("Hand tracking enabled")
        } else {
            shell.showToast("Camera permission denied. Touch mode remains available.")
        }
    }

    LaunchedEffect(Unit) {
        shell.loadApps()
    }

    LaunchedEffect(shell.noteTextState.value) {
        settings.noteText = shell.noteTextState.value
    }

    LaunchedEffect(shell.toast) {
        if (shell.toast != null) {
            delay(2600)
            shell.toast = null
        }
    }

    LaunchedEffect(shell.controller.menuRequest) {
        if (shell.controller.menuRequest > 0) {
            shell.menuOpen = true
        }
    }

    val colors = if (settings.theme == 3) {
        lightColorScheme(primary = Color(0xFF3A6FF2), secondary = Color(0xFF7C4DFF))
    } else {
        darkColorScheme(primary = Color(0xFF77C9FF), secondary = Color(0xFF8A7CFF))
    }

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalInteractionRegistry provides shell.interaction) {
            val view = LocalView.current

            shell.controller.onUserClick = {
                if (settings.haptics) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                shell.uiSound()
            }

            if (!onboardingDone) {
                OnboardingScreen(
                    onGrantCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onContinueWithoutCamera = {
                        onboardingDone = true
                        permissionGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                )
            } else {
                SpatialShell(shell = shell, settings = settings, cameraPermission = permissionGranted)
            }

            ToastOverlay(shell.toast)
        }
    }
}

@Composable
private fun OnboardingScreen(
    onGrantCamera: () -> Unit,
    onContinueWithoutCamera: () -> Unit
) {
    var step by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> {
                    Text("Spatial Android", color = Color.White, fontSize = 32.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "A futuristic hand-tracked shell for your phone.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                1 -> {
                    Text("Camera Permission", color = Color.White, fontSize = 26.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Camera access is used only locally for hand tracking. No frames are uploaded.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                2 -> {
                    Text("Point and Pinch", color = Color.White, fontSize = 26.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Point with your index finger to move the cursor. Pinch thumb and index to click. Hold pinch to drag windows.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                else -> {
                    Text("Ready", color = Color.White, fontSize = 26.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "You can recalibrate anytime in Settings. Touch input remains available as a fallback.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step < 3) {
                    SpatialButton(label = "Next", onClick = { step++ }, zIndex = 10000, id = "onboard_next_$step")
                } else {
                    SpatialButton(label = "Grant Camera", onClick = onGrantCamera, zIndex = 10000, id = "onboard_grant")
                    SpatialButton(
                        label = "Continue Without Camera",
                        onClick = onContinueWithoutCamera,
                        zIndex = 10000,
                        id = "onboard_skip"
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastOverlay(message: String?) {
    if (message != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(message, color = Color.White)
            }
        }
    }
}
