package dev.spatial.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.spatial.android.InstalledApp
import dev.spatial.android.spatial.DragPhase
import dev.spatial.android.spatial.LocalInteractionRegistry
import dev.spatial.android.spatial.SpatialInteractive
import dev.spatial.android.spatial.TextInputController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = if (highlighted) 0.35f else 0.14f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun SpatialButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    zIndex: Int = 0,
    small: Boolean = false,
    id: String? = null
) {
    SpatialInteractive(
        id = id ?: "btn/$label/$zIndex",
        zIndex = zIndex,
        onClick = onClick,
        modifier = modifier
    ) { hovered ->
        GlassSurface(
            highlighted = hovered,
            modifier = Modifier
                .graphicsLayer {
                    if (hovered) {
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
                }
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = if (small) 12.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 3.dp else 6.dp)
            )
        }
    }
}

@Composable
fun SpatialSwitch(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    baseZ: Int,
    id: String
) {
    SpatialInteractive(
        id = id,
        zIndex = baseZ,
        onClick = onToggle,
        modifier = Modifier.padding(vertical = 4.dp)
    ) { hovered ->
        GlassSurface(highlighted = hovered, modifier = Modifier.width(300.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(46.dp, 26.dp)
                        .clip(CircleShape)
                        .background(if (checked) Color(0xFF4FC3F7) else Color.Gray.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun SpatialTextField(
    id: String,
    state: MutableState<String>,
    input: TextInputController,
    hint: String,
    baseZ: Int,
    modifier: Modifier = Modifier
) {
    val active = input.activeState === state

    SpatialInteractive(
        id = id,
        zIndex = baseZ,
        onClick = { input.activeState = state },
        modifier = modifier
    ) { hovered ->
        GlassSurface(highlighted = hovered || active) {
            Text(
                text = if (state.value.isEmpty()) hint else state.value,
                color = if (state.value.isEmpty()) Color.White.copy(alpha = 0.5f) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun rememberAppIcon(app: InstalledApp): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(app.key) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(app.packageName)
                drawable.toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    return bitmap
}

@Composable
fun AppIcon(
    app: InstalledApp,
    modifier: Modifier = Modifier
) {
    val icon = rememberAppIcon(app)

    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = app.label,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.take(1), color = Color.White)
        }
    }
}
