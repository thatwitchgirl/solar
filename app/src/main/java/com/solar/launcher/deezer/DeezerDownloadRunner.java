package com.solar.launcher.deezer;

import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ponytail: one track download with ARL tier fallback (user → bundled premium → bundled free).
 * Used by background album queue and sync library saves.
 */
public final class DeezerDownloadRunner {
    public interface Progress {
        void onProgress(int percent, long done, long total);
    }

    private DeezerDownloadRunner() {}

    /** Ordered tiers: user ARL only when PC setup completed, then silent bundled fallbacks. */
    public static List<DeezerAccount.ArlFallbackTier> downloadTierOrder(SharedPreferences prefs) {
        List<DeezerAccount.ArlFallbackTier> out = new ArrayList<DeezerAccount.ArlFallbackTier>();
        if (DeezerAccount.isUserArlConfigured(prefs)) {
            out.add(DeezerAccount.ArlFallbackTier.USER);
        }
        if (DeezerAccount.hasBundledDemoArl()) {
            out.add(DeezerAccount.ArlFallbackTier.DEMO);
        }
        if (DeezerAccount.hasBundledFreeArl()) {
            out.add(DeezerAccount.ArlFallbackTier.FREE);
        }
        return out;
    }

    /**
     * Download one track; each tier is tried twice (immediate + one retry) before escalating.
     * @return null on success, error message on failure
     */
    public static String downloadWithFallback(SharedPreferences prefs, final DeezerResult result,
            final File dest, final String ext, final Progress progress) {
        if (result == null || dest == null) return "Invalid track";
        List<DeezerAccount.ArlFallbackTier> tiers = downloadTierOrder(prefs);
        if (tiers.isEmpty()) return "Deezer not available";
        String lastErr = "Download failed";
        for (DeezerAccount.ArlFallbackTier tier : tiers) {
            for (int attempt = 0; attempt < 2; attempt++) {
                String err = downloadOnce(prefs, tier, result, dest, ext, progress);
                if (err == null) return null;
                lastErr = err;
            }
        }
        if (dest.exists()) dest.delete();
        return lastErr;
    }

    private static String downloadOnce(SharedPreferences prefs, DeezerAccount.ArlFallbackTier tier,
            final DeezerResult result, final File dest, final String ext,
            final Progress progress) {
        if (dest.exists()) dest.delete();
        final DeezerClient client = new DeezerClient(prefs);
        String arl = DeezerAccount.arlForTier(tier, prefs);
        if (arl.isEmpty()) return "No ARL for tier";
        if (tier != DeezerAccount.ArlFallbackTier.USER) {
            client.setArlOverride(arl);
        }
        try {
            if (!client.isSessionValid()) client.initSession();
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "Session failed";
        }
        final Object lock = new Object();
        final boolean[] done = {false};
        final String[] err = {null};
        DeezerDownloader downloader = new DeezerDownloader(client);
        downloader.downloadToFile(result, dest, new DeezerDownloader.Listener() {
            @Override public void onProgress(long doneBytes, long total) {
                if (progress == null) return;
                int pct = total > 0 ? (int) (doneBytes * 100 / total) : -1;
                progress.onProgress(pct, doneBytes, total);
            }
            @Override public void onPartialReady(File d, long bytesRead) {
                if (progress != null && bytesRead > 0) {
                    progress.onProgress(-1, bytesRead, 0);
                }
            }
            @Override public void onComplete(File d, DeezerTrackData track) {
                done[0] = true;
                synchronized (lock) { lock.notifyAll(); }
            }
            @Override public void onError(String message) {
                err[0] = message != null ? message : "Download failed";
                synchronized (lock) { lock.notifyAll(); }
            }
        });
        synchronized (lock) {
            try {
                while (!done[0] && err[0] == null) {
                    lock.wait(300000);
                }
            } catch (InterruptedException e) {
                downloader.cancel();
                return "Cancelled";
            }
        }
        downloader.cancel();
        if (err[0] != null) return err[0];
        if (!dest.isFile() || dest.length() == 0) return "Empty file";
        return null;
    }
}
