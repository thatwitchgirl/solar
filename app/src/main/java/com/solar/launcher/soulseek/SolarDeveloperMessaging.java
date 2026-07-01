package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.DeviceFeatures;

import java.util.List;

/**
 * Consolidated developer support thread: one local conversation, three wire recipients.
 * Auto diagnostic PMs are never stored here.
 */
public final class SolarDeveloperMessaging {

    private static final long OPEN_SHIP_DEBOUNCE_MS = 60L * 60L * 1000L;
    private static volatile long lastOpenShipMs;

    private SolarDeveloperMessaging() {}

    public static List<SoulseekMessaging.Message> thread(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> raw = SoulseekMessaging.thread(
                ctx, prefs, SolarDeveloperAccounts.VIRTUAL_PEER);
        if (raw == null) return raw;
        java.util.ArrayList<SoulseekMessaging.Message> out = new java.util.ArrayList<SoulseekMessaging.Message>();
        for (SoulseekMessaging.Message m : raw) {
            if (m == null || SolarDeveloperAccounts.isAutoDiagnosticText(m.text)) continue;
            out.add(m);
        }
        return out;
    }

    public static void appendIncoming(Context ctx, SharedPreferences prefs, String fromDev,
            int msgId, int timestamp, String text) {
        if (ctx == null || text == null || SolarDeveloperAccounts.isAutoDiagnosticText(text)) return;
        SoulseekMessaging.append(ctx, prefs, new SoulseekMessaging.Message(
                msgId, timestamp, SolarDeveloperAccounts.VIRTUAL_PEER, text, true));
    }

    /**
     * Thread open — ship hidden diagnostics before the user's first support message;
     * after that, refresh at most once per hour.
     */
    public static void onThreadOpened(Context ctx, SharedPreferences prefs) {
        if (ctx == null || prefs == null) return;
        if (!SolarDeveloperAccounts.isExperimentEnabled(prefs)) return;
        boolean firstContact = !hasUserSentVisibleMessage(ctx, prefs);
        long now = System.currentTimeMillis();
        if (!firstContact && now - lastOpenShipMs < OPEN_SHIP_DEBOUNCE_MS) return;
        lastOpenShipMs = now;
        SolarDiagnosticReporter.shipOnDeveloperSupportOpen(ctx, prefs);
    }

    /** Outbound user lines in the virtual support thread (diagnostic wire PMs excluded). */
    public static boolean hasUserSentVisibleMessage(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> raw = SoulseekMessaging.thread(
                ctx, prefs, SolarDeveloperAccounts.VIRTUAL_PEER);
        if (raw == null) return false;
        for (SoulseekMessaging.Message m : raw) {
            if (m == null || m.incoming) continue;
            if (SolarDeveloperAccounts.isAutoDiagnosticText(m.text)) continue;
            return true;
        }
        return false;
    }

    public static void sendUserMessage(final Context ctx, final SharedPreferences prefs,
            final SoulseekClient client, final String text,
            final SoulseekClient.MessageSendCallback callback) {
        if (ctx == null || text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onError("Unavailable");
            return;
        }
        final String trimmed = text.trim();
        final String body = formatUserSupportWireBody(ctx, prefs, trimmed);
        SoulseekMessaging.append(ctx, prefs, new SoulseekMessaging.Message(
                (int) (System.currentTimeMillis() & 0x7fffffff),
                (int) (System.currentTimeMillis() / 1000L),
                SolarDeveloperAccounts.VIRTUAL_PEER, trimmed, false));
        SoulseekAccount acct = SoulseekAccount.load(prefs, ctx);
        final String[] devs = SolarDeveloperAccounts.wireRecipientsForSender(
                acct != null ? acct.username : null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean diagOk = sendViaDiagSession(ctx, prefs, devs, body);
                if (!diagOk) {
                    SolarDeveloperOutbox.enqueue(ctx, body);
                }
                if (callback == null) return;
                // Local thread already has the message — queue silently, never toast.
                callback.onSent();
            }
        }, "SolarDevPM").start();
    }

    /** Every wire PM attributes the Reach user — not only the first message in a session. */
    static String formatUserSupportWireBody(Context ctx, SharedPreferences prefs, String text) {
        SoulseekAccount acct = SoulseekAccount.load(prefs, ctx);
        String model = DeviceFeatures.deviceModelLabel();
        String user = acct.username != null ? acct.username : "?";
        return "[Solar support] user=" + user + " device=" + model + "\n" + text;
    }

    /**
     * PM fan-out via -diag session (Nicotine+ client id 160 — server relays to legacy inboxes).
     */
    static boolean sendViaDiagSession(Context ctx, SharedPreferences prefs,
            String[] recipients, String body) {
        if (ctx == null || prefs == null || body == null || body.isEmpty()) return false;
        return SolarDiagSessionManager.sendToAll(ctx, prefs, recipients, body);
    }

    /** Silent cross-forward so all three dev inboxes stay in sync (via diag session). */
    public static void forwardToOtherDevs(Context ctx, SharedPreferences prefs,
            String fromDev, String text) {
        if (ctx == null || text == null || fromDev == null) return;
        String body = "Forwarded from " + fromDev + ": " + text;
        sendViaDiagSession(ctx, prefs, SolarDeveloperAccounts.forwardTargets(fromDev), body);
    }

    public static SoulseekMessaging.Message lastVisibleMessage(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> t = thread(ctx, prefs);
        if (t == null || t.isEmpty()) return null;
        return t.get(t.size() - 1);
    }

    /** Test hook — reset open-ship debounce. */
    static void resetOpenShipDebounceForTest() {
        lastOpenShipMs = 0L;
    }
}
