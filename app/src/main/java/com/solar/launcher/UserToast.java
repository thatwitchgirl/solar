package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Toasts with optional gating for technical error messages. */
public final class UserToast {
    private static final Set<String> USER_FACING_ERRORS = new HashSet<String>(Arrays.asList(
            "toast_pairing_failed",
            "toast_audio_connect_failed",
            "toast_corrupted_file",
            "podcasts_delete_failed",
            "podcasts_save_failed",
            "podcasts_stream_failed",
            "podcasts_stream_error",
            "soulseek_save_failed",
            "soulseek_login_failed",
            "soulseek_login_failed_check_account",
            "soulseek_invalid_username",
            "soulseek_password_required",
            "library_playlist_save_failed",
            "dialog_power_off_blocked",
            "theme_gallery_wifi_required",
            "toast_wifi_required",
            "toast_internet_required",
            "toast_requires_wifi",
            "toast_network_required",
            "soulseek_wifi_required",
            "toast_wifi_forget_failed",
            "toast_bt_forget_failed",
            "update_download_failed",
            "update_install_failed",
            "update_reboot_failed",
            "toast_install_failed",
            "context_action_lock_failed",
            "toast_fm_unavailable",
            "toast_audio_permission",
            "loading_themes_failed",
            "toast_time_failed",
            "reach_error_too_many_peers",
            "soulseek_error_unknown",
            "soulseek_error_peer_unreachable",
            "soulseek_error_nat",
            "soulseek_error_transfer_timeout",
            "soulseek_error_file_timeout"));

    private UserToast() {}

    public static void show(Context ctx, CharSequence text) {
        show(ctx, text, Toast.LENGTH_SHORT);
    }

    public static void show(Context ctx, CharSequence text, int duration) {
        if (ctx == null || text == null || text.length() == 0) return;
        Toast.makeText(ctx, text, duration).show();
    }

    public static void show(Context ctx, int resId) {
        show(ctx, resId, Toast.LENGTH_SHORT);
    }

    public static void show(Context ctx, int resId, int duration) {
        if (ctx == null) return;
        show(ctx, ctx.getString(resId), duration);
    }

    public static void showError(Context ctx, SharedPreferences prefs, int resId) {
        showError(ctx, prefs, resId, Toast.LENGTH_SHORT);
    }

    public static void showError(Context ctx, SharedPreferences prefs, int resId, int duration) {
        if (shouldShowError(ctx, prefs, resId)) {
            show(ctx, resId, duration);
        }
    }

    /** Dynamic / technical messages — only when Show Errors is enabled. */
    public static void showError(Context ctx, SharedPreferences prefs, CharSequence text, int duration) {
        if (DebugPrefs.showErrors(prefs)) {
            show(ctx, text, duration);
        }
    }

    public static boolean shouldShowError(Context ctx, SharedPreferences prefs, int errorResId) {
        if (DebugPrefs.showErrors(prefs)) return true;
        return isUserFacingError(ctx, errorResId);
    }

    /** Failures users should see even when Show Errors is off. */
    static boolean isUserFacingError(Context ctx, int resId) {
        if (ctx == null) return false;
        try {
            return isUserFacingErrorName(ctx.getResources().getResourceEntryName(resId));
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isUserFacingErrorName(String name) {
        return name != null && USER_FACING_ERRORS.contains(name);
    }
}
