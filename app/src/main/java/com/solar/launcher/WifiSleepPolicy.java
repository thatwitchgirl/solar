package com.solar.launcher;

/** Wi-Fi sleep policy prefs — turn radio off after screen sleep on battery. */
public final class WifiSleepPolicy {

    public static final String PREF_ENABLED = "wifi_sleep_power_off";
    public static final String PREF_DISABLED_BY_POLICY = "wifi_disabled_by_sleep_policy";

    private WifiSleepPolicy() {}

    public static boolean shouldRestore(boolean inMemoryFlag, boolean persistedFlag) {
        return inMemoryFlag || persistedFlag;
    }

    public static void selfCheck() {
        if (shouldRestore(false, false)) {
            throw new AssertionError("no restore");
        }
        if (!shouldRestore(true, false)) {
            throw new AssertionError("memory");
        }
        if (!shouldRestore(false, true)) {
            throw new AssertionError("persisted");
        }
    }
}
