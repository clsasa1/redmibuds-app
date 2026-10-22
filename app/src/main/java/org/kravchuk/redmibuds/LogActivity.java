package org.kravchuk.redmibuds;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

/** Recycling ListView over a snapshot of the ring buffer; auto-refresh max 1×/s. */
public class LogActivity extends Activity {
    private final List<String> data = new ArrayList<>();
    private BaseAdapter adapter;
    private ListView lv;
    private long seen = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { refresh(false); h.postDelayed(this, 1000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        root.setPadding(0, Ui.dp(this, 24), 0, 0);
        LinearLayout bar = Ui.row(this, root, 0); bar.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 8));
        Ui.addWeighted(bar, Ui.chip(this, "Копировать", v -> {
            try {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("log", Log2.all()));
                Toast.makeText(this, "Скопировано (последние строки)", Toast.LENGTH_SHORT).show();
            } catch (Throwable e) { Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
        }), 3);
        Ui.addWeighted(bar, Ui.button(this, "Файл лога", Ui.ACCENT, v -> { if (Log2.file != null) App.share(this, Log2.file, "Журнал"); }), 3);
        Ui.addWeighted(bar, Ui.chip(this, "Очистить", v -> { Log2.clear(); refresh(true); }), 3);
        lv = new ListView(this); lv.setDivider(null); lv.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        adapter = new BaseAdapter() {
            public int getCount() { return data.size(); }
            public Object getItem(int i) { return data.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) {
                TextView t = cv instanceof TextView ? (TextView) cv : new TextView(LogActivity.this);
                if (cv == null) { t.setTypeface(Typeface.MONOSPACE); t.setTextSize(10.5f); t.setPadding(16, 2, 16, 2); }
                String s = data.get(i);
                t.setText(s.length() > 2000 ? s.substring(0, 2000) + "…" : s);
                t.setTextColor(s.contains(" TX ") ? 0xFF80DEEA : s.contains(" RX ") ? 0xFFFFCC80 : s.contains("CRASH") || s.contains("FAIL") ? Ui.RED : Ui.TEXT);
                return t;
            }
        };
        lv.setAdapter(adapter);
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }
    private void refresh(boolean force) {
        long v = Log2.version();
        if (!force && v == seen) return;
        seen = v; data.clear(); data.addAll(Log2.snapshot()); adapter.notifyDataSetChanged();
    }
    @Override protected void onResume() { super.onResume(); refresh(true); lv.setSelection(Math.max(0, data.size() - 1)); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
}
