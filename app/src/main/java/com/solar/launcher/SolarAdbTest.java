package com.solar.launcher;

import android.util.Log;

/** Logcat probe for {@code scripts/test_context_queue_adb.sh} — tag {@code SolarAdbTest}. */
final class SolarAdbTest {
    static final String TAG = "SolarAdbTest";

    private SolarAdbTest() {}

    static void queueScroll(int focusIndex, int count, int topPad, int scrollY, int maxScroll, int slot) {
        Log.i(TAG, "queueScroll focus=" + focusIndex + " count=" + count + " topPad=" + topPad
                + " scrollY=" + scrollY + " maxScroll=" + maxScroll + " slot=" + slot);
    }

    static void fail(String reason) {
        Log.i(TAG, "FAIL " + reason);
    }

    static void pass(String reason) {
        Log.i(TAG, "PASS " + reason);
    }
}
