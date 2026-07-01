package com.solar.launcher.ui;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Animates dual-pane settings layout morphing to full-width Reach browse.
 */
public final class LayoutMorphTransition {

    public static final int MORPH_MS = 180;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);

    private static volatile boolean animating;

    private LayoutMorphTransition() {}

    public static boolean isAnimating() {
        return animating;
    }

    public static void morphToFullWidth(final FrameLayout menuHost, final View previewPane,
            final View maskView, final Runnable onComplete) {
        if (!canRun(menuHost)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        animating = true;
        final int startW = menuHost.getLayoutParams().width;
        final int startMargin = menuHost.getLayoutParams() instanceof FrameLayout.LayoutParams
                ? ((FrameLayout.LayoutParams) menuHost.getLayoutParams()).leftMargin : 0;
        final int targetW = menuHost.getResources().getDisplayMetrics().widthPixels;
        final int previewW = previewPane != null && previewPane.getWidth() > 0
                ? previewPane.getWidth() : targetW / 3;

        if (previewPane != null) {
            previewPane.setVisibility(View.VISIBLE);
            previewPane.setAlpha(1f);
            previewPane.setTranslationX(0f);
        }

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(MORPH_MS);
        va.setInterpolator(EASE);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = menuHost.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    flp.width = startW + (int) ((targetW - startW) * t);
                    flp.leftMargin = startMargin + (int) ((0 - startMargin) * t);
                    menuHost.setLayoutParams(flp);
                }
                if (previewPane != null) {
                    previewPane.setTranslationX(previewW * t);
                    previewPane.setAlpha(1f - t);
                }
                if (maskView != null) {
                    maskView.setAlpha(1f - t);
                }
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                animating = false;
                if (previewPane != null) {
                    previewPane.setVisibility(View.GONE);
                    previewPane.setAlpha(0f);
                    previewPane.setTranslationX(0f);
                }
                if (maskView != null) {
                    maskView.setAlpha(1f);
                }
                if (onComplete != null) onComplete.run();
            }
        });
        va.start();
    }

    public static void morphFromFullWidth(final FrameLayout menuHost, final View previewPane,
            final View maskView, final int restoredWidth, final int restoredMargin,
            final Runnable onComplete) {
        if (!canRun(menuHost)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        animating = true;
        final int startW = menuHost.getLayoutParams().width;
        if (previewPane != null) {
            previewPane.setVisibility(View.VISIBLE);
            previewPane.setAlpha(0f);
        }

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(MORPH_MS);
        va.setInterpolator(EASE);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = menuHost.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    flp.width = startW + (int) ((restoredWidth - startW) * t);
                    flp.leftMargin = (int) (restoredMargin * t);
                    menuHost.setLayoutParams(flp);
                }
                if (previewPane != null) {
                    previewPane.setAlpha(t);
                }
                if (maskView != null) {
                    maskView.setAlpha(t);
                }
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                animating = false;
                if (previewPane != null) {
                    previewPane.setVisibility(View.VISIBLE);
                    previewPane.setAlpha(1f);
                }
                if (onComplete != null) onComplete.run();
            }
        });
        va.start();
    }

    private static boolean canRun(View host) {
        if (host == null || animating || ScreenTransition.isAnimating()) return false;
        return !ScreenTransition.systemAnimationsDisabled(host.getContext());
    }
}
