package com.solar.launcher.flow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Now Playing album slot — slanted 3D cover via {@link FlowAlbumArt3d} (not flat ImageView).
 */
public final class PlayerAlbumArt3dView extends View {

    private final Paint coverPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap cover;

    public PlayerAlbumArt3dView(Context context) {
        this(context, null);
    }

    public PlayerAlbumArt3dView(Context context, AttributeSet attrs) {
        super(context, attrs);
        coverPaint.setFilterBitmap(false);
        setBackgroundColor(0x00000000);
    }

    public void setCoverBitmap(Bitmap bmp) {
        cover = bmp;
        invalidate();
    }

    public Bitmap getCoverBitmap() {
        return cover;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cover == null || cover.isRecycled()) return;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;
        FlowAlbumArt3d.AlbumArtPose pose = FlowAlbumArt3d.playerPose(w, h);
        // ponytail: NP slot has no floor — reflection drew outside view + white mask broke layout.
        FlowAlbumArt3d.drawCover(canvas, cover, pose.drawRect, pose.rotationYDeg, 1f, coverPaint);
    }
}
