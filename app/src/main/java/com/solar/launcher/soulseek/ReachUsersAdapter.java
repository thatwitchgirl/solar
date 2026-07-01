package com.solar.launcher.soulseek;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.solar.launcher.R;
import com.solar.launcher.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/** Virtualized Find Reach users list. */
public final class ReachUsersAdapter extends BaseAdapter {
    public static final int PAGE_SIZE = 40;

    public interface Listener {
        void onUserSelected(ReachDirectoryUser user);
        void onShowMore();
        String countryCodeForUser(String username);
        String previewForUser(String username, String basePreview);
    }

    private final Activity activity;
    private final Listener listener;
    private List<ReachDirectoryUser> allUsers = new ArrayList<ReachDirectoryUser>();
    private int visibleCount = PAGE_SIZE;
    private int selectedPosition = -1;
    private int rowWidthPx;
    private int rowHeightPx;

    public ReachUsersAdapter(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        rowWidthPx = activity.getResources().getDisplayMetrics().widthPixels;
        rowHeightPx = ReachMessageRow.measureListRowHeight(activity, true);
    }

    public void setRowWidthPx(int w) {
        rowWidthPx = w;
    }

    public void setUsers(List<ReachDirectoryUser> users) {
        allUsers = users != null ? new ArrayList<ReachDirectoryUser>(users) : new ArrayList<ReachDirectoryUser>();
        if (visibleCount < PAGE_SIZE) visibleCount = PAGE_SIZE;
        if (visibleCount > allUsers.size()) visibleCount = allUsers.size();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void clearSelectedPosition() {
        if (selectedPosition < 0) return;
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    /** Wheel/selectable user rows only — not the Show more footer. */
    public boolean isWheelSelectable(int adapterIndex) {
        return adapterIndex >= 0 && adapterIndex < getDataCount();
    }

    public void showMore() {
        if (!hasShowMoreRow()) return;
        visibleCount = Math.min(visibleCount + PAGE_SIZE, allUsers.size());
        notifyDataSetChanged();
        if (listener != null) listener.onShowMore();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int pos) {
        if (pos == selectedPosition) return;
        selectedPosition = pos;
        notifyDataSetChanged();
    }

    public boolean hasShowMoreRow() {
        return visibleCount < allUsers.size();
    }

    public int getDataCount() {
        return Math.min(visibleCount, allUsers.size());
    }

    public ReachDirectoryUser userAt(int position) {
        if (position < 0 || position >= getDataCount()) return null;
        return allUsers.get(position);
    }

    @Override
    public int getCount() {
        return getDataCount() + (hasShowMoreRow() ? 1 : 0);
    }

    @Override
    public Object getItem(int position) {
        return userAt(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean isEnabled(int position) {
        return position < getDataCount();
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position >= getDataCount() ? 1 : 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (getItemViewType(position) == 1) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(activity);
                tv.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT));
                ThemeManager.applyThemedTextStyle(tv, ThemeManager.getItemTextColorNormal());
            }
            tv.setText(activity.getString(R.string.soulseek_show_more, visibleCount, allUsers.size()));
            tv.setFocusable(false);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMore();
                }
            });
            return tv;
        }

        final ReachDirectoryUser user = allUsers.get(position);
        FrameLayout row;
        if (convertView instanceof FrameLayout) {
            row = (FrameLayout) convertView;
        } else {
            row = ReachMessageRow.create(activity, rowHeightPx);
        }
        final String basePreview = activity.getString(R.string.soulseek_reach_user_badge, user.device);
        final String preview = listener != null
                ? listener.previewForUser(user.username, basePreview) : basePreview;
        final String cc = listener != null ? listener.countryCodeForUser(user.username) : null;
        final FrameLayout rowView = row;
        ReachMessageRow.attachFocusHighlight(row, new ReachMessageRow.HighlightBind() {
            @Override
            public void bind(boolean highlighted) {
                boolean show = highlighted || position == selectedPosition;
                ReachMessageRow.bindInboxRow(activity, rowView,
                        SolarDeveloperAccounts.displayNameForPeer(activity, user.username),
                        preview, "", show, rowWidthPx, rowHeightPx, cc);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onUserSelected(user);
            }
        });
        return row;
    }
}
