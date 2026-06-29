package com.solar.launcher.deezer;

import android.content.SharedPreferences;

/** Stores Deezer ARL cookie and quality preferences (impersonation auth). */
public final class DeezerAccount {
    /** Must match MainActivity {@code PREFS} / {@link com.solar.launcher.LibraryBrowsePrefs}. */
    public static final String PREFS_NAME = "SOLAR_SETTINGS";
    /** Legacy web-setup bug wrote here — migrated on read. */
    private static final String LEGACY_PREFS_NAME = "solar_prefs";

    public static final String PREF_ARL = "deezer_arl";
    /** True only after the user pastes an ARL on the PC setup page — bundled ARLs never set this. */
    public static final String PREF_USER_ARL_CONFIGURED = "deezer_user_arl_configured";
    public static final String PREF_ENABLED = "deezer_enabled";
    public static final String PREF_QUALITY = "deezer_quality";
    public static final String PREF_USER_NAME = "deezer_user_name";
    public static final String PREF_USER_ID = "deezer_user_id";
    public static final String PREF_PREMIUM = "deezer_premium";
    public static final String PREF_INCLUDE_IN_GET_MUSIC = "deezer_include_in_get_music";

    public static final String QUALITY_MP3 = "mp3";
    public static final String QUALITY_FLAC = "flac";

    private static final int MIN_ARL_LEN = 64;

    /**
     * ponytail: REMOVE before public launch — shared tester Deezer ARL (premium).
     * Never written to prefs; used silently when the user has not set up their own account.
     */
    private static final String BUNDLED_DEMO_ARL =
            "69e3ba43fab2a4adc88dbcfc0cb5b5b7eee40cc6fbd4f935d9c58768202f43e3dc994087cfdcc5c7fedd0d9e0d0188da256184ac3a22f726db7e1b77ff2bb657f57270ff58e705da7f7ace0e2de2b7d22f67d3e3980a0da9b221ee65c3b5a33a";

    /** Last-resort free-tier bundled ARL — runtime only, never persisted. */
    private static final String BUNDLED_FREE_ARL =
            "ee329c76f397990855696c303c87b7b3e483d6db03f8b4c86f55c9c1562dea4888ea2610498afbea83a1f6ba07a2ce53259f7026ae08fef1c2480c20f290e673c11c07673c5f3624b012bb352541de7ec71e2ad1991331a83a49c3b410526b36";

    /** Download auth tier — user ARL first when configured, then silent bundled fallbacks. */
    public enum ArlFallbackTier {
        USER, DEMO, FREE
    }

    private DeezerAccount() {}

    public static boolean hasBundledDemoArl() {
        return BUNDLED_DEMO_ARL.length() >= MIN_ARL_LEN;
    }

    public static boolean hasBundledFreeArl() {
        return BUNDLED_FREE_ARL.length() >= MIN_ARL_LEN;
    }

    public static String bundledFreeArl() {
        return hasBundledFreeArl() ? BUNDLED_FREE_ARL : "";
    }

    /** Built-in premium tester ARL — never written to prefs. */
    public static String bundledDemoArl() {
        return hasBundledDemoArl() ? BUNDLED_DEMO_ARL : "";
    }

    /** User pasted their own ARL via PC setup (not a bundled silent fallback). */
    public static boolean isUserArlConfigured(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (!prefs.getBoolean(PREF_USER_ARL_CONFIGURED, false)) return false;
        String arl = loadArl(prefs);
        return arl.length() >= MIN_ARL_LEN && !isBundledArl(arl);
    }

    /** Deezer can run: user account and/or silent bundled premium/free ARLs. */
    public static boolean hasUsableDeezer(SharedPreferences prefs) {
        return isUserArlConfigured(prefs) || hasBundledDemoArl() || hasBundledFreeArl();
    }

    /**
     * @deprecated use {@link #hasUsableDeezer} or {@link #isUserArlConfigured} explicitly
     */
    public static boolean hasArl(SharedPreferences prefs) {
        return hasUsableDeezer(prefs);
    }

    /** Session ARL when no transient override: user if configured, else bundled premium, else free. */
    public static String defaultSessionArl(SharedPreferences prefs) {
        if (isUserArlConfigured(prefs)) return loadArl(prefs);
        if (hasBundledDemoArl()) return bundledDemoArl();
        if (hasBundledFreeArl()) return bundledFreeArl();
        return "";
    }

    public static boolean isBundledArl(String arl) {
        if (arl == null || arl.isEmpty()) return false;
        return (hasBundledDemoArl() && BUNDLED_DEMO_ARL.equals(arl))
                || (hasBundledFreeArl() && BUNDLED_FREE_ARL.equals(arl));
    }

    /** @deprecated bundled ARLs are not stored in prefs anymore */
    public static boolean isUsingDemoArl(SharedPreferences prefs) {
        return prefs != null && BUNDLED_DEMO_ARL.equals(loadArl(prefs));
    }

    /** @deprecated bundled ARLs are not stored in prefs anymore */
    public static boolean isUsingFreeArl(SharedPreferences prefs) {
        return prefs != null && BUNDLED_FREE_ARL.equals(loadArl(prefs));
    }

    /** User configured their own ARL; download may fall back to bundled tiers on failure. */
    public static boolean canFallbackToDemoArl(SharedPreferences prefs) {
        return isUserArlConfigured(prefs) && hasBundledDemoArl();
    }

