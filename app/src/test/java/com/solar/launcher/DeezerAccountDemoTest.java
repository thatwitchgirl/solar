package com.solar.launcher;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.deezer.DeezerDownloadRunner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Bundled tester ARL masking rules. */
public class DeezerAccountDemoTest {

    @Test
    public void bundledDemoArlIsPresent() {
        assertTrue(DeezerAccount.hasBundledDemoArl());
    }

    @Test
    public void bundledFreeArlIsPresent() {
        assertTrue(DeezerAccount.hasBundledFreeArl());
    }

    @Test
    public void displayLabelEmptyUntilUserConfigures() {
        assertFalse(DeezerAccount.isUserArlConfigured(null));
        assertEquals("", DeezerAccount.displayLabel(null, "Test/Demo Account"));
    }

    @Test
    public void hasUsableDeezerWithoutUserSetup() {
        assertTrue(DeezerAccount.hasUsableDeezer(null));
        assertEquals(DeezerAccount.bundledDemoArl(), DeezerAccount.defaultSessionArl(null));
    }

    @Test
    public void canFallbackFalseWhenNoUserSetup() {
        assertFalse(DeezerAccount.canFallbackToDemoArl(null));
        assertFalse(DeezerAccount.bundledDemoArl().isEmpty());
    }

    @Test
    public void downloadTierOrderSkipsUserWhenNotConfigured() {
        assertFalse(DeezerDownloadRunner.downloadTierOrder(null).contains(
                DeezerAccount.ArlFallbackTier.USER));
        assertTrue(DeezerDownloadRunner.downloadTierOrder(null).contains(
                DeezerAccount.ArlFallbackTier.DEMO));
    }
}
