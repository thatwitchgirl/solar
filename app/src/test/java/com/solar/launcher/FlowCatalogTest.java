package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;
import com.solar.launcher.flow.FlowItem;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowCatalogTest {

    private LibraryBrowsePrefs prefs;
    private List<ArtistBrowsePolicy.Track> tracks;

    @Before
    public void setUp() {
        prefs = new LibraryBrowsePrefs(new LibraryBrowsePrefsTest.MemSharedPreferences());
        tracks = Arrays.asList(
                new ArtistBrowsePolicy.Track("Dr. Dre feat. Snoop Dogg", "2001", "Dr. Dre", 100L),
                new ArtistBrowsePolicy.Track("Snoop Dogg", "Doggystyle", "Snoop Dogg", 300L));
    }

    @Test
    public void artistOrderMatchesPolicy() {
        List<FlowItem> flow = FlowCatalog.buildArtists(tracks, prefs);
        List<String> policy = ArtistBrowsePolicy.collectArtists(tracks, prefs);
        assertEquals(policy.size(), flow.size());
        for (int i = 0; i < policy.size(); i++) {
            assertEquals(policy.get(i), flow.get(i).title);
        }
    }

    @Test
    public void albumsFromLibraryRows() {
        File f = new File("/tmp/a.mp3");
        List<FlowCatalog.SongRow> rows = Arrays.asList(
                new FlowCatalog.SongRow(f, "t", "Snoop Dogg", "Doggystyle", "Snoop Dogg", 1L));
        List<FlowItem> albums = FlowCatalog.buildAlbums(rows, prefs, tracks);
        assertFalse(albums.isEmpty());
        assertEquals("Doggystyle", albums.get(0).title);
    }

    @Test
    public void artlessAlbumIncludedInCatalog() throws IOException {
        File f = File.createTempFile("flow_noart", ".mp3");
        f.deleteOnExit();
        List<FlowCatalog.SongRow> rows = Arrays.asList(
                new FlowCatalog.SongRow(f, "Track", "Band", "No Art Album", "Band", 1L));
        List<FlowItem> albums = FlowCatalog.buildAlbums(rows, prefs, tracks);
        assertEquals(1, albums.size());
        assertEquals("No Art Album", albums.get(0).title);
        assertFalse(albums.get(0).coverKey.isEmpty());
        assertEquals(1, albums.get(0).tracks.size());
    }

    @Test
    public void sortTracksByTrackNumberThenTitle() throws IOException {
        File f1 = File.createTempFile("flow_sort_03", ".mp3");
        File f2 = File.createTempFile("flow_sort_01", ".mp3");
        File f3 = File.createTempFile("flow_sort_02", ".mp3");
        f1.deleteOnExit();
        f2.deleteOnExit();
        f3.deleteOnExit();
        List<FlowCatalog.SongRow> library = Arrays.asList(
                new FlowCatalog.SongRow(f1, "Three", "Artist", "Album", "Artist", 1L, 3),
                new FlowCatalog.SongRow(f2, "One", "Artist", "Album", "Artist", 1L, 1),
                new FlowCatalog.SongRow(f3, "Two", "Artist", "Album", "Artist", 1L, 2));
        LibraryBrowsePrefsTest.MemSharedPreferences mem =
                new LibraryBrowsePrefsTest.MemSharedPreferences();
        mem.edit().putInt("lib_song_sort", LibraryBrowsePrefs.SONG_SORT_ALBUM).commit();
        LibraryBrowsePrefs albumSortPrefs = new LibraryBrowsePrefs(mem);
        List<FlowItem> albums = FlowCatalog.buildAlbums(library, albumSortPrefs, tracks);
        assertEquals(1, albums.size());
        List<File> sorted = albums.get(0).tracks;
        assertEquals(3, sorted.size());
        assertEquals(f2, sorted.get(0));
        assertEquals(f3, sorted.get(1));
        assertEquals(f1, sorted.get(2));
    }

    @Test
    public void multiTrackOnlySkipsSingleTrackAlbums() throws IOException {
        File f1 = File.createTempFile("flow_solo", ".mp3");
        File f2 = File.createTempFile("flow_full_a", ".mp3");
        File f3 = File.createTempFile("flow_full_b", ".mp3");
        f1.deleteOnExit();
        f2.deleteOnExit();
        f3.deleteOnExit();
        List<FlowCatalog.SongRow> rows = Arrays.asList(
                new FlowCatalog.SongRow(f1, "Solo", "Artist", "Solo Album", "Artist", 1L),
                new FlowCatalog.SongRow(f2, "One", "Band", "Full Album", "Band", 1L),
                new FlowCatalog.SongRow(f3, "Two", "Band", "Full Album", "Band", 1L));
        List<FlowItem> all = FlowCatalog.buildAlbums(rows, prefs, tracks, false);
        assertEquals(2, all.size());
        List<FlowItem> filtered = FlowCatalog.buildAlbums(rows, prefs, tracks, true);
        assertEquals(1, filtered.size());
        assertEquals("Full Album", filtered.get(0).title);
    }

    @Test
    public void primaryArtistFromVotesPicksDominantTag() {
        java.util.Map<String, Integer> votes = new java.util.HashMap<String, Integer>();
        votes.put("Artist A", 3);
        votes.put("Artist B", 1);
        assertEquals("Artist A", FlowCatalog.primaryArtistFromVotes(votes, "Artist B"));
    }

    @Test
    public void multiTrackOnlyFiltersSingletonAlbums() throws IOException {
        File f1 = File.createTempFile("flow_mt_1", ".mp3");
        File f2 = File.createTempFile("flow_mt_2", ".mp3");
        f1.deleteOnExit();
        f2.deleteOnExit();
        List<FlowCatalog.SongRow> rows = Arrays.asList(
                new FlowCatalog.SongRow(f1, "a", "Artist", "Solo", "Artist", 1L),
                new FlowCatalog.SongRow(f2, "b", "Artist", "Duo", "Artist", 1L),
                new FlowCatalog.SongRow(f2, "c", "Artist", "Duo", "Artist", 1L));
        List<FlowItem> all = FlowCatalog.buildAlbums(rows, prefs, tracks, false);
        assertEquals(2, all.size());
        List<FlowItem> multi = FlowCatalog.buildAlbums(rows, prefs, tracks, true);
        assertEquals(1, multi.size());
        assertEquals("Duo", multi.get(0).title);
    }

    @Test
    public void albumArtistUsesDominantTag() throws IOException {
        File f1 = File.createTempFile("flow_dom_1", ".mp3");
        File f2 = File.createTempFile("flow_dom_2", ".mp3");
        File f3 = File.createTempFile("flow_dom_3", ".mp3");
        f1.deleteOnExit();
        f2.deleteOnExit();
        f3.deleteOnExit();
        List<FlowCatalog.SongRow> rows = Arrays.asList(
                new FlowCatalog.SongRow(f1, "t1", "Feat Artist", "Shared", "Shared", 1L),
                new FlowCatalog.SongRow(f2, "t2", "Main Artist", "Shared", "Shared", 1L),
                new FlowCatalog.SongRow(f3, "t3", "Main Artist", "Shared", "Shared", 1L));
        List<FlowItem> albums = FlowCatalog.buildAlbums(rows, prefs, tracks);
        assertEquals(1, albums.size());
        assertTrue(albums.get(0).coverKey.toLowerCase(java.util.Locale.US).contains("main artist"));
    }

    @Test
    public void trackDisplayLabelUsesId3TitleAndNumber() {
        File f = new File("/tmp/whatever.mp3");
        List<FlowCatalog.SongRow> library = Arrays.asList(
                new FlowCatalog.SongRow(f, "Bad Guy", "Billie Eilish", "Album", "Artist", 1L, 5));
        assertEquals("05 Bad Guy", FlowCatalog.trackDisplayLabel(f, library));
    }
}
