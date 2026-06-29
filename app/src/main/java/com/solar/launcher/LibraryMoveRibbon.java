package com.solar.launcher;

import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * ponytail: 3-slot move ribbon for library playlist reorder — O(1) views per wheel step,
 * same ticker animation model as {@link ThemedContextMenu} queue move ribbon.
 */
final class LibraryMoveRibbon {
    private static final int TAG_RIBBON_SLOT = 0x70cc0020;

    static final int ANIM_MS = 130;
    static final int ENTER_MS = 150;
    static final int CONFIRM_SHOW_MS = 120;
    static final int CONFIRM_HOLD_MS = 450;

    interface Binder {
        void bindRow(FrameLayout row, int dataIndex, int ribbonSlot, boolean empty);

        int itemCount();

        int rowHeightPx();

        int rowWidthPx();
    }

    private final LinearLayout host;
    private final ScrollView scrollParent;
    private final Binder binder;
    private final android.app.Activity activity;
    private int moveFrom = -1;
    private int enterBrowseSlot = QueueMoveWindow.RIBBON_CENTER;
    private int hostPadTopPx = 0;
    private boolean ribbonActive;
    private boolean animating;
    private Runnable confirmClearTask;

    LibraryMoveRibbon(LinearLayout host, ScrollView scrollParent, Binder binder,
            android.app.Activity activity) {
        this.host = host;
        this.scrollParent = scrollParent;
        this.binder = binder;
        this.activity = activity;
    }

    int moveFrom() {
        return moveFrom;
    }

    boolean isAnimating() {
        return animating;
    }

    void enter(int pickIndex, int browseSlot, int padTopPx) {
        moveFrom = pickIndex;
        enterBrowseSlot = browseSlot;
        hostPadTopPx = Math.max(0, padTopPx);
        ribbonActive = false;
        animating = false;
        enterRibbon();
        animateRibbonEnter();
    }

    /** @deprecated use {@link #enter(int, int, int)} */
    void enter(int pickIndex) {
        enter(pickIndex, QueueMoveWindow.RIBBON_CENTER, 0);
    }

    void teardown() {
        if (confirmClearTask != null && host != null) {
            host.removeCallbacks(confirmClearTask);
            confirmClearTask = null;
        }
        animating = false;
        ribbonActive = false;
        moveFrom = -1;
        if (host != null) host.removeAllViews();
    }

    /** Wheel step after backing list order was updated. */
    void onMoveIndexChanged(int newIndex, int wheelDelta) {
        moveFrom = newIndex;
        if (!ribbonActive) {
            enterRibbon();
            populateRibbon();
            return;
        }
        Runnable bind = new Runnable() {
            @Override public void run() {
                populateRibbon();
            }
        };
        if (wheelDelta != 0 && !animating) {
            animateRibbonStrip(wheelDelta, bind);
        } else if (!animating) {
            bind.run();
        }
    }

