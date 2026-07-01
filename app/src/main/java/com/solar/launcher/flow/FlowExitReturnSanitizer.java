package com.solar.launcher.flow;

/**
 * Validates where Flow Back should land — never Flow or Now Playing (would loop).
 */
public final class FlowExitReturnSanitizer {

    private FlowExitReturnSanitizer() {}

    /**
     * @param candidate requested exit screen (e.g. from {@code flowLaunchRequest.returnScreen})
     * @param entrySnapshot screen active when user first opened Flow (menu, browser, …)
     * @param stateFlow {@code MainActivity.STATE_FLOW}
     * @param statePlayer {@code MainActivity.STATE_PLAYER}
     * @param stateMenu {@code MainActivity.STATE_MENU}
     */
    public static int sanitize(int candidate, int entrySnapshot,
            int stateFlow, int statePlayer, int stateMenu) {
        if (candidate == stateFlow || candidate == statePlayer || candidate < 0) {
            if (entrySnapshot >= 0 && entrySnapshot != stateFlow && entrySnapshot != statePlayer) {
                return entrySnapshot;
            }
            return stateMenu;
        }
        return candidate;
    }
}
