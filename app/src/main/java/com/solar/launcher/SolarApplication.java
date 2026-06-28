package com.solar.launcher;

import android.app.Application;

import com.solar.launcher.net.TlsHelper;
import com.solar.launcher.theme.ThemeManager;

import org.json.JSONObject;

/** ponytail: Cobrowse-style Conscrypt at boot — TLS 1.2 for podcasts on API 17 Y1. */
public class SolarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // #region agent log
        try {
            org.json.JSONObject dbg = new org.json.JSONObject();
            dbg.put("phase", "application_onCreate");
            DebugSessionLog.log("SolarApplication.onCreate", "app start", "H5", dbg);
        } catch (Exception ignored) {}
        // #endregion
        SolarLog.installUncaughtHandler();
        TlsHelper.init(this);
        com.solar.launcher.theme.ActiveThemeEngine.init(this);
        Y1InputKeys.selfCheckWheelMapping();
        Y1RomPrep.ensureSwitchScripts(this);
        RockboxDisable.ensureOnce(this);
        WifiMacRandomizer.ensureOnce(this);
        ThemeManager.ensureThemesRootReady(this);
        LauncherDefault.ensureDefaultHome(this);
    }
}
