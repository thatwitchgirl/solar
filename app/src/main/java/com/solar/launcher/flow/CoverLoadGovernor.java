package com.solar.launcher.flow;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single-thread cover decode queue — dedup in-flight keys, distance-ordered priority.
 * Rockbox PictureFlow loader: one slide per pump, {@link Thread#yield()} between decodes.
 * Decoded bitmaps land in {@link FlowCoverCache} immediately on the UI thread.
 */
public final class CoverLoadGovernor {

    public interface DecodeTask {
        Bitmap run();
    }

    public interface Callback {
        void onDecoded(String coverKey, Bitmap bitmap);
    }

    private static final class Queued {
        final String coverKey;
        final int distance;
        final DecodeTask task;

        Queued(String coverKey, int distance, DecodeTask task) {
            this.coverKey = coverKey;
            this.distance = distance;
            this.task = task;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Set<String> inFlight = new HashSet<String>();
    private final PriorityQueue<Queued> queue = new PriorityQueue<Queued>(16, new Comparator<Queued>() {
        @Override
        public int compare(Queued a, Queued b) {
            return Integer.compare(a.distance, b.distance);
        }
    });
    private Callback callback;
    private Runnable frameKick;
    private boolean pumpRunning;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setFrameKick(Runnable frameKick) {
        this.frameKick = frameKick;
    }

    private void kickUiFrame() {
        if (frameKick != null) frameKick.run();
    }

    /** True when key is queued or decoding — draw path must not re-request. */
    public boolean isPending(String coverKey) {
        if (coverKey == null || coverKey.isEmpty()) return false;
        synchronized (this) {
            return inFlight.contains(coverKey);
        }
    }

    public void request(String coverKey, int distance, DecodeTask task) {
        if (coverKey == null || coverKey.isEmpty() || task == null) return;
        synchronized (this) {
            if (inFlight.contains(coverKey)) return;
            inFlight.add(coverKey);
            queue.offer(new Queued(coverKey, distance, task));
        }
        schedulePump();
    }

    private void schedulePump() {
        synchronized (this) {
            if (pumpRunning) return;
            pumpRunning = true;
        }
        executor.execute(pumpLoop);
    }

    /** ponytail: one worker loop drains queue — Rockbox while(queue_empty) load_one; yield. */
    private final Runnable pumpLoop = new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    final Queued job;
                    synchronized (CoverLoadGovernor.this) {
                        job = queue.poll();
                        if (job == null) {
                            pumpRunning = false;
                            return;
                        }
                    }
                    Thread.yield();
                    final Bitmap bmp = job.task.run();
                    final String coverKey = job.coverKey;
                    synchronized (CoverLoadGovernor.this) {
                        inFlight.remove(coverKey);
                    }
                    final CountDownLatch applied = new CountDownLatch(1);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (bmp != null && callback != null) {
                                    callback.onDecoded(coverKey, bmp);
                                }
                                kickUiFrame();
                            } finally {
                                applied.countDown();
                            }
                        }
                    });
                    try {
                        applied.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    Thread.yield();
                }
            } finally {
                synchronized (CoverLoadGovernor.this) {
                    if (queue.isEmpty()) {
                        pumpRunning = false;
                    } else {
                        schedulePump();
                    }
                }
            }
        }
    };

    public void cancelAll() {
        synchronized (this) {
            queue.clear();
            inFlight.clear();
            pumpRunning = false;
        }
    }

    /** Queued + in-flight — for debug perf logs and anim tick keepalive. */
    public int pendingWorkCount() {
        synchronized (this) {
            return queue.size() + inFlight.size();
        }
    }
}
