package com.solar.launcher.flow;

import com.solar.launcher.AlbumNames;
import com.solar.launcher.ArtistBrowsePolicy;
import com.solar.launcher.ArtistNames;
import com.solar.launcher.ArtistParser;
import com.solar.launcher.LibraryBrowsePrefs;
import com.solar.launcher.PlaylistManager;
import com.solar.launcher.deezer.DeezerPlaylist;
import com.solar.launcher.podcast.OpenRssClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Build ordered FlowItem lists — same policy hooks as list browse. */
public final class FlowCatalog {

    /** Minimal song row for catalog building (decoupled from MainActivity.SongItem). */
    public static final class SongRow {
        public final File file;
        public final String title;
        public final String artist;
        public final String album;
        public final String albumArtist;
        public final long lastModified;
        public final int trackNumber;

        public SongRow(File file, String title, String artist, String album,
                String albumArtist, long lastModified) {
            this(file, title, artist, album, albumArtist, lastModified, 0);
        }

        public SongRow(File file, String title, String artist, String album,
                String albumArtist, long lastModified, int trackNumber) {
            this.file = file;
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
            this.album = album != null ? album : "";
            this.albumArtist = albumArtist != null ? albumArtist : "";
            this.lastModified = lastModified;
            this.trackNumber = trackNumber;
        }
    }

    private FlowCatalog() {}

    public static String podcastMatchKey(String feedUrl) {
        if (feedUrl == null) return "";
        return feedUrl.trim().toLowerCase(Locale.US);
    }

    public static List<FlowItem> build(FlowMode mode, List<SongRow> library,
            LibraryBrowsePrefs prefs, List<ArtistBrowsePolicy.Track> policyTracks,
            File musicRoot, List<DeezerPlaylist> deezerPlaylists,
            List<OpenRssClient.Podcast> podcasts) {
        if (mode == null) return Collections.emptyList();
        switch (mode) {
            case ALBUM:
                return buildAlbums(library, prefs, policyTracks);
            case ARTIST:
                return buildArtists(policyTracks, prefs);
            case PLAYLIST:
                return buildPlaylists(musicRoot, deezerPlaylists);
            case PODCAST:
                return buildPodcasts(podcasts);
            default:
                return Collections.emptyList();
        }
    }

    public static List<FlowItem> buildAlbums(List<SongRow> library, LibraryBrowsePrefs prefs,
            List<ArtistBrowsePolicy.Track> policyTracks) {
        return buildAlbums(library, prefs, policyTracks, false);
    }

