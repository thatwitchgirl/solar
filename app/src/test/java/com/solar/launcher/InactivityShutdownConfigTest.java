package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InactivityShutdownConfigTest {

    @Test
    public void iconKeyOff() {
        assertEquals("timedShutdown_off", InactivityShutdownConfig.iconKeyForMinutes(0));
    }

    @Test
    public void iconKeyMinutes() {
        assertEquals("timedShutdown_30", InactivityShutdownConfig.iconKeyForMinutes(30));
        assertEquals("timedShutdown_120", InactivityShutdownConfig.iconKeyForMinutes(120));
    }

    @Test
    public void shutdownDelayZeroWhenOff() {
        assertEquals(0L, InactivityShutdownConfig.shutdownDelayMs(0));
        assertEquals(30 * 60_000L, InactivityShutdownConfig.shutdownDelayMs(30));
    }

    @Test
    public void cycleIndices() {
        int next = InactivityShutdownConfig.nextIndex(
                InactivityShutdownConfig.indexForMinutes(120));
        assertEquals(0, InactivityShutdownConfig.minutesAtIndex(next));
    }
}
