package com.solar.launcher.flow;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.solar.launcher.Debug1cf0c7Log;
import com.solar.launcher.DebugB8b871Log;
import com.solar.launcher.ui.ScreenTransition;

/**
 * Lightweight alpha crossfade between Flow and Now Playing layouts.
 * Uses ViewPropertyAnimator for Choreographer-synced pacing on MT6572.
 */
public final class FlowScreenTransition {

    public static final int CROSSFADE_MS = ScreenTransition.CROSSFADE_MS;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);

    private FlowScreenTransition() {}

    public static void crossfadeToPlayer(final View flowLayout, final View playerLayout,
            final View bgBlur, final Runnable onComplete) {
        if (playerLayout == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        playerLayout.setVisibility(View.VISIBLE);
        playerLayout.setAlpha(0f);
        if (bgBlur != null) bgBlur.setAlpha(0f);
        if (flowLayout != null) flowLayout.setAlpha(1f);
        ScreenTransition.enableHardwareLayer(flowLayout);
        ScreenTransition.enableHardwareLayer(playerLayout);
        ScreenTransition.enableHardwareLayer(bgBlur);

        final Runnable done = new Runnable() {
            @Override
            public void run() {
                ScreenTransition.clearHardwareLayer(flowLayout);
                ScreenTransition.clearHardwareLayer(playerLayout);
                ScreenTransition.clearHardwareLayer(bgBlur);
                if (onComplete != null) onComplete.run();
            }
        };
        playerLayout.animate().alpha(1f).setDuration(CROSSFADE_MS).setInterpolator(EASE)
                .setListener(ScreenTransition.endListenerFor(done)).start();
        if (bgBlur != null) {
            bgBlur.animate().alpha(1f).setDuration(CROSSFADE_MS).setInterpolator(EASE).start();
        }
        if (flowLayout != null) {
            flowLayout.animate().alpha(0f).setDuration(CROSSFADE_MS).setInterpolator(EASE).start();
        } else if (onComplete == null) {
            done.run();
        }
    }

    public static void crossfadeToFlow(final View flowLayout, final View playerLayout,
            final View bgBlur, final Runnable onComplete) {
        if (flowLayout == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        flowLayout.setVisibility(View.VISIBLE);
        flowLayout.setAlpha(0f);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("flowW", flowLayout.getWidth());
            d.put("flowH", flowLayout.getHeight());
            d.put("durationMs", CROSSFADE_MS);
            DebugB8b871Log.log(flowLayout.getContext(), "FlowScreenTransition.crossfadeToFlow",
                    "start", "H-A", d);
        } catch (Exception ignored) {}
        // #endregion
        if (playerLayout != null) {
            playerLayout.setVisibility(View.VISIBLE);
            playerLayout.setAlpha(1f);
        }
        if (bgBlur != null) bgBlur.setAlpha(1f);
        ScreenTransition.enableHardwareLayer(flowLayout);
        ScreenTransition.enableHardwareLayer(playerLayout);
        ScreenTransition.enableHardwareLayer(bgBlur);

        final Runnable done = new Runnable() {
            @Override
            public void run() {
                if (playerLayout != null) {
                    playerLayout.setAlpha(0f);
                    playerLayout.setVisibility(View.GONE);
                }
                if (bgBlur != null) bgBlur.setAlpha(0f);
                flowLayout.setAlpha(1f);
                ScreenTransition.clearHardwareLayer(flowLayout);
                ScreenTransition.clearHardwareLayer(playerLayout);
                ScreenTransition.clearHardwareLayer(bgBlur);
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("flowAlpha", flowLayout.getAlpha());
                    d.put("childCount", flowLayout instanceof android.view.ViewGroup
                            ? ((android.view.ViewGroup) flowLayout).getChildCount() : -1);
                    Debug1cf0c7Log.log(flowLayout.getContext(),
                            "FlowScreenTransition.crossfadeToFlow", "complete", "H-A", d);
                } catch (Exception ignored) {}
                // #endregion
                if (onComplete != null) onComplete.run();
            }
        };
        flowLayout.animate().alpha(1f).setDuration(CROSSFADE_MS).setInterpolator(EASE)
                .setListener(ScreenTransition.endListenerFor(done)).start();
        if (playerLayout != null) {
            playerLayout.animate().alpha(0f).setDuration(CROSSFADE_MS).setInterpolator(EASE).start();
        }
        if (bgBlur != null) {
            bgBlur.animate().alpha(0f).setDuration(CROSSFADE_MS).setInterpolator(EASE).start();
        }
    }

    public static void fadeInView(final View view, final Runnable onComplete) {
        ScreenTransition.fadeInView(view, onComplete);
    }

    public static void fadeOutView(final View view, final Runnable onComplete) {
        ScreenTransition.fadeOutView(view, onComplete);
    }
}
