package com.solar.launcher.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/**
 * Context-menu modal present/dismiss — gated on menu_transitions like list drill.
 */
public final class ModalTransition {

    private ModalTransition() {}

    public static boolean enabled(Context ctx) {
        if (ctx == null) return false;
        if (ScreenTransition.systemAnimationsDisabled(ctx)) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(
                ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        return prefs.getBoolean(ListDrillTransition.PREF_MENU_TRANSITIONS, true);
    }

    public static boolean isAnimating() {
        return ScreenTransition.isModalAnimating();
    }

    public static void animatePresent(View scrim, View panel, Runnable onComplete) {
        ScreenTransition.animateModalPresent(scrim, panel, onComplete);
    }

    public static void animateDismiss(View scrim, View panel, Runnable onComplete) {
        ScreenTransition.animateModalDismiss(scrim, panel, onComplete);
    }
}
