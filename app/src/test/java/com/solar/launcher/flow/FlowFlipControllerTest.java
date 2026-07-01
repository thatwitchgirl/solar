package com.solar.launcher.flow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowFlipControllerTest {

    private static void advanceFlipToBack(FlowFlipController flip) {
        flip.startFlipToBack(null);
        long end = System.currentTimeMillis() + 400;
        for (long t = System.currentTimeMillis() + 16; t <= end; t += 16) {
            flip.tick(t);
            if (flip.getState() == FlowFlipController.STATE_BACK) return;
        }
    }

    private static void advanceFlipToFront(FlowFlipController flip) {
        flip.startFlipToFront(null);
        long end = System.currentTimeMillis() + 400;
        for (long t = System.currentTimeMillis() + 16; t <= end; t += 16) {
            flip.tick(t);
            if (flip.getState() == FlowFlipController.STATE_IDLE) return;
        }
    }

    @Test
    public void flipToBackReachesBackState() {
        FlowFlipController flip = new FlowFlipController();
        flip.setBackContent("Album", "Artist", Arrays.asList(
                new FlowScreenHost.FlowBackRow("Track 1", "", null, null)));
        advanceFlipToBack(flip);
        assertEquals(FlowFlipController.STATE_BACK, flip.getState());
        assertEquals(1f, flip.flipProgress(), 0.01f);
    }

    @Test
    public void scrollBackClampsAtEnds() {
        FlowFlipController flip = new FlowFlipController();
        flip.setBackContent("A", "", Arrays.asList(
                new FlowScreenHost.FlowBackRow("One", "", null, null),
                new FlowScreenHost.FlowBackRow("Two", "", null, null)));
        advanceFlipToBack(flip);
        assertTrue(flip.scrollBackBy(1));
        assertEquals(1, flip.backIndex());
        assertFalse(flip.scrollBackBy(1));
        assertEquals(1, flip.backIndex());
        assertTrue(flip.scrollBackBy(-1));
        assertEquals(0, flip.backIndex());
        assertFalse(flip.scrollBackBy(-1));
    }

    @Test
    public void backStackPushPop() {
        FlowFlipController flip = new FlowFlipController();
        flip.setBackContent("Artist", "", Arrays.asList(
                new FlowScreenHost.FlowBackRow("Album A", "", null, null, null, null)));
        advanceFlipToBack(flip);
        flip.pushBackLevel();
        flip.setBackContent("Album A", "", Arrays.asList(
                new FlowScreenHost.FlowBackRow("Track 1", "", null, null)));
        assertTrue(flip.popBackLevel());
        assertEquals("Artist", flip.headerTitle());
        assertEquals(1, flip.backRows().size());
        assertEquals("Album A", flip.backRows().get(0).title);
    }

    @Test
    public void flipToFrontReturnsIdle() {
        FlowFlipController flip = new FlowFlipController();
        flip.setBackContent("A", "", Collections.singletonList(
                new FlowScreenHost.FlowBackRow("T", "", null, null)));
        advanceFlipToBack(flip);
        advanceFlipToFront(flip);
        assertEquals(FlowFlipController.STATE_IDLE, flip.getState());
        assertFalse(flip.isFlippedOrFlipping());
    }

    @Test
    public void sideDimAlphaVisibleAtBack() {
        FlowFlipController flip = new FlowFlipController();
        flip.setBackContent("Album", "", Collections.singletonList(
                new FlowScreenHost.FlowBackRow("Track", "", null, null)));
        assertEquals(1f, flip.sideDimAlpha(), 0.01f);
        advanceFlipToBack(flip);
        assertEquals(0.47f, flip.sideDimAlpha(), 0.01f);
    }

    @Test
    public void easeOutQuadEndsAtOne() {
        assertEquals(1f, FlowFlipController.easeOutQuad(1f), 0.001f);
        assertTrue(FlowFlipController.easeOutQuad(0.5f) > 0.5f);
    }

    @Test
    public void setMaxVisibleRowsLimitsDrawnWindow() {
        FlowFlipController flip = new FlowFlipController();
        java.util.ArrayList<FlowScreenHost.FlowBackRow> rows = new java.util.ArrayList<FlowScreenHost.FlowBackRow>();
        for (int i = 0; i < 16; i++) {
            rows.add(new FlowScreenHost.FlowBackRow("Track " + i, "", null, null));
        }
        flip.setBackContent("Album", "", rows);
        flip.setMaxVisibleRows(5);
        assertEquals(5, flip.visibleBackRowCount());
        advanceFlipToBack(flip);
        assertEquals(0, flip.visibleBackRowStart());
        assertTrue(flip.scrollBackBy(4));
        assertEquals(4, flip.backIndex());
        assertEquals(0, flip.visibleBackRowStart());
        assertTrue(flip.scrollBackBy(1));
        assertEquals(5, flip.backIndex());
        assertEquals(1, flip.visibleBackRowStart());
    }

    @Test
    public void backIndexAlwaysInsideVisibleWindow() {
        FlowFlipController flip = new FlowFlipController();
        java.util.ArrayList<FlowScreenHost.FlowBackRow> rows = new java.util.ArrayList<FlowScreenHost.FlowBackRow>();
        for (int i = 0; i < 16; i++) {
            rows.add(new FlowScreenHost.FlowBackRow("Track " + i, "", null, null));
        }
        flip.setBackContent("Album", "", rows);
        flip.setMaxVisibleRows(4);
        advanceFlipToBack(flip);
        for (int step = 0; step < 15; step++) {
            int winStart = flip.visibleBackRowStart();
            int winCount = flip.visibleBackRowCount();
            int idx = flip.backIndex();
            assertTrue("backIndex " + idx + " outside [" + winStart + "," + (winStart + winCount) + ")",
                    idx >= winStart && idx < winStart + winCount);
            assertTrue(flip.scrollBackBy(1));
        }
        int winStart = flip.visibleBackRowStart();
        int winCount = flip.visibleBackRowCount();
        int idx = flip.backIndex();
        assertTrue(idx >= winStart && idx < winStart + winCount);
        assertEquals(15, idx);
    }
}
