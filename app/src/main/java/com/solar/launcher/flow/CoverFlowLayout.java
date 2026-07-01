package com.solar.launcher.flow;

/**
 * Apple / Classipod Cover Flow layout — 45° square covers, glossy floor reflection.
 * Scroll physics stay in {@link PictureFlowLayout#rockboxScrollSpeed}; this is pose + metrics only.
 */
public final class CoverFlowLayout {

    public static final int IANGLE_MAX = 1024;
    /** Rockbox default — idle shows ranks 2,1,0 per side + center (~7 visible). */
    public static final int SIDE_SLIDES = 4;
    /** ±70° side tilt — Rockbox / Classipod tuck; less horizontal overlap than 45°. */
    public static final int ITILT = 70 * IANGLE_MAX / 360;
    /** Flipped tracklist card — iPod Classic: ~90% tall, ~84% wide, bottom flush with floor.
     *  Tune against local {@code /reference} Classipod on 480×360 if side margins look off. */
    public static final float BACK_FACE_HEIGHT_FRAC = 0.90f;
    public static final float BACK_FACE_WIDTH_FRAC = 0.84f;
    /** South margin below card bottom (dp) — small gap above screen edge / transport chrome. */
    public static final float BACK_FACE_BOTTOM_MARGIN_DP = 4f;

    private CoverFlowLayout() {}

    public static final class Metrics {
        public final float viewW;
        public final float viewH;
        /** Square cover edge in px. */
        public final float displaySize;
        public final float reflectTop;
        public final float reflectHeight;
        public final float slideSpacing;
        public final float offsetX;
        public final float offsetY;
        public final float camDist;
        public final float centerY;
        public final int[] reflectTable;

        Metrics(float viewW, float viewH, float displaySize, float reflectTop, float reflectHeight,
                float slideSpacing, float offsetX, float offsetY, float camDist, float centerY,
                int[] reflectTable) {
            this.viewW = viewW;
            this.viewH = viewH;
            this.displaySize = displaySize;
            this.reflectTop = reflectTop;
            this.reflectHeight = reflectHeight;
            this.slideSpacing = slideSpacing;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.camDist = camDist;
            this.centerY = centerY;
            this.reflectTable = reflectTable;
        }

        /** Decode size for carousel — slightly above on-screen cover, capped for MT6572 RAM. */
        public int thumbSizePx() {
            int px = (int) (displaySize * 1.12f);
            return Math.min(144, Math.max(96, px));
        }
    }

    /** Per-cover pose (compatible with Rockbox scroll animation in {@link FlowEngine}). */
    public static final class SlidePose {
        public int itemIndex = -1;
        public float cx;
        public float cy;
        public int angle;
        public int alpha = 256;
        public int drawBucket;
        public int sideRank;
    }

    public static Metrics metricsForViewport(float viewW, float viewH) {
        if (viewW <= 0f || viewH <= 0f) {
            return metricsForViewport(480f, 360f);
        }
        // Classic 2/3 art band, 1/3 reflection floor — tuned for 480×360 (no top clip).
        float reflectHeight = viewH / 3f;
        float reflectTop = viewH - reflectHeight;
        float displaySize = Math.min(viewW * 0.48f, viewH * 0.58f);
        float slideSpacing = displaySize / 4f;
        float centerMargin = (viewW - displaySize) / 12f;
        float camDist = Math.max(Math.min(viewW, viewH), 120f);
        float offsetX = PictureFlowLayout.recalcOffsetX(displaySize, centerMargin, 100f, camDist);
        float offsetY = displaySize * 0.04f;
        float centerY = viewH * 0.33f - viewH * 0.5f;
        int[] reflectTable = PictureFlowLayout.buildReflectTable(reflectHeight);
        return new Metrics(viewW, viewH, displaySize, reflectTop, reflectHeight,
                slideSpacing, offsetX, offsetY, camDist, centerY, reflectTable);
    }

    public static float angleToDegrees(int angleUnits) {
        return angleUnits * 360f / IANGLE_MAX;
    }

    /** How far toward center (0=side, 1=focused) — drives scale and paint order during scroll. */
    public static float centernessFromPose(SlidePose pose, Metrics m) {
        if (pose == null || m.offsetX <= 0f) return 0f;
        float fromCx = 1f - Math.min(1f, Math.abs(pose.cx) / m.offsetX);
        float fromAngle = 1f - Math.min(1f, Math.abs(pose.angle) / (float) ITILT);
        return Math.min(1f, Math.max(fromCx, fromAngle));
    }

