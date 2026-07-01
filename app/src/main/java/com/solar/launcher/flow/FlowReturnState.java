package com.solar.launcher.flow;

import android.graphics.RectF;

/**
 * Snapshot of Flow carousel when playback starts — restored on Back from Now Playing.
 */
public final class FlowReturnState {

    public final FlowMode mode;
    public final String carouselMatchKey;
    public final int carouselIndex;
    /** Kept for Center-OK re-flip; not auto-restored on Back from player. */
    public final FlowFlipController.Snapshot flipSnapshot;
    /** Forward handoff start pose (may be back-face encroach when flipped). */
    public final RectF handoffFromRect;
    public final float handoffFromRotY;
    /** Reverse handoff landing — front-center cover at 0°. */
    public final RectF handoffReturnRect;
    public final float handoffReturnRotY;

    public FlowReturnState(FlowMode mode, String carouselMatchKey, int carouselIndex,
            FlowFlipController.Snapshot flipSnapshot, RectF handoffFromRect, float handoffFromRotY,
            RectF handoffReturnRect, float handoffReturnRotY) {
        this.mode = mode != null ? mode : FlowMode.UNSPECIFIED;
        this.carouselMatchKey = carouselMatchKey;
        this.carouselIndex = carouselIndex;
        this.flipSnapshot = flipSnapshot;
        this.handoffFromRect = handoffFromRect != null ? new RectF(handoffFromRect) : null;
        this.handoffFromRotY = handoffFromRotY;
        this.handoffReturnRect = handoffReturnRect != null ? new RectF(handoffReturnRect) : null;
        this.handoffReturnRotY = handoffReturnRotY;
    }
}
