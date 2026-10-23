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
    static final int BG = 0xFF000000, SURFACE = 0xFF1C1C1E, ON_BG = 0xFFFFFFFF, ON_BG_DIM = 0xFF8E8E93,
            INK = 0xFFFFFFFF, INK_DIM = 0xFF8E8E93, BLUE = 0xFF2E7CF6, DIVIDER = 0xFF2C2C2E,
            MINT = SURFACE, MINT2 = SURFACE, PEACH = SURFACE, LAVENDER = SURFACE, YELLOW = SURFACE,
            STONE = 0xFF2C2C2E, ACTIVE = BLUE, BLUE_SOFT = 0xFF14305E, LIGHT = 0xFFE5E5EA,
            BAT_GREEN = 0xFF34C759, BAT_PURPLE = 0xFF34C759, BAT_YELLOW = 0xFF34C759, BAT_RED = 0xFFFF453A;

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
        t.setBackground(round(t.getContext(), on ? ACTIVE : idle, 26));
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

    /** Small battery icon (green fill, ⚡ when charging). */
    static final class MiniBat extends View {
        private int v = -1; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MiniBat(Context c) { super(c); }
        void set(int x) { v = x; invalidate(); }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(dp(getContext(), 26), dp(getContext(), 13)); }
        @Override protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight(), n = dp(getContext(), 2.5f), sw = dp(getContext(), 1.3f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw); p.setColor(0xFF8E8E93);
            RectF o = new RectF(sw, sw, W - n - sw, H - sw); cv.drawRoundRect(o, 3 * sw, 3 * sw, p);
            p.setStyle(Paint.Style.FILL); cv.drawRect(W - n, H * .32f, W, H * .68f, p);
            int pct = v < 0 ? 0 : v % 1000;
            if (pct > 0) {
                p.setColor(pct <= 20 ? BAT_RED : BAT_GREEN);
                float in = sw * 1.6f;
                cv.drawRoundRect(new RectF(o.left + in, o.top + in, o.left + in + (o.width() - 2 * in) * pct / 100f, o.bottom - in), sw, sw, p);
            }
            if (v >= 1000) { p.setColor(0xFF000000); p.setTextSize(H * .9f); p.setTextAlign(Paint.Align.CENTER); cv.drawText("⚡", o.centerX(), H * .82f, p); }
        }
    }

    /** Open dark-gray case with two buds (vector-ish). */
    static final class CaseArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        CaseArt(Context c) { super(c); }
        @Override protected void onDraw(Canvas cv) {
            float H = getHeight(), W = getWidth(), s = Math.min(W / 1.25f, H);
            if (s <= 0) return;
            float cx = W / 2f, top = (H - s) / 2f, w = s * 1.1f, l = cx - w / 2, r = cx + w / 2;
            // lid (back, open)
            p.setShader(new LinearGradient(0, top, 0, top + s * .42f, 0xFF4A4A4E, 0xFF1E1E20, Shader.TileMode.CLAMP));
            cv.drawRoundRect(new RectF(l + s * .04f, top, r - s * .04f, top + s * .45f), s * .2f, s * .2f, p);
            p.setShader(null); p.setColor(0xFF0E0E10);
            cv.drawRoundRect(new RectF(l + s * .1f, top + s * .06f, r - s * .1f, top + s * .42f), s * .16f, s * .16f, p);
            // buds
            for (int i = -1; i <= 1; i += 2) {
                float bx = cx + i * s * .2f, by = top + s * .43f;
                p.setShader(new RadialGradient(bx - i * s * .03f, by - s * .06f, s * .2f, 0xFF7A7A80, 0xFF2A2A2E, Shader.TileMode.CLAMP));
                cv.drawOval(new RectF(bx - s * .17f, by - s * .14f, bx + s * .17f, by + s * .12f), p);
                p.setShader(null);
            }
            // base
            RectF base = new RectF(l, top + s * .45f, r, top + s);
            p.setShader(new LinearGradient(0, base.top, 0, base.bottom, 0xFF5A5A5F, 0xFF222224, Shader.TileMode.CLAMP));
            cv.drawRoundRect(base, s * .22f, s * .22f, p);
            cv.drawRect(base.left, base.top, base.right, base.top + s * .15f, p);
            p.setShader(null); p.setColor(0xFF3A3A3E);
            cv.drawRect(base.left, base.top, base.right, base.top + s * .02f, p);
            p.setColor(0xFFDDDDDD);
            cv.drawRoundRect(new RectF(cx - s * .09f, top + s * .6f, cx + s * .09f, top + s * .615f), 4, 4, p);
        }
    }

    /** Blue circle with white glyph. */
    static TextView icon(Context c, String g, int bg, int fg, int sizeDp) {
        TextView t = tv(c, g, sizeDp * 0.42f, fg, true); t.setGravity(android.view.Gravity.CENTER);
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(bg); t.setBackground(d);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
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
