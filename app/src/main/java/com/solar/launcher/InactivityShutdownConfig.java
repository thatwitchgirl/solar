package com.solar.launcher;

/** Inactivity auto-shutdown duration + theme icon keys (Y1 timedShutdown_* parity). */
public final class InactivityShutdownConfig {

    public static final String PREF_MINUTES = "inactivity_shutdown_minutes";
    public static final String LEGACY_PREF_ENABLED = "power_saving_shutdown";
    static final int[] SHUTDOWN_MINUTES = {0, 10, 20, 30, 60, 90, 120};
    private static final int DEFAULT_MINUTES = 30;

    private InactivityShutdownConfig() {}

    public static int indexForMinutes(int minutes) {
        for (int i = 0; i < SHUTDOWN_MINUTES.length; i++) {
            if (SHUTDOWN_MINUTES[i] == minutes) return i;
        }
        return indexForMinutes(DEFAULT_MINUTES);
    }

    public static int minutesAtIndex(int index) {
        if (index < 0 || index >= SHUTDOWN_MINUTES.length) return DEFAULT_MINUTES;
        return SHUTDOWN_MINUTES[index];
    }

    public static int nextIndex(int index) {
        return (index + 1) % SHUTDOWN_MINUTES.length;
    }

    /** Theme settingConfig key for preview icon. */
    public static String iconKeyForMinutes(int minutes) {
        if (minutes <= 0) return "timedShutdown_off";
        String key = "timedShutdown_" + minutes;
        for (int m : SHUTDOWN_MINUTES) {
            if (m == minutes) return key;
        }
        return "timedShutdown_30";
    }

    public static long shutdownDelayMs(int minutes) {
        if (minutes <= 0) return 0L;
        return minutes * 60_000L;
    }

    public static int defaultMinutes() {
        return DEFAULT_MINUTES;
    }
}
