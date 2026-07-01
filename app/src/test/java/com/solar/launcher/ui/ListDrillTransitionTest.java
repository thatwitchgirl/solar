package com.solar.launcher.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ListDrillTransitionTest {

    @Test
    public void prefKeyMatchesSettingLookup() {
        assertEquals("menu_transitions", ListDrillTransition.PREF_MENU_TRANSITIONS);
    }
}
