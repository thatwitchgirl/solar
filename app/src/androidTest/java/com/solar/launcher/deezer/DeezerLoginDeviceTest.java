package com.solar.launcher.deezer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Pass -e deezer_arl <cookie> to verify initSession on device (no secrets in source). */
@RunWith(AndroidJUnit4.class)
public class DeezerLoginDeviceTest {
    @Test
    public void device_initSessionWithArlArg() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String arl = args.getString("deezer_arl");
        if (arl == null || arl.trim().length() < 64) {
            throw new AssertionError("Missing -e deezer_arl (min 64 chars)");
        }
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = ctx.getSharedPreferences("solar_prefs", Context.MODE_PRIVATE);
        DeezerAccount.saveUserArl(prefs, arl.trim());
        DeezerClient client = new DeezerClient(prefs);
        boolean ok = client.initSession();
        org.json.JSONObject d = new org.json.JSONObject();
        d.put("initOk", ok);
        d.put("user", client.userName());
        d.put("premium", client.isPremium());
        DeezerDebugLog.log(ctx, "DeezerLoginDeviceTest", "initSession", "B", d);
        if (!ok) throw new AssertionError("initSession returned false");
        if (!client.isSessionValid()) throw new AssertionError("session not valid");
    }
}
