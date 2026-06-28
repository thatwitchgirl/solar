package com.solar.launcher;

import android.view.KeyEvent;

/**
 * Y1 hardware keys — InputReader uses Generic.kl for mtk-kpd/mtk-tpd-kpd on Y1 firmware.
 * Wheel: scancode 114/115 → DPAD_UP/DOWN (19/20); 105/106 → MEDIA_PLAY/PAUSE (126/127).
 * Accept both — firmware paths differ; side 165/163 → MEDIA_PREVIOUS/NEXT (88/87).
 */
public final class Y1InputKeys {

    public static final int KEY_BACK = KeyEvent.KEYCODE_BACK; // 4
    public static final int KEY_TRACK_PREV = KeyEvent.KEYCODE_MEDIA_PREVIOUS; // 88
    public static final int KEY_TRACK_NEXT = KeyEvent.KEYCODE_MEDIA_NEXT; // 87
    public static final int KEY_CENTER = KeyEvent.KEYCODE_ENTER; // 66
    public static final int KEY_PLAY_PAUSE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; // 85
    public static final int KEY_WHEEL_UP = KeyEvent.KEYCODE_MEDIA_PLAY; // 126
    public static final int KEY_WHEEL_DOWN = KeyEvent.KEYCODE_MEDIA_PAUSE; // 127

    private Y1InputKeys() {}

    public static boolean isBackKey(int keyCode) {
        return keyCode == KEY_BACK || keyCode == 4;
    }

    public static boolean isCenterKey(int keyCode) {
        return keyCode == KEY_CENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == 66 || keyCode == 23;
    }

    public static boolean isPlayPauseKey(int keyCode) {
        return keyCode == KEY_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == 86;
    }

    /** Wheel up — Y1-Rockbox.kl 114 → DPAD_UP (19) or 105 → MEDIA_PLAY (126). */
    public static boolean isWheelUp(int keyCode) {
        return keyCode == KEY_WHEEL_UP || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == 19;
    }

    /** Wheel down — Y1-Rockbox.kl 115 → DPAD_DOWN (20) or 106 → MEDIA_PAUSE (127). */
    public static boolean isWheelDown(int keyCode) {
        return keyCode == KEY_WHEEL_DOWN || keyCode == 127
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == 20;
    }

    /** Side previous — mtk-kpd scancode 165 → MEDIA_PREVIOUS (88). */
    public static boolean isTrackPreviousKey(int keyCode) {
        return keyCode == KEY_TRACK_PREV || keyCode == 88;
    }

    /** Side next — mtk-kpd scancode 163 → MEDIA_NEXT (87). */
    public static boolean isTrackNextKey(int keyCode) {
        return keyCode == KEY_TRACK_NEXT || keyCode == 87;
    }

    public static boolean isWheelKey(int keyCode) {
        return isWheelUp(keyCode) || isWheelDown(keyCode);
    }

    public static int wheelMenuDelta(int keyCode) {
        if (isWheelUp(keyCode)) return -1;
        if (isWheelDown(keyCode)) return 1;
        return 0;
    }

    /** AVRCP PASSTHROUGH PLAY → MEDIA_PLAY (126) only; not DPAD_UP (19) from Y1 wheel. */
    public static boolean isDiscreteMediaPlay(int keyCode) {
        return keyCode == KEY_WHEEL_UP || keyCode == 126;
    }

    /** AVRCP PASSTHROUGH PAUSE → MEDIA_PAUSE (127) only; not DPAD_DOWN (20) from Y1 wheel. */
    public static boolean isDiscreteMediaPause(int keyCode) {
        return keyCode == KEY_WHEEL_DOWN || keyCode == 127;
    }

    /** Discrete skip from AVRCP.kl — same keycodes as Y1 side keys (87/88). */
    public static boolean isAvrcpSkipNext(int keyCode) {
        return keyCode == KEY_TRACK_NEXT || keyCode == 87;
    }

    public static boolean isAvrcpSkipPrevious(int keyCode) {
        return keyCode == KEY_TRACK_PREV || keyCode == 88;
    }

    public static boolean isAvrcpSkipKey(int keyCode) {
        return isAvrcpSkipNext(keyCode) || isAvrcpSkipPrevious(keyCode);
    }

    /** Keycodes BT remotes may send via AVRCP.kl or ACTION_MEDIA_BUTTON. */
    public static boolean isAvrcpMediaTransportKeyCode(int keyCode) {
        return isDiscreteMediaPlay(keyCode) || isDiscreteMediaPause(keyCode)
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == 86
                || isAvrcpSkipKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == 79;
    }

    /** Volume from BT remote / ACTION_MEDIA_BUTTON — not Y1 wheel. */
    public static boolean isBluetoothVolumeKeyCode(int keyCode) {
        return isVolumeDownKey(keyCode) || isVolumeUpKey(keyCode);
    }

    /** Keys Solar handles from ACTION_MEDIA_BUTTON (AVRCP + volume). */
    public static boolean isBluetoothMediaButtonKeyCode(int keyCode) {
        return isAvrcpMediaTransportKeyCode(keyCode) || isBluetoothVolumeKeyCode(keyCode);
    }

    public static boolean isVolumeDownKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == 25 || keyCode == 114 || keyCode == 160;
    }

    public static boolean isVolumeUpKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == 24 || keyCode == 115 || keyCode == 161;
    }

    static void selfCheckWheelMapping() {
        if (wheelMenuDelta(126) != -1) throw new AssertionError("wheel MEDIA_PLAY");
        if (wheelMenuDelta(127) != 1) throw new AssertionError("wheel MEDIA_PAUSE");
        if (wheelMenuDelta(19) != -1) throw new AssertionError("wheel DPAD_UP");
        if (wheelMenuDelta(20) != 1) throw new AssertionError("wheel DPAD_DOWN");
        if (isDiscreteMediaPlay(19)) throw new AssertionError("dpad up not avrcp play");
        if (isDiscreteMediaPause(20)) throw new AssertionError("dpad down not avrcp pause");
        if (wheelMenuDelta(88) != 0) throw new AssertionError("track prev not wheel");
        if (wheelMenuDelta(87) != 0) throw new AssertionError("track next not wheel");
        if (!isTrackPreviousKey(88)) throw new AssertionError("track prev media");
        if (!isTrackNextKey(87)) throw new AssertionError("track next media");
        if (isTrackPreviousKey(21)) throw new AssertionError("dpad left not track prev");
        if (isTrackNextKey(22)) throw new AssertionError("dpad right not track next");
    }
}
