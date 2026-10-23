package org.kravchuk.redmibuds;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** v0.12 bright dark style shared by Main and Settings. */
final class Style {
    static final int BG = 0xFF1E1B19, CARD = 0xFF2B2724, PILL = 0xFF3A3531, TEXT = 0xFFF3EEE9, SUB = 0xFFC9C0B7,
            DIM = 0xFF948B82, BLUE = 0xFF5FB58A, GREEN = 0xFF34C759, YELLOW = 0xFFFFC53D, RED = 0xFFFF5A5F, TRACK = 0xFF4A443F;

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
    static GradientDrawable round(Context c, int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, r)); return g; }
    static int levelColor(int v) { return v < 0 ? TRACK : v <= 20 ? RED : v <= 40 ? YELLOW : BLUE; }

    static TextView tv(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, CARD, 20));
        l.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 10);
        parent.addView(l, lp); return l;
    }
    /** Pill button, >= 56dp tall. */
    static TextView pill(Context c, String s, View.OnClickListener cl) {
        TextView t = tv(c, s, 17, TEXT, false); t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 56)); t.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        t.setMaxLines(1); t.setSingleLine(true);
        t.setAutoSizeTextTypeUniformWithConfiguration(11, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        t.setOnClickListener(cl); setPill(t, false); return t;
    }
    static void setPill(TextView t, boolean on) {
        t.setBackground(round(t.getContext(), on ? BLUE : PILL, 28));
        t.setTextColor(on ? TEXT : SUB);
        t.setTypeface(Typeface.create(on ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    }

    /** Circular battery ring with label + percent inside. */
    static final class Ring extends View {
        private int v = -1; private final String label;
        private final Paint tr = new Paint(Paint.ANTI_ALIAS_FLAG), fg = new Paint(Paint.ANTI_ALIAS_FLAG),
                lp = new Paint(Paint.ANTI_ALIAS_FLAG), np = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ring(Context c, String label) {
            super(c); this.label = label;
            float w = dp(c, 8);
            tr.setStyle(Paint.Style.STROKE); tr.setStrokeWidth(w); tr.setColor(TRACK);
            fg.setStyle(Paint.Style.STROKE); fg.setStrokeWidth(w); fg.setStrokeCap(Paint.Cap.ROUND);
            lp.setColor(SUB); lp.setTextAlign(Paint.Align.CENTER); lp.setTextSize(dp(c, 15));
            np.setColor(TEXT); np.setTextAlign(Paint.Align.CENTER); np.setTextSize(dp(c, 21));
            np.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        void set(int value) { v = value; invalidate(); }
        @Override protected void onMeasure(int w, int h) { int s = dp(getContext(), 90); setMeasuredDimension(resolveSize(s, w), s); }
        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight()), pad = dp(getContext(), 7);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = s / 2f - pad;
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            cv.drawArc(o, 0, 360, false, tr);
            if (v >= 0) { fg.setColor(levelColor(v)); cv.drawArc(o, -90, 360f * Math.min(100, v) / 100f, false, fg); }
            cv.drawText(label, cx, cy - dp(getContext(), 4), lp);
            cv.drawText(v < 0 ? "—" : v + "%", cx, cy + dp(getContext(), 20), np);
        }
    }

    /** Line illustration: white outline case + two blue outline earbuds. */
    static final class BudsArt extends View {
        private final Paint w = new Paint(Paint.ANTI_ALIAS_FLAG), b = new Paint(Paint.ANTI_ALIAS_FLAG);
        BudsArt(Context c) {
            super(c);
            w.setStyle(Paint.Style.STROKE); w.setStrokeWidth(dp(c, 3)); w.setColor(TEXT); w.setStrokeJoin(Paint.Join.ROUND);
            b.setStyle(Paint.Style.STROKE); b.setStrokeWidth(dp(c, 2.6f)); b.setColor(BLUE); b.setStrokeCap(Paint.Cap.ROUND);
        }
        void colors(int caseColor, int budColor) { w.setColor(caseColor); b.setColor(budColor); invalidate(); }
        @Override protected void onMeasure(int wm, int hm) {
            int h = MeasureSpec.getMode(hm) == MeasureSpec.UNSPECIFIED ? dp(getContext(), 160) : MeasureSpec.getSize(hm);
            setMeasuredDimension(MeasureSpec.getSize(wm), Math.max(0, h));
        }
        @Override protected void onDraw(Canvas cv) {
            float u = Math.min(getHeight() / 200f, getWidth() / 280f); float oy = (getHeight() - 200 * u) / 2f, ox = getWidth() / 2f - 130 * u;
            cv.save(); cv.translate(0, oy);
            // case
            RectF cs = new RectF(ox + 10 * u, 10 * u, ox + 170 * u, 165 * u);
            cv.drawRoundRect(cs, 34 * u, 34 * u, w);
            cv.drawLine(cs.left, 55 * u, cs.right, 55 * u, w);
            cv.drawRoundRect(new RectF(ox + 75 * u, 55 * u, ox + 105 * u, 63 * u), 3 * u, 3 * u, w);
            // earbuds
            bud(cv, ox + 160 * u, 100 * u, u, false);
            bud(cv, ox + 215 * u, 100 * u, u, true);
            cv.restore();
        }
        private void bud(Canvas cv, float x, float y, float u, boolean mirror) {
            float m = mirror ? 1 : -1;
            // head
            cv.drawOval(new RectF(x - 22 * u, y, x + 22 * u, y + 46 * u), b);
            // tip
            cv.drawOval(new RectF(x + m * 18 * u - 10 * u, y + 8 * u, x + m * 18 * u + 10 * u, y + 30 * u), b);
            // stem
            RectF st = new RectF(x - 9 * u, y + 20 * u, x + 9 * u, y + 95 * u);
            cv.drawRoundRect(st, 9 * u, 9 * u, b);
            cv.drawLine(x, y + 30 * u, x, y + 48 * u, b);
        }
    }

    /** Simple ear outline icon. */
    static final class EarIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        EarIcon(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(c, 2.2f)); p.setColor(SUB); p.setStrokeCap(Paint.Cap.ROUND); }
        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight()), x = (getWidth() - s) / 2f, y = (getHeight() - s) / 2f;
            Path a = new Path();
            a.moveTo(x + s * .3f, y + s * .42f);
            a.cubicTo(x + s * .3f, y + s * .05f, x + s * .85f, y + s * .05f, x + s * .78f, y + s * .45f);
            a.cubicTo(x + s * .74f, y + s * .62f, x + s * .55f, y + s * .62f, x + s * .55f, y + s * .8f);
            a.cubicTo(x + s * .55f, y + s * .97f, x + s * .32f, y + s * .97f, x + s * .3f, y + s * .82f);
            cv.drawPath(a, p);
            Path in = new Path();
            in.moveTo(x + s * .45f, y + s * .4f);
            in.cubicTo(x + s * .45f, y + s * .22f, x + s * .66f, y + s * .22f, x + s * .64f, y + s * .4f);
            cv.drawPath(in, p);
        }
    }

    /** Round gear button. */
    static final class GearButton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        GearButton(Context c) { super(c); bg.setColor(CARD); p.setColor(SUB); p.setStyle(Paint.Style.FILL); }
        @Override protected void onMeasure(int w, int h) { int s = dp(getContext(), 48); setMeasuredDimension(s, s); }
        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = getWidth() / 2f;
            cv.drawCircle(cx, cy, R, bg);
            float ro = R * .42f, ri = R * .30f, hole = R * .14f;
            Path g = new Path();
            int teeth = 8;
            for (int i = 0; i < teeth * 2; i++) {
                double a0 = Math.PI * 2 * i / (teeth * 2), a1 = Math.PI * 2 * (i + 1) / (teeth * 2);
                float r = (i % 2 == 0) ? ro : ri;
                float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
                float x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
                if (i == 0) g.moveTo(x0, y0); else g.lineTo(x0, y0);
                g.lineTo(x1, y1);
            }
            g.close();
            g.addCircle(cx, cy, hole, Path.Direction.CCW);
            g.setFillType(Path.FillType.EVEN_ODD);
            cv.drawPath(g, p);
        }
    }
}
