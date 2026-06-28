package com.solar.launcher;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WifiMacRandomizerTest {

    @Test
    public void generateRandomMac_isLocallyAdministeredUnicast() {
        byte[] mac = WifiMacRandomizer.generateRandomMac(new Random(42));
        assertEquals(6, mac.length);
        assertEquals(0, mac[0] & 0x01); // unicast
        assertTrue((mac[0] & 0x02) != 0); // locally administered
    }

    @Test
    public void patchMacBytes_writesAtMtkOffset() {
        byte[] blob = new byte[] {0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 9};
        byte[] mac = new byte[] {(byte) 0x02, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55};
        WifiMacRandomizer.patchMacBytes(blob, WifiMacRandomizer.MAC_OFFSET, mac);
        assertEquals(0x02, blob[4] & 0xff);
        assertEquals(0x55, blob[9] & 0xff);
        assertEquals(9, blob[10] & 0xff);
    }

    @Test
    public void formatMac_lowercaseColonSeparated() {
        byte[] mac = new byte[] {(byte) 0x02, (byte) 0xAB, (byte) 0xCD,
                (byte) 0xEF, (byte) 0x01, (byte) 0x23};
        assertEquals("02:ab:cd:ef:01:23", WifiMacRandomizer.formatMac(mac));
    }
}
