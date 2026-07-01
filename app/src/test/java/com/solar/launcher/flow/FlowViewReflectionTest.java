package com.solar.launcher.flow;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowViewReflectionTest {

    @Test
    public void centerSkipsReflectionDuringHandoff() {
        assertTrue(FlowView.shouldSkipCoverReflection(false, true, false, true));
    }

    @Test
    public void centerSkipsReflectionDuringReflectFade() {
        assertTrue(FlowView.shouldSkipCoverReflection(false, false, false, true, 0.5f));
    }

    @Test
    public void sideSkipsReflectionDuringHandoff() {
        assertTrue(FlowView.shouldSkipCoverReflection(false, true, false, false));
    }

    @Test
    public void centerSkipsReflectionWhileScrolling() {
        assertTrue(FlowView.shouldSkipCoverReflection(false, false, true, true));
    }

    @Test
    public void sideSkipsReflectionWhileScrolling() {
        assertTrue(FlowView.shouldSkipCoverReflection(false, false, true, false));
    }

    @Test
    public void noReflectionsOverridesCenter() {
        assertTrue(FlowView.shouldSkipCoverReflection(true, false, false, true));
    }

    @Test
    public void centerKeepsReflectionWhenIdle() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, false, true));
    }

    @Test
    public void centerKeepsReflectionAfterReflectFadeComplete() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, false, true, 1f));
    }

    @Test
    public void sideSkipsReflectionWhenIdle() {
        assertTrue(FlowView.shouldSkipCoverReflection(false, false, false, false));
    }
}
