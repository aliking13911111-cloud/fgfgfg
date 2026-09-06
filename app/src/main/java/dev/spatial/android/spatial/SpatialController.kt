package dev.spatial.android.spatial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.spatial.android.SettingsStore
import dev.spatial.android.hand.CursorSmoother
import dev.spatial.android.hand.PinchDetector
import dev.spatial.android.hand.TrackingState
import kotlin.math.max
import kotlin.math.min

data class DebugInfo(
    val fps: Float = 0f,
    val processMs: Long = 0L,
    val handDetected: Boolean = false,
    val pinchDistance: Float = 0f,
    val cursorX: Float = 0f,
    val cursorY: Float = 0f,
    val state: String = "UNAVAILABLE",
    val memoryMb: Long = 0L,
    val droppedFrames: Int = 0
)

class SpatialController(
    private val settings: SettingsStore,
    private val interaction: InteractionRegistry
) {
    var cursor by mutableStateOf(Offset.Zero)
        private set

    var cursorAlpha by mutableStateOf(0f)
        private set

    var trackingState by mutableStateOf(TrackingState.UNAVAILABLE)
        private set

    var debug by mutableStateOf(DebugInfo())
        private set

    var latestPinchDistance by mutableStateOf(1f)
        private set

    var landmarksScreen by mutableStateOf<List<Offset>>(emptyList())
        private set

    var menuRequest by mutableStateOf(0)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var onUserClick: () -> Unit = {}

    private var screenSize = Size(0f, 0f)
    private val smoother = CursorSmoother()
    private val pinch = PinchDetector()

    private var hadHand = false
    private var lastFrameTime = 0L
    private var fpsSmooth = 0f

    private var pinchStartCursor = Offset.Zero
    private var movedSincePinch = 0f

    private var middleHoldStart = 0L
    private var menuGestureTriggered = false

    fun setScreenSize(size: Size) {
        screenSize = size
    }

    fun onCameraError(message: String) {
        errorMessage = message
        trackingState = TrackingState.UNAVAILABLE
        interaction.cancelPress()
        pinch.reset()
    }

    fun onHandFrame(landmarks: List<Offset>?, handCount: Int, processMs: Long) {
        val now = System.currentTimeMillis()

        updateFps(now)

        if (landmarks == null || landmarks.size < 21 || screenSize.width <= 0f) {
            handleNoHand()
            updateDebug(processMs, false, 0f)
            lastFrameTime = now
            return
        }

        hadHand = true
        cursorAlpha = min(1f, cursorAlpha + 0.2f)

        val wrist = landmarks[0]
        val middleMcp = landmarks[9]
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val middleTip = landmarks[12]

        val handScale = max(distance(wrist, middleMcp), 0.001f)
        val pinchDistance = distance(thumbTip, indexTip) / handScale
        latestPinchDistance = pinchDistance

        smoother.alpha = settings.cursorSmoothing

        val targetX = mapAxis(indexTip.x, settings.invertX, screenSize.width)
        val targetY = mapAxis(indexTip.y, settings.invertY, screenSize.height)
        val smoothed = smoother.next(targetX, targetY)
        cursor = Offset(smoothed.first, smoothed.second)

        interaction.updateCursor(cursor)

        landmarksScreen = landmarks.map {
            Offset(
                directAxis(it.x, settings.invertX) * screenSize.width,
                directAxis(it.y, settings.invertY) * screenSize.height
            )
        }

        pinch.startThreshold = settings.pinchStart
        pinch.releaseThreshold = settings.pinchRelease
        pinch.confirmMs = settings.confirmMs.toLong()
        pinch.dragDelayMs = settings.dragDelayMs.toLong()

        movedSincePinch = if (pinch.phase == PinchDetector.Phase.IDLE) {
            0f
        } else {
            distance(cursor, pinchStartCursor)
        }

        val events = pinch.update(pinchDistance, now, movedSincePinch)

        for (event in events) {
            when (event) {
                PinchDetector.Event.START -> {
                    trackingState = TrackingState.PINCH_START
                    pinchStartCursor = cursor
                    movedSincePinch = 0f
                    interaction.onPinchDown()
                }

                PinchDetector.Event.CONFIRM -> {
                    trackingState = TrackingState.PINCH_ACTIVE
                }

                PinchDetector.Event.DRAG -> {
                    val dragging = interaction.onDragMaybe(cursor)
                    trackingState = if (dragging) TrackingState.DRAGGING else TrackingState.PINCH_ACTIVE
                }

                PinchDetector.Event.RELEASE -> {
                    val clicked = interaction.onPinchRelease(
                        cursor,
                        movedSincePinch,
                        pinch.wasConfirmed
                    )
                    if (clicked) {
                        onUserClick()
                    }
                    trackingState = TrackingState.HAND_DETECTED
                }
            }
        }

        if (interaction.draggingId != null) {
            interaction.onDragMove(cursor)
        }

        if (trackingState != TrackingState.PINCH_START &&
            trackingState != TrackingState.PINCH_ACTIVE &&
            trackingState != TrackingState.DRAGGING
        ) {
            trackingState = if (interaction.hoveredId != null) {
                TrackingState.HOVERING
            } else {
                TrackingState.POINTING
            }
        }

        handleMenuGesture(thumbTip, middleTip, handScale, now)
        updateDebug(processMs, true, pinchDistance)
        lastFrameTime = now
    }

    private fun handleNoHand() {
        if (pinch.phase != PinchDetector.Phase.IDLE) {
            interaction.cancelPress()
            pinch.reset()
        }

        landmarksScreen = emptyList()
        cursorAlpha = max(0f, cursorAlpha - 0.08f)

        if (cursorAlpha <= 0f) {
            smoother.reset()
            hadHand = false
        }

        trackingState = if (hadHand) TrackingState.TRACKING_LOST else TrackingState.NO_HAND
    }

    private fun handleMenuGesture(
        thumbTip: Offset,
        middleTip: Offset,
        handScale: Float,
        now: Long
    ) {
        if (!settings.menuGestureEnabled) return

        val middleDistance = distance(thumbTip, middleTip) / handScale

        if (middleDistance < settings.pinchStart * 1.15f && pinch.phase == PinchDetector.Phase.IDLE) {
            if (middleHoldStart == 0L) {
                middleHoldStart = now
            } else if (!menuGestureTriggered && now - middleHoldStart > 600L) {
                menuRequest++
                menuGestureTriggered = true
            }
        } else {
            middleHoldStart = 0L
            menuGestureTriggered = false
        }
    }

    private fun updateFps(now: Long) {
        if (lastFrameTime != 0L) {
            val delta = (now - lastFrameTime).coerceAtLeast(1L)
            val instant = 1000f / delta
            fpsSmooth = if (fpsSmooth == 0f) instant else fpsSmooth * 0.8f + instant * 0.2f
        }
    }

    private fun updateDebug(processMs: Long, handDetected: Boolean, pinchDistance: Float) {
        val runtime = Runtime.getRuntime()
        val memoryMb = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L

        debug = DebugInfo(
            fps = fpsSmooth,
            processMs = processMs,
            handDetected = handDetected,
            pinchDistance = pinchDistance,
            cursorX = cursor.x,
            cursorY = cursor.y,
            state = trackingState.name,
            memoryMb = memoryMb
        )
    }

    private fun mapAxis(raw: Float, invert: Boolean, size: Float): Float {
        val v = if (invert) 1f - raw else raw
        val norm = ((v - 0.12f) / 0.76f).coerceIn(0f, 1f)
        val speed = settings.cursorSpeed * settings.sensitivity
        val centered = (norm - 0.5f) * speed + 0.5f
        return centered.coerceIn(0f, 1f) * size
    }

    private fun directAxis(raw: Float, invert: Boolean): Float {
        return if (invert) 1f - raw else raw
    }

    private fun distance(a: Offset, b: Offset): Float {
        return (a - b).getDistance()
    }
}
