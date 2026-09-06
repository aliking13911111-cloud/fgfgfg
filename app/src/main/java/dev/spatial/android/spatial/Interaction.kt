package dev.spatial.android.spatial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize

enum class DragPhase {
    START,
    MOVE,
    END
}

data class InteractiveRegion(
    val id: String,
    val zIndex: Int,
    val bounds: Rect,
    val onClick: (() -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
    val drag: ((DragPhase, Offset) -> Unit)? = null,
    val enabled: Boolean = true
)

class InteractionRegistry {
    val regions = mutableStateMapOf<String, InteractiveRegion>()

    var hoveredId by mutableStateOf<String?>(null)
        private set

    var pressedId by mutableStateOf<String?>(null)
        private set

    var draggingId by mutableStateOf<String?>(null)
        private set

    private val clickSlop = 80f

    fun updateCursor(p: Offset) {
        hoveredId = hitTest(p)?.id
    }

    fun hitTest(p: Offset): InteractiveRegion? {
        return regions.values
            .filter { it.enabled && it.bounds.contains(p) }
            .maxByOrNull { it.zIndex }
    }

    fun onPinchDown() {
        pressedId = hoveredId
    }

    fun onDragMaybe(p: Offset): Boolean {
        val region = pressedId?.let { regions[it] }
        if (region?.drag != null) {
            draggingId = pressedId
            region.drag.invoke(DragPhase.START, p)
            return true
        }
        return false
    }

    fun onDragMove(p: Offset) {
        val region = draggingId?.let { regions[it] }
        region?.drag?.invoke(DragPhase.MOVE, p)
    }

    fun onPinchRelease(p: Offset, moved: Float, confirmed: Boolean): Boolean {
        val pressed = pressedId?.let { regions[it] }

        if (draggingId != null) {
            pressed?.drag?.invoke(DragPhase.END, p)
            draggingId = null
            pressedId = null
            return false
        }

        if (!confirmed) {
            pressedId = null
            return false
        }

        val hovered = hitTest(p)?.id
        val clicked = pressed != null && hovered == pressedId && moved < clickSlop

        if (clicked) {
            pressed?.onClick?.invoke()
        }

        pressedId = null
        return clicked
    }

    fun cancelPress() {
        if (draggingId != null) {
            val region = draggingId?.let { regions[it] }
            region?.drag?.invoke(DragPhase.END, Offset.Zero)
        }
        draggingId = null
        pressedId = null
    }
}

val LocalInteractionRegistry = staticCompositionLocalOf<InteractionRegistry> {
    error("InteractionRegistry not provided")
}

@Composable
fun SpatialInteractive(
    id: String,
    zIndex: Int,
    onClick: (() -> Unit)? = null,
    drag: ((DragPhase, Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (Boolean) -> Unit
) {
    val registry = LocalInteractionRegistry.current
    var bounds by remember { mutableStateOf(Rect.Zero) }

    val onClickState by rememberUpdatedState(onClick)
    val dragState by rememberUpdatedState(drag)

    val hovered = registry.hoveredId == id

    DisposableEffect(id, zIndex, bounds) {
        registry.regions[id] = InteractiveRegion(
            id = id,
            zIndex = zIndex,
            bounds = bounds,
            onClick = { onClickState?.invoke() },
            onLongClick = null,
            drag = { phase, p -> dragState?.invoke(phase, p) }
        )

        onDispose {
            if (registry.regions[id]?.bounds == bounds) {
                registry.regions.remove(id)
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val newBounds = Rect(
                    coordinates.positionInRoot(),
                    coordinates.size.toSize()
                )
                if (newBounds != bounds) {
                    bounds = newBounds
                    registry.regions[id]?.let {
                        registry.regions[id] = it.copy(bounds = newBounds)
                    }
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClickState?.invoke() }
                } else {
                    Modifier
                }
            )
    ) {
        content(hovered)
    }
}
