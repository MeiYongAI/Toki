package com.seepd.toki;

/** Supported defaults are intentionally finite so the player hook receives known-safe values. */
final class PlaybackSpeed {
    static final float DEFAULT = 1.0f;
    private static final float[] SUPPORTED = {1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private PlaybackSpeed() {
    }

    static float sanitize(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT;
        }
        for (float supported : SUPPORTED) {
            if (Math.abs(value - supported) < 0.001f) {
                return supported;
            }
        }
        return DEFAULT;
    }

    static float[] supportedValues() {
        return SUPPORTED.clone();
    }
}
