package com.seepd.toki;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AntiBurnInGestureTrackerTest {
    private static final float CENTER_TOLERANCE_PX = 12f;
    private static final float SPAN_TOLERANCE_PX = 4f;

    @Test
    public void shortHoldDoesNotTrigger() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        assertNull(tracker.pointerUp(599L));
    }

    @Test
    public void stationaryLongHoldEntersClearModeExactlyAtThreshold() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        tracker.move(2, 600L, 0f, 0f, 100f, 0f);

        assertTrue(tracker.pointerUp(600L));
    }

    @Test
    public void stationaryLongHoldExitsClearModeWhenInitiallyEnabled() {
        AntiBurnInGestureTracker tracker = startedTracker(true);

        assertFalse(tracker.pointerUp(750L));
    }

    @Test
    public void movementWithinToleranceStillTriggers() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        tracker.move(2, 300L, 4f, 5f, 108f, 5f);

        assertEquals(Boolean.TRUE, tracker.pointerUp(650L));
    }

    @Test
    public void centerMovementBeyondToleranceCancels() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        tracker.move(2, 300L, 13f, 0f, 113f, 0f);

        assertNull(tracker.pointerUp(700L));
    }

    @Test
    public void distanceChangeBeyondToleranceCancels() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        tracker.move(2, 300L, 0f, 0f, 113f, 0f);

        assertNull(tracker.pointerUp(700L));
    }

    @Test
    public void pinchMovementCancelsEvenWhenTheCenterDoesNotMove() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        tracker.move(2, 300L, -3f, 0f, 103f, 0f);

        assertNull(tracker.pointerUp(700L));
    }

    @Test
    public void thirdPointerCancelsFromDownOrMove() {
        AntiBurnInGestureTracker downTracker = startedTracker(false);
        downTracker.down(3, 200L, 0f, 0f, 100f, 0f, false);

        AntiBurnInGestureTracker moveTracker = startedTracker(false);
        moveTracker.move(3, 200L, 0f, 0f, 100f, 0f);

        assertNull(downTracker.pointerUp(900L));
        assertNull(moveTracker.pointerUp(900L));
    }

    @Test
    public void cancelClearsGestureAndNextGestureCanStart() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        tracker.cancel();
        assertNull(tracker.pointerUp(900L));

        tracker.down(2, 1_000L, 0f, 0f, 100f, 0f, true);
        assertEquals(Boolean.FALSE, tracker.pointerUp(1_600L));
    }

    @Test
    public void qualifiedGestureCanOnlyTriggerOnce() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        assertEquals(Boolean.TRUE, tracker.pointerUp(600L));
        assertNull(tracker.pointerUp(700L));
    }

    @Test
    public void delayedDeadlineCanTriggerBeforePointerUp() {
        AntiBurnInGestureTracker tracker = startedTracker(false);

        assertTrue(tracker.isTracking());
        assertEquals(Boolean.TRUE, tracker.longPress(600L));
        assertFalse(tracker.isTracking());
        assertNull(tracker.pointerUp(700L));
    }

    private static AntiBurnInGestureTracker startedTracker(boolean initiallyEnabled) {
        AntiBurnInGestureTracker tracker = new AntiBurnInGestureTracker(
                CENTER_TOLERANCE_PX,
                SPAN_TOLERANCE_PX);
        tracker.down(2, 0L, 0f, 0f, 100f, 0f, initiallyEnabled);
        return tracker;
    }
}
