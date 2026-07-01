package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Locale;

/** Resolves credentials for the hidden -diag Soulseek identity (log shipping only). */
public final class SolarDiagAccount {
    public static final String PREF_DIAG_USER = "solar_diag_user";
    public static final String PREF_DIAG_PASS = "solar_diag_pass";

    public final String username;
    public final String password;

    private SolarDiagAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static SolarDiagAccount load(SharedPreferences prefs, Context context) {
        SoulseekAccount main = SoulseekAccount.load(prefs, context);
        String storedUser = prefs.getString(PREF_DIAG_USER, "");
        String storedPass = prefs.getString(PREF_DIAG_PASS, "");
        if (storedUser != null && !storedUser.trim().isEmpty()
                && storedPass != null && !storedPass.isEmpty()) {
            return new SolarDiagAccount(storedUser.trim(), storedPass);
        }
        String user = resolveAvailableUsername(main.username, prefs);
        String pass = passwordForMainAccount(main, prefs);
        prefs.edit()
                .putString(PREF_DIAG_USER, user)
                .putString(PREF_DIAG_PASS, pass)
                .commit();
        return new SolarDiagAccount(user, pass);
    }

    /** Probe server until a -diag username accepts login (register on first success). */
    static String resolveAvailableUsername(String mainUsername, SharedPreferences prefs) {
        String[] candidates = SolarDeveloperAccounts.diagUsernameFallbacks(mainUsername);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            String pass = passwordForMainUsername(mainUsername, prefs);
            String err = SoulseekClient.testLoginPm(candidate, pass);
            if (err == null) return candidate;
            if (err.toLowerCase(Locale.US).contains("invalid password")
                    || err.toLowerCase(Locale.US).contains("incorrect")) {
                continue;
            }
            // Username free — Soulseek registers on first successful login.
            err = SoulseekClient.testLoginPm(candidate, pass);
            if (err == null) return candidate;
        }
        return SolarDeveloperAccounts.deriveDiagUsername(mainUsername);
    }

    private static String passwordForMainAccount(SoulseekAccount main, SharedPreferences prefs) {
        return passwordForMainUsername(main.username, prefs, main.custom, main.password);
    }

    private static String passwordForMainUsername(String mainUsername, SharedPreferences prefs) {
        boolean custom = prefs.getBoolean(SoulseekAccount.PREF_CUSTOM, false);
        String pass = prefs.getString(SoulseekAccount.PREF_PASS, "");
        return passwordForMainUsername(mainUsername, prefs, custom, pass);
    }

    private static String passwordForMainUsername(String mainUsername, SharedPreferences prefs,
            boolean custom, String mainPass) {
        if (!custom && mainPass != null && !mainPass.isEmpty()) {
            return mainPass;
        }
        if (custom) {
            String stored = prefs.getString(PREF_DIAG_PASS, "");
            if (stored != null && !stored.isEmpty()) return stored;
            String generated = randomPassword();
            prefs.edit().putString(PREF_DIAG_PASS, generated).commit();
            return generated;
        }
        return SoulseekAccount.generateAutoPassword();
    }

    private static String randomPassword() {
        byte[] buf = new byte[12];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : buf) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
