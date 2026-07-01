package com.solar.launcher.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LayoutMorphTransitionTest {

    @Test
    public void morphDurationMatchesPlan() {
        assertEquals(180, LayoutMorphTransition.MORPH_MS);
    }
}
