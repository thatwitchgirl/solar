package com.solar.launcher.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SettingLookupTest {

    @Test
    public void artworkPerspectiveLabelMaps() {
        assertEquals("artwork_perspective", SettingLookup.prefKeyForLabel("Artwork Perspective"));
        assertEquals("artwork_perspective", SettingLookup.prefKeyForLabel("artwork perspective"));
    }

    @Test
    public void normalizeStripsParenthetical() {
        assertEquals("full width menus", SettingLookup.normalize("Full Width Menus (experimental)"));
    }

    @Test
    public void unknownLabelReturnsNull() {
        assertNull(SettingLookup.prefKeyForLabel("Not A Real Setting"));
    }
}
