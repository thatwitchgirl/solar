package com.solar.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * Prevents the system's UsbStorageActivity from staying visible while Solar is active.
 *
 * ponytail: approach history:
 *  - ACTION_CLOSE_SYSTEM_DIALOGS → crashed SystemUI on API 17
 *  - pm disable component → SystemUI crash loop (system retries endlessly)
 *  - moveTaskToFront at staggered intervals → too slow, dialog flashes
 *
 * Current approach: rapid focus polling while charging.
 * Polls hasWindowFocus every 50ms while the device is charging. When Solar loses
 * focus (system USB dialog appeared), immediately fires moveTaskToFront + HOME key.
 * Polling stops when the device is unplugged (no USB dialog possible anyway).
 * This catches the system dialog within 50ms — effectively invisible.
 */
public final class Y1UsbFocusHelper {

    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String EXTRA_USB_CONNECTED = "connected";

    /** How often to check focus while charging (ms). */
    private static final int POLL_INTERVAL_MS = 50;

    public interface UsbListener {
        void onUsbStateChanged(boolean connected);
    }

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver usbReceiver;
    private BroadcastReceiver chargingReceiver;
    private boolean usbRegistered;
    private boolean chargingRegistered;
    private final UsbListener listener;
    private boolean usbConnected = false;
    private volatile boolean charging = false;
    private boolean polling = false;
    /** Set when user dismisses the USB dialog — pauses polling until replug. */
    private boolean userDismissed = false;

    public Y1UsbFocusHelper(Activity activity, UsbListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void onResume() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                registerIfNeeded();
                registerChargingIfNeeded();
                updatePolling();
            }
        });
    }

    public void onDestroy() {
        stopPolling();
        if (usbRegistered && usbReceiver != null) {
            try { activity.unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
            usbRegistered = false;
        }
        if (chargingRegistered && chargingReceiver != null) {
            try { activity.unregisterReceiver(chargingReceiver); } catch (Exception ignored) {}
            chargingRegistered = false;
        }
    }

    private Boolean lastConnectedState = null;

    private void registerIfNeeded() {
        if (usbRegistered) return;
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return;
                final boolean connected = intent.getBooleanExtra(EXTRA_USB_CONNECTED, false);
                if (lastConnectedState != null && lastConnectedState == connected) return;
                lastConnectedState = connected;
                usbConnected = connected;
                if (connected) {
                    // Immediate preemptive strike — fire before the system dialog appears
                    bringToFront("usbState-immediate");
                    if (listener != null) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                listener.onUsbStateChanged(true);
                            }
                        }, 200);
                    }
                } else {
                    if (listener != null) listener.onUsbStateChanged(false);
                }
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("connected", connected);
                    DebugAgentLog.log(activity, "Y1UsbFocusHelper.onUsbState",
                            "USB state changed", "H-USB-STATE", d);
                } catch (Exception ignored) {}
                // #endregion
            }
        };
        activity.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_STATE));
        usbRegistered = true;
    }

    private void registerChargingIfNeeded() {
        if (chargingRegistered) return;
        // Read initial charging state
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = activity.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        // Listen for plug/unplug
        chargingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                    charging = true;
                    userDismissed = false; // New plug-in → reset dismiss state
                    updatePolling();
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    charging = false;
                    userDismissed = false;
                    updatePolling();
                }
            }
        };
        IntentFilter cf = new IntentFilter();
        cf.addAction(Intent.ACTION_POWER_CONNECTED);
        cf.addAction(Intent.ACTION_POWER_DISCONNECTED);
        activity.registerReceiver(chargingReceiver, cf);
        chargingRegistered = true;
    }

    // --- Focus polling ---

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling || !charging || userDismissed) return;
            if (!activity.hasWindowFocus()) {
                bringToFront("poll");
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /**
     * Pause focus polling — called when the user dismisses the Solar USB dialog
     * (either via the Dismiss button or Back key). Polling resumes only on a new
     * power connect event (cable replug).
     */
    public void pausePolling() {
        userDismissed = true;
        stopPolling();
    }

    private void updatePolling() {
        if (charging && !polling && !userDismissed) {
            startPolling();
        } else if ((!charging || userDismissed) && polling) {
            stopPolling();
        }
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

    /** Returns true if USB cable is currently connected. */
    public boolean isUsbConnected() {
        return usbConnected;
    }

    /**
     * Bring Solar to front using both mechanisms simultaneously:
     * 1. moveTaskToFront() — instant system API, moves Solar's task above UsbStorageActivity
     * 2. HOME key via root — low-level ActivityManager dispatch
     *
     * Rockbox is disabled on startup so HOME always resolves to Solar.
     */
    private void bringToFront(String reason) {
        // 1. moveTaskToFront — instant, no process spawn
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(activity.getTaskId(), 0);
            }
        } catch (Exception ignored) {}

        // 2. HOME key via root — handled at low level by ActivityManager
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(
                            new String[]{"su", "-c", "input keyevent 3"}).waitFor();
                } catch (Exception ignored) {}
            }
        }, "HomeKey").start();

        try {
            activity.getWindow().getDecorView().requestFocus();
        } catch (Exception ignored) {}

        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("reason", reason);
            d.put("hasWindowFocus", activity.hasWindowFocus());
            DebugAgentLog.log(activity, "Y1UsbFocusHelper.bringToFront",
                    "usb focus reclaim", "H-USB-FOCUS", d);
        } catch (Exception ignored) {}
        // #endregion
    }
}
