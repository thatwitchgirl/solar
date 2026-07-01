package com.solar.launcher.flow;

import android.graphics.RectF;

import com.solar.launcher.ArtistBrowsePolicy;
import com.solar.launcher.LibraryBrowsePrefs;
import com.solar.launcher.deezer.DeezerPlaylist;
import com.solar.launcher.podcast.OpenRssClient;

import java.io.File;
import java.util.List;

/**
 * Maps a playing track + {@link FlowPlaybackOrigin} to a Flow carousel return pose.
 * ponytail: catalog build + key lookup only; caller measures handoffReturnRect after layout.
 */
public final class FlowReturnResolver {

    public static final class Resolved {
        public final FlowReturnState state;
        public final int carouselIndex;
        /** Built or session-cached catalog — avoids second build in {@code applyReturnState}. */
        public final List<FlowItem> catalog;

        Resolved(FlowReturnState state, int carouselIndex, List<FlowItem> catalog) {
            this.state = state;
            this.carouselIndex = carouselIndex;
            this.catalog = catalog;
        }
    }

    public interface Host {
        List<FlowCatalog.SongRow> libraryRows();
        LibraryBrowsePrefs libraryBrowsePrefs();
        List<ArtistBrowsePolicy.Track> policyTracks();
        boolean flowMultiTrackAlbumsOnly();
        File musicRoot();
        List<DeezerPlaylist> deezerPlaylists();
        List<OpenRssClient.Podcast> podcastShows();
        /** Now Playing 3D album slot in screen space. */
        RectF playerHandoffScreenRect();
    }

    private FlowReturnResolver() {}

    /** @return null when track has no matching Flow rack item. */
    public static Resolved resolve(Host host, FlowPlaybackOrigin origin) {
        return resolve(host, origin, null);
    }

    /**
     * @param catalogHint session-precooked list — avoids full catalog build on handoff hot path.
     * @return null when track has no matching Flow rack item.
     */
    public static Resolved resolve(Host host, FlowPlaybackOrigin origin, List<FlowItem> catalogHint) {
        if (host == null || origin == null || origin.kind == FlowPlaybackOrigin.Kind.FLOW_FLIP) {
            return null;
        }
        String matchKey = origin.carouselMatchKey;
        if (matchKey == null || matchKey.isEmpty()) return null;

        // #region agent log
        long resolveStartMs = System.currentTimeMillis();
        // #endregion
        List<FlowItem> catalog = catalogHint;
        if (catalog == null || catalog.isEmpty()) {
            // ponytail: ALBUM rack only for local library returns — skip deezer/podcast build.
            if (origin.flowMode == FlowMode.ALBUM) {
                catalog = FlowCatalog.buildAlbums(host.libraryRows(), host.libraryBrowsePrefs(),
                        host.policyTracks(), host.flowMultiTrackAlbumsOnly());
            } else {
                catalog = FlowCatalog.build(origin.flowMode, host.libraryRows(),
                        host.libraryBrowsePrefs(), host.policyTracks(), host.musicRoot(),
                        host.deezerPlaylists(), host.podcastShows());
            }
        }
        if (catalog.isEmpty()) return null;

        FlowEngine engine = new FlowEngine();
        int index = engine.findIndexForKey(catalog, matchKey);
        if (index < 0) return null;

        RectF playerRect = host.playerHandoffScreenRect();
        if (playerRect == null || playerRect.width() <= 0f) return null;

        FlowReturnState state = new FlowReturnState(
                origin.flowMode,
                matchKey,
                index,
                null,
                playerRect,
                FlowAlbumArt3d.PLAYER_ROT_Y_DEG,
                null,
                0f);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("durationMs", System.currentTimeMillis() - resolveStartMs);
            d.put("catalogSize", catalog.size());
            d.put("cacheHit", catalogHint != null && !catalogHint.isEmpty());
            d.put("index", index);
            d.put("kind", origin.kind.name());
            com.solar.launcher.DebugSessionLog.log(
                    "FlowReturnResolver.resolve", "resolve done", "H1-H5", d);
        } catch (Exception ignored) {}
        // #endregion
        return new Resolved(state, index, catalog);
    }
}
