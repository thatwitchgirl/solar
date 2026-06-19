package com.solar.launcher;

import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Debug-only prefs: error toasts and unfinished home shortcuts. */
public final class DebugPrefs {
    public static final String PREF_SHOW_ERRORS = "debug_show_errors";
    public static final String PREF_SHOW_UNIMPLEMENTED = "debug_show_unimplemented";

    private static final Set<String> UNIMPLEMENTED = new HashSet<String>(Arrays.asList(
            HomeMenuConfig.ID_FM, HomeMenuConfig.ID_VIDEOS, HomeMenuConfig.ID_PHOTOS));

    private DebugPrefs() {}

    public static boolean showErrors(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_SHOW_ERRORS, false);
    }

    public static boolean showUnimplemented(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_SHOW_UNIMPLEMENTED, false);
    }

    public static boolean isUnimplemented(String id) {
        return id != null && UNIMPLEMENTED.contains(HomeMenuConfig.migrateIdStatic(id));
    }

    /** Hide roadmap shortcuts from home, More, and editor unless debug toggle is on. */
    public static boolean shouldShowShortcut(SharedPreferences prefs, String id) {
        return !isUnimplemented(id) || showUnimplemented(prefs);
    }
}
