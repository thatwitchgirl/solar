package com.solar.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;

/** Installed APK version — PackageManager is source of truth; BuildConfig is fallback only. */
public final class AppVersion {
    private AppVersion() {}

    public static String installedVersionName(Context context) {
        if (context == null) return fallbackVersionName();
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (info != null && info.versionName != null && !info.versionName.trim().isEmpty()) {
                return info.versionName.trim();
            }
        } catch (Exception ignored) {}
        return fallbackVersionName();
    }

    public static int installedVersionCode(Context context) {
        if (context == null) return BuildConfig.VERSION_CODE;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (info != null) return info.versionCode;
        } catch (Exception ignored) {}
        return BuildConfig.VERSION_CODE;
    }

    /** User-facing label: {@code nightly-30} or {@code v0.2.1}. */
    public static String displayLabel(Context context) {
        return SolarUpdateClient.formatVersionLabel(installedVersionName(context));
    }

    public static boolean isNightlyInstall(Context context) {
        return isNightlyName(installedVersionName(context));
    }

    public static boolean isNightlyName(String versionName) {
        return versionName != null && versionName.trim().startsWith("nightly-");
    }

    private static String fallbackVersionName() {
        return BuildConfig.VERSION_NAME != null ? BuildConfig.VERSION_NAME : "";
    }
}
