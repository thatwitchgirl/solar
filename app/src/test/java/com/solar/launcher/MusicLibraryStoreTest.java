package com.solar.launcher;

import org.junit.Test;

import java.util.Locale;

/** JVM-safe checks for music library cache helpers. */
public class MusicLibraryStoreTest {

    @Test
    public void durationSecFromMsHelper() {
        if (MusicLibraryStore.durationSecFromMs("180000") != 180) {
            throw new AssertionError("durationSecFromMs");
        }
        if (MusicLibraryStore.durationSecFromMs("") != 0) {
            throw new AssertionError("empty duration");
        }
    }

    @Test
    public void durationSecFromMs() {
        MusicLibraryStore.Track t = new MusicLibraryStore.Track(
                "/music/a.mp3", 1L, 100L, "T", "A", "Al", "G", "AA", "180000");
        if (t.durationSec() != 180) throw new AssertionError("durationSec=" + t.durationSec());
        MusicLibraryStore.Track empty = new MusicLibraryStore.Track(
                "/b.mp3", 0L, 0L, "", "", "", "", "", "");
        if (empty.durationSec() != 0) throw new AssertionError("expected zero duration");
    }

    @Test
    public void normPathLowerCases() {
        String norm = MusicLibraryStore.normPath("/Storage/SD/Music/Song.MP3");
        if (!"/storage/sd/music/song.mp3".equals(norm)) {
            throw new AssertionError("norm=" + norm);
        }
        if (!"".equals(MusicLibraryStore.normPath(null))) throw new AssertionError("null norm");
        if (!Locale.US.equals(Locale.US)) { /* keep javac happy */ }
    }
}
