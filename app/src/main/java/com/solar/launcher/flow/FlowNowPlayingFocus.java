package com.solar.launcher.flow;

import java.util.List;

/**
 * Map active queue album/artist to Flow carousel match keys — dominant-artist catalog alignment.
 */
public final class FlowNowPlayingFocus {

    private FlowNowPlayingFocus() {}

    /** Per-track probe key before catalog alignment. */
    public static String probeMatchKey(String album, String artist) {
        if (album == null || album.trim().isEmpty()) return "";
        return FlowCoverResolver.albumMatchKey(album.trim(), artist != null ? artist : "");
    }

    /**
     * Catalog item key for the playing album — uses dominant artist from {@link FlowCatalog}.
     * Falls back to probe when the album is not in the rack yet.
     */
    public static String catalogAlignedMatchKey(String album, String artist, List<FlowItem> catalog) {
        String probe = probeMatchKey(album, artist);
        if (probe.isEmpty() || catalog == null || catalog.isEmpty()) return probe;
        int index = carouselIndexForMatchKey(catalog, probe);
        if (index < 0 || index >= catalog.size()) return probe;
        FlowItem item = catalog.get(index);
        if (item != null && item.matchKey != null && !item.matchKey.isEmpty()) {
            return item.matchKey;
        }
        return probe;
    }

    /** Carousel index for a match key, or -1 when not in catalog. */
    public static int carouselIndexForMatchKey(List<FlowItem> catalog, String matchKey) {
        if (catalog == null || catalog.isEmpty() || matchKey == null || matchKey.isEmpty()) {
            return -1;
        }
        return new FlowEngine().findIndexForKey(catalog, matchKey);
    }
}
