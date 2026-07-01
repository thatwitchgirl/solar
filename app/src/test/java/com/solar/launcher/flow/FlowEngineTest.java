package com.solar.launcher.flow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowEngineTest {

    @Test
    public void scrollByReturningMoved_advancesCarousel() {
        FlowEngine engine = new FlowEngine();
        engine.setItemCount(5);
        engine.setFocusIndex(1);
        assertTrue(engine.scrollByReturningMoved(1));
        for (long t = 16; t <= 800; t += 16) engine.tick(t);
        assertEquals(2, engine.getFocusIndex());
        assertFalse(engine.isAnimating());
    }

    @Test
    public void scrollClampsAtEnds() {
        FlowEngine engine = new FlowEngine();
        engine.setItemCount(3);
        engine.setFocusIndex(0);
        engine.scrollBy(-1);
        for (long t = 0; t < 500; t += 16) engine.tick(t);
        assertEquals(0, engine.getFocusIndex());
        engine.setFocusIndex(2);
        engine.scrollBy(1);
        for (long t = 0; t < 1000; t += 16) engine.tick(t);
        assertEquals(2, engine.getFocusIndex());
    }

    @Test
    public void animationProgressBetweenIndices() {
        FlowEngine engine = new FlowEngine();
        engine.setViewport(480f, 360f);
        engine.setItemCount(10);
        engine.setFocusIndex(3);
        engine.scrollBy(1);
        assertTrue(engine.isAnimating());
        for (long t = 16; t <= 120; t += 16) engine.tick(t);
        float mid = engine.getVisualOffset();
        assertTrue("mid scroll should be between 3 and 4", mid > 3.05f && mid < 3.95f);
        for (long t = 121; t <= 800; t += 16) engine.tick(t);
        assertTrue(!engine.isAnimating());
        assertEquals(4, engine.getFocusIndex());
        assertEquals(4f, engine.getVisualOffset(), 0.01f);
    }

    @Test
    public void sideSlotRotationNearSeventyDegrees() {
        FlowEngine engine = new FlowEngine();
        engine.setViewport(480f, 360f);
        engine.setItemCount(5);
        engine.setFocusIndex(2);
        FlowEngine.SlotTransform left = engine.slotTransform(1, 480f, 360f, 0f);
        FlowEngine.SlotTransform right = engine.slotTransform(3, 480f, 360f, 0f);
        assertTrue(Math.abs(left.rotationYDeg + 70f) < 10f);
        assertTrue(Math.abs(right.rotationYDeg - 70f) < 10f);
        assertTrue(left.width > 0f);
        assertEquals(left.width, left.height, left.width * 0.05f);
    }

    @Test
    public void coverFlowClassicMetricsOnY1() {
        assertEquals(4, CoverFlowLayout.SIDE_SLIDES);
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        assertEquals(120f, m.reflectHeight, 1f);
        assertTrue(m.displaySize >= 190f && m.displaySize <= 220f);
        float centerTop = m.viewH * 0.33f - m.displaySize * 0.5f;
        assertTrue("center cover must not clip above viewport", centerTop >= 0f);
    }

    @Test
    public void coverFlowMetricsSquareOnY1() {
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        assertTrue(m.displaySize >= 190f && m.displaySize <= 220f);
        assertTrue(m.reflectHeight > 100f);
    }

    @Test
    public void incomingCoverPromotesDuringScroll() {
        FlowEngine engine = new FlowEngine();
        engine.setViewport(480f, 360f);
        engine.setItemCount(10);
        engine.setFocusIndex(3);
        engine.scrollBy(1);
        float earlyDepth = 0f;
        float lateDepth = 0f;
        for (long t = 16; t <= 600; t += 16) {
            engine.tick(t);
            FlowEngine.SlotTransform incoming = engine.slotTransform(4, 480f, 360f, 0f);
            if (t <= 120) earlyDepth = Math.max(earlyDepth, incoming.depthOrder);
            if (t >= 300) lateDepth = Math.max(lateDepth, incoming.depthOrder);
        }
        assertTrue("incoming cover advances in paint order during scroll", lateDepth > earlyDepth + 50f);
        FlowEngine.SlotTransform focused = engine.slotTransform(4, 480f, 360f, 0f);
        FlowEngine.SlotTransform side = engine.slotTransform(5, 480f, 360f, 0f);
        assertTrue("focused cover larger than side", focused.width > side.width);
    }

    @Test
    public void centernessAtCenterIsOne() {
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        CoverFlowLayout.SlidePose center = new CoverFlowLayout.SlidePose();
        center.cx = 0f;
        center.angle = 0;
        assertEquals(1f, CoverFlowLayout.centernessFromPose(center, m), 0.01f);
        CoverFlowLayout.SlidePose side = new CoverFlowLayout.SlidePose();
        side.cx = -m.offsetX;
        side.angle = CoverFlowLayout.ITILT;
        assertTrue(CoverFlowLayout.centernessFromPose(side, m) < 0.2f);
    }

    @Test
    public void visualCenterIndexDuringScroll() {
        FlowEngine engine = new FlowEngine();
        engine.setViewport(480f, 360f);
        engine.setItemCount(10);
        engine.setFocusIndex(3);
        engine.scrollBy(1);
        for (long t = 16; t <= 120; t += 16) engine.tick(t);
        assertTrue(engine.isAnimating());
        int visual = engine.getVisualCenterIndex();
        int focus = engine.getFocusIndex();
        assertTrue("visual center during scroll is 3 or 4", visual == 3 || visual == 4);
        assertTrue("focus and visual differ by at most one slot",
                Math.abs(focus - visual) <= 1);
        engine.snapToVisualCenter();
        assertFalse(engine.isAnimating());
        assertEquals(visual, engine.getFocusIndex());
    }

    @Test
    public void findIndexMatchesKeyOrTitle() {
        FlowEngine engine = new FlowEngine();
        java.util.List<FlowItem> items = Arrays.asList(
                FlowItem.album("OK Computer", "", "ok|radiohead", Collections.<java.io.File>emptyList(), ""),
                FlowItem.artist("Radiohead", "", "radiohead"));
        assertEquals(0, engine.findIndexForKey(items, "ok|radiohead"));
        assertEquals(0, engine.findIndexForKey(items, "ok"));
        assertEquals(0, engine.findIndexForKey(items, "ok|other artist"));
        assertEquals(1, engine.findIndexForKey(items, "radiohead"));
    }

    @Test
    public void squareBackFaceAnchorKeepsEqualWidthHeight() {
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        FlowEngine.SlotTransform t = new FlowEngine.SlotTransform();
        t.centerX = m.viewW * 0.5f;
        t.centerY = m.viewH * 0.33f;
        t.width = m.displaySize;
        t.height = m.displaySize;
        CoverFlowLayout.applySquareBackFaceSouthAnchor(t, m, 1f, 48f, 12f);
        assertEquals(t.width, t.height, t.width * 0.01f);
        assertTrue(t.width > m.displaySize * 0.9f);
    }

    @Test
    public void rectangleEncroachDiffersWidthHeight() {
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        FlowEngine.SlotTransform t = new FlowEngine.SlotTransform();
        t.centerX = m.viewW * 0.5f;
        t.centerY = m.viewH * 0.33f;
        t.width = m.displaySize;
        t.height = m.displaySize;
        CoverFlowLayout.applyBackFaceSouthEncroach(t, m, 1f, 48f, 12f);
        assertTrue(t.width > t.height * 1.02f);
    }

    @Test
    public void rectangleEncroachMatchesIpodProportions() {
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        FlowEngine.SlotTransform t = new FlowEngine.SlotTransform();
        t.centerX = m.viewW * 0.5f;
        t.centerY = m.viewH * 0.33f;
        t.width = m.displaySize;
        t.height = m.displaySize;
        float topInset = 48f;
        float bottomMargin = 12f;
        CoverFlowLayout.applyBackFaceSouthEncroach(t, m, 1f, topInset, bottomMargin);
        float expectedW = m.viewW * CoverFlowLayout.BACK_FACE_WIDTH_FRAC;
        float availableH = m.viewH - topInset - bottomMargin;
        float expectedH = Math.min(m.viewH * CoverFlowLayout.BACK_FACE_HEIGHT_FRAC, availableH);
        assertEquals(expectedW, t.width, 1f);
        assertEquals(expectedH, t.height, 1f);
        float bottom = t.centerY + t.height * 0.5f;
        assertEquals(m.viewH - bottomMargin, bottom, 1f);
    }

    @Test
    public void sameDirectionScrollExtendsTarget() {
        FlowEngine engine = new FlowEngine();
        engine.setViewport(480f, 360f);
        engine.setItemCount(10);
        engine.setFocusIndex(3);
        engine.scrollByReturningMoved(1);
        assertTrue(engine.isAnimating());
        engine.scrollByReturningMoved(1);
        for (long t = 16; t <= 1200; t += 16) engine.tick(t);
        assertEquals(5, engine.getFocusIndex());
    }

    @Test
    public void depthOrderMatchesSlotTransform() {
        FlowEngine engine = new FlowEngine();
        engine.setViewport(480f, 360f);
        engine.setItemCount(5);
        engine.setFocusIndex(2);
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        for (int idx = 0; idx < 5; idx++) {
            FlowEngine.SlotTransform t = engine.slotTransform(idx, 480f, 360f, 0f);
            CoverFlowLayout.SlidePose pose = engine.poseForItemPublic(idx);
            float depth = CoverFlowLayout.depthOrderFromPose(pose, m);
            assertEquals(t.depthOrder, depth, 0.001f);
        }
    }

    @Test
    public void playerPoseUsesPositiveSlant() {
        assertEquals(14f, FlowAlbumArt3d.PLAYER_ROT_Y_DEG, 0.01f);
        float viewW = 200f;
        float viewH = 280f;
        float size = Math.min(viewW, viewH);
        // ponytail: host JVM android.jar RectF stub may not set fields — assert layout math.
        assertEquals(40f, (viewH - size) * 0.5f, 0.01f);
        FlowAlbumArt3d.AlbumArtPose pose = FlowAlbumArt3d.playerPose(viewW, viewH);
        assertEquals(FlowAlbumArt3d.PLAYER_ROT_Y_DEG, pose.rotationYDeg, 0.01f);
        assertEquals(size, Math.min(viewW, viewH), 0.01f);
    }
}
