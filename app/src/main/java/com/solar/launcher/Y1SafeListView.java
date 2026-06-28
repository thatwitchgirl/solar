package com.solar.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * API 17 MT6572 crashes in View.onDrawScrollBars / ScrollBarDrawable.setAlpha on ListView draw.
 * ponytail: same workaround as {@link Y1SafeScrollView} — dispatchDraw only on Jelly Bean.
 */
public class Y1SafeListView extends ListView {

    public Y1SafeListView(Context context) {
        super(context);
        init();
    }

    public Y1SafeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Y1SafeListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        Y1ScrollIndicators.applyVerticalListView(this);
    }

    @Override
    public void draw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= 19) {
            super.draw(canvas);
            return;
        }
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();
        canvas.save();
        canvas.clipRect(scrollX, scrollY, scrollX + getWidth(), scrollY + getHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
