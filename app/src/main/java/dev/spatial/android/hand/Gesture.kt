package dev.spatial.android.hand

enum class TrackingState {
    NO_HAND,
    HAND_DETECTED,
    POINTING,
    HOVERING,
    PINCH_START,
    PINCH_ACTIVE,
    DRAGGING,
    PINCH_RELEASE,
    TRACKING_LOST,
    UNAVAILABLE
}

class PinchDetector(
    var startThreshold: Float = 0.22f,
    var releaseThreshold: Float = 0.34f,
    var confirmMs: Long = 90L,
    var dragDelayMs: Long = 240L,
    var dragSlop: Float = 60f
) {
    enum class Phase {
        IDLE,
        PENDING,
        ACTIVE,
        DRAGGING
    }

    enum class Event {
        START,
        CONFIRM,
        DRAG,
        RELEASE
    }

    var phase = Phase.IDLE
        private set

    var wasConfirmed = false
        private set

    private var pendingSince = 0L
    private var activeSince = 0L
    private var smoothed = -1f

    fun reset() {
        phase = Phase.IDLE
        wasConfirmed = false
        pendingSince = 0L
        activeSince = 0L
        smoothed = -1f
    }

    fun update(distance: Float, now: Long, movedPixels: Float): List<Event> {
        smoothed = if (smoothed < 0f) distance else smoothed * 0.65f + distance * 0.35f
        val events = mutableListOf<Event>()

        when (phase) {
            Phase.IDLE -> {
                if (smoothed <= startThreshold) {
                    phase = Phase.PENDING
                    pendingSince = now
                    wasConfirmed = false
                    events.add(Event.START)
                }
            }

            Phase.PENDING -> {
                if (smoothed >= releaseThreshold) {
                    phase = Phase.IDLE
                    events.add(Event.RELEASE)
                } else if (now - pendingSince >= confirmMs) {
                    phase = Phase.ACTIVE
                    activeSince = now
                    wasConfirmed = true
                    events.add(Event.CONFIRM)
                }
            }

            Phase.ACTIVE -> {
                if (smoothed >= releaseThreshold) {
                    phase = Phase.IDLE
                    events.add(Event.RELEASE)
                } else if (movedPixels > dragSlop && now - activeSince >= dragDelayMs) {
                    phase = Phase.DRAGGING
                    events.add(Event.DRAG)
                }
            }

            Phase.DRAGGING -> {
                if (smoothed >= releaseThreshold * 1.2f) {
                    phase = Phase.IDLE
                    events.add(Event.RELEASE)
                }
            }
        }

        return events
    }
}

class CursorSmoother(var alpha: Float = 0.5f) {
    private var lastX: Float? = null
    private var lastY: Float? = null

    fun reset() {
        lastX = null
        lastY = null
    }

    fun next(targetX: Float, targetY: Float): Pair<Float, Float> {
        val lx = lastX
        val ly = lastY

        if (lx == null || ly == null) {
            lastX = targetX
            lastY = targetY
            return targetX to targetY
        }

        val a = alpha.coerceIn(0f, 0.98f)
        val x = lx * a + targetX * (1f - a)
        val y = ly * a + targetY * (1f - a)

        lastX = x
        lastY = y

        return x to y
    }
}

object DragCalculator {
    fun apply(
        initialObjectX: Float,
        initialObjectY: Float,
        initialPointerX: Float,
        initialPointerY: Float,
        currentPointerX: Float,
        currentPointerY: Float
    ): Pair<Float, Float> {
        return Pair(
            initialObjectX + currentPointerX - initialPointerX,
            initialObjectY + currentPointerY - initialPointerY
        )
    }
}
