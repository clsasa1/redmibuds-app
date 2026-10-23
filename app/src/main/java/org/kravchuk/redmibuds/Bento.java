package org.kravchuk.redmibuds;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** v0.14 Material You "bento" palette. */
final class Bento {
    static final int BG = 0xFF1E1B19, SURFACE = 0xFF2B2724, ON_BG = 0xFFF3EEE9, ON_BG_DIM = 0xFFBDB5AD,
            INK = 0xFF1F1D1B, INK_DIM = 0xFF55504B,
            MINT = 0xFFCFE9DB, MINT2 = 0xFFE7F1E6, PEACH = 0xFFF7D2BE, LAVENDER = 0xFFDCD0F3, YELLOW = 0xFFF8E3AE,
            STONE = 0xFFE6E2DA, ACTIVE = 0xFF5FB58A, BLUE_SOFT = 0xFFD5DCF7,
            BAT_GREEN = 0xFF5CB85C, BAT_PURPLE = 0xFF8E6AD8, BAT_YELLOW = 0xFFF2C21B, BAT_RED = 0xFFE5534B;

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
    static GradientDrawable round(Context c, int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, r)); return g; }
    static GradientDrawable grad(Context c, int a, int b, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b}); g.setCornerRadius(dp(c, r)); return g;
    }
    static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }
    /** One-line label that shrinks instead of wrapping. */
    static TextView one(Context c, String s, int minSp, int maxSp, int color, boolean bold) {
        TextView t = tv(c, s, maxSp, color, bold);
        t.setMaxLines(1); t.setSingleLine(true);
        t.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        return t;
    }
    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l; }

    /** Pill: 56dp tall, active = filled. */
    static TextView pill(Context c, String s, View.OnClickListener cl) {
        TextView t = one(c, s, 11, 17, INK, true);
        t.setGravity(Gravity.CENTER); t.setMinHeight(dp(c, 56)); t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        t.setOnClickListener(cl); setPill(t, false, STONE); return t;
    }
    static void setPill(TextView t, boolean on, int idle) {
        t.setBackground(round(t.getContext(), on ? ACTIVE : idle, 28));
        t.setTextColor(on ? 0xFFFFFFFF : INK);
    }

    /** Horizontal battery gauge (rounded rect with fill) + percent text. */
    static final class BatBar extends View {
        private int v = -1, color;
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        BatBar(Context c, int color) {
            super(c); this.color = color;
            frame.setStyle(Paint.Style.STROKE); frame.setStrokeWidth(dp(c, 1.5f)); frame.setColor(INK_DIM);
            txt.setColor(INK); txt.setTextSize(dp(c, 14)); txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        void set(int value) { v = value; invalidate(); }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), dp(getContext(), 28)); }
        @Override protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight(), r = H / 2.6f, nub = dp(getContext(), 3);
            RectF o = new RectF(1, 1, W - nub - 2, H - 1);
            cv.drawRoundRect(o, r, r, frame);
            cv.drawRect(W - nub - 1, H * .35f, W, H * .65f, frame);
            int pct = v < 0 ? 0 : v % 1000;
            if (pct > 0) {
                float inset = dp(getContext(), 3);
                RectF in = new RectF(o.left + inset, o.top + inset, o.left + inset + (o.width() * 0.42f - inset) * pct / 100f + 1, o.bottom - inset);
                fill.setColor(pct <= 20 ? BAT_RED : color);
                cv.drawRoundRect(in, r / 1.5f, r / 1.5f, fill);
            }
            String s = v < 0 ? "—" : pct + "%" + (v >= 1000 ? " ⚡" : "");
            cv.drawText(s, o.left + o.width() * 0.71f, H / 2f - (txt.descent() + txt.ascent()) / 2f, txt);
        }
    }

    /** Small outline earbud / case glyph for battery tiles. */
    static final class Glyph extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final boolean isCase;
        Glyph(Context c, int color, boolean isCase) {
            super(c); this.isCase = isCase;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(c, 2.5f)); p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight()) * 0.9f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (s <= 0) return;
            if (isCase) {
                RectF r = new RectF(cx - s * .45f, cy - s * .35f, cx + s * .45f, cy + s * .35f);
                cv.drawRoundRect(r, s * .18f, s * .18f, p);
                cv.drawLine(r.left, cy - s * .1f, r.right, cy - s * .1f, p);
                cv.drawCircle(cx, cy + s * .05f, s * .025f, p);
            } else {
                cv.drawOval(new RectF(cx - s * .22f, cy - s * .42f, cx + s * .18f, cy - s * .02f), p);
                cv.drawOval(new RectF(cx + s * .1f, cy - s * .32f, cx + s * .3f, cy - s * .1f), p);
                RectF st = new RectF(cx - s * .1f, cy - s * .15f, cx + s * .06f, cy + s * .45f);
                cv.drawRoundRect(st, s * .08f, s * .08f, p);
            }
        }
    }
}
