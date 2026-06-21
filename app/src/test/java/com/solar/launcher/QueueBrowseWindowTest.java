package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QueueBrowseWindowTest {

    @Test
    public void windowStart_smallQueueIsZero() {
        assertEquals(0, QueueBrowseWindow.windowStart(3, 7, 3, 2));
    }

    @Test
    public void windowStart_centersOnFocus() {
        assertEquals(2, QueueBrowseWindow.windowStart(5, 20, 3, 2));
    }

    @Test
    public void windowStart_clampsAtEnd() {
        int count = 20;
        int visible = 3;
        int buffer = 2;
        int windowSize = QueueBrowseWindow.windowSize(visible, buffer);
        assertEquals(count - windowSize, QueueBrowseWindow.windowStart(19, count, visible, buffer));
    }

    @Test
    public void browseViewportSlot_edgesAndCenter() {
        assertEquals(0, QueueBrowseWindow.browseViewportSlot(0, 2, 3));
        assertEquals(2, QueueBrowseWindow.browseViewportSlot(1, 2, 3));
        assertEquals(1, QueueBrowseWindow.browseViewportSlot(2, 8, 3));
        assertEquals(1, QueueBrowseWindow.browseViewportSlot(1, 4, 3));
    }

    @Test
    public void shortListTopPadding_anchorsLastItem() {
        assertEquals(0, QueueBrowseWindow.shortListTopPadding(0, 2, 90, 30));
        assertEquals(30, QueueBrowseWindow.shortListTopPadding(1, 2, 90, 30));
    }
}
