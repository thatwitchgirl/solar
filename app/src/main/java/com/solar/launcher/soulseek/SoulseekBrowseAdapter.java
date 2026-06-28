package com.solar.launcher.soulseek;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.solar.launcher.R;
import com.solar.launcher.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/** Flattened, paginated Soulseek peer library browse list. */
public final class SoulseekBrowseAdapter extends BaseAdapter {
    public static final int PAGE_SIZE = 25;

    public static final int TYPE_FOLDER = 0;
    public static final int TYPE_FILE = 1;
    public static final int TYPE_SHOW_MORE = 2;
    public static final int TYPE_STATUS = 3;

    public static final class Entry {
        public final int type;
        public final String folderPath;
        public final SoulseekWire.BrowseFile file;

        public Entry(int type, String folderPath, SoulseekWire.BrowseFile file) {
            this.type = type;
            this.folderPath = folderPath;
            this.file = file;
        }
    }

    public interface Listener {
        void onFolderSelected(String path);
        void onFileSelected(SoulseekWire.BrowseFile file);
    }

    private final Activity activity;
    private final Listener listener;
    private final List<Entry> allEntries = new ArrayList<Entry>();
    private int visibleCount = PAGE_SIZE;
    private int selectedPosition = -1;
    private String statusText = "";
    private boolean statusMode = false;

    public SoulseekBrowseAdapter(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void setLoading(String message) {
        statusMode = true;
        statusText = message != null ? message : "";
        allEntries.clear();
        visibleCount = PAGE_SIZE;
        notifyDataSetChanged();
    }

    public void setStatus(String message) {
        statusMode = true;
        statusText = message != null ? message : "";
        allEntries.clear();
        notifyDataSetChanged();
    }

    public void setFolders(List<SoulseekWire.BrowseFolder> folders) {
        statusMode = false;
        statusText = "";
        allEntries.clear();
        if (folders != null) {
            for (SoulseekWire.BrowseFolder folder : folders) {
                if (folder.files == null || folder.files.isEmpty()) continue;
                allEntries.add(new Entry(TYPE_FOLDER, folder.path, null));
                for (SoulseekWire.BrowseFile file : folder.files) {
                    allEntries.add(new Entry(TYPE_FILE, folder.path, file));
                }
            }
        }
        visibleCount = PAGE_SIZE;
        notifyDataSetChanged();
    }

    public boolean isLoading() {
        return statusMode;
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
        return !statusMode && visibleCount < allEntries.size();
    }

    public int getDataCount() {
        if (statusMode) return 0;
        return Math.min(visibleCount, allEntries.size());
    }

    public Entry entryAt(int position) {
        if (position < 0 || position >= getDataCount()) return null;
        return allEntries.get(position);
    }

    public java.util.List<SoulseekWire.BrowseFile> listFilesInFolder(String folderPath) {
        java.util.ArrayList<SoulseekWire.BrowseFile> out = new java.util.ArrayList<SoulseekWire.BrowseFile>();
        String norm = folderPath != null ? folderPath : "";
        for (Entry e : allEntries) {
            if (e.type != TYPE_FILE || e.file == null) continue;
            if (norm.isEmpty() || norm.equals(e.folderPath)) {
                out.add(e.file);
            }
        }
        return out;
    }

    @Override
    public int getCount() {
        if (statusMode) return 1;
        int data = getDataCount();
        if (data == 0) return 1;
        return data + (hasShowMoreRow() ? 1 : 0);
    }

    @Override
    public Object getItem(int position) {
        if (statusMode) return statusText;
        if (getDataCount() == 0) return statusText;
        return entryAt(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean isEnabled(int position) {
        if (statusMode) return false;
        if (getDataCount() == 0) return false;
        return getItemViewType(position) != TYPE_SHOW_MORE;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        if (statusMode || getDataCount() == 0) return TYPE_STATUS;
        if (position >= getDataCount()) return TYPE_SHOW_MORE;
        return allEntries.get(position).type;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int type = getItemViewType(position);
        if (type == TYPE_STATUS) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(activity);
                tv.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT));
            }
            String text = statusText;
            if (text == null || text.isEmpty()) {
                text = activity.getString(R.string.soulseek_browse_loading);
            }
            tv.setText(text);
            ThemeManager.applyThemedTextStyle(tv, ThemeManager.getSubtitleTextColor());
            return tv;
        }
        if (type == TYPE_SHOW_MORE) {
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
            tv.setText(activity.getString(R.string.soulseek_show_more, visibleCount, allEntries.size()));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    visibleCount = Math.min(visibleCount + PAGE_SIZE, allEntries.size());
                    notifyDataSetChanged();
                }
            });
            return tv;
        }

        final Entry entry = allEntries.get(position);
        Button btn;
        if (convertView instanceof Button) {
            btn = (Button) convertView;
        } else {
            btn = new Button(activity);
            btn.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));
        }
        if (entry.type == TYPE_FOLDER) {
            btn.setText("\uD83D\uDCC1 " + entry.folderPath);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onFolderSelected(entry.folderPath);
                }
            });
        } else {
            btn.setText("  " + (entry.file != null ? entry.file.name : ""));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null && entry.file != null) listener.onFileSelected(entry.file);
                }
            });
        }
        return btn;
    }
}
