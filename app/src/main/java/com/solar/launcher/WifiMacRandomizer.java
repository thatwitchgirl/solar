package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Random;

/**
 * One-shot Wi-Fi MAC randomization for rooted MTK Y1/Y2 devices.
 * ponytail: writes bytes 4–9 of {@code /data/nvram/APCFG/APRDEB/WIFI} via su — same NVRAM slot
 * OEM tools use; chmod 444 after write so the radio stack does not clobber on next boot.
 */
public final class WifiMacRandomizer {
    private static final String TAG = "WifiMacRandomizer";
    /** Same prefs file as {@link MainActivity} settings. */
    static final String PREFS = "SOLAR_SETTINGS";
    private static final String PREF_DONE = "wifi_mac_randomized_v1";
    private static final String PREF_LAST_MAC = "wifi_mac_last_v1";

    /** MTK Wi-Fi NVRAM — try common mount paths. */
    private static final String[] NVRAM_WIFI_PATHS = {
            "/data/nvram/APCFG/APRDEB/WIFI",
            "/nvdata/APCFG/APRDEB/WIFI",
    };

    /** MAC starts at byte 4 in the WIFI NVRAM blob (see MTK hex-edit guides). */
    static final int MAC_OFFSET = 4;
    static final int MAC_LEN = 6;

    private static final String WORK_NAME = "wifi_nvram_work";

    public interface Callback {
        void onComplete(boolean success, String macFormatted);
    }

    private WifiMacRandomizer() {}

    public static boolean hasRandomizedOnce(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_DONE, false);
    }

    /** First Solar start only — silent failure if su/NVRAM missing. */
    public static void ensureOnce(final Context context) {
        if (context == null || hasRandomizedOnce(context)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                Result result = randomize(context);
                if (result.success) {
                    markDone(context, result.macFormatted);
                    cycleWifi(context);
                    SolarLog.i(TAG, "one-shot ok " + result.macFormatted);
                } else {
                    SolarLog.w(TAG, "one-shot failed: " + result.error);
                }
            }
        }, "WifiMacOnce").start();
    }

    /** User-initiated refresh from Wi-Fi settings context menu. */
    public static void refreshManual(final Context context, final Callback callback) {
        if (context == null) {
            dispatch(callback, false, null);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                Result result = randomize(context);
                if (result.success) {
                    markDone(context, result.macFormatted);
                    cycleWifi(context);
                }
                dispatch(callback, result.success, result.macFormatted);
            }
        }, "WifiMacRefresh").start();
    }

    private static void markDone(Context context, String mac) {
        SharedPreferences.Editor ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        ed.putBoolean(PREF_DONE, true);
        if (mac != null) ed.putString(PREF_LAST_MAC, mac);
        ed.commit();
    }

    private static void dispatch(final Callback callback, final boolean ok, final String mac) {
        if (callback == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                callback.onComplete(ok, mac);
            }
        });
    }

    /** @return locally administered unicast MAC (6 bytes). */
    static byte[] generateRandomMac(Random rng) {
        byte[] mac = new byte[MAC_LEN];
        mac[0] = (byte) ((rng.nextInt(256) & 0xFC) | 0x02);
        for (int i = 1; i < MAC_LEN; i++) {
            mac[i] = (byte) rng.nextInt(256);
        }
        return mac;
    }

    static void patchMacBytes(byte[] nvram, int offset, byte[] mac) {
        if (nvram == null || mac == null || mac.length < MAC_LEN) return;
        if (offset < 0 || offset + MAC_LEN > nvram.length) return;
        System.arraycopy(mac, 0, nvram, offset, MAC_LEN);
    }

    static String formatMac(byte[] mac) {
        if (mac == null || mac.length < MAC_LEN) return "";
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < MAC_LEN; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.US, "%02x", mac[i] & 0xff));
        }
        return sb.toString();
    }

    private static final class Result {
        final boolean success;
        final String macFormatted;
        final String error;

        Result(boolean success, String macFormatted, String error) {
            this.success = success;
            this.macFormatted = macFormatted;
            this.error = error;
        }
    }

    private static Result randomize(Context context) {
        String nvramPath = findNvramWifiPath();
        if (nvramPath == null) {
            return new Result(false, null, "nvram_missing");
        }
        File work = new File(context.getCacheDir(), WORK_NAME);
        File cacheDir = context.getCacheDir();
        if (cacheDir != null && !cacheDir.exists()) cacheDir.mkdirs();
        String workPath = work.getAbsolutePath();
        final int uid = android.os.Process.myUid();
        if (!runSu("mkdir -p " + shQuote(cacheDir != null ? cacheDir.getAbsolutePath() : workPath)
                + " && cat " + shQuote(nvramPath) + " > " + shQuote(workPath)
                + " && chown " + uid + ":" + uid + " " + shQuote(workPath)
                + " && chmod 600 " + shQuote(workPath))) {
            return new Result(false, null, "nvram_read");
        }
        byte[] blob = readFileBytes(work);
        if (blob == null || blob.length < MAC_OFFSET + MAC_LEN) {
            return new Result(false, null, "nvram_short");
        }
        byte[] mac = generateRandomMac(new Random());
        patchMacBytes(blob, MAC_OFFSET, mac);
        if (!writeFileBytes(work, blob)) {
            return new Result(false, null, "work_write");
        }
        // ponytail: prior run may have chmod 444 — reopen for write, then lock read-only again.
        if (!runSu("chmod 660 " + shQuote(nvramPath)
                + " && cat " + shQuote(workPath) + " > " + shQuote(nvramPath)
                + " && chmod 444 " + shQuote(nvramPath))) {
            return new Result(false, null, "nvram_write");
        }
        return new Result(true, formatMac(mac), null);
    }

    private static String findNvramWifiPath() {
        for (String path : NVRAM_WIFI_PATHS) {
            if (runSu("test -f " + shQuote(path))) return path;
        }
        return null;
    }

    /** Toggle Wi-Fi so the new NVRAM MAC is picked up without reboot. */
    private static void cycleWifi(Context context) {
        runSu("svc wifi disable; sleep 1; svc wifi enable");
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && !wm.isWifiEnabled()) {
                wm.setWifiEnabled(true);
            }
        } catch (Exception ignored) {}
    }

    private static byte[] readFileBytes(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            int len = (int) file.length();
            if (len <= 0 || len > 65536) return null;
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) {
                int n = in.read(buf, off, len - off);
                if (n < 0) return null;
                off += n;
            }
            return buf;
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static boolean writeFileBytes(File file, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file, false);
            out.write(data);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static boolean runSu(String command) {
        if (command == null || command.trim().isEmpty()) return false;
        for (String su : new String[] {"/system/xbin/su", "su"}) {
            Process process = null;
            try {
                process = Runtime.getRuntime().exec(new String[] {su, "-c", command});
                if (process.waitFor() == 0) return true;
            } catch (Exception ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return false;
    }

    private static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
