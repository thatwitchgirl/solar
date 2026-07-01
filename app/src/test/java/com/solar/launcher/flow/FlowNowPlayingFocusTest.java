package com.solar.launcher.flow;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlowNowPlayingFocusTest {

    @Test
    public void catalogAlignedKeyFallsBackToProbeWhenAlbumMissingFromRack() {
        List<FlowItem> items = Collections.singletonList(
                FlowItem.album("Other", "", "other|band",
                        Collections.<File>emptyList(), ""));
        String aligned = FlowNowPlayingFocus.catalogAlignedMatchKey("Missing", "Artist", items);
        assertEquals(FlowNowPlayingFocus.probeMatchKey("Missing", "Artist"), aligned);
        assertEquals(-1, FlowNowPlayingFocus.carouselIndexForMatchKey(items, aligned));
    }

    @Test
    public void catalogAlignedKeyUsesDominantArtist() {
        File f1 = new File("/tmp/a.mp3");
        File f2 = new File("/tmp/b.mp3");
        File f3 = new File("/tmp/c.mp3");
        List<FlowCatalog.SongRow> rows = Arrays.asList(
                new FlowCatalog.SongRow(f1, "t1", "Feat Artist", "Shared", "Shared", 1L),
                new FlowCatalog.SongRow(f2, "t2", "Main Artist", "Shared", "Shared", 1L),
                new FlowCatalog.SongRow(f3, "t3", "Main Artist", "Shared", "Shared", 1L));
        List<FlowItem> catalog = FlowCatalog.buildAlbums(rows, null, Collections.<com.solar.launcher.ArtistBrowsePolicy.Track>emptyList());
        assertEquals(1, catalog.size());
        String catalogKey = catalog.get(0).matchKey;
        // Playing track tagged with guest artist still lands on catalog rack item.
        String aligned = FlowNowPlayingFocus.catalogAlignedMatchKey("Shared", "Feat Artist", catalog);
        assertEquals(catalogKey, aligned);
        assertEquals(0, FlowNowPlayingFocus.carouselIndexForMatchKey(catalog, aligned));
    }

    @Test
    public void probeKeyFuzzyResolvesToCatalogIndex() {
        List<FlowItem> items = Arrays.asList(
                FlowItem.album("OK Computer", "", "ok computer|radiohead",
                        Collections.<File>emptyList(), ""),
                FlowItem.album("Other", "", "other|band",
                        Collections.<File>emptyList(), ""));
        String probe = FlowNowPlayingFocus.probeMatchKey("OK Computer", "Radiohead");
        assertEquals(0, FlowNowPlayingFocus.carouselIndexForMatchKey(items, probe));
        assertTrue(probe.toLowerCase(java.util.Locale.US).contains("radiohead"));
    }
}
