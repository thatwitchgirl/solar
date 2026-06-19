package com.solar.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Split multi-artist ID3 strings (TPE1/TPE2) into canonical segments.
 * ponytail: case-fold dedup only; no fuzzy artist matching.
 */
public final class ArtistTagParser {
    public enum Role { PRIMARY, FEATURED, EQUAL_PRIMARY }

    public static final class Credit {
        public final String display;
        public final String canonicalKey;
        public final Role role;

        Credit(String display, Role role) {
            this.display = display;
            this.canonicalKey = canonicalKey(display);
            this.role = role;
        }
    }

    private static final Pattern LIST_SEP = Pattern.compile("[,;/]+");
    private static final Pattern FEAT_SPLIT = Pattern.compile(
            "(?i)\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+");
    private static final Pattern COLLAB_SPLIT = Pattern.compile(
            "(?i)\\s+(?:&|x|vs\\.?|versus)\\s+");

    private ArtistTagParser() {}

    public static String canonicalKey(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.US);
    }

    public static boolean isIgnorable(String raw) {
        if (raw == null) return true;
        String t = raw.trim();
        return t.isEmpty()
                || "Unknown Artist".equalsIgnoreCase(t)
                || "Various Artists".equalsIgnoreCase(t);
    }

    /** Parse one tag field into ordered credits with roles. */
    public static List<Credit> parseField(String raw) {
        List<Credit> out = new ArrayList<Credit>();
        if (isIgnorable(raw)) return out;
        String t = raw.trim();
        if (LIST_SEP.matcher(t).find()) {
            String[] parts = LIST_SEP.split(t);
            for (int i = 0; i < parts.length; i++) {
                addSegmentCredits(out, parts[i], Role.EQUAL_PRIMARY);
            }
            return out;
        }
        addSegmentCredits(out, t, Role.PRIMARY);
        return out;
    }

    /** Names only — for Reach / search phrase expansion. */
    public static List<String> splitNames(String raw) {
        List<String> out = new ArrayList<String>();
        for (Credit c : parseField(raw)) {
            addUniqueName(out, c.display);
        }
        return out;
    }

    /** Merge TPE1 + TPE2 credits; dedupe by canonical key (first spelling wins). */
    public static List<Credit> mergeTrackCredits(String trackArtist, String albumArtist) {
        List<Credit> merged = new ArrayList<Credit>();
        appendUniqueCredits(merged, parseField(trackArtist));
        appendUniqueCredits(merged, parseField(albumArtist));
        return merged;
    }

    private static void appendUniqueCredits(List<Credit> merged, List<Credit> add) {
        for (Credit c : add) {
            boolean found = false;
            for (Credit m : merged) {
                if (m.canonicalKey.equals(c.canonicalKey)) {
                    found = true;
                    break;
                }
            }
            if (!found) merged.add(c);
        }
    }

    private static void addSegmentCredits(List<Credit> out, String segment, Role defaultRole) {
        String s = segment != null ? segment.trim() : "";
        if (s.isEmpty()) return;

        String[] featParts = FEAT_SPLIT.split(s, 2);
        if (featParts.length == 2) {
            addSingleCredit(out, featParts[0], Role.PRIMARY);
            addSingleCredit(out, featParts[1], Role.FEATURED);
            return;
        }

        String[] collabParts = COLLAB_SPLIT.split(s, -1);
        if (collabParts.length > 1) {
            for (String part : collabParts) {
                addSingleCredit(out, part, defaultRole);
            }
            return;
        }

        if (s.contains("&")) {
            String[] ampParts = s.split("\\s*&\\s*");
            for (String part : ampParts) {
                addSingleCredit(out, part, defaultRole);
            }
            return;
        }

        addSingleCredit(out, s, defaultRole);
    }

    private static void addSingleCredit(List<Credit> out, String name, Role role) {
        String t = name != null ? name.trim() : "";
        if (t.isEmpty() || isIgnorable(t)) return;
        String key = canonicalKey(t);
        for (Credit c : out) {
            if (c.canonicalKey.equals(key)) return;
        }
        out.add(new Credit(t, role));
    }

    private static void addUniqueName(List<String> out, String name) {
        if (name == null || name.trim().isEmpty()) return;
        String key = canonicalKey(name);
        for (String existing : out) {
            if (canonicalKey(existing).equals(key)) return;
        }
        out.add(name.trim());
    }

    /** Whether artist counts as primary for library browse when primary-only mode is on. */
    public static boolean isPrimaryCredit(Credit credit, int indexInMerged, List<Credit> merged,
            String albumArtistRaw, boolean primaryOnlyMode) {
        if (credit == null) return false;
        if (!primaryOnlyMode) return true;
        if (credit.role == Role.FEATURED) return false;
        if (credit.role == Role.PRIMARY) return true;

        // EQUAL_PRIMARY: semicolon/comma list — first only, unless TPE2 matches this segment
        if (indexInMerged == 0) return true;
        if (!isIgnorable(albumArtistRaw)) {
            List<Credit> aa = parseField(albumArtistRaw);
            if (aa.size() == 1 && aa.get(0).canonicalKey.equals(credit.canonicalKey)) {
                return true;
            }
        }
        return false;
    }
}
