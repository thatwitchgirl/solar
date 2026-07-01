package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/** Debug session 1cf0c7 — Flow fade / NP handoff / carousel focus. Pull to .cursor/debug-1cf0c7.log */
public final class Debug1cf0c7Log {
    private static final String TAG = "SolarDbg1cf0c7";
    private static final String SESSION = "1cf0c7";
    private static final String SDCARD_FILE = "/storage/sdcard0/solar/debug-1cf0c7.log";

    private Debug1cf0c7Log() {}

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
                append(new File(ctx.getFilesDir(), "debug-1cf0c7.log").getAbsolutePath(), line);
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
