package com.solar.launcher;

/** Fixed 3-slot ribbon for context-menu queue reorder — center is always the mover. */
final class QueueMoveWindow {
    static final int VISIBLE_ROWS = 3;
    static final int RIBBON_ABOVE = 0;
    static final int RIBBON_CENTER = 1;
    static final int RIBBON_BELOW = 2;

    private QueueMoveWindow() {}

    static int ribbonAboveIndex(int moveIdx) {
        return moveIdx > 0 ? moveIdx - 1 : -1;
    }

    static int ribbonBelowIndex(int moveIdx, int count) {
        if (moveIdx < 0 || count <= 0) return -1;
        return moveIdx < count - 1 ? moveIdx + 1 : -1;
    }

    /** One wheel step — now playing moves like any other row. */
    static int nextMoveIndex(int from, int delta, int size) {
        if (size <= 0) return from;
        int to = from + delta;
        if (to < 0 || to >= size) return from;
        return to;
    }

    static boolean canMoveTo(int from, int to) {
        return from >= 0 && to >= 0 && from != to;
    }
}
