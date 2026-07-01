package com.solar.launcher;

import android.content.Context;

import org.json.JSONObject;

/**
 * Gently replaces SystemUI {@code UsbStorageActivity} with Solar's USB lock screen.
 * ponytail: one rooted BACK + HOME every {@link #MIN_INTERVAL_MS} — not a 50ms HOME storm.
 * {@code pm disable} on UsbStorageActivity crashes SystemUI in a loop; finishing it is safe.
 */
public final class SystemUiUsbSuppressor {

    /** Min gap between su dismiss attempts — avoids ANR / activity-manager spam. */
    private static final long MIN_INTERVAL_MS = 2000L;
    private static volatile long lastRunMs = 0L;

    private SystemUiUsbSuppressor() {}

    /**
     * If SystemUI's USB activity is foreground, send BACK then bring Solar HOME once.
     * Safe to call from main thread — work runs on a background thread.
     */
    public static void dismissIfNeeded(final Context context) {
        if (context == null) return;
        final long now = System.currentTimeMillis();
        if (now - lastRunMs < MIN_INTERVAL_MS) return;
        lastRunMs = now;
        runDismiss(context.getPackageName());
    }

    /** Force the next call through (e.g. right after enabling UMS). */
    public static void dismissNow(final Context context) {
        if (context == null) return;
        lastRunMs = 0L;
        dismissIfNeeded(context);
    }

    private static void runDismiss(final String pkg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ran = false;
                String err = null;
                try {
                    // ponytail: BACK finishes UsbStorageActivity; HOME shows Solar lock screen.
                    String cmd = "dumpsys activity top 2>/dev/null | grep -q UsbStorageActivity && "
                            + "{ input keyevent 4; sleep 0.15; "
                            + "am start -a android.intent.action.MAIN -c android.intent.category.HOME "
                            + "-n " + pkg + "/.MainActivity -f 0x34000000; echo ran; }";
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if ("ran".equals(line.trim())) ran = true;
                    }
                    br.close();
                    p.waitFor();
                } catch (Exception e) {
                    err = e.getClass().getSimpleName();
                }
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("ran", ran);
                    d.put("err", err != null ? err : JSONObject.NULL);
                    DebugSessionLog.log("SystemUiUsbSuppressor.dismissIfNeeded", "su dismiss", "H-SUPPRESS", d);
                } catch (Exception ignored) {}
                // #endregion
            }
        }, "SystemUiUsbSuppress").start();
    }
}
