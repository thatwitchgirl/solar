package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Background shell watchdog — re-homes Solar when SystemUI {@code UsbStorageActivity}
 * is on top but mass storage is not exported. Complements in-app intercept when the
 * Java layer loses the focus race or the user dismissed the enable dialog.
 *
 * ponytail: only acts while {@code com.solar.launcher} is running and org.rockbox is disabled.
 */
public final class UsbRecoveryAgent {
    private static final String TAG = "UsbRecoveryAgent";
    private static final String ASSET = "y1/solar-usb-recovery-agent.sh";
    private static final String SYSTEM_SCRIPT = "/system/etc/solar/solar-usb-recovery-agent.sh";
    private static final String DATA_SCRIPT = "/data/data/solar-usb-recovery-agent.sh";
    private static final long START_MIN_INTERVAL_MS = 60_000L;
    private static volatile long lastStartMs = 0L;

    private UsbRecoveryAgent() {}

    /** Start the singleton recovery loop (no-op if already running). */
    public static void ensureRunning(final Context context) {
        if (context == null) return;
        if (!RockboxDisable.isSolarEnabled(context)) return;
        long now = System.currentTimeMillis();
        if (now - lastStartMs < START_MIN_INTERVAL_MS) return;
        lastStartMs = now;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String script = resolveScriptPath(context);
                    if (script == null) return;
                    if (LauncherSwitch.isRockboxEnabled(context)) return;
                    runSu("sh " + shellQuote(script) + " </dev/null >/dev/null 2>&1 &");
                    Log.i(TAG, "recovery agent started");
                } catch (Exception e) {
                    Log.w(TAG, "ensureRunning failed: " + e.getMessage());
                }
            }
        }, "UsbRecoveryAgent").start();
    }

    private static String resolveScriptPath(Context context) {
        File system = new File(SYSTEM_SCRIPT);
        if (system.isFile()) return SYSTEM_SCRIPT;
        File data = new File(DATA_SCRIPT);
        if (data.isFile()) return DATA_SCRIPT;
        File cached = new File(context.getCacheDir(), "solar-usb-recovery-agent.sh");
        if (!extractAsset(context, cached)) return null;
        if (!runSu("cp " + shellQuote(cached.getAbsolutePath()) + " " + shellQuote(DATA_SCRIPT)
                + " && chmod 755 " + shellQuote(DATA_SCRIPT))) {
            return cached.getAbsolutePath();
        }
        return DATA_SCRIPT;
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
