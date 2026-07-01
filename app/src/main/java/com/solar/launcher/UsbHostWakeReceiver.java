package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Wakes Solar when a USB host (PC) connects while the process is not running.
 * ponytail: dynamic receivers in MainActivity miss USB_STATE if SystemUI wins
 * the race and Solar was force-stopped; this manifest receiver starts HOME.
 */
public final class UsbHostWakeReceiver extends BroadcastReceiver {

    static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";

    static boolean isUsbHostIntent(Intent intent) {
        if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return false;
        if (!intent.getBooleanExtra("connected", false)) return false;
        return intent.getBooleanExtra("host_connected", false)
                || intent.getBooleanExtra("mass_storage", false)
                || intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isUsbHostIntent(intent)) return;
        try {
            if (!context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), 0).enabled) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        Intent home = new Intent(context, MainActivity.class);
        home.setAction(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(home);
    }
}
