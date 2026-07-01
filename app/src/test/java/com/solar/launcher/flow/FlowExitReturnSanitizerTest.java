package com.solar.launcher.flow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlowExitReturnSanitizerTest {

    private static final int STATE_FLOW = 24;
    private static final int STATE_PLAYER = 25;
    private static final int STATE_MENU = 0;
    private static final int STATE_BROWSER = 1;

    @Test
    public void rejectsFlowAndPlayerAsExitTargets() {
        assertEquals(STATE_MENU, FlowExitReturnSanitizer.sanitize(
                STATE_FLOW, STATE_MENU, STATE_FLOW, STATE_PLAYER, STATE_MENU));
        assertEquals(STATE_MENU, FlowExitReturnSanitizer.sanitize(
                STATE_PLAYER, STATE_MENU, STATE_FLOW, STATE_PLAYER, STATE_MENU));
    }

    @Test
    public void usesEntrySnapshotWhenCandidateInvalid() {
        assertEquals(STATE_BROWSER, FlowExitReturnSanitizer.sanitize(
                STATE_FLOW, STATE_BROWSER, STATE_FLOW, STATE_PLAYER, STATE_MENU));
    }

    @Test
    public void preservesValidExitScreen() {
        assertEquals(STATE_BROWSER, FlowExitReturnSanitizer.sanitize(
                STATE_BROWSER, STATE_MENU, STATE_FLOW, STATE_PLAYER, STATE_MENU));
    }
}
