package com.solar.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

/**
 * Intercepts the system's UsbStorageActivity by bringing Solar to front when USB
 * is connected. Polling only runs between USB host connect and user dismissal of
 * the Solar USB dialog.
 *
 * ponytail: approach history:
 *  - ACTION_CLOSE_SYSTEM_DIALOGS → crashed SystemUI on API 17
 *  - pm disable component → SystemUI crash loop
 *  - 50ms poll with HOME key every tick → 20 root shells/sec, destroyed FPS
 *  - moveTaskToFront() without REORDER_TASKS perm → SecurityException spam
 *
 * Current approach:
 *  - Listen for USB_STATE broadcast → detect host_connected
 *  - On host connect: fire one-shot HOME key, start moveTaskToFront polling
 *  - On user dismiss/turn-on: stop polling immediately, set flag
 *  - Polling ONLY restarts on actual USB disconnect→reconnect cycle
 *  - Charging-only connections (no host) are completely ignored
 */
public final class Y1UsbFocusHelper {

    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String EXTRA_USB_CONNECTED = "connected";
    /** Extra that indicates a USB host (PC) is connected, not just a charger. */
    private static final String EXTRA_HOST_CONNECTED = "host_connected";

    /** How often to check focus while USB host is first connected (ms). */
    private static final int POLL_INTERVAL_MS = 80;

    public interface UsbListener {
        void onUsbStateChanged(boolean connected);
    }

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver usbReceiver;
    private boolean usbRegistered;
    private final UsbListener listener;
    private boolean usbConnected = false;
    private boolean polling = false;
    /** Set when user dismisses the USB dialog — pauses polling until USB disconnect+reconnect. */
    private boolean userDismissed = false;
    /** True if a USB host (PC) is connected, not just a charger. */
    private boolean hostConnected = false;

    public Y1UsbFocusHelper(Activity activity, UsbListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void onResume() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                registerIfNeeded();
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

    private Boolean lastConnectedState = null;

    private void registerIfNeeded() {
        if (usbRegistered) return;
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return;
                final boolean connected = intent.getBooleanExtra(EXTRA_USB_CONNECTED, false);
                final boolean host = intent.getBooleanExtra(EXTRA_HOST_CONNECTED, false)
                        || intent.getBooleanExtra("mass_storage", false)
                        || intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false);
                android.util.Log.d("UsbFocus", "onReceive: connected=" + connected + ", host=" + host + ", hostConnected=" + hostConnected + ", usbConnected=" + usbConnected);

                if (!connected && usbConnected) {
                    android.util.Log.d("UsbFocus", "USB cable disconnected — reset everything");
                    // USB cable disconnected — reset everything
                    usbConnected = false;
                    hostConnected = false;
                    userDismissed = false;
                    lastConnectedState = false;
                    stopPolling();
                    if (listener != null) listener.onUsbStateChanged(false);
                    return;
                }

                if (connected && host && !hostConnected) {
                    android.util.Log.d("UsbFocus", "USB host (PC) newly connected");
                    // USB host (PC) newly connected
                    hostConnected = true;
                    usbConnected = true;
                    lastConnectedState = true;
                    userDismissed = false;

                    // Immediate preemptive strike — moveTaskToFront + one-shot HOME key
                    bringToFrontFull();
                    startPolling();

                    if (listener != null) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                android.util.Log.d("UsbFocus", "Firing onUsbStateChanged(true) to listener");
                                listener.onUsbStateChanged(true);
                            }
                        }, 200);
                    }
                } else if (connected && !host) {
                    android.util.Log.d("UsbFocus", "Charger-only connection");
                    // Charger-only connection — do nothing
                    usbConnected = true;
                    lastConnectedState = true;
                }
            }
        };
        activity.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_STATE));
        usbRegistered = true;
    }

    // --- Focus polling ---

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling || userDismissed) {
                polling = false;
                return;
            }
            if (!activity.hasWindowFocus()) {
                // Poll uses moveTaskToFront ONLY — no root shell spawn
                bringToFrontLightPublic();
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /**
     * Pause focus polling — called when the user dismisses the Solar USB dialog
     * (Dismiss button, Turn On, or Back key). Polling only restarts on a fresh
     * USB disconnect→reconnect cycle.
     */
    public void pausePolling() {
        userDismissed = true;
        stopPolling();
    }

    private void startPolling() {
        if (polling || userDismissed) return;
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

    /** Returns true if a USB host (PC) is connected, not just a charger. */
    public boolean isHostConnected() {
        return hostConnected;
    }

    /**
     * Full bring-to-front: moveTaskToFront + one-shot HOME key via root.
     * Used ONLY on the initial USB host connect event.
     */
    private void bringToFrontFull() {
        bringToFrontLightPublic();
        try {
            android.content.Intent startMain = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            startMain.addCategory(android.content.Intent.CATEGORY_HOME);
            startMain.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activity != null) {
                activity.startActivity(startMain);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Light bring-to-front: moveTaskToFront + requestFocus only.
     * No root shell spawn — safe to call at polling intervals.
     */
    public void bringToFrontLightPublic() {
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(activity.getTaskId(), 0);
            }
        } catch (Exception ignored) {}

        try {
            activity.getWindow().getDecorView().requestFocus();
        } catch (Exception ignored) {}
    }
}
