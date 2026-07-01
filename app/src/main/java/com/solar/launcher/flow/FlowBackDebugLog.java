package com.solar.launcher.flow;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session c472d1 — Flow Back navigation + NP volume after Flow.
 * Pull: adb pull /storage/sdcard0/solar/debug-c472d1.log .cursor/debug-c472d1.log
 */
public final class FlowBackDebugLog {
    private static final String TAG = "SolarDbgC472d1";
    private static final String SESSION = "c472d1";
    private static final String FILE = "debug-c472d1.log";
    public static volatile boolean ENABLED = true;

    private FlowBackDebugLog() {}

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
            append(new File(dir, FILE), line);
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
