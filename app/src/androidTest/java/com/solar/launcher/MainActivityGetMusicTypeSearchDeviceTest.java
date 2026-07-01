package com.solar.launcher;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Regression: Get Music → Type search… must not crash. */
@RunWith(AndroidJUnit4.class)
public class MainActivityGetMusicTypeSearchDeviceTest {
    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class, true, false);

    @Test
    public void getMusicTypeSearch_opensKeyboardWithoutCrash() throws InterruptedException {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.setClassName(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                "com.solar.launcher.MainActivity");
        final MainActivity activity = activityRule.launchActivity(launch);
        Thread.sleep(1500);

        final int[] screen = new int[1];
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.deviceTestOpenGetMusicTypeSearch();
                screen[0] = activity.deviceTestCurrentScreenState();
            }
        });
        Thread.sleep(800);

        if (screen[0] != MainActivity.STATE_WIFI_KEYBOARD) {
            throw new AssertionError("expected keyboard screen, got state=" + screen[0]);
        }
    }
}
