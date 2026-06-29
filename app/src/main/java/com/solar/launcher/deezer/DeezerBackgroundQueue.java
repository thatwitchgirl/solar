package com.solar.launcher.deezer;

import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sequential background Deezer album downloads into an already-queued playback list. */
public final class DeezerBackgroundQueue {
    public static final class Job {
        public final DeezerResult track;
        public final File dest;

        public Job(DeezerResult track, File dest) {
            this.track = track;
            this.dest = dest;
        }
    }

    public interface Listener {
        void onTrackProgress(Job job, int percent);
        void onTrackComplete(Job job);
        void onTrackFailed(Job job, String error);
        void onAlbumComplete();
    }

    private final SharedPreferences prefs;
    private final Listener listener;
    private final List<Job> jobs = new ArrayList<Job>();
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private Thread worker;

    public DeezerBackgroundQueue(SharedPreferences prefs, Listener listener) {
        this.prefs = prefs;
        this.listener = listener;
    }

    public void start(List<Job> queueJobs) {
        enqueue(queueJobs);
    }

    /** Append jobs and start worker if idle — does not cancel in-flight downloads. */
    public synchronized void enqueue(List<Job> queueJobs) {
        if (queueJobs == null || queueJobs.isEmpty()) return;
        jobs.addAll(queueJobs);
        if (worker != null && worker.isAlive()) return;
        cancel.set(false);
        worker = new Thread(new Runnable() {
            @Override public void run() {
                runQueue();
            }
        }, "DeezerBgQueue");
        worker.start();
    }

    public synchronized void cancel() {
        cancel.set(true);
        if (worker != null && worker.isAlive()) worker.interrupt();
        worker = null;
    }

    private synchronized void runQueue() {
        String ext = "mp3";
        try {
            DeezerClient probe = new DeezerClient(prefs);
            if (!probe.isSessionValid()) probe.initSession();
            ext = probe.fileExtension();
        } catch (Exception ignored) {}
        for (int i = 0; i < jobs.size(); i++) {
            if (cancel.get()) return;
            final Job j = jobs.get(i);
            DeezerDownloadRunner.Progress progress = new DeezerDownloadRunner.Progress() {
                @Override public void onProgress(int percent, long done, long total) {
                    if (listener == null) return;
                    int pct = percent;
                    if (pct < 0 && total > 0) pct = (int) (done * 100 / total);
                    if (pct < 0 && done > 0) pct = 1;
                    listener.onTrackProgress(j, Math.min(99, Math.max(0, pct)));
                }
            };
            String err = DeezerDownloadRunner.downloadWithFallback(
                    prefs, j.track, j.dest, ext, progress);
            if (cancel.get()) return;
            if (err != null) {
                if (listener != null) listener.onTrackFailed(j, err);
            } else {
                if (listener != null) {
                    listener.onTrackProgress(j, 100);
                    listener.onTrackComplete(j);
                }
            }
        }
        if (!cancel.get() && listener != null) listener.onAlbumComplete();
        synchronized (this) {
            if (!cancel.get()) worker = null;
        }
    }

    /** Reserve a unique temp path under cache (empty file so queue rows can reference it). */
    public static File reservePlaceholder(File cacheDir, DeezerResult result, String ext)
            throws IOException {
        if (cacheDir != null && !cacheDir.isDirectory()) cacheDir.mkdirs();
        String safe = result.filenameBase() + "." + ext;
        File dest = new File(cacheDir, safe);
        int n = 1;
        while (dest.exists()) {
            dest = new File(cacheDir, result.filenameBase() + " (" + n + ")." + ext);
            n++;
        }
        if (!dest.createNewFile()) throw new IOException("mkdir placeholder");
        return dest;
    }
}
