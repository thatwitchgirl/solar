package com.solar.launcher.ui;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug-mode perf logger — session c0d6cf.
 * ponytail: pull with {@code adb pull /storage/sdcard0/solar/debug-c0d6cf.log .cursor/}
 */
public final class TransitionPerfLog {
    private static final String TAG = "SolarDbgC0d6cf";
    private static final String SESSION = "c0d6cf";
    private static final String FILE = "debug-c0d6cf.log";
    public static volatile boolean ENABLED = false;

    private TransitionPerfLog() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            File dir = new File("/storage/sdcard0/solar");
            if (!dir.exists()) dir.mkdirs();
            try {
                FileWriter w = new FileWriter(new File(dir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
}
