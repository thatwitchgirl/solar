package com.solar.launcher;

import android.view.KeyEvent;

/**
 * Y1 scroll wheel and volume keys under unified {@code Rockbox.kl} (Generic.kl).
 * ponytail: canonical wheel is DPAD_UP/DOWN (19/20); legacy Stock.kl used 21/22.
 */
public final class Y1InputKeys {

    private Y1InputKeys() {}

    /** Wheel up / previous row (Rockbox.kl 114→19; legacy Stock 103→21). */
    public static boolean isWheelUp(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == 19
                || keyCode == 21;
    }

    /** Wheel down / next row (Rockbox.kl 115→20; legacy Stock 108→22). */
    public static boolean isWheelDown(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == 20
                || keyCode == 22;
    }

    public static boolean isWheelKey(int keyCode) {
        return isWheelUp(keyCode) || isWheelDown(keyCode);
    }

    /** -1 up, +1 down, 0 if not a wheel key. */
    public static int wheelMenuDelta(int keyCode) {
        if (isWheelUp(keyCode)) return -1;
        if (isWheelDown(keyCode)) return 1;
        return 0;
    }

    public static boolean isVolumeDownKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == 25
                || keyCode == 114
                || keyCode == 160;
    }

    public static boolean isVolumeUpKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == 24
                || keyCode == 115
                || keyCode == 161;
    }

    /** ponytail: self-check — fails if Rockbox.kl wheel mapping regresses. */
    static void selfCheckWheelMapping() {
        if (wheelMenuDelta(19) != -1) throw new AssertionError("DPAD_UP");
        if (wheelMenuDelta(20) != 1) throw new AssertionError("DPAD_DOWN");
        if (wheelMenuDelta(21) != -1) throw new AssertionError("legacy LEFT");
        if (wheelMenuDelta(22) != 1) throw new AssertionError("legacy RIGHT");
    }
}
