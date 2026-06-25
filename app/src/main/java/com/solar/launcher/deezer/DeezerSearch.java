package com.solar.launcher.deezer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Public api.deezer.com search — no auth required. */
public final class DeezerSearch {
    private final DeezerClient client;

    public DeezerSearch(DeezerClient client) {
        this.client = client;
    }

    public List<DeezerResult> searchTracks(String query) throws IOException {
        return parseTrackSearch(searchPublic("search/track?q=" + encodeQuery(query)));
    }

    public List<DeezerArtist> searchArtists(String query) throws IOException {
        List<DeezerArtist> out = new ArrayList<DeezerArtist>();
        try {
            JSONObject root = new JSONObject(new String(
                    searchPublic("search/artist?q=" + encodeQuery(query)), utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                out.add(new DeezerArtist(
                        item.optLong("id", 0),
                        item.optString("name", ""),
                        item.optString("picture_small", "")));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Artist search failed");
        }
        return out;
    }

    public List<DeezerAlbum> listArtistAlbums(long artistId) throws IOException {
        List<DeezerAlbum> out = new ArrayList<DeezerAlbum>();
        if (artistId <= 0) return out;
        try {
            JSONObject root = new JSONObject(new String(
                    searchPublic("artist/" + artistId + "/albums"), utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                out.add(new DeezerAlbum(
                        item.optLong("id", 0),
                        item.optString("title", ""),
                        item.optString("record_type", "album"),
                        item.optInt("nb_tracks", 0),
                        albumListCover(item)));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Album list failed");
        }
        return out;
    }

    public List<DeezerResult> listAlbumTracks(long albumId) throws IOException {
        List<DeezerResult> out = new ArrayList<DeezerResult>();
        if (albumId <= 0) return out;
        String albumTitle = "";
        long albumIdResolved = albumId;
        String cover = "";
        String artistName = "";
        try {
            JSONObject albumRoot = new JSONObject(new String(searchPublic("album/" + albumId), utf8()));
            albumTitle = albumRoot.optString("title", "");
            albumIdResolved = albumRoot.optLong("id", albumId);
            cover = DeezerCoverArt.albumCoverFromJson(albumRoot);
            JSONObject artist = albumRoot.optJSONObject("artist");
            if (artist != null) artistName = artist.optString("name", "");
        } catch (Exception ignored) {}
        try {
            JSONObject root = new JSONObject(new String(
                    searchPublic("album/" + albumId + "/tracks"), utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject trackArtist = item.optJSONObject("artist");
                out.add(new DeezerResult(
                        item.optLong("id", 0),
                        item.optString("title", ""),
                        trackArtist != null ? trackArtist.optString("name", artistName) : artistName,
                        albumTitle,
                        albumIdResolved,
                        item.optInt("duration", 0),
                        item.optString("preview", ""),
                        cover));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Track list failed");
        }
        return out;
    }

    public List<DeezerResult> listPlaylistTracks(long playlistId) throws IOException {
        List<DeezerResult> out = new ArrayList<DeezerResult>();
        if (playlistId <= 0) return out;
        String playlistTitle = "";
        try {
            JSONObject plRoot = new JSONObject(new String(
                    searchPublic("playlist/" + playlistId), utf8()));
            playlistTitle = plRoot.optString("title", "");
        } catch (Exception ignored) {}
        try {
            JSONObject root = new JSONObject(new String(
                    searchPublic("playlist/" + playlistId + "/tracks"), utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject album = item.optJSONObject("album");
                JSONObject artist = item.optJSONObject("artist");
                out.add(new DeezerResult(
                        item.optLong("id", 0),
                        item.optString("title", ""),
                        artist != null ? artist.optString("name", "") : "",
                        album != null ? album.optString("title", playlistTitle) : playlistTitle,
                        album != null ? album.optLong("id", 0) : 0,
                        item.optInt("duration", 0),
                        item.optString("preview", ""),
                        album != null ? DeezerCoverArt.albumCoverFromJson(album) : ""));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Playlist tracks failed");
        }
        return out;
    }

    public List<DeezerPlaylist> listUserPlaylists(long userId) throws IOException {
        List<DeezerPlaylist> out = new ArrayList<DeezerPlaylist>();
        if (userId <= 0) return out;
        try {
            JSONObject root = new JSONObject(new String(
                    searchPublic("user/" + userId + "/playlists"), utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                out.add(new DeezerPlaylist(
                        item.optLong("id", 0),
                        item.optString("title", ""),
                        item.optInt("nb_tracks", 0),
                        item.optString("picture_small", "")));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Playlist list failed");
        }
        return out;
    }

    private List<DeezerResult> parseTrackSearch(byte[] body) throws IOException {
        List<DeezerResult> out = new ArrayList<DeezerResult>();
        try {
            JSONObject root = new JSONObject(new String(body, utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject album = item.optJSONObject("album");
                JSONObject artist = item.optJSONObject("artist");
                out.add(new DeezerResult(
                        item.optLong("id", 0),
                        item.optString("title", ""),
                        artist != null ? artist.optString("name", "") : "",
                        album != null ? album.optString("title", "") : "",
                        album != null ? album.optLong("id", 0) : 0,
                        item.optInt("duration", 0),
                        item.optString("preview", ""),
                        DeezerCoverArt.albumCoverFromJson(album)));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Search failed");
        }
        return out;
    }

    private static String albumListCover(JSONObject item) {
        if (item == null) return "";
        String[] keys = {"cover_xl", "cover_big", "cover_medium", "cover", "cover_small"};
        for (String key : keys) {
            String url = item.optString(key, "").trim();
            if (!url.isEmpty()) return DeezerCoverArt.bestCoverUrl(url);
        }
        return "";
    }

    private byte[] searchPublic(String pathAndQuery) throws IOException {
        String url = pathAndQuery.startsWith("http") ? pathAndQuery
                : "https://api.deezer.com/" + pathAndQuery;
        return client.getPublic(url);
    }

    private static String encodeQuery(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) return "";
        try {
            return URLEncoder.encode(query.trim(), "UTF-8");
        } catch (Exception e) {
            return query.trim();
        }
    }

    public List<DeezerPodcastShow> searchPodcasts(String query) throws IOException {
        List<DeezerPodcastShow> out = new ArrayList<DeezerPodcastShow>();
        if (query == null || query.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(new String(
                    searchPublic("search/podcast?q=" + encodeQuery(query)), utf8()));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                out.add(new DeezerPodcastShow(
                        item.optLong("id", 0),
                        item.optString("title", ""),
                        item.optString("description", ""),
                        item.optString("picture_medium", "")));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Podcast search failed");
        }
        return out;
    }

    private static java.nio.charset.Charset utf8() {
        try {
            return java.nio.charset.Charset.forName("UTF-8");
        } catch (Exception e) {
            return java.nio.charset.Charset.defaultCharset();
        }
    }

    public static final class DeezerArtist {
        public final long id;
        public final String name;
        public final String pictureUrl;

        public DeezerArtist(long id, String name, String pictureUrl) {
            this.id = id;
            this.name = name != null ? name : "";
            this.pictureUrl = pictureUrl != null ? pictureUrl : "";
        }
    }

    public static final class DeezerAlbum {
        public final long id;
        public final String title;
        public final String recordType;
        public final int trackCount;
        public final String coverUrl;

        public DeezerAlbum(long id, String title, String recordType, int trackCount, String coverUrl) {
            this.id = id;
            this.title = title != null ? title : "";
            this.recordType = recordType != null ? recordType : "album";
            this.trackCount = trackCount;
            this.coverUrl = coverUrl != null ? coverUrl : "";
        }
    }

    public static final class DeezerPodcastShow {
        public final long id;
        public final String title;
        public final String description;
        public final String pictureUrl;

        public DeezerPodcastShow(long id, String title, String description, String pictureUrl) {
            this.id = id;
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.pictureUrl = pictureUrl != null ? pictureUrl : "";
        }
    }
}
