package org.kravchuk.redmibuds;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xFF0F1115, CARD = 0xFF1B1F26, CARD2 = 0xFF262B34, TEXT = 0xFFECEFF4, SUB = 0xFF9AA3B2,
            ACCENT = 0xFF2EC4B6, ORANGE = 0xFFFF8A3D, RED = 0xFFEF5350, GREEN = 0xFF66BB6A;

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    static GradientDrawable round(int color, float r, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, r)); return g;
    }
    static GradientDrawable stroke(int fill, int strokeColor, float r, Context c) {
        GradientDrawable g = round(fill, r, c); g.setStroke(dp(c, 1.5f), strokeColor); return g;
    }

    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 22, c));
        int p = dp(c, 18); l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(c, 14);
        parent.addView(l, lp); return l;
    }
    static TextView text(Context c, LinearLayout p, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.addView(t); return t;
    }
    static TextView header(Context c, LinearLayout p, String s) {
        TextView t = text(c, p, s, 13, SUB, true); t.setAllCaps(true); t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(c, 10)); return t;
    }
    /** Pill button / chip. */
    static TextView chip(Context c, String s, View.OnClickListener cl) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(14); t.setGravity(Gravity.CENTER);
        t.setTextColor(TEXT); t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        t.setOnClickListener(cl); t.setClickable(true); setChip(t, false, ACCENT); return t;
    }
    static void setChip(TextView t, boolean sel, int accent) {
        Context c = t.getContext();
        t.setBackground(sel ? round(accent, 50, c) : stroke(CARD2, CARD2, 50, c));
        t.setTextColor(sel ? 0xFF0F1115 : TEXT);
    }
    static LinearLayout row(Context c, LinearLayout p, float topMargin) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, topMargin);
        p.addView(l, lp); return l;
    }
    static void addWeighted(LinearLayout row, View v, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(dp(row.getContext(), marginDp)); lp.setMarginEnd(dp(row.getContext(), marginDp));
        row.addView(v, lp);
    }
    static TextView button(Context c, String s, int color, View.OnClickListener cl) {
        TextView t = chip(c, s, cl); t.setBackground(round(color, 50, c)); t.setTextColor(0xFF0F1115);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }

    /** Circular battery gauge. */
    static final class Ring extends View {
        int value = -1; boolean charging; String label;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), fg = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG), lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ring(Context c, String label) {
            super(c); this.label = label;
            float w = dp(c, 7);
            bg.setStyle(Paint.Style.STROKE); bg.setStrokeWidth(w); bg.setColor(CARD2);
            fg.setStyle(Paint.Style.STROKE); fg.setStrokeWidth(w); fg.setStrokeCap(Paint.Cap.ROUND);
            tp.setColor(TEXT); tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(dp(c, 18)); tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            lp.setColor(SUB); lp.setTextAlign(Paint.Align.CENTER); lp.setTextSize(dp(c, 12));
        }
        void set(int v) { charging = v >= 1000; value = v < 0 ? -1 : v % 1000; invalidate(); }
        @Override protected void onMeasure(int w, int h) { int s = dp(getContext(), 96); setMeasuredDimension(resolveSize(s, w), s + dp(getContext(), 22)); }
        @Override protected void onDraw(Canvas cv) {
            int size = dp(getContext(), 88); float cx = getWidth() / 2f, top = dp(getContext(), 4);
            RectF r = new RectF(cx - size / 2f, top, cx + size / 2f, top + size);
            cv.drawArc(r, 0, 360, false, bg);
            if (value >= 0) {
                fg.setColor(charging ? GREEN : value <= 20 ? RED : ACCENT);
                cv.drawArc(r, -90, 360f * value / 100f, false, fg);
            }
            String t = value < 0 ? "—" : value + "%";
            cv.drawText(t, cx, r.centerY() + dp(getContext(), 6), tp);
            if (charging) { lp.setColor(GREEN); cv.drawText("⚡", cx, r.centerY() + dp(getContext(), 24), lp); lp.setColor(SUB); }
            cv.drawText(label, cx, r.bottom + dp(getContext(), 18), lp);
        }
    }

    /** Small colored dot. */
    static View dot(Context c, int color) {
        View v = new View(c); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(color);
        v.setBackground(g); v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 10), dp(c, 10))); return v;
    }
}