    /** Map slide pose to screen transform for {@link FlowView}. */
    public static FlowEngine.SlotTransform toSlotTransform(SlidePose pose, Metrics m) {
        FlowEngine.SlotTransform t = new FlowEngine.SlotTransform();
        if (pose == null || pose.itemIndex < 0 || pose.alpha <= 0) {
            t.alpha = 0f;
            return t;
        }
        float angleRad = (float) Math.toRadians(angleToDegrees(pose.angle));
        float sinr = (float) Math.sin(angleRad);
        float zo = m.displaySize * 0.5f * Math.abs(sinr);
        float persp = m.camDist / (m.camDist + zo);
        float sideScale = 0.86f;
        float centerScale = 1.0f;
        float centerness = centernessFromPose(pose, m);
        float scale = persp * (sideScale + (centerScale - sideScale) * centerness);

        t.width = m.displaySize * scale;
        t.height = m.displaySize * scale;
        t.centerX = m.viewW * 0.5f + pose.cx;
        t.centerY = m.viewH * 0.5f + m.centerY + pose.cy;
        t.rotationYDeg = -angleToDegrees(pose.angle);
        t.zDepth = -zo;
        t.alpha = pose.alpha / 256f;
        // Continuous depth: focused cover advances with scroll, no end-of-scroll pop.
        t.depthOrder = depthOrderFromPose(pose, m);
        return t;
    }

    /** Paint-order key only — cheaper than full {@link #toSlotTransform} during sort. */
    public static float depthOrderFromPose(SlidePose pose, Metrics m) {
        if (pose == null || pose.itemIndex < 0 || pose.alpha <= 0) return Float.MAX_VALUE;
        float centerness = centernessFromPose(pose, m);
        return Math.abs(pose.cx) + centerness * 800f + pose.sideRank * 2f;
    }

    public static int reflectionAlpha(Metrics m, int row, float coverAlpha) {
        if (m.reflectTable == null || row < 0 || row >= m.reflectTable.length) return 0;
        int lalpha = m.reflectTable[row];
        return (lalpha * Math.round(coverAlpha * 256) + 129) >> 8;
    }

    /**
     * iPod Classic: once the flip passes midpoint, the back face grows and slides south so the
     * bottom edge nearly touches the content floor — "brought the record closer to read tracks."
     *
     * @param flipPastMid 0 at 90° (mid-flip), 1 when fully on back face
     * @param topInsetPx    status bar + small margin — card top stays below this
     * @param bottomMarginPx gap above physical bottom (dp→px)
     */
    public static void applyBackFaceSouthEncroach(FlowEngine.SlotTransform t, Metrics m,
            float flipPastMid, float topInsetPx, float bottomMarginPx) {
        if (t == null || m == null || flipPastMid <= 0f) return;
        float eased = flipPastMid * flipPastMid * (3f - 2f * flipPastMid);
        float contentTop = Math.max(0f, topInsetPx);
        float contentBottom = m.viewH - Math.max(0f, bottomMarginPx);
        float availableH = Math.max(0f, contentBottom - contentTop);
        float targetH = Math.min(m.viewH * BACK_FACE_HEIGHT_FRAC, availableH);
        float targetW = m.viewW * BACK_FACE_WIDTH_FRAC;
        float targetBottom = contentBottom;
        float targetCenterY = targetBottom - targetH * 0.5f;
        float targetTop = targetCenterY - targetH * 0.5f;
        if (targetTop < contentTop) {
            targetCenterY = contentTop + targetH * 0.5f;
        }
        t.centerY += (targetCenterY - t.centerY) * eased;
        t.width += (targetW - t.width) * eased;
        t.height += (targetH - t.height) * eased;
    }

    /**
     * Legacy 3D handoff path — square back face (unused; rectangle encroach is canonical).
     */
    public static void applySquareBackFaceSouthAnchor(FlowEngine.SlotTransform t, Metrics m,
            float flipPastMid, float topInsetPx, float bottomMarginPx) {
        if (t == null || m == null || flipPastMid <= 0f) return;
        float eased = flipPastMid * flipPastMid * (3f - 2f * flipPastMid);
        float contentTop = Math.max(0f, topInsetPx);
        float contentBottom = m.viewH - Math.max(0f, bottomMarginPx);
        float availableH = Math.max(0f, contentBottom - contentTop);
        float targetSize = Math.min(m.viewW * BACK_FACE_WIDTH_FRAC, availableH);
        targetSize = Math.min(targetSize, m.displaySize * 1.12f);
        float targetCenterY = contentBottom - targetSize * 0.5f;
        float targetTop = targetCenterY - targetSize * 0.5f;
        if (targetTop < contentTop) {
            targetCenterY = contentTop + targetSize * 0.5f;
        }
        t.centerY += (targetCenterY - t.centerY) * eased;
        t.width += (targetSize - t.width) * eased;
        t.height += (targetSize - t.height) * eased;
    }

    /** iPod-style: side albums fan out and recede while center card flips. */
    public static void applyFlipFanOut(FlowEngine.SlotTransform t, float poseCx, float flipP) {
        if (t == null || flipP <= 0f) return;
        float sign = poseCx >= 0f ? 1f : -1f;
        float spread = Math.abs(poseCx) * flipP * 0.42f;
        t.centerX += sign * spread;
        float shrink = 1f - flipP * 0.16f;
        t.width *= shrink;
        t.height *= shrink;
        // ponytail: side dim is t.alpha in FlowView only — geometry-only here avoids double-dim.
        t.zDepth -= flipP * 35f;
        t.depthOrder -= flipP * 120f;
    }
}
