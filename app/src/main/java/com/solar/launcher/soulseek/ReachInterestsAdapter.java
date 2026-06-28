package com.solar.launcher.soulseek;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.solar.launcher.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/** Virtualized Reach interests list (likes + dislikes with section headers). */
public final class ReachInterestsAdapter extends BaseAdapter {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    public interface Listener {
        void onInterestSelected(String interest, boolean isLike);
    }

    private static final class Item {
        final int type;
        final String label;
        final boolean isLike;

        Item(int type, String label, boolean isLike) {
            this.type = type;
            this.label = label;
            this.isLike = isLike;
        }
    }

    private final Activity activity;
    private final Listener listener;
    private final List<Item> items = new ArrayList<Item>();
    private int selectedPosition = -1;
    private int rowWidthPx;
    private int rowHeightPx;

    public ReachInterestsAdapter(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        rowWidthPx = activity.getResources().getDisplayMetrics().widthPixels;
        rowHeightPx = ReachMessageRow.measureListRowHeight(activity, true);
    }

    public void setRowWidthPx(int w) {
        rowWidthPx = w;
    }

    public void setData(List<String> likes, List<String> dislikes, String likesHeader, String dislikesHeader) {
        items.clear();
        items.add(new Item(TYPE_HEADER, likesHeader, true));
        if (likes != null) {
            for (String s : likes) {
                items.add(new Item(TYPE_ROW, s, true));
            }
        }
        items.add(new Item(TYPE_HEADER, dislikesHeader, false));
        if (dislikes != null) {
            for (String s : dislikes) {
                items.add(new Item(TYPE_ROW, s, false));
            }
        }
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int pos) {
        if (pos == selectedPosition) return;
        selectedPosition = pos;
        notifyDataSetChanged();
    }

    public boolean isSelectable(int position) {
        return position >= 0 && position < items.size()
                && items.get(position).type == TYPE_ROW;
    }

    public int firstSelectablePosition() {
        for (int i = 0; i < items.size(); i++) {
            if (isSelectable(i)) return i;
        }
        return -1;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        return isSelectable(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Item item = items.get(position);
        if (item.type == TYPE_HEADER) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(activity);
                tv.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT));
                ThemeManager.applyThemedTextStyle(tv, ThemeManager.getSubtitleTextColor());
            }
            tv.setText(item.label);
            return tv;
        }

        FrameLayout row;
        if (convertView instanceof FrameLayout) {
            row = (FrameLayout) convertView;
        } else {
            row = ReachMessageRow.create(activity, rowHeightPx);
        }
        final FrameLayout rowView = row;
        ReachMessageRow.attachFocusHighlight(row, new ReachMessageRow.HighlightBind() {
            @Override
            public void bind(boolean highlighted) {
                boolean show = highlighted || position == selectedPosition;
                ReachMessageRow.bind(activity, rowView, item.label, null,
                        false, show, null, rowWidthPx, rowHeightPx, false);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onInterestSelected(item.label, item.isLike);
            }
        });
        return row;
    }
}
