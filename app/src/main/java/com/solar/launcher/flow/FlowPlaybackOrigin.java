package com.solar.launcher.flow;

/**
 * Snapshot of where playback started — drives Now Playing → Flow reverse handoff routing.
 */
public final class FlowPlaybackOrigin {

    public enum Kind {
        /** Flow flip tracklist play — {@link FlowScreenHost#capturePlayerReturn} handles return. */
        FLOW_FLIP,
        /** Library All Songs — return to album rack item in Flow. */
        LIBRARY_ALL_SONGS,
        /** Library album / artist-album browse — return to that album in Flow. */
        LIBRARY_ALBUM,
        /** Library playlist — return to playlist item in Flow. */
        LIBRARY_PLAYLIST
    }

    public final Kind kind;
    public final FlowMode flowMode;
    /** {@link FlowCoverResolver#albumMatchKey} or {@code local:} + playlist path. */
    public final String carouselMatchKey;
    public final int browserReturnScreen;
    public final int browserMode;
    /** Restore library drill-down on second Back from Flow. */
    public final String virtualQueryType;
    public final String virtualQueryValue;

    public FlowPlaybackOrigin(Kind kind, FlowMode flowMode, String carouselMatchKey,
            int browserReturnScreen, int browserMode, String virtualQueryType,
            String virtualQueryValue) {
        this.kind = kind != null ? kind : Kind.FLOW_FLIP;
        this.flowMode = flowMode != null ? flowMode : FlowMode.ALBUM;
        this.carouselMatchKey = carouselMatchKey;
        this.browserReturnScreen = browserReturnScreen;
        this.browserMode = browserMode;
        this.virtualQueryType = virtualQueryType != null ? virtualQueryType : "";
        this.virtualQueryValue = virtualQueryValue != null ? virtualQueryValue : "";
    }
}
