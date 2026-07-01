package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * One-shot {@code pm disable org.rockbox} on Solar ROM first boot or first Solar launch.
 * ponytail: marker {@code /data/data/.solar_rom_home_ready} — skipped after first run so
 * Settings → Switch to Rockbox still works; switch-to-stock.sh re-disables on return.
 */
public final class RockboxDisable {
    private static final String TAG = "RockboxDisable";
    static final String MARKER_PATH = "/data/data/.solar_rom_home_ready";
    private static final String ASSET = "y1/disable-rockbox-for-solar.sh";
    private static final String SYSTEM_SCRIPT = "/system/etc/solar/disable-rockbox-for-solar.sh";

    private RockboxDisable() {}

    /** Run when Rockbox is co-installed — re-run disable if both launchers ended up enabled. */
    public static void ensureOnce(Context context) {
        if (context == null) return;
        if (!LauncherSwitch.isRockboxAvailable(context)) return;
        if (!isSolarEnabled(context)) return;
        final boolean dualEnabled = LauncherSwitch.isRockboxEnabled(context);
        if (!dualEnabled && new File(MARKER_PATH).exists()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!dualEnabled && new File(MARKER_PATH).exists()) return;
                    boolean ok = runDisableScript(context);
                    Log.i(TAG, ok ? "Rockbox disabled (one-shot)" : "disable script failed");
                } catch (Exception e) {
                    Log.w(TAG, "ensureOnce failed: " + e.getMessage());
                }
            }
        }, "RockboxDisable").start();
    }

    static boolean isSolarEnabled(Context context) {
        try {
            return context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), 0).enabled;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean runDisableScript(Context context) {
        File system = new File(SYSTEM_SCRIPT);
        if (system.isFile()) {
            return runSu("sh " + shellQuote(SYSTEM_SCRIPT));
        }
        File cached = new File(context.getCacheDir(), "disable-rockbox-for-solar.sh");
        if (!extractAsset(context, cached)) return false;
        return runSu("sh " + shellQuote(cached.getAbsolutePath()));
    }

    private static boolean extractAsset(Context context, File out) {
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = context.getAssets().open(ASSET);
            fos = new FileOutputStream(out);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) fos.write(buf, 0, n);
            }
            fos.flush();
            runSu("chmod 755 " + shellQuote(out.getAbsolutePath()));
            return out.length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}
        }
    }

    private static boolean runSu(String command) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
