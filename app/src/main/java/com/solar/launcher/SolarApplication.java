package com.solar.launcher;

import android.app.Application;

import com.solar.launcher.net.TlsHelper;
import com.solar.launcher.theme.ThemeManager;

import com.solar.launcher.soulseek.SolarDiagnosticReporter;

/** ponytail: Cobrowse-style Conscrypt at boot — TLS 1.2 for podcasts on API 17 Y1. */
public class SolarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SolarLog.installUncaughtHandler();
        SolarDiagnosticReporter.onProcessStart(this);
        com.solar.launcher.theme.ActiveThemeEngine.init(this);
        // ponytail: heavy I/O off Application main thread — home menu paints first.
        new Thread(new Runnable() {
            @Override
            public void run() {
                TlsHelper.init(SolarApplication.this);
                Y1InputKeys.selfCheckWheelMapping();
                Y1RomPrep.ensureSwitchScripts(SolarApplication.this);
                RockboxDisable.ensureOnce(SolarApplication.this);
                WifiMacRandomizer.ensureOnce(SolarApplication.this);
                ThemeManager.ensureThemesRootReady(SolarApplication.this);
                LauncherDefault.ensureDefaultHome(SolarApplication.this);
            }
        }, "SolarAppBootstrap").start();
    }
}
