package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug-mode NDJSON logger — session 6a585a.
 * ponytail: writes to SD card + app files; pull with adb to .cursor/debug-6a585a.log
 */
public final class DebugSessionLog {
    private static final String TAG = "SolarDbg6a585a";
    private static final String SESSION = "6a585a";
    private static final String FILE = "debug-6a585a.log";
    /** ponytail: hot-path sync SD I/O — on only for short debug sessions. */
    public static volatile boolean ENABLED = false;

    private DebugSessionLog() {}

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
            } catch (Exception ignored2) {}
            try {
                File appDir = new File("/data/data/com.solar.launcher/files");
                if (!appDir.exists()) appDir.mkdirs();
                FileWriter w = new FileWriter(new File(appDir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            } catch (Exception ignored3) {}
        } catch (Exception ignored) {}
    }
}
