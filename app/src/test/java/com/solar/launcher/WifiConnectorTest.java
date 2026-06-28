package com.solar.launcher;

import android.net.wifi.WifiConfiguration;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class WifiConnectorTest {

    @Test
    public void selfCheck() {
        WifiConnector.selfCheck();
        SolarLog.selfCheck();
    }

    @Test
    public void quotedSsidAndFindSaved() {
        if (!"\"MyNet\"".equals(WifiConnector.quotedSsid("MyNet"))) {
            throw new AssertionError("quotedSsid");
        }
        List<WifiConfiguration> configs = new ArrayList<WifiConfiguration>();
        WifiConfiguration c = new WifiConfiguration();
        c.SSID = "\"Cafe\"";
        c.networkId = 42;
        configs.add(c);
        if (WifiConnector.findSavedNetId(configs, "Cafe") != 42) {
            throw new AssertionError("find saved");
        }
        if (WifiConnector.findSavedNetId(configs, "Other") != -1) {
            throw new AssertionError("missing ssid");
        }
        if (WifiConnector.findSavedNetId(null, "x") != -1) {
            throw new AssertionError("null list");
        }
    }
}
