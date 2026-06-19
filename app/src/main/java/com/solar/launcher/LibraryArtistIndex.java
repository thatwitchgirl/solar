package com.solar.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scan-time artist index: canonical names, browse matching, co-occurrence for Reach.
 * ponytail: rebuild once per library scan; top-16 collaborators per artist cap.
 */
public final class LibraryArtistIndex {
    private static final int MAX_COLLABORATORS = 16;

    public static final class Track {
        public final String artist;
        public final String albumArtist;

        public Track(String artist, String albumArtist) {
            this.artist = artist != null ? artist : "";
            this.albumArtist = albumArtist != null ? albumArtist : "";
        }
    }

    private final Map<String, String> canonicalToDisplay = new HashMap<String, String>();
    private final List<String> sortedArtists = new ArrayList<String>();
    private final Map<String, Map<String, Integer>> coOccurrence = new HashMap<String, Map<String, Integer>>();
    private boolean primaryOnlyMode;

    public void rebuild(List<Track> tracks, boolean primaryOnlyMode) {
        canonicalToDisplay.clear();
        sortedArtists.clear();
        coOccurrence.clear();
        this.primaryOnlyMode = primaryOnlyMode;
        if (tracks == null || tracks.isEmpty()) return;

        for (Track track : tracks) {
            List<ArtistTagParser.Credit> credits = ArtistTagParser.mergeTrackCredits(
                    track.artist, track.albumArtist);
            if (credits.isEmpty()) continue;

            for (ArtistTagParser.Credit c : credits) {
                if (!canonicalToDisplay.containsKey(c.canonicalKey)) {
                    canonicalToDisplay.put(c.canonicalKey, c.display);
                }
            }

            for (int i = 0; i < credits.size(); i++) {
                ArtistTagParser.Credit a = credits.get(i);
                for (int j = i + 1; j < credits.size(); j++) {
                    ArtistTagParser.Credit b = credits.get(j);
                    bumpCo(a.canonicalKey, b.canonicalKey);
                    bumpCo(b.canonicalKey, a.canonicalKey);
                }
            }
        }

        sortedArtists.addAll(canonicalToDisplay.values());
        Collections.sort(sortedArtists, String.CASE_INSENSITIVE_ORDER);
    }

    public List<String> allArtists() {
        return Collections.unmodifiableList(sortedArtists);
    }

    public String displayForCanonical(String displayOrKey) {
        if (displayOrKey == null) return null;
        String key = ArtistTagParser.canonicalKey(displayOrKey);
        String d = canonicalToDisplay.get(key);
        return d != null ? d : displayOrKey;
    }

    public boolean trackMatchesArtist(Track track, String artistDisplayName) {
        return trackMatchesArtist(track, artistDisplayName, primaryOnlyMode);
    }

    public boolean trackMatchesArtist(Track track, String artistDisplayName, boolean primaryOnly) {
        if (track == null || artistDisplayName == null) return false;
        String key = ArtistTagParser.canonicalKey(artistDisplayName);
        List<ArtistTagParser.Credit> credits = ArtistTagParser.mergeTrackCredits(
                track.artist, track.albumArtist);
        for (int i = 0; i < credits.size(); i++) {
            ArtistTagParser.Credit c = credits.get(i);
            if (!c.canonicalKey.equals(key)) continue;
            return ArtistTagParser.isPrimaryCredit(c, i, credits, track.albumArtist, primaryOnly);
        }
        return false;
    }

    /** Ranked collaborator display names for Reach context suggestions. */
    public List<String> topCollaborators(String artistDisplayName, int max) {
        if (max <= 0 || artistDisplayName == null) return Collections.emptyList();
        String key = ArtistTagParser.canonicalKey(artistDisplayName);
        Map<String, Integer> neighbors = coOccurrence.get(key);
        if (neighbors == null || neighbors.isEmpty()) return Collections.emptyList();

        List<Map.Entry<String, Integer>> ranked = new ArrayList<Map.Entry<String, Integer>>(
                neighbors.entrySet());
        Collections.sort(ranked, new java.util.Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                int cmp = b.getValue().compareTo(a.getValue());
                if (cmp != 0) return cmp;
                String da = canonicalToDisplay.get(a.getKey());
                String db = canonicalToDisplay.get(b.getKey());
                if (da == null) da = a.getKey();
                if (db == null) db = b.getKey();
                return da.compareToIgnoreCase(db);
            }
        });

        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : ranked) {
            if (out.size() >= max) break;
            String d = canonicalToDisplay.get(e.getKey());
            if (d != null && !ArtistTagParser.canonicalKey(d).equals(key)) {
                out.add(d);
            }
        }
        return out;
    }

    private void bumpCo(String aKey, String bKey) {
        if (aKey.equals(bKey)) return;
        Map<String, Integer> map = coOccurrence.get(aKey);
        if (map == null) {
            map = new HashMap<String, Integer>();
            coOccurrence.put(aKey, map);
        }
        Integer n = map.get(bKey);
        int next = n != null ? n + 1 : 1;
        map.put(bKey, next);
        trimCoMap(map);
    }

    private void trimCoMap(Map<String, Integer> map) {
        if (map.size() <= MAX_COLLABORATORS) return;
        List<Map.Entry<String, Integer>> ranked = new ArrayList<Map.Entry<String, Integer>>(
                map.entrySet());
        Collections.sort(ranked, new java.util.Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        map.clear();
        for (int i = 0; i < MAX_COLLABORATORS && i < ranked.size(); i++) {
            Map.Entry<String, Integer> e = ranked.get(i);
            map.put(e.getKey(), e.getValue());
        }
    }
}
