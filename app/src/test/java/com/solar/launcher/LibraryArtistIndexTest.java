package com.solar.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LibraryArtistIndexTest {

    @Test
    public void rebuildDedupesCase() {
        LibraryArtistIndex index = new LibraryArtistIndex();
        List<LibraryArtistIndex.Track> tracks = new ArrayList<LibraryArtistIndex.Track>();
        tracks.add(new LibraryArtistIndex.Track("skepta", ""));
        tracks.add(new LibraryArtistIndex.Track("Skepta", ""));
        index.rebuild(tracks, false);
        if (index.allArtists().size() != 1) {
            throw new AssertionError("expected 1 artist, got " + index.allArtists().size());
        }
    }

    @Test
    public void collabTrackMatchesBothArtists() {
        LibraryArtistIndex index = new LibraryArtistIndex();
        List<LibraryArtistIndex.Track> tracks = new ArrayList<LibraryArtistIndex.Track>();
        tracks.add(new LibraryArtistIndex.Track("Skepta; Yung Gravy", ""));
        index.rebuild(tracks, false);
        LibraryArtistIndex.Track t = tracks.get(0);
        if (!index.trackMatchesArtist(t, "Skepta")) throw new AssertionError("Skepta");
        if (!index.trackMatchesArtist(t, "Yung Gravy")) throw new AssertionError("Yung Gravy");
    }

    @Test
    public void primaryOnlyHidesFeatured() {
        LibraryArtistIndex index = new LibraryArtistIndex();
        List<LibraryArtistIndex.Track> tracks = new ArrayList<LibraryArtistIndex.Track>();
        tracks.add(new LibraryArtistIndex.Track("Snoop Dogg feat. Dr. Dre", ""));
        index.rebuild(tracks, true);
        LibraryArtistIndex.Track t = tracks.get(0);
        if (!index.trackMatchesArtist(t, "Snoop Dogg")) throw new AssertionError("primary");
        if (index.trackMatchesArtist(t, "Dr. Dre")) {
            throw new AssertionError("featured should not match in primary-only mode");
        }
    }

    @Test
    public void coOccurrenceRanking() {
        LibraryArtistIndex index = new LibraryArtistIndex();
        List<LibraryArtistIndex.Track> tracks = new ArrayList<LibraryArtistIndex.Track>();
        tracks.add(new LibraryArtistIndex.Track("A; B", ""));
        tracks.add(new LibraryArtistIndex.Track("A; B", ""));
        tracks.add(new LibraryArtistIndex.Track("A; C", ""));
        index.rebuild(tracks, false);
        List<String> collabs = index.topCollaborators("A", 2);
        if (collabs.isEmpty()) throw new AssertionError("no collabs");
        if (!"B".equals(collabs.get(0))) throw new AssertionError("expected B first, got " + collabs);
    }
}