    public static String arlForTier(ArlFallbackTier tier, SharedPreferences prefs) {
        if (tier == null) return "";
        if (tier == ArlFallbackTier.USER) {
            return isUserArlConfigured(prefs) ? loadArl(prefs) : "";
        }
        if (tier == ArlFallbackTier.DEMO) return bundledDemoArl();
        if (tier == ArlFallbackTier.FREE) return bundledFreeArl();
        return "";
    }

    /** Next looser tier after a failed download retry, or null when exhausted. */
    public static ArlFallbackTier nextFallbackTier(ArlFallbackTier current, SharedPreferences prefs) {
        if (current == ArlFallbackTier.USER) {
            if (canFallbackToDemoArl(prefs)) return ArlFallbackTier.DEMO;
            if (hasBundledFreeArl()) return ArlFallbackTier.FREE;
        } else if (current == ArlFallbackTier.DEMO) {
            if (hasBundledFreeArl()) return ArlFallbackTier.FREE;
        }
        return null;
    }

    public static String loadArl(SharedPreferences prefs) {
        if (prefs == null) return "";
        String arl = prefs.getString(PREF_ARL, "");
        return arl != null ? arl.trim() : "";
    }

    /** Save ARL from PC setup — marks the account as user-configured. */
    public static void saveUserArl(SharedPreferences prefs, String arl) {
        if (prefs == null) return;
        prefs.edit()
                .putString(PREF_ARL, arl != null ? arl.trim() : "")
                .putBoolean(PREF_USER_ARL_CONFIGURED, true)
                .commit();
    }

    /** Internal prefs write — does not mark user setup (legacy migration only). */
    public static void saveArl(SharedPreferences prefs, String arl) {
        if (prefs == null) return;
        prefs.edit().putString(PREF_ARL, arl != null ? arl.trim() : "").commit();
    }

    /** Copy user ARL from legacy store; strip any bundled ARL mistakenly saved to prefs. */
    public static void migrateLegacyArl(android.content.Context context, SharedPreferences prefs) {
        if (context == null || prefs == null) return;
        if (!isUserArlConfigured(prefs) && loadArl(prefs).length() < MIN_ARL_LEN) {
            SharedPreferences legacy = context.getApplicationContext()
                    .getSharedPreferences(LEGACY_PREFS_NAME, android.content.Context.MODE_PRIVATE);
            String legacyArl = legacy.getString(PREF_ARL, "");
            if (legacyArl != null && legacyArl.trim().length() >= MIN_ARL_LEN
                    && !isBundledArl(legacyArl.trim())) {
                saveUserArl(prefs, legacyArl.trim());
                String user = legacy.getString(PREF_USER_NAME, "");
                if (user != null && !user.isEmpty()) {
                    saveSessionInfo(prefs, user,
                            legacy.getString(PREF_USER_ID, ""),
                            legacy.getBoolean(PREF_PREMIUM, false));
                }
            }
        }
        stripBundledArlFromPrefs(prefs);
    }

    /** Remove old builds that seeded bundled ARL into prefs — bundled auth is runtime-only. */
    private static void stripBundledArlFromPrefs(SharedPreferences prefs) {
        if (prefs == null) return;
        String arl = loadArl(prefs);
        if (!isBundledArl(arl)) return;
        prefs.edit()
                .remove(PREF_ARL)
                .putBoolean(PREF_USER_ARL_CONFIGURED, false)
                .remove(PREF_USER_NAME)
                .remove(PREF_USER_ID)
                .putBoolean(PREF_PREMIUM, false)
                .commit();
    }

    /** @deprecated bundled ARL is never seeded into prefs */
    public static void seedBundledDemoArlIfNeeded(SharedPreferences prefs) {
        stripBundledArlFromPrefs(prefs);
    }

    public static boolean isEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        return prefs.getBoolean(PREF_ENABLED, true);
    }

    public static String loadQuality(SharedPreferences prefs) {
        if (prefs == null) return QUALITY_MP3;
        String q = prefs.getString(PREF_QUALITY, QUALITY_MP3);
        return QUALITY_FLAC.equals(q) ? QUALITY_FLAC : QUALITY_MP3;
    }

    /** User-visible account name — empty until the user configures via PC setup. */
    public static String displayLabel(SharedPreferences prefs, String demoAccountLabel) {
        if (!isUserArlConfigured(prefs)) return "";
        return displayUser(prefs);
    }

    public static void saveSessionInfo(SharedPreferences prefs, String userName, String userId,
            boolean premium) {
        if (prefs == null) return;
        if (!isUserArlConfigured(prefs)) {
            userName = "";
        }
        prefs.edit()
                .putString(PREF_USER_NAME, userName != null ? userName : "")
                .putString(PREF_USER_ID, userId != null ? userId : "")
                .putBoolean(PREF_PREMIUM, premium)
                .commit();
    }

    /** Clear user account; Deezer keeps working silently via bundled fallbacks. */
    public static void clear(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
                .remove(PREF_ARL)
                .putBoolean(PREF_USER_ARL_CONFIGURED, false)
                .remove(PREF_USER_NAME)
                .remove(PREF_USER_ID)
                .putBoolean(PREF_PREMIUM, false)
                .commit();
    }

    public static String displayUser(SharedPreferences prefs) {
        if (prefs == null) return "";
        String name = prefs.getString(PREF_USER_NAME, "");
        return name != null && !name.isEmpty() ? name : "";
    }

    public static long loadUserId(SharedPreferences prefs) {
        if (prefs == null) return 0;
        try {
            return Long.parseLong(prefs.getString(PREF_USER_ID, "0"));
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean includeInGetMusic(SharedPreferences prefs) {
        if (prefs == null) return true;
        return prefs.getBoolean(PREF_INCLUDE_IN_GET_MUSIC, true);
    }
}
