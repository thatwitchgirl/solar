package com.solar.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * Y1 shows systemui UsbStorageActivity when USB is connected — it steals wheel focus.
 *
 * ponytail: previous approach used ACTION_CLOSE_SYSTEM_DIALOGS broadcast which crashes
 * SystemUI on API 17 ("Unfortunately, System UI has stopped"). New approach uses HOME key
 * injection via root — since Solar IS the launcher, pressing HOME naturally brings Solar
 * to the front and pushes the system UsbStorageActivity behind it without killing anything.
 */
public final class Y1UsbFocusHelper {
    /** API 17 UsbManager constants — not all are public on older SDK stubs. */
    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String EXTRA_USB_CONNECTED = "connected";

    public interface UsbListener {
        void onUsbStateChanged(boolean connected);
    }

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private boolean registered;
    private final UsbListener listener;
    private boolean usbConnected = false;

    public Y1UsbFocusHelper(Activity activity, UsbListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void onResume() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // ponytail: do NOT press HOME on resume — if we're already in the
                // foreground, pressing HOME resets the scroll position and makes
                // list views unusable. Just request focus on our decor view.
                if (usbConnected) {
                    try {
                        activity.getWindow().getDecorView().requestFocus();
                    } catch (Exception ignored) {}
                }
                registerIfNeeded();
            }
        });
    }

    public void onDestroy() {
        if (registered && receiver != null) {
            try {
                activity.unregisterReceiver(receiver);
            } catch (Exception ignored) {}
            registered = false;
        }
    }

    private Boolean lastConnectedState = null;

    private void registerIfNeeded() {
        if (registered) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return;
                final boolean connected = intent.getBooleanExtra(EXTRA_USB_CONNECTED, false);
                if (lastConnectedState != null && lastConnectedState == connected) return;
                lastConnectedState = connected;
                usbConnected = connected;
                if (connected) {
                    // ponytail: always schedule HOME presses on USB connect.
                    // The hasWindowFocus() check is inside each Runnable because
                    // when this broadcast fires the system dialog hasn't appeared
                    // yet — by 500/800ms later it will have stolen focus.
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!activity.hasWindowFocus()) {
                                reclaimInputFocus("usbState-1");
                            }
                        }
                    }, 500);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!activity.hasWindowFocus()) {
                                reclaimInputFocus("usbState-2");
                            }
                            if (listener != null) listener.onUsbStateChanged(true);
                        }
                    }, 800);
                } else {
                    if (listener != null) listener.onUsbStateChanged(false);
                }
            }
        };
        activity.registerReceiver(receiver, new IntentFilter(ACTION_USB_STATE));
        registered = true;
    }

    /** Returns true if USB cable is currently connected. */
    public boolean isUsbConnected() {
        return usbConnected;
    }

    /**
     * Reclaim focus by pressing HOME via root. Since Solar is the default launcher,
     * HOME brings Solar to the foreground and pushes the system UsbStorageActivity
     * to the background — without crashing SystemUI.
     *
     * Does NOT use ACTION_CLOSE_SYSTEM_DIALOGS (that crashes SystemUI on API 17).
     */
    void reclaimInputFocus(final String reason) {
        // HOME key via root — gentle, never crashes SystemUI
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(
                            new String[]{"su", "-c", "input keyevent 3"}).waitFor();
                } catch (Exception ignored) {}
            }
        }).start();

        // Also request focus on our own decor view for d-pad navigation
        try {
            activity.getWindow().getDecorView().requestFocus();
        } catch (Exception ignored) {}

        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("reason", reason);
            d.put("hasWindowFocus", activity.hasWindowFocus());
            DebugAgentLog.log(activity, "Y1UsbFocusHelper.reclaimInputFocus",
                    "usb focus reclaim (HOME key)", "H-USB-FOCUS", d);
        } catch (Exception ignored) {}
        // #endregion
    }
}
