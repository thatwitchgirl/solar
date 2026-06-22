package com.solar.launcher;

import org.junit.Test;

public class AppVersionTest {
    @Test
    public void isNightlyName() {
        if (!AppVersion.isNightlyName("nightly-31")) throw new AssertionError("nightly");
        if (AppVersion.isNightlyName("0.2.1")) throw new AssertionError("stable");
        if (AppVersion.isNightlyName("v0.2.1")) throw new AssertionError("prefixed stable");
    }
}
