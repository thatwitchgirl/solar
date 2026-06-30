package com.solar.launcher.theme;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class to map human-readable setting labels (as they appear in theme config.json)
 * to the corresponding SharedPreferences keys used by the app.
 *
 * The mapping is case-insensitive and ignores any parenthetical text.
 * For example, "Full Width Menus (experimental)" maps to "full_width_menus".
 *
 * Themes can specify overrides using the "solarConfig" object, e.g.
 * {
 *   "solarConfig": {
 *     "enableFull_Width_Menus": true,
 *     "disableLCD_Album_Art": false,
 *     "enableMatch_Now_Playing_To_Theme": true
 *   }
 * }
 *
 * Called by {@link ThemeManager} when a theme is applied. Updates SharedPreferences
 * so the rest of the application reads the overridden values.
 */
public class SettingLookup {
    /** Maps normalised English label → SharedPreferences key string. */
    private static final Map<String, String> LABEL_TO_PREF_KEY = new HashMap<>();

    static {
        // ponytail: map theme config labels to actual pref keys used in MainActivity.
        // Labels are normalised (lowercase, underscores→spaces, parentheses stripped).
        put("full width menus", "full_width_menus");
        put("lcd album art", "now_playing_lcd_art");
        put("3d album art", "now_playing_3d_album_art");
        put("match now playing to theme", "now_playing_match_font");
        put("now playing backdrop", "now_playing_backdrop");
        put("status bar match font", "status_bar_match_font");
        put("usb auto connect", "usb_auto_connect");
    }

    private static void put(String label, String prefKey) {
        LABEL_TO_PREF_KEY.put(normalize(label), prefKey);
    }

    /**
     * Normalises a label by trimming, converting to lower case and discarding any text
     * after the first opening parenthesis "(".
     */
    static String normalize(String label) {
        if (label == null) return "";
        int parenIdx = label.indexOf('(');
        if (parenIdx != -1) {
            label = label.substring(0, parenIdx);
        }
        return label.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns the SharedPreferences key for a given human-readable label, or null if unknown. */
    public static String prefKeyForLabel(String label) {
        return LABEL_TO_PREF_KEY.get(normalize(label));
    }

    /**
     * Apply overrides defined in a theme's "solarConfig" JSON object.
     *
     * @param ctx       application context
     * @param overrides map of raw solarConfig keys (e.g. "enableFull_Width_Menus") to values
     */
    public static void applySolarConfigOverrides(Context ctx, Map<String, Object> overrides) {
        if (ctx == null || overrides == null || overrides.isEmpty()) return;
        SharedPreferences prefs = ctx.getSharedPreferences(
                ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean anyChange = false;
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            String rawKey = e.getKey();
            // Determine operation prefix: enable / disable / set
            String operation = "set";
            String remainder = rawKey;
            if (rawKey.length() > 6 && rawKey.regionMatches(true, 0, "enable", 0, 6)) {
                operation = "enable";
                remainder = rawKey.substring(6);
            } else if (rawKey.length() > 7 && rawKey.regionMatches(true, 0, "disable", 0, 7)) {
                operation = "disable";
                remainder = rawKey.substring(7);
            } else if (rawKey.length() > 3 && rawKey.regionMatches(true, 0, "set", 0, 3)) {
                operation = "set";
                remainder = rawKey.substring(3);
            }
            // Convert underscores to spaces for lookup
            String label = remainder.replace('_', ' ');
            String prefKey = prefKeyForLabel(label);
            if (prefKey == null) continue; // Unknown setting — skip

            boolean value;
            if ("enable".equals(operation)) {
                value = true;
            } else if ("disable".equals(operation)) {
                value = false;
            } else {
                // "set" — use the provided value if boolean, otherwise skip
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    value = (Boolean) v;
                } else {
                    continue;
                }
            }
            editor.putBoolean(prefKey, value);
            anyChange = true;
        }
        if (anyChange) editor.apply();
    }
}
