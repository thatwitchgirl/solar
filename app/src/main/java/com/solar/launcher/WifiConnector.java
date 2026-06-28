package com.solar.launcher;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ponytail: one place for Wi-Fi connect/forget — background thread, validated netId, logged errors.
 */
public final class WifiConnector {

    private static final String TAG = "WifiConnector";
    private static final AtomicInteger WORK_GEN = new AtomicInteger();

    public interface Callback {
        void onComplete(boolean success);
    }

    private WifiConnector() {}

    /** Connect open or WPA network (add/update config, enable, reconnect). */
    public static void connect(final Context context, final String ssid, final String password,
                               final boolean open, final Callback callback) {
        if (context == null || ssid == null || ssid.isEmpty()) {
            notifyCallback(callback, false);
            return;
        }
        final int gen = WORK_GEN.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    WifiManager wm = wifiManager(context);
                    if (wm != null) {
                        ok = connectBlocking(wm, ssid, password, open);
                    }
                } catch (Exception e) {
                    SolarLog.e(TAG, "connect " + ssid, e);
                }
                if (gen != WORK_GEN.get()) return;
                final boolean result = ok;
                if (!ok) {
                    SolarLog.e(TAG, "connect failed ssid=" + ssid, null);
                }
                postResult(callback, result);
            }
        }, "WifiConnect").start();
    }

    /** Enable a previously saved network by SSID. */
    public static void connectSaved(final Context context, final String ssid, final Callback callback) {
        if (context == null || ssid == null || ssid.isEmpty()) {
            notifyCallback(callback, false);
            return;
        }
        final int gen = WORK_GEN.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    WifiManager wm = wifiManager(context);
                    if (wm != null) {
                        int netId = findSavedNetId(wm.getConfiguredNetworks(), ssid);
                        ok = netId >= 0 && enableSavedBlocking(wm, netId);
                    }
                } catch (Exception e) {
                    SolarLog.e(TAG, "connectSaved " + ssid, e);
                }
                if (gen != WORK_GEN.get()) return;
                if (!ok) SolarLog.e(TAG, "connectSaved failed ssid=" + ssid, null);
                postResult(callback, ok);
            }
        }, "WifiConnectSaved").start();
    }

    /** Remove saved network configuration. */
    public static void forget(final Context context, final String ssid, final Callback callback) {
        if (context == null || ssid == null || ssid.isEmpty()) {
            notifyCallback(callback, false);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    WifiManager wm = wifiManager(context);
                    if (wm != null) {
                        int netId = findSavedNetId(wm.getConfiguredNetworks(), ssid);
                        if (netId >= 0) {
                            ok = wm.removeNetwork(netId);
                            saveConfigurationQuiet(wm);
                        }
                    }
                } catch (Exception e) {
                    SolarLog.e(TAG, "forget " + ssid, e);
                }
                if (!ok) SolarLog.e(TAG, "forget failed ssid=" + ssid, null);
                postResult(callback, ok);
            }
        }, "WifiForget").start();
    }

    public static void cancelPending() {
        WORK_GEN.incrementAndGet();
    }

    static int findSavedNetId(List<WifiConfiguration> configs, String ssid) {
        if (configs == null || ssid == null) return -1;
        String quoted = quotedSsid(ssid);
        for (WifiConfiguration conf : configs) {
            if (conf.SSID != null && conf.SSID.equals(quoted)) {
                return conf.networkId;
            }
        }
        return -1;
    }

    static String quotedSsid(String ssid) {
        return ssid == null ? "\"\"" : "\"" + ssid + "\"";
    }

    static boolean connectBlocking(WifiManager wm, String ssid, String password, boolean open) throws Exception {
        if (wm == null || ssid == null || ssid.isEmpty()) return false;
        int netId = findSavedNetId(wm.getConfiguredNetworks(), ssid);
        if (netId >= 0 && !open) {
            WifiConfiguration update = new WifiConfiguration();
            update.networkId = netId;
            update.SSID = quotedSsid(ssid);
            update.preSharedKey = "\"" + password + "\"";
            wm.updateNetwork(update);
        } else if (netId < 0) {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = quotedSsid(ssid);
            if (open) {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                conf.preSharedKey = "\"" + password + "\"";
            }
            netId = wm.addNetwork(conf);
        }
        if (netId < 0) return false;
        return enableSavedBlocking(wm, netId);
    }

    static boolean enableSavedBlocking(WifiManager wm, int netId) throws Exception {
        if (wm == null || netId < 0) return false;
        wm.disconnect();
        boolean enabled = wm.enableNetwork(netId, true);
        wm.reconnect();
        saveConfigurationQuiet(wm);
        return enabled;
    }

    private static void saveConfigurationQuiet(WifiManager wm) {
        try {
            wm.saveConfiguration();
        } catch (Exception e) {
            SolarLog.w(TAG, "saveConfiguration: " + e.getMessage());
        }
    }

    private static WifiManager wifiManager(Context context) {
        return (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private static void postResult(final Callback callback, final boolean ok) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyCallback(callback, ok);
            }
        });
    }

    private static void notifyCallback(Callback callback, boolean ok) {
        if (callback != null) callback.onComplete(ok);
    }

    static void selfCheck() {
        if (!"\"Cafe\"".equals(quotedSsid("Cafe"))) throw new AssertionError("quotedSsid");
        if (findSavedNetId(null, "x") != -1) throw new AssertionError("null configs");
    }
}
