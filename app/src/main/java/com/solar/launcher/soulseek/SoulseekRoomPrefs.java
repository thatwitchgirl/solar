package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Per-user chat room leave/hide prefs (Innioasis cannot be left). */
public final class SoulseekRoomPrefs {

    private static final String PREF_LEFT = "soulseek_left_chat_rooms";

    private SoulseekRoomPrefs() {}

    public static boolean isLeft(SharedPreferences prefs, String room) {
        if (prefs == null || room == null || room.trim().isEmpty()) return false;
        if (ReachCommunityRooms.isProtected(room)) return false;
        Set<String> left = prefs.getStringSet(PREF_LEFT, null);
        if (left == null || left.isEmpty()) return false;
        String key = room.trim().toLowerCase(Locale.US);
        for (String s : left) {
            if (s != null && s.toLowerCase(Locale.US).equals(key)) return true;
        }
        return false;
    }

    public static void markLeft(Context ctx, SharedPreferences prefs, String room) {
        if (ctx == null || prefs == null || room == null || room.trim().isEmpty()) return;
        if (ReachCommunityRooms.isProtected(room)) return;
        String key = room.trim().toLowerCase(Locale.US);
        Set<String> prev = prefs.getStringSet(PREF_LEFT, new HashSet<String>());
        HashSet<String> next = new HashSet<String>(prev != null ? prev : new HashSet<String>());
        next.add(key);
        prefs.edit().putStringSet(PREF_LEFT, next).apply();
    }
}
