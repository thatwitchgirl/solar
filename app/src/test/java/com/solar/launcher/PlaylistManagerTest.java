package com.solar.launcher;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlaylistManagerTest {
    @Test
    public void parseM3u_resolvesRelativePaths() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "solar_pl_test");
        dir.mkdirs();
        File track = new File(dir, "a.mp3");
        if (!track.exists()) track.createNewFile();
        File m3u = new File(dir, "test.m3u");
        java.io.FileWriter fw = new java.io.FileWriter(m3u);
        fw.write("#EXTM3U\n#EXTINF:-1,Track A\na.mp3\n");
        fw.close();
        PlaylistManager.Entry e = PlaylistManager.parse(m3u, dir);
        if (e.tracks.size() != 1) throw new AssertionError("track count");
        track.delete();
        m3u.delete();
        dir.delete();
    }

    @Test
    public void safeFileName_stripsInvalidChars() {
        if (!"My Mix".equals(PlaylistManager.safeFileName("My Mix"))) throw new AssertionError("trim");
        if (!"Playlist".equals(PlaylistManager.safeFileName("  "))) throw new AssertionError("empty");
        if (!"a_b".equals(PlaylistManager.safeFileName("a/b"))) throw new AssertionError("slash");
    }

    @Test
    public void appendTracks_dedupesAndPreservesOrder() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "solar_pl_append");
        File plDir = new File(root, "Playlists");
        plDir.mkdirs();
        File t1 = new File(root, "one.mp3");
        File t2 = new File(root, "two.mp3");
        t1.createNewFile();
        t2.createNewFile();
        List<File> initial = new ArrayList<File>();
        initial.add(t1);
        PlaylistManager.Entry created = PlaylistManager.createPlaylist(root, "Test", initial);
        PlaylistManager.appendTracks(created.sourceFile, root, initial);
        PlaylistManager.Entry after = PlaylistManager.parse(created.sourceFile, root);
        if (after.tracks.size() != 1) throw new AssertionError("dedupe");
        List<File> add = new ArrayList<File>();
        add.add(t1);
        add.add(t2);
        PlaylistManager.appendTracks(created.sourceFile, root, add);
        after = PlaylistManager.parse(created.sourceFile, root);
        if (after.tracks.size() != 2) throw new AssertionError("append count");
        t1.delete();
        t2.delete();
        created.sourceFile.delete();
        plDir.delete();
        root.delete();
    }

    @Test
    public void scan_includesEmptyPlaylist() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "solar_pl_empty");
        root.mkdirs();
        PlaylistManager.createPlaylist(root, "Empty Shell", new ArrayList<File>());
        List<PlaylistManager.Entry> found = PlaylistManager.scan(root);
        boolean hasEmpty = false;
        for (PlaylistManager.Entry e : found) {
            if ("Empty Shell".equals(e.name) && e.tracks.isEmpty()) hasEmpty = true;
        }
        File plDir = PlaylistManager.playlistsDir(root);
        File[] leftovers = plDir.listFiles();
        if (leftovers != null) {
            for (File f : leftovers) f.delete();
        }
        plDir.delete();
        root.delete();
        if (!hasEmpty) throw new AssertionError("empty playlist not scanned");
    }
}
