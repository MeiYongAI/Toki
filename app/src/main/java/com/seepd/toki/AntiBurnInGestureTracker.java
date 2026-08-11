package com.seepd.toki;

/** Tracks a stationary two-pointer hold without depending on Android input classes. */
public final class AntiBurnInGestureTracker {
    public static final long LONG_PRESS_MILLIS = 600L;

    private final float centerTolerancePx;
    private final float spanTolerancePx;

    private boolean tracking;
    private boolean targetEnabled;
    private long startedAtMillis;
    private float initialCenterX;
    private float initialCenterY;
    private double initialDistance;

    public AntiBurnInGestureTracker(float centerTolerancePx, float spanTolerancePx) {
        if (!Float.isFinite(centerTolerancePx) || centerTolerancePx < 0f) {
            throw new IllegalArgumentException(
                    "centerTolerancePx must be finite and non-negative");
        }
        if (!Float.isFinite(spanTolerancePx) || spanTolerancePx < 0f) {
            throw new IllegalArgumentException(
                    "spanTolerancePx must be finite and non-negative");
        }
        this.centerTolerancePx = centerTolerancePx;
        this.spanTolerancePx = spanTolerancePx;
    }

    /** Starts tracking when the second pointer goes down. Any other count cancels the gesture. */
    public void down(
            int pointerCount,
            long eventTimeMillis,
            float x1,
            float y1,
            float x2,
            float y2,
            boolean initiallyEnabled
    ) {
        reset();
        if (pointerCount != 2 || !hasFiniteCoordinates(x1, y1, x2, y2)) {
            return;
        }

        tracking = true;
        targetEnabled = !initiallyEnabled;
        startedAtMillis = eventTimeMillis;
        initialCenterX = center(x1, x2);
        initialCenterY = center(y1, y2);
        initialDistance = distance(x1, y1, x2, y2);
    }

    /** Cancels tracking when either the center or pointer distance leaves the allowed radius. */
    public void move(
            int pointerCount,
            long eventTimeMillis,
            float x1,
            float y1,
            float x2,
            float y2
    ) {
        if (!tracking) {
            return;
        }
        if (pointerCount != 2
                || eventTimeMillis < startedAtMillis
                || !hasFiniteCoordinates(x1, y1, x2, y2)) {
            reset();
            return;
        }

        double centerMovement = distance(
                initialCenterX,
                initialCenterY,
                center(x1, x2),
                center(y1, y2)
        );
        double distanceChange = Math.abs(distance(x1, y1, x2, y2) - initialDistance);
        if (centerMovement > centerTolerancePx || distanceChange > spanTolerancePx) {
            reset();
        }
    }

    /**
     * Finishes the gesture when a pointer goes up.
     *
     * @return the anti-burn-in state to apply, or {@code null} when the gesture did not qualify
     */
    public Boolean pointerUp(long eventTimeMillis) {
        return longPress(eventTimeMillis);
    }

    /**
     * Completes a qualifying hold at the long-press deadline. Calling this more than once is safe.
     */
    public Boolean longPress(long eventTimeMillis) {
        Boolean result = null;
        if (tracking
                && eventTimeMillis >= startedAtMillis
                && eventTimeMillis - startedAtMillis >= LONG_PRESS_MILLIS) {
            result = targetEnabled;
        }
        reset();
        return result;
    }

    public boolean isTracking() {
        return tracking;
    }

    public void cancel() {
        reset();
    }

    private void reset() {
        tracking = false;
        targetEnabled = false;
        startedAtMillis = 0L;
        initialCenterX = 0f;
        initialCenterY = 0f;
        initialDistance = 0d;
    }

    private static float center(float first, float second) {
        return first + (second - first) / 2f;
    }

    private static double distance(float x1, float y1, float x2, float y2) {
        return Math.hypot((double) x2 - x1, (double) y2 - y1);
    }

    private static boolean hasFiniteCoordinates(float x1, float y1, float x2, float y2) {
        return Float.isFinite(x1)
                && Float.isFinite(y1)
                && Float.isFinite(x2)
                && Float.isFinite(y2);
    }
}
