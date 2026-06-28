package com.solar.launcher;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * App-wide error/crash logs under /storage/sdcard0/solar/logs for user support.
 * ponytail: rotate at 512KB; also mirrors to logcat.
 */
public final class SolarLog {

    private static final String TAG = "SolarLog";
    public static final String LOG_DIR = "/storage/sdcard0/solar/logs";
    private static final String CRASH_FILE = "crash.log";
    private static final String ERROR_FILE = "error.log";
    private static final long MAX_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();

    private SolarLog() {}

    public static void installUncaughtHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                logCrash(thread, e);
                if (previous != null) {
                    previous.uncaughtException(thread, e);
                } else {
                    System.exit(1);
                }
            }
        });
    }

    public static void e(String tag, String message, Throwable t) {
        if (t != null) {
            Log.e(tag, message, t);
        } else {
            Log.e(tag, message);
        }
        append(ERROR_FILE, formatLine("ERROR", tag, message, t));
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        append(ERROR_FILE, formatLine("WARN", tag, message, null));
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    private static void logCrash(Thread thread, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String body = sw.toString();
            Log.e("SolarCrash", body);
            String header = "\n--- CRASH " + ts() + " thread=" + thread.getName() + " ---\n";
            append(CRASH_FILE, header + body);
            append(ERROR_FILE, header + body);
        } catch (Exception ignored) {}
    }

    private static String formatLine(String level, String tag, String message, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(ts()).append(' ').append(level).append(' ').append(tag).append(": ").append(message);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append('\n').append(sw.toString().trim());
        }
        return sb.toString();
    }

    private static String ts() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static void append(String fileName, String text) {
        synchronized (LOCK) {
            try {
                File dir = new File(LOG_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    dir = new File("/data/data/com.solar.launcher/files/solar/logs");
                    dir.mkdirs();
                }
                File log = new File(dir, fileName);
                if (log.isFile() && log.length() > MAX_BYTES) {
                    File rotated = new File(dir, fileName + ".old");
                    log.renameTo(rotated);
                }
                FileOutputStream fos = new FileOutputStream(log, true);
                fos.write((text + "\n").getBytes("UTF-8"));
                fos.close();
            } catch (Exception ex) {
                Log.w(TAG, "append failed: " + ex.getMessage());
            }
        }
    }

    // ponytail self-check
    static void selfCheck() {
        if (!"crash.log".equals(CRASH_FILE)) throw new AssertionError("crash file");
        if (quotedSsidForTest("a").equals("\"a\"")) { /* ok */ } else throw new AssertionError("quote");
    }

    static String quotedSsidForTest(String ssid) {
        return ssid == null ? "\"\"" : "\"" + ssid + "\"";
    }
}
