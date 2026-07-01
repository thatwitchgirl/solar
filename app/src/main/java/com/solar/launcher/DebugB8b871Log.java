package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/** Debug session b8b871 — Flow 2D transition / NP handoff. Pull via adb to .cursor/debug-b8b871.log */
public final class DebugB8b871Log {
    private static final String TAG = "SolarDbgB8b871";
    private static final String SESSION = "b8b871";
    private static final String SDCARD_FILE = "/storage/sdcard0/solar/debug-b8b871.log";

    private DebugB8b871Log() {}

    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
        log(ctx, location, message, hypothesisId, data, "pre-fix");
    }

    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data, String runId) {
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            o.put("runId", runId != null ? runId : "pre-fix");
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            append(SDCARD_FILE, line);
            if (ctx != null) {
                append(new File(ctx.getFilesDir(), "debug-b8b871.log").getAbsolutePath(), line);
            }
        } catch (Exception ignored) {}
    }

    private static void append(String path, String line) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