    void flashConfirm(int index, final Runnable onComplete) {
        if (host == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        View row = findRibbonSlotRow(QueueMoveWindow.RIBBON_CENTER);
        if (!(row instanceof FrameLayout)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        FrameLayout fl = (FrameLayout) row;
        binder.bindRow(fl, index, QueueMoveWindow.RIBBON_CENTER, false);
        android.widget.ImageView confirm = (android.widget.ImageView) fl.findViewWithTag(
                MoveRibbonRows.TAG_CONFIRM);
        if (confirm == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (confirmClearTask != null) {
            host.removeCallbacks(confirmClearTask);
            confirmClearTask = null;
        }
        confirm.animate().cancel();
        confirm.setAlpha(0f);
        confirm.setVisibility(View.VISIBLE);
        confirm.animate().alpha(1f).setDuration(CONFIRM_SHOW_MS).start();
        confirmClearTask = new Runnable() {
            @Override public void run() {
                confirmClearTask = null;
                confirm.animate()
                        .alpha(0f)
                        .setDuration(CONFIRM_SHOW_MS)
                        .setListener(new android.animation.AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(android.animation.Animator animation) {
                                confirm.animate().setListener(null);
                                confirm.setVisibility(View.GONE);
                                confirm.setAlpha(1f);
                                if (onComplete != null) onComplete.run();
                            }
                        })
                        .start();
            }
        };
        host.postDelayed(confirmClearTask, CONFIRM_HOLD_MS);
    }

    private void enterRibbon() {
        if (host == null) return;
        ribbonActive = true;
        animating = false;
        host.removeAllViews();
        host.setPadding(0, hostPadTopPx, 0, 0);
        int rowH = binder.rowHeightPx();
        host.addView(createSlotRow(QueueMoveWindow.RIBBON_ABOVE, rowH));
        host.addView(createSlotRow(QueueMoveWindow.RIBBON_CENTER, rowH));
        host.addView(createSlotRow(QueueMoveWindow.RIBBON_BELOW, rowH));
        if (scrollParent != null) scrollParent.scrollTo(0, 0);
    }

    private FrameLayout createSlotRow(int ribbonSlot, int rowH) {
        FrameLayout row = MoveRibbonRows.createLibraryMoveRow(activity, rowH);
        row.setTag(TAG_RIBBON_SLOT, Integer.valueOf(ribbonSlot));
        return row;
    }

    private View findRibbonSlotRow(int ribbonSlot) {
        if (host == null) return null;
        for (int c = 0; c < host.getChildCount(); c++) {
            View row = host.getChildAt(c);
            Object tag = row.getTag(TAG_RIBBON_SLOT);
            if (tag instanceof Integer && ((Integer) tag).intValue() == ribbonSlot) {
                return row;
            }
        }
        return null;
    }

    private void populateRibbon() {
        if (!ribbonActive || host == null) return;
        final int count = binder.itemCount();
        final int moveIdx = moveFrom;
        populateRibbonRow(findRibbonSlotRow(QueueMoveWindow.RIBBON_ABOVE),
                QueueMoveWindow.ribbonAboveIndex(moveIdx), QueueMoveWindow.RIBBON_ABOVE);
        populateRibbonRow(findRibbonSlotRow(QueueMoveWindow.RIBBON_CENTER),
                moveIdx, QueueMoveWindow.RIBBON_CENTER);
        populateRibbonRow(findRibbonSlotRow(QueueMoveWindow.RIBBON_BELOW),
                QueueMoveWindow.ribbonBelowIndex(moveIdx, count), QueueMoveWindow.RIBBON_BELOW);
        if (scrollParent != null) scrollParent.scrollTo(0, 0);
    }

    private void populateRibbonRow(View row, int dataIndex, int ribbonSlot) {
        if (!(row instanceof FrameLayout)) return;
        FrameLayout fl = (FrameLayout) row;
        boolean empty = dataIndex < 0 || dataIndex >= binder.itemCount();
        row.setTranslationY(0f);
        if (empty) {
            row.setVisibility(View.INVISIBLE);
            row.setAlpha(0.35f);
            MoveRibbonRows.bindEmptySlot(row);
            return;
        }
        row.setVisibility(View.VISIBLE);
        row.setAlpha(ribbonSlot == QueueMoveWindow.RIBBON_CENTER ? 1f : 0.82f);
        binder.bindRow(fl, dataIndex, ribbonSlot, false);
    }

    private void animateRibbonEnter() {
        populateRibbon();
        int slotH = binder.rowHeightPx() + 2;
        float startTy = (enterBrowseSlot - QueueMoveWindow.RIBBON_CENTER) * (float) slotH;
        if (Math.abs(startTy) < 0.5f) {
            animateRibbonEnterFade();
            return;
        }
        animating = true;
        DecelerateInterpolator ease = new DecelerateInterpolator(1.35f);
        final int[] remaining = new int[] { QueueMoveWindow.VISIBLE_ROWS };
        for (int slot = QueueMoveWindow.RIBBON_ABOVE; slot <= QueueMoveWindow.RIBBON_BELOW; slot++) {
            final View row = findRibbonSlotRow(slot);
            if (row == null || row.getVisibility() != View.VISIBLE) {
                remaining[0]--;
                continue;
            }
            row.animate().cancel();
            row.setTranslationY(startTy);
            row.animate()
                    .translationY(0f)
                    .setDuration(ENTER_MS)
                    .setInterpolator(ease)
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            row.animate().setListener(null);
                            remaining[0]--;
                            if (remaining[0] <= 0) animating = false;
                        }
                    });
        }
        if (remaining[0] <= 0) animating = false;
    }

    private void animateRibbonEnterFade() {
        animating = true;
        DecelerateInterpolator ease = new DecelerateInterpolator(1.35f);
        final int[] remaining = new int[] { 0 };
        for (int slot = QueueMoveWindow.RIBBON_ABOVE; slot <= QueueMoveWindow.RIBBON_BELOW; slot++) {
            final View row = findRibbonSlotRow(slot);
            if (row == null || row.getVisibility() != View.VISIBLE) continue;
            remaining[0]++;
            final float targetAlpha = slot == QueueMoveWindow.RIBBON_CENTER ? 1f : 0.82f;
            row.animate().cancel();
            row.setTranslationY(0f);
            row.setAlpha(0.45f);
            row.animate()
                    .alpha(targetAlpha)
                    .setDuration(ENTER_MS)
                    .setInterpolator(ease)
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            row.animate().setListener(null);
                            remaining[0]--;
                            if (remaining[0] <= 0) animating = false;
                        }
                    });
        }
        if (remaining[0] <= 0) animating = false;
    }

    private void animateRibbonStrip(final int wheelDelta, final Runnable onEnd) {
        if (wheelDelta == 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        View above = findRibbonSlotRow(QueueMoveWindow.RIBBON_ABOVE);
        View below = findRibbonSlotRow(QueueMoveWindow.RIBBON_BELOW);
        View center = findRibbonSlotRow(QueueMoveWindow.RIBBON_CENTER);
        if (center == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        center.animate().cancel();
        center.setTranslationY(0f);
        center.setAlpha(1f);
        int slotH = binder.rowHeightPx() + 2;
        final float dy = wheelDelta > 0 ? -slotH : slotH;
        final View[] outers = new View[] { above, below };
        int animCount = 0;
        for (View v : outers) {
            if (v != null && v.getVisibility() == View.VISIBLE) animCount++;
        }
        if (animCount == 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        animating = true;
        final int[] remaining = new int[] { animCount };
        for (final View v : outers) {
            if (v == null || v.getVisibility() != View.VISIBLE) continue;
            v.animate().cancel();
            v.setTranslationY(0f);
            v.animate()
                    .translationY(dy)
                    .alpha(0.42f)
                    .setDuration(ANIM_MS)
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            v.animate().setListener(null);
                            remaining[0]--;
                            if (remaining[0] <= 0) finishStripAnim(onEnd);
                        }
                    });
        }
    }

    private void finishStripAnim(Runnable onEnd) {
        animating = false;
        for (int slot = QueueMoveWindow.RIBBON_ABOVE; slot <= QueueMoveWindow.RIBBON_BELOW; slot++) {
            View row = findRibbonSlotRow(slot);
            if (row == null) continue;
            row.animate().cancel();
            row.setTranslationY(0f);
        }
        if (onEnd != null) onEnd.run();
    }
}
