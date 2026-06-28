package com.solar.launcher.soulseek;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reach-wide community chat rooms — always joined and pinned in the room list. */
public final class ReachCommunityRooms {

    public static final String INNIOASIS = "Innioasis";

    private ReachCommunityRooms() {}

    public static boolean isProtected(String name) {
        return name != null && INNIOASIS.equalsIgnoreCase(name.trim());
    }

    public static SoulseekWire.RoomEntry innioasisEntry() {
        return new SoulseekWire.RoomEntry(INNIOASIS, 0);
    }

    /** Default list when no search has run — Innioasis only. */
    public static List<SoulseekWire.RoomEntry> defaultRoomList() {
        List<SoulseekWire.RoomEntry> out = new ArrayList<SoulseekWire.RoomEntry>(1);
        out.add(innioasisEntry());
        return out;
    }

    /** Pin Innioasis first; skip duplicates, left rooms, and protected duplicates. */
    public static List<SoulseekWire.RoomEntry> pinInnioasis(SharedPreferences prefs,
            List<SoulseekWire.RoomEntry> rooms) {
        List<SoulseekWire.RoomEntry> out = new ArrayList<SoulseekWire.RoomEntry>();
        out.add(innioasisEntry());
        if (rooms == null) return out;
        for (SoulseekWire.RoomEntry e : rooms) {
            if (e == null || e.name == null || e.name.trim().isEmpty()) continue;
            if (isProtected(e.name)) continue;
            if (SoulseekRoomPrefs.isLeft(prefs, e.name)) continue;
            if (containsName(out, e.name)) continue;
            out.add(e);
        }
        return out;
    }

    private static boolean containsName(List<SoulseekWire.RoomEntry> list, String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.US);
        for (SoulseekWire.RoomEntry e : list) {
            if (e != null && e.name != null && e.name.toLowerCase(Locale.US).equals(key)) {
                return true;
            }
        }
        return false;
    }
}
