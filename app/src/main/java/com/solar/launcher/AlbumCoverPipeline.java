package com.solar.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.solar.launcher.theme.ThemeManager;

/** Shared decode → LCD dither → scale path for Flow and Now Playing. */
public final class AlbumCoverPipeline {

    private AlbumCoverPipeline() {}

    /** Now Playing / home preview — may apply LCD dither when pref on. */
    public static Bitmap decorateForDisplay(Bitmap raw, int targetW, int targetH) {
        if (raw == null) return null;
        if (!ThemeManager.isNowPlayingLcdArtEnabled()) {
            if (raw.getWidth() == targetW && raw.getHeight() == targetH) return raw;
            return Bitmap.createScaledBitmap(raw, targetW, targetH, false);
        }
        return AlbumArtLcdFilter.apply(raw, LcdArtPalette.fromTheme(), targetW, targetH);
    }

    /** Flow carousel covers — scale only, never LCD / pixel art; RGB_565 saves RAM on MT6572. */
    public static Bitmap scaleForFlow(Bitmap raw, int targetW, int targetH) {
        if (raw == null) return null;
        Bitmap scaled;
        if (raw.getWidth() == targetW && raw.getHeight() == targetH) {
            scaled = raw;
        } else {
            scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, false);
        }
        if (scaled.getConfig() != Bitmap.Config.RGB_565) {
            Bitmap rgb = scaled.copy(Bitmap.Config.RGB_565, false);
            if (rgb != null && rgb != scaled) {
                if (scaled != raw && !scaled.isRecycled()) scaled.recycle();
                return rgb;
            }
        }
        return scaled;
    }

    /** Built-in "no artwork" drawable through the same LCD / scale path as embedded art. */
    public static Bitmap defaultAlbumArt(Context ctx, int targetW, int targetH) {
        if (ctx == null || targetW <= 0 || targetH <= 0) return null;
        Bitmap raw = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.default_album);
        if (raw == null) return null;
        Bitmap out = decorateForDisplay(raw, targetW, targetH);
        if (out != raw && !raw.isRecycled()) raw.recycle();
        return out;
    }
}
