package com.solar.launcher;

import org.junit.Test;

public class RockboxDisableTest {

    @Test
    public void markerPath_matchesRomInit() {
        if (!"/data/data/.solar_rom_home_ready".equals(RockboxDisable.MARKER_PATH)) {
            throw new AssertionError("marker path drift from 99SolarInit / build-rom wipe");
        }
    }
}