    /**
     * Two-pass album catalog — artist vote pass then item build; O(n) not O(n²).
     * @param multiTrackOnly when true, omit albums with only one track (Flow settings).
     */
    public static List<FlowItem> buildAlbums(List<SongRow> library, LibraryBrowsePrefs prefs,
            List<ArtistBrowsePolicy.Track> policyTracks, boolean multiTrackOnly) {
        if (library == null || library.isEmpty()) return Collections.emptyList();
        Map<String, String> albumByKey = new HashMap<String, String>();
        Map<String, List<File>> tracksByAlbumKey = new HashMap<String, List<File>>();
        // ponytail: pass 1 counts artist votes per albumKey; pass 2 groups tracks; finalize picks primary artist.
        Map<String, Map<String, Integer>> artistVotesByAlbumKey = new HashMap<String, Map<String, Integer>>();

        for (SongRow song : library) {
            if (song.album == null || song.album.trim().isEmpty()
                    || "Unknown Album".equalsIgnoreCase(song.album.trim())) continue;
            String album = song.album.trim();
            String albumKey = AlbumNames.matchKey(album);
            if (prefs != null && prefs.normalizeAlbumCase()) {
                registerAlbumVariant(albumByKey, album);
            } else {
                albumByKey.put(albumKey, album);
            }
            String rawArtist = song.artist != null ? song.artist.trim() : "";
            if (!rawArtist.isEmpty()) {
                Map<String, Integer> votes = artistVotesByAlbumKey.get(albumKey);
                if (votes == null) {
                    votes = new HashMap<String, Integer>();
                    artistVotesByAlbumKey.put(albumKey, votes);
                }
                Integer n = votes.get(rawArtist);
                votes.put(rawArtist, n != null ? n + 1 : 1);
            }
        }

        for (SongRow song : library) {
            if (song.album == null || song.album.trim().isEmpty()
                    || "Unknown Album".equalsIgnoreCase(song.album.trim())) continue;
            String albumKey = AlbumNames.matchKey(song.album.trim());
            List<File> tracks = tracksByAlbumKey.get(albumKey);
            if (tracks == null) {
                tracks = new ArrayList<File>();
                tracksByAlbumKey.put(albumKey, tracks);
            }
            if (song.file != null && song.file.isFile()) tracks.add(song.file);
        }

        List<String> albumKeys = new ArrayList<String>(tracksByAlbumKey.keySet());
        Collections.sort(albumKeys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                String aa = albumByKey.get(a);
                String bb = albumByKey.get(b);
                return (aa != null ? aa : a).compareToIgnoreCase(bb != null ? bb : b);
            }
        });

        List<FlowItem> out = new ArrayList<FlowItem>();
        for (String albumKey : albumKeys) {
            List<File> tracks = tracksByAlbumKey.get(albumKey);
            if (multiTrackOnly && (tracks == null || tracks.size() <= 1)) continue;
            String album = albumByKey.get(albumKey);
            String artist = primaryArtistFromVotes(artistVotesByAlbumKey.get(albumKey), "");
            String itemKey = FlowCoverResolver.albumMatchKey(album, artist);
            String sub = ArtistBrowsePolicy.albumBrowseSubtitle(album, artist, policyTracks, prefs);
            if (sub == null || sub.isEmpty()) sub = artist;
            out.add(FlowItem.album(album, sub, itemKey, sortTracks(tracks, prefs, library), ""));
        }
        return out;
    }

    /** Pick dominant artist tag for an album — same policy as legacy primaryArtistForAlbum. */
    public static String primaryArtistFromVotes(Map<String, Integer> votes, String seedArtist) {
        if (votes == null || votes.isEmpty()) {
            return seedArtist != null ? seedArtist.trim() : "";
        }
        String best = seedArtist != null ? seedArtist.trim() : "";
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best != null ? best : "";
    }

    private static void registerAlbumVariant(Map<String, String> albumByKey, String raw) {
        String key = AlbumNames.matchKey(raw);
        String existing = albumByKey.get(key);
        if (existing == null) {
            albumByKey.put(key, raw);
        } else if (!existing.equals(raw)) {
            Map<String, Integer> counts = new HashMap<String, Integer>();
            counts.put(existing, 1);
            counts.put(raw, 1);
            albumByKey.put(key, AlbumNames.chooseCanonical(counts));
        }
    }

    public static List<FlowItem> buildArtists(List<ArtistBrowsePolicy.Track> policyTracks,
            LibraryBrowsePrefs prefs) {
        List<String> names = ArtistBrowsePolicy.collectArtists(policyTracks, prefs);
        List<FlowItem> out = new ArrayList<FlowItem>();
        for (String name : names) {
            String key = ArtistNames.matchKey(name);
            out.add(FlowItem.artist(name, "", key));
        }
        return out;
    }

    public static List<FlowItem> buildPlaylists(File musicRoot, List<DeezerPlaylist> deezer) {
        List<FlowItem> out = new ArrayList<FlowItem>();
        if (musicRoot != null) {
            for (PlaylistManager.Entry e : PlaylistManager.scan(musicRoot)) {
                String key = "local:" + e.sourceFile.getAbsolutePath();
                out.add(FlowItem.playlist(key, e.name, key, new ArrayList<File>(e.tracks)));
            }
        }
        if (deezer != null) {
            for (DeezerPlaylist p : deezer) {
                if (p == null) continue;
                String key = "deezer:" + p.id;
                out.add(FlowItem.playlist(key, p.title, key, Collections.<File>emptyList()));
            }
        }
        Collections.sort(out, new Comparator<FlowItem>() {
            @Override
            public int compare(FlowItem a, FlowItem b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        return out;
    }

    public static List<FlowItem> buildPodcasts(List<OpenRssClient.Podcast> shows) {
        if (shows == null || shows.isEmpty()) return Collections.emptyList();
        List<FlowItem> out = new ArrayList<FlowItem>();
        Set<String> seen = new HashSet<String>();
        for (OpenRssClient.Podcast p : shows) {
            if (p == null || p.feedUrl == null || p.feedUrl.isEmpty()) continue;
            String key = podcastMatchKey(p.feedUrl);
            if (!seen.add(key)) continue;
            String sub = p.publisher != null ? p.publisher : "";
            out.add(FlowItem.podcast(p.feedUrl, p.title, sub, p.artworkUrl));
        }
        return out;
    }

    /** Tracks for album item (sorted). Uses matchKey artist|album when present. */
    public static List<File> tracksForAlbum(FlowItem item, List<SongRow> library,
            LibraryBrowsePrefs prefs) {
        if (item == null || library == null) return Collections.emptyList();
        if (item.tracks != null && !item.tracks.isEmpty()) {
            return sortTracks(new ArrayList<File>(item.tracks), prefs, library);
        }
        String artistFilter = null;
        int pipe = item.matchKey != null ? item.matchKey.indexOf('|') : -1;
        if (pipe > 0 && pipe + 1 < item.matchKey.length()) {
            artistFilter = item.matchKey.substring(pipe + 1);
        }
        List<File> out = new ArrayList<File>();
        for (SongRow song : library) {
            if (!AlbumNames.equals(song.album, item.title)) continue;
            if (artistFilter != null && !artistFilter.isEmpty()
                    && !ArtistParser.containsArtist(song.artist, artistFilter)) {
                continue;
            }
            if (song.file != null && song.file.isFile()) out.add(song.file);
        }
        return sortTracks(out, prefs, library);
    }

    /** Tracks for artist — respects guest browse policy. */
    public static List<File> tracksForArtist(String artist, List<SongRow> library,
            LibraryBrowsePrefs prefs, List<ArtistBrowsePolicy.Track> policyTracks) {
        if (artist == null || library == null) return Collections.emptyList();
        List<File> out = new ArrayList<File>();
        for (SongRow song : library) {
            if (ArtistParser.containsArtist(song.artist, artist)
                    && song.file != null && song.file.isFile()) {
                out.add(song.file);
            }
        }
        return sortTracks(out, prefs, library);
    }

    /** Album sub-items on artist flip-back. */
    public static List<FlowItem> albumsForArtist(String artist, List<SongRow> library,
            LibraryBrowsePrefs prefs, List<ArtistBrowsePolicy.Track> policyTracks) {
        if (ArtistBrowsePolicy.shouldSkipAlbumPicker(artist, prefs, policyTracks)) {
            return Collections.emptyList();
        }
        Map<String, List<File>> byAlbum = new HashMap<String, List<File>>();
        Map<String, String> display = new HashMap<String, String>();
        for (SongRow song : library) {
            if (!ArtistParser.containsArtist(song.artist, artist)) continue;
            if (song.album == null || song.album.trim().isEmpty()) continue;
            String album = song.album.trim();
            String key = AlbumNames.matchKey(album);
            if (!display.containsKey(key)) display.put(key, album);
            List<File> t = byAlbum.get(key);
            if (t == null) {
                t = new ArrayList<File>();
                byAlbum.put(key, t);
            }
            if (song.file != null && song.file.isFile()) t.add(song.file);
        }
        List<String> keys = new ArrayList<String>(display.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return display.get(a).compareToIgnoreCase(display.get(b));
            }
        });
        List<FlowItem> out = new ArrayList<FlowItem>();
        for (String key : keys) {
            String album = display.get(key);
            String sub = ArtistBrowsePolicy.albumBrowseSubtitle(album, artist, policyTracks, prefs);
            String match = FlowCoverResolver.albumMatchKey(album, artist);
            out.add(FlowItem.album(album, sub, match, sortTracks(byAlbum.get(key), prefs, library), artist));
        }
        return out;
    }

    private static List<File> sortTracks(List<File> tracks, LibraryBrowsePrefs prefs,
            List<SongRow> library) {
        if (tracks == null || tracks.size() <= 1) {
            return tracks != null ? tracks : Collections.<File>emptyList();
        }
        final Map<File, SongRow> byFile = new HashMap<File, SongRow>();
        if (library != null) {
            for (SongRow row : library) {
                if (row.file != null) byFile.put(row.file, row);
            }
        }
        final int sort = prefs != null ? prefs.songSort() : LibraryBrowsePrefs.SONG_SORT_TITLE;
        List<File> copy = new ArrayList<File>(tracks);
        Collections.sort(copy, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                SongRow sa = byFile.get(a);
                SongRow sb = byFile.get(b);
                if (sort == LibraryBrowsePrefs.SONG_SORT_DATE) {
                    return Long.signum(a.lastModified() - b.lastModified());
                }
                if (sort == LibraryBrowsePrefs.SONG_SORT_ALBUM
                        || sort == LibraryBrowsePrefs.SONG_SORT_ARTIST) {
                    int ta = sa != null ? sa.trackNumber : 0;
                    int tb = sb != null ? sb.trackNumber : 0;
                    if (ta > 0 && tb > 0 && ta != tb) return Integer.compare(ta, tb);
                }
                String la = sa != null && !sa.title.isEmpty() ? sa.title : trackLabel(a);
                String lb = sb != null && !sb.title.isEmpty() ? sb.title : trackLabel(b);
                return la.compareToIgnoreCase(lb);
            }
        });
        return copy;
    }

    /** Display label for a track file — ID3 title with optional track number prefix. */
    public static String trackDisplayLabel(File f, List<SongRow> library) {
        if (f == null) return "";
        SongRow row = findSongRow(f, library);
        String title = row != null && !row.title.isEmpty() ? row.title : trackLabel(f);
        if (row != null && row.trackNumber > 0) {
            return String.format(Locale.US, "%02d %s", row.trackNumber, title);
        }
        return title;
    }

    static SongRow findSongRow(File f, List<SongRow> library) {
        if (f == null || library == null) return null;
        for (SongRow row : library) {
            if (f.equals(row.file)) return row;
        }
        return null;
    }

    private static List<File> sortTracks(List<File> tracks, LibraryBrowsePrefs prefs) {
        return sortTracks(tracks, prefs, null);
    }

    private static String trackLabel(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

}
