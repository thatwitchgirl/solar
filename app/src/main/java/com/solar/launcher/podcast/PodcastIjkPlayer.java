package com.solar.launcher.podcast;

import android.content.Context;
import android.media.AudioManager;
import android.os.PowerManager;

import com.solar.launcher.video.SolarIjkPlayerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Podcast audio engine — IJK/FFmpeg on API 17 where {@link android.media.MediaPlayer}
 * lacks {@link android.media.PlaybackParams}. Variable speed uses SoundTouch time-stretch
 * (voice stays natural); without it IJK resamples and pitch shifts (chipmunk effect).
 * Audio-only (vn=1); no Surface required.
 */
public final class PodcastIjkPlayer {

    /** One player option applied before setDataSource / prepare. */
    public static final class Option {
        public final int category;
        public final String name;
        public final long longValue;

        Option(int category, String name, long longValue) {
            this.category = category;
            this.name = name;
            this.longValue = longValue;
        }
    }

    public interface OnPreparedListener {
        void onPrepared(PodcastIjkPlayer mp);
    }

    public interface OnCompletionListener {
        void onCompletion(PodcastIjkPlayer mp);
    }

    public interface OnErrorListener {
        /** @return true if error was handled. */
        boolean onError(PodcastIjkPlayer mp, int what, int extra);
    }

    private final IjkMediaPlayer player;
    private float speed = 1f;
    private OnPreparedListener preparedListener;
    private OnCompletionListener completionListener;
    private OnErrorListener errorListener;

    /** Podcast-only IJK flags (SoundTouch + audio-only); testable without loading native code. */
    public static List<Option> podcastPlayerOptions() {
        List<Option> out = new ArrayList<Option>();
        out.add(new Option(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1));
        out.add(new Option(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0));
        // Time-stretch for setSpeed; skips SDL_AoutSetPlaybackRate pitch shift on Android.
        out.add(new Option(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1));
        return Collections.unmodifiableList(out);
    }

    /** JVM self-check — option keys/values without loading native code. */
    static void selfCheck() {
        List<Option> opts = podcastPlayerOptions();
        if (opts.size() != 3) throw new AssertionError("option count");
        expect(opts, 0, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1);
        expect(opts, 1, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
        expect(opts, 2, IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
    }

    private static void expect(List<Option> opts, int index, int category, String name, long value) {
        Option o = opts.get(index);
        if (o.category != category || !name.equals(o.name) || o.longValue != value) {
            throw new AssertionError("option " + index + " " + name);
        }
    }

    /** Apply podcast-specific IJK options; call after factory Y1 defaults and before prepare. */
    private static void applyPodcastPlayerOptions(IjkMediaPlayer player) {
        if (player == null) return;
        for (Option opt : podcastPlayerOptions()) {
            player.setOption(opt.category, opt.name, opt.longValue);
        }
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    public PodcastIjkPlayer() {
        player = SolarIjkPlayerFactory.create();
        applyPodcastPlayerOptions(player);
        player.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                applySpeedInternal();
                if (preparedListener != null) preparedListener.onPrepared(PodcastIjkPlayer.this);
            }
        });
        player.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer mp) {
                if (completionListener != null) completionListener.onCompletion(PodcastIjkPlayer.this);
            }
        });
        player.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                return errorListener != null && errorListener.onError(PodcastIjkPlayer.this, what, extra);
            }
        });
    }

    public void setWakeMode(Context ctx, int mode) {
        player.setWakeMode(ctx, mode);
    }

    public void setOnPreparedListener(OnPreparedListener l) {
        preparedListener = l;
    }

    public void setOnCompletionListener(OnCompletionListener l) {
        completionListener = l;
    }

    public void setOnErrorListener(OnErrorListener l) {
        errorListener = l;
    }

    public void setDataSource(String path) throws IOException {
        player.setDataSource(path);
    }

    public void setDataSource(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        player.setDataSource(fis.getFD());
        fis.close();
    }

    public void prepareAsync() {
        player.prepareAsync();
    }

    public void start() {
        player.start();
        applySpeedInternal();
    }

    public void pause() {
        try {
            if (player.isPlaying()) player.pause();
        } catch (IllegalStateException ignored) {}
    }

    public boolean isPlaying() {
        try {
            return player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getCurrentPosition() {
        try {
            return (int) player.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int getDuration() {
        try {
            return (int) player.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public void seekTo(int ms) {
        try {
            player.seekTo(ms);
        } catch (IllegalStateException ignored) {}
    }

    /** Playback rate; SoundTouch keeps pitch natural when soundtouch=1 is set. */
    public void setSpeed(float speed) {
        this.speed = speed > 0f ? speed : 1f;
        applySpeedInternal();
    }

    public float getSpeed() {
        return speed;
    }

    private void applySpeedInternal() {
        try {
            player.setSpeed(speed);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("speed", speed);
                d.put("playing", isPlaying());
                d.put("posMs", getCurrentPosition());
                com.solar.launcher.DebugSessionLog.log("PodcastIjkPlayer.applySpeedInternal",
                        "setSpeed called", "H-A", d);
            } catch (Exception ignoredLog) {}
            // #endregion
        } catch (Exception ignored) {}
    }

    public void reset() {
        try {
            player.reset();
            SolarIjkPlayerFactory.applyY1Options(player);
            applyPodcastPlayerOptions(player);
            player.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(IMediaPlayer mp) {
                    applySpeedInternal();
                    if (preparedListener != null) preparedListener.onPrepared(PodcastIjkPlayer.this);
                }
            });
            player.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(IMediaPlayer mp) {
                    if (completionListener != null) completionListener.onCompletion(PodcastIjkPlayer.this);
                }
            });
            player.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(IMediaPlayer mp, int what, int extra) {
                    return errorListener != null && errorListener.onError(PodcastIjkPlayer.this, what, extra);
                }
            });
        } catch (IllegalStateException ignored) {}
    }

    public void release() {
        try {
            player.stop();
        } catch (IllegalStateException ignored) {}
        player.release();
    }
}
