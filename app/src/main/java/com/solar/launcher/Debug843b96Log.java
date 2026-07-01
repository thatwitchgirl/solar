package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/** Debug session 843b96 — developer PM fan-out / aggregation. Pull via adb from sdcard or app files. */
public final class Debug843b96Log {
    private static final String TAG = "SolarDevDbg843";
    private static final String SESSION = "843b96";
    private static final String SDCARD_FILE = "/storage/sdcard0/debug-843b96.log";
    public static volatile boolean ENABLED = true;

    private Debug843b96Log() {}

    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
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
            try {
                FileWriter w = new FileWriter(new File(SDCARD_FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            } catch (Exception ignored) {}
            if (ctx != null) {
                FileWriter w = new FileWriter(new File(ctx.getFilesDir(), "debug-843b96.log"), true);
                w.write(line);
                w.write('\n');
                w.close();
            }
        } catch (Exception ignored) {}
    }
}
