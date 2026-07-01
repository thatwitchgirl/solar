package com.solar.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * Intercepts SystemUI {@code UsbStorageActivity} by keeping Solar in front while a USB
 * host (PC) is connected. Polling runs for the whole host session — cable disconnect or
 * {@link #onDestroy()} stops polling.
 *
 * ponytail: user dismissed the enable dialog → {@link #setInterceptPaused} stops in-app
 * HOME fights; {@link UsbRecoveryAgent} re-homes Solar if SystemUI stays on top.
 * While UMS is exported, {@link SystemUiUsbSuppressor} replaces SystemUI's lock screen
 * with Solar's — no 50ms HOME storm.
 */
public final class Y1UsbFocusHelper {

    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String EXTRA_USB_CONNECTED = "connected";
    private static final String EXTRA_HOST_CONNECTED = "host_connected";

    /** How often to reclaim focus from SystemUI while USB host is connected (ms). */
    private static final int POLL_INTERVAL_MS = 400;
    /** Slower poll when Solar already has focus — saves CPU during Flow / playback. */
    private static final int POLL_INTERVAL_STABLE_MS = 900;
    /** Slow watchdog while UMS is exported — SystemUI lock screen replaced by Solar, not fought at 50ms. */
    private static final int POLL_INTERVAL_MASS_STORAGE_MS = 2000;
    /** Min gap between UI intercept callbacks — avoids su dumpsys storms on the main thread. */
    private static final long INTERCEPT_NOTIFY_MIN_MS = 2000L;
    /** Min gap between HOME intents when UMS is off — avoids freezing SystemUI's enable dialog. */
    private static final long BRING_FULL_MIN_INTERVAL_MS = 500L;
    /**
     * Host must stay disconnected at least this long before a dismissed session may intercept again.
     * Shorter reconnects are cable flaps — keep {@link #userDeclinedHostSession} and stay idle.
     */
    public static final long USB_HOST_SESSION_RESET_MS = 3000L;

    public interface UsbListener {
        void onUsbStateChanged(boolean connected);
        /** Charger-only USB cable — no PC host; Solar still offers storage enable prompt. */
        void onChargerOnlyConnected();
        /** Solar lost window focus to SystemUI — show/replace USB intercept UI. */
        void onUsbFocusIntercept();
    }

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver usbReceiver;
    private boolean usbRegistered;
    private final UsbListener listener;
    private boolean usbConnected = false;
    private boolean polling = false;
    private boolean hostConnected = false;
    /** Use HOME + moveTaskToFront every poll tick while UMS is exported. */
    private boolean massStorageIntercept = false;
    /** User dismissed Solar's enable dialog — no in-app intercept; recovery agent handles stuck SystemUI. */
    private boolean interceptPaused = false;
    /** Dismissed this host plug — no poll/HOME/intercept until cable off longer than session reset. */
    private boolean userDeclinedHostSession = false;
    /** USB lock screen: global context menu open — skip HOME/moveTaskToFront reclaim. */
    private boolean reclaimSuspended = false;
    private long lastBringToFrontFullMs = 0L;
    private long lastInterceptNotifyMs = 0L;
    private long lastHostDisconnectAtMs = 0L;
    /** Consecutive polls where SystemUI UsbStorageActivity stayed on top after reclaim (H1/H5). */
    private int reclaimFailureStreak = 0;
    /** HOME/full reclaim calls in the current 5s window — detects intent storms (H1). */
    private int homeCallsInWindow = 0;
    private long homeRateWindowStartMs = 0L;

    public Y1UsbFocusHelper(Activity activity, UsbListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void onResume() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                registerIfNeeded();
                if (hostConnected && !interceptPaused && !userDeclinedHostSession) {
                    startPolling();
                }
            }
        });
    }

    public void onDestroy() {
        stopPolling();
        if (usbRegistered && usbReceiver != null) {
            try { activity.unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
            usbRegistered = false;
        }
    }

    private void registerIfNeeded() {
        if (usbRegistered) return;
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleUsbStateIntent(intent);
            }
        };
        activity.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_STATE));
        usbRegistered = true;
        // ponytail: sticky USB_STATE may have fired before MainActivity registered.
        Intent sticky = activity.registerReceiver(null, new IntentFilter(ACTION_USB_STATE));
        if (sticky != null) handleUsbStateIntent(sticky);
    }

    private void handleUsbStateIntent(Intent intent) {
        if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return;
        final boolean connected = intent.getBooleanExtra(EXTRA_USB_CONNECTED, false);
        final boolean host = intent.getBooleanExtra(EXTRA_HOST_CONNECTED, false)
                || intent.getBooleanExtra("mass_storage", false)
                || intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false);
        android.util.Log.d("UsbFocus", "onReceive: connected=" + connected + ", host=" + host
                + ", hostConnected=" + hostConnected + ", usbConnected=" + usbConnected);
        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("connected", connected);
            d.put("host", host);
            d.put("hostConnected", hostConnected);
            d.put("usbConnected", usbConnected);
            d.put("interceptPaused", interceptPaused);
            d.put("extra_host_connected", intent.getBooleanExtra(EXTRA_HOST_CONNECTED, false));
            d.put("extra_mass_storage", intent.getBooleanExtra("mass_storage", false));
            d.put("extra_USB_IS_PC_KNOW_ME", intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false));
            d.put("polling", polling);
            DebugSessionLog.log("Y1UsbFocusHelper.onReceive", "USB_STATE", "H1", d);
        } catch (Exception ignored) {}
        // #endregion

        if (!connected && usbConnected) {
            android.util.Log.d("UsbFocus", "USB cable disconnected");
            lastHostDisconnectAtMs = System.currentTimeMillis();
            usbConnected = false;
            hostConnected = false;
            reclaimSuspended = false;
            massStorageIntercept = false;
            userDeclinedHostSession = false;
            interceptPaused = false;
            stopPolling();
            if (listener != null) listener.onUsbStateChanged(false);
            return;
        }

        if (connected && host && !hostConnected) {
            long offMs = lastHostDisconnectAtMs > 0L
                    ? System.currentTimeMillis() - lastHostDisconnectAtMs
                    : Long.MAX_VALUE;
            boolean flapWhileDeclined = userDeclinedHostSession
                    && offMs < USB_HOST_SESSION_RESET_MS;
            if (userDeclinedHostSession && offMs >= USB_HOST_SESSION_RESET_MS) {
                userDeclinedHostSession = false;
                interceptPaused = false;
            }
            android.util.Log.d("UsbFocus", "USB host (PC) connected offMs=" + offMs
                    + " flapWhileDeclined=" + flapWhileDeclined);
            hostConnected = true;
            usbConnected = true;
            if (flapWhileDeclined) {
                interceptPaused = true;
                stopPolling();
            } else if (!userDeclinedHostSession) {
                interceptPaused = false;
                bringToFrontFull();
                startPolling();
            }
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("offMs", offMs);
                d.put("flapWhileDeclined", flapWhileDeclined);
                d.put("userDeclinedHostSession", userDeclinedHostSession);
                d.put("interceptPaused", interceptPaused);
                d.put("polling", polling);
                DebugSessionLog.log("Y1UsbFocusHelper.handleUsbStateIntent",
                        "host connect", "H-USB-SESSION", d);
            } catch (Exception ignored) {}
            // #endregion
            if (listener != null) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        listener.onUsbStateChanged(true);
                    }
                }, 200);
            }
        } else if (connected && !host) {
            android.util.Log.d("UsbFocus", "Charger-only connection");
            usbConnected = true;
            stopPolling();
            if (listener != null) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        listener.onChargerOnlyConnected();
                    }
                }, 200);
            }
        } else if (connected && host && hostConnected
                && !interceptPaused && !userDeclinedHostSession && !reclaimSuspended) {
            startPolling();
            bringToFrontFullIfDue();
            notifyFocusIntercept();
        }
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            if (interceptPaused || userDeclinedHostSession) {
                stopPolling();
                return;
            }
            if (reclaimSuspended) {
                handler.postDelayed(this, POLL_INTERVAL_STABLE_MS);
                return;
            }
            boolean hadFocus = activity.hasWindowFocus();
            boolean systemUiUsbTop = isSystemUiUsbOnTop();
            boolean needsReclaim = massStorageIntercept
                    ? (systemUiUsbTop || !hadFocus)
                    : !hadFocus;
            if (needsReclaim) {
                bringToFrontFullIfDue();
                // ponytail: suppressor only while UMS exported — BACK during enable prompt kills our modal.
                if (massStorageIntercept && systemUiUsbTop) {
                    SystemUiUsbSuppressor.dismissIfNeeded(activity);
                }
                trackReclaimOutcome(hadFocus);
                if (!massStorageIntercept && !interceptPaused) {
                    notifyFocusIntercept();
                }
            } else if (reclaimFailureStreak > 0) {
                reclaimFailureStreak = 0;
            }
            // #region agent log
            if (DebugSessionLog.ENABLED && (!hadFocus || massStorageIntercept)) {
                try {
                    JSONObject d = new JSONObject();
                    d.put("polling", polling);
                    d.put("hasWindowFocus", hadFocus);
                    d.put("hasFocusAfter", activity.hasWindowFocus());
                    d.put("hostConnected", hostConnected);
                    d.put("massStorageIntercept", massStorageIntercept);
                    d.put("interceptPaused", interceptPaused);
                    DebugSessionLog.log("Y1UsbFocusHelper.pollRunnable", "poll tick", "H2-H3", d);
                } catch (Exception ignored) {}
            }
            // #endregion
            boolean stable = hadFocus
                    && (!massStorageIntercept || !systemUiUsbTop);
            int delay = stable ? POLL_INTERVAL_STABLE_MS
                    : (massStorageIntercept ? POLL_INTERVAL_MASS_STORAGE_MS : POLL_INTERVAL_MS);
            handler.postDelayed(this, delay);
        }
    };

    private void notifyFocusIntercept() {
        if (listener == null || interceptPaused || userDeclinedHostSession) return;
        long now = System.currentTimeMillis();
        if (now - lastInterceptNotifyMs < INTERCEPT_NOTIFY_MIN_MS) return;
        lastInterceptNotifyMs = now;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!interceptPaused && !userDeclinedHostSession) {
                    listener.onUsbFocusIntercept();
                }
            }
        });
    }

    private void startPolling() {
        if (polling) return;
        polling = true;
        handler.post(pollRunnable);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(pollRunnable);
    }

    public boolean isUsbConnected() {
        return usbConnected;
    }

    public boolean isHostConnected() {
        return hostConnected;
    }

    public boolean isPolling() {
        return polling;
    }

    public boolean isInterceptPaused() {
        return interceptPaused;
    }

    public boolean isUserDeclinedHostSession() {
        return userDeclinedHostSession;
    }

    /**
     * User declined USB storage — stop in-app HOME/intercept loops so SystemUI's dialog
     * can be dismissed; {@link UsbRecoveryAgent} re-homes Solar when needed.
     */
    public void setInterceptPaused(boolean paused) {
        interceptPaused = paused;
        if (paused) {
            userDeclinedHostSession = true;
            massStorageIntercept = false;
            stopPolling();
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("polling", polling);
                DebugSessionLog.log("Y1UsbFocusHelper.setInterceptPaused",
                        "declined — polling stopped", "H-USB-IDLE", d);
            } catch (Exception ignored) {}
            // #endregion
        }
    }

    /** User chose Turn on USB storage — resume normal host intercept for this plug. */
    public void clearHostInterceptDecline() {
        userDeclinedHostSession = false;
        interceptPaused = false;
        if (hostConnected) {
            bringToFrontFull();
            startPolling();
        }
    }

    /** While the user has the global context menu open on the USB lock screen, do not HOME-reclaim. */
    public void setReclaimSuspended(boolean suspended) {
        reclaimSuspended = suspended;
    }

    public boolean isReclaimSuspended() {
        return reclaimSuspended;
    }

    /** While UMS is exported, gently watch for SystemUI lock screen and replace with Solar's. */
    public void setMassStorageInterceptActive(boolean active) {
        if (interceptPaused && active) return;
        massStorageIntercept = active;
        if (active && hostConnected) {
            SystemUiUsbSuppressor.dismissNow(activity);
            startPolling();
        }
    }

    public boolean isMassStorageInterceptActive() {
        return massStorageIntercept;
    }

    private void bringToFrontFullIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastBringToFrontFullMs < BRING_FULL_MIN_INTERVAL_MS) {
            bringToFrontLightPublic();
            return;
        }
        bringToFrontFull();
    }

    private void bringToFrontFull() {
        if (reclaimSuspended || interceptPaused || userDeclinedHostSession) return;
        lastBringToFrontFullMs = System.currentTimeMillis();
        noteHomeCall();
        String topBefore = topResumedActivityName();
        // #region agent log
        if (DebugSessionLog.ENABLED) {
            try {
                JSONObject d = new JSONObject();
                d.put("topBefore", topBefore);
                d.put("massStorageIntercept", massStorageIntercept);
                d.put("interceptPaused", interceptPaused);
                d.put("homeCallsInWindow", homeCallsInWindow);
                d.put("reclaimFailureStreak", reclaimFailureStreak);
                if (activity instanceof MainActivity) {
                    d.put("screen", ((MainActivity) activity).debugScreenState());
                }
                DebugSessionLog.log("Y1UsbFocusHelper.bringToFrontFull", "HOME", "H2", d);
            } catch (Exception ignored) {}
        }
        // #endregion
        bringToFrontLightPublic();
        try {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(startMain);
        } catch (Exception ignored) {}
        // #region agent log
        if (DebugSessionLog.ENABLED) {
            try {
                JSONObject d = new JSONObject();
                d.put("topBefore", topBefore);
                d.put("topAfter", topResumedActivityName());
                d.put("hasWindowFocus", activity.hasWindowFocus());
                d.put("systemUiStillTop", isSystemUiUsbOnTop());
                DebugSessionLog.log("Y1UsbFocusHelper.bringToFrontFull", "after HOME", "H1-H5", d);
            } catch (Exception ignored) {}
        }
        // #endregion
    }

    private void noteHomeCall() {
        long now = System.currentTimeMillis();
        if (now - homeRateWindowStartMs > 5000L) {
            homeRateWindowStartMs = now;
            homeCallsInWindow = 0;
        }
        homeCallsInWindow++;
        if (homeCallsInWindow >= 8) {
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("homeCallsInWindow", homeCallsInWindow);
                d.put("massStorageIntercept", massStorageIntercept);
                d.put("polling", polling);
                DebugSessionLog.log("Y1UsbFocusHelper.noteHomeCall", "HOME storm", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
        }
    }

    private void trackReclaimOutcome(boolean hadFocusBefore) {
        if (interceptPaused || userDeclinedHostSession) return;
        boolean systemUiTop = isSystemUiUsbOnTop();
        boolean stillNoFocus = !activity.hasWindowFocus();
        if (systemUiTop || (!hadFocusBefore && stillNoFocus)) {
            reclaimFailureStreak++;
        } else {
            reclaimFailureStreak = 0;
        }
        if (reclaimFailureStreak == 3 || reclaimFailureStreak == 10 || reclaimFailureStreak == 20) {
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("streak", reclaimFailureStreak);
                d.put("systemUiTop", systemUiTop);
                d.put("hasWindowFocus", activity.hasWindowFocus());
                d.put("topActivity", topResumedActivityName());
                d.put("massStorageIntercept", massStorageIntercept);
                d.put("homeCallsInWindow", homeCallsInWindow);
                DebugSessionLog.log("Y1UsbFocusHelper.trackReclaimOutcome",
                        "reclaim failing", "H5", d);
            } catch (Exception ignored) {}
            // #endregion
        }
    }

    private boolean isSystemUiUsbOnTop() {
        String top = topResumedActivityName();
        return top != null && top.contains("UsbStorageActivity");
    }

    public void bringToFrontLightPublic() {
        if (reclaimSuspended || interceptPaused || userDeclinedHostSession) return;
        String topBefore = DebugSessionLog.ENABLED ? topResumedActivityName() : null;
        boolean moveOk = false;
        String moveErr = null;
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(activity.getTaskId(), 0);
                moveOk = true;
            }
        } catch (Exception e) {
            moveErr = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        try {
            activity.getWindow().getDecorView().requestFocus();
        } catch (Exception ignored) {}

        // #region agent log
        if (DebugSessionLog.ENABLED) {
            try {
                JSONObject d = new JSONObject();
                d.put("topBefore", topBefore);
                d.put("topAfter", topResumedActivityName());
                d.put("moveOk", moveOk);
                d.put("moveErr", moveErr != null ? moveErr : JSONObject.NULL);
                d.put("taskId", activity.getTaskId());
                d.put("hasWindowFocus", activity.hasWindowFocus());
                d.put("polling", polling);
                d.put("hostConnected", hostConnected);
                DebugSessionLog.log("Y1UsbFocusHelper.bringToFront", "moveTaskToFront", "H2-H5", d);
            } catch (Exception ignored) {}
        }
        // #endregion
    }

    private String topResumedActivityName() {
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "?";
            java.util.List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return "?";
            ActivityManager.RunningTaskInfo t = tasks.get(0);
            if (t.topActivity != null) return t.topActivity.flattenToShortString();
            return "?";
        } catch (Exception e) {
            return "err:" + e.getClass().getSimpleName();
        }
    }
}
