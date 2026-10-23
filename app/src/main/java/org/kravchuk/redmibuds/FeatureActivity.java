package org.kravchuk.redmibuds;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

import static org.kravchuk.redmibuds.Bento.*;

/** Sub-screens: anc / gestures / sound / find / extra. Scrollable, rebuilt values on every refresh. */
public class FeatureActivity extends Activity implements Hub.Ui {
    private final Hub h = Hub.I;
    private final List<Runnable> binders = new ArrayList<>();
    private LinearLayout root;

    static final String[] ACTIONS = {"Голосовой ассистент", "Пауза / воспроизведение", "Предыдущий трек", "Следующий трек",
            "Громкость +", "Громкость −", "Режим шумоподавления", "Нет действия"};
    static final int[] ACTION_CODES = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x08};
    static final String[] TAPS = {"Одиночное касание", "Двойное касание", "Тройное касание", "Долгое нажатие"};
    static final int[] TAP_CODES = {4, 1, 2, 3};
    static final String[] CYCLE = {"Шумодав · Прозрачность · Выкл", "Шумодав ↔ Прозрачность", "Прозрачность ↔ Выкл", "Шумодав ↔ Выкл"};
    static final int[] CYCLE_CODES = {7, 6, 5, 3};
    static final String[] EQ = {"Стандарт", "Бас", "Голос", "Высокие"};
    static final int[] EQ_CODES = {0x00, 0x05, 0x01, 0x06};
    static final String[] TR = {"Обычная", "Голос", "Окружение"};

    private int dp(float v) { return Bento.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG); sv.setFillViewport(true);
        root = col(this); root.setPadding(dp(16), dp(10), dp(16), dp(28));
        sv.addView(root); setContentView(sv);
        String page = getIntent().getStringExtra("page"); if (page == null) page = "anc";
        LinearLayout tr = row(this); tr.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv(this, "←", 26, ON_BG, false); back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        tr.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));
        String t = page.equals("gestures") ? "Жесты" : page.equals("sound") ? "Звук и эквалайзер" : page.equals("find") ? "Поиск наушников"
                : page.equals("extra") ? "Доп. настройки" : "Шумоподавление";
        tr.addView(one(this, t, 18, 28, ON_BG, true), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(tr);
        switch (page) {
            case "gestures": gestures(); break;
            case "sound": sound(); break;
            case "find": find(); break;
            case "extra": extra(); break;
            default: anc();
        }
    }
    @Override protected void onResume() { super.onResume(); h.uis.addIfAbsent(this); refresh(); }
    @Override protected void onPause() { super.onPause(); h.uis.remove(this); }
    @Override public void refresh() { for (Runnable r : binders) r.run(); }

    private boolean need() {
        if (h.authed) return true;
        Toast.makeText(this, "Нет связи с наушниками", Toast.LENGTH_SHORT).show();
        return false;
    }

    private LinearLayout tile(int color, String title) {
        LinearLayout c = col(this); c.setBackground(round(this, SURFACE, 20)); c.setPadding(dp(18), dp(16), dp(18), dp(16));
        if (title != null) { TextView tt = one(this, title, 14, 20, INK, true); tt.setPadding(0, 0, 0, dp(10)); c.addView(tt); }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12);
        root.addView(c, lp); return c;
    }
    private TextView note(LinearLayout p, String s) { TextView t = tv(this, s, 13, INK_DIM, false); t.setPadding(0, dp(8), 0, 0); p.addView(t); return t; }

    private void pills(LinearLayout p, String[] labels, int[] codes, java.util.function.IntSupplier cur, java.util.function.IntConsumer set) {
        LinearLayout r = row(this);
        TextView[] ts = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int c = codes[i];
            ts[i] = pill(this, labels[i], v -> { if (need()) { set.accept(c); refresh(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f); if (i > 0) lp.leftMargin = dp(6);
            r.addView(ts[i], lp);
        }
        p.addView(r);
        binders.add(() -> { int v = cur.getAsInt(); for (int i = 0; i < ts.length; i++) setPill(ts[i], v == codes[i], STONE); });
    }
    private void toggle(LinearLayout p, String label, java.util.function.IntSupplier cur, java.util.function.Consumer<Boolean> set) {
        LinearLayout r = row(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setMinimumHeight(dp(64));
        TextView t = tv(this, label, 17, INK, false);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setThumbTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFFFFFFFF, 0xFFBDBDBD}));
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{BLUE, 0xFF48484A}));
        r.addView(sw);
        p.addView(r);
        final boolean[] mute = {false};
        sw.setOnCheckedChangeListener((b, on) -> { if (mute[0]) return; if (!need()) { refresh(); return; } set.accept(on); });
        binders.add(() -> { mute[0] = true; sw.setChecked(cur.getAsInt() == 1); sw.setEnabled(h.authed); mute[0] = false; });
    }
    private SeekBar slider(LinearLayout p, int max, java.util.function.IntSupplier cur, java.util.function.IntConsumer set) {
        SeekBar s = new SeekBar(this); s.setMax(max);
        s.setProgressTintList(android.content.res.ColorStateList.valueOf(ACTIVE));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(ACTIVE));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int v, boolean user) {}
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) { if (need()) set.accept(sb.getProgress()); else refresh(); }
        });
        p.addView(s, new LinearLayout.LayoutParams(-1, dp(44)));
        binders.add(() -> { int v = cur.getAsInt(); if (v >= 0) s.setProgress(v); });
        return s;
    }

    // ---- pages ----
    private void anc() {
        LinearLayout m = tile(MINT, "Режим");
        pills(m, new String[]{"Шумодав", "Выкл", "Прозрачн."}, new int[]{1, 0, 2}, () -> h.anc, v -> { Hub.conn.setAnc(v); h.anc = v; });
        LinearLayout d = tile(LAVENDER, "Глубина шумоподавления");
        slider(d, 0x13, () -> h.ancDepth, v -> { Hub.conn.setAncDepth(v); h.ancDepth = v; });
        TextView dv = note(d, "");
        binders.add(() -> dv.setText(h.ancDepth < 0 ? "Значение неизвестно" : "Уровень " + (h.ancDepth + 1) + " из 20"));
        LinearLayout t = tile(PEACH, "Уровень прозрачности");
        pills(t, TR, new int[]{0, 1, 2}, () -> h.trLevel, v -> { Hub.conn.setTransparency(v); h.trLevel = v; });
    }
    private void sound() {
        LinearLayout e = tile(YELLOW, "Эквалайзер");
        LinearLayout r1 = col(this); e.addView(r1);
        pills(r1, new String[]{EQ[0], EQ[1]}, new int[]{EQ_CODES[0], EQ_CODES[1]}, () -> h.eq, v -> { Hub.conn.setEq(v); h.eq = v; });
        LinearLayout r2 = col(this); r2.setPadding(0, dp(6), 0, 0); e.addView(r2);
        pills(r2, new String[]{EQ[2], EQ[3]}, new int[]{EQ_CODES[2], EQ_CODES[3]}, () -> h.eq, v -> { Hub.conn.setEq(v); h.eq = v; });
        TextView ev = note(e, "");
        binders.add(() -> ev.setText(h.eq < 0 ? "" : java.util.Arrays.stream(EQ_CODES).noneMatch(x -> x == h.eq) ? "Сейчас: другой пресет (0x" + Integer.toHexString(h.eq) + ")" : ""));
        LinearLayout a = tile(MINT, null);
        toggle(a, "Адаптивный звук", () -> h.adaptive, on -> { Hub.conn.setAdaptive(on); h.adaptive = on ? 1 : 0; });
    }
    private void find() {
        LinearLayout f = tile(PEACH, "Воспроизвести звук");
        note(f, "Наушники громко запищат. Выньте их из ушей.");
        String[] l = {"Левый", "Правый", "Оба"}; int[] s = {1, 2, 3};
        LinearLayout r = row(this); r.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < 3; i++) {
            final int side = s[i];
            TextView t = pill(this, l[i], v -> { if (need()) { Hub.conn.find(true, side); h.find = side; refresh(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f); if (i > 0) lp.leftMargin = dp(6);
            r.addView(t, lp);
            binders.add(() -> setPill(t, h.find == side, STONE));
        }
        f.addView(r);
        TextView stop = pill(this, "Остановить", v -> { if (need()) { Hub.conn.find(false, h.find == 0 ? 3 : h.find); h.find = 0; refresh(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56)); lp.topMargin = dp(10);
        f.addView(stop, lp);
        setPill(stop, false, STONE);
    }
    private void extra() {
        LinearLayout x = tile(LAVENDER, null);
        toggle(x, "Обнаружение уха", () -> h.ear, on -> { Hub.conn.setEarDetection(on); h.ear = on ? 1 : 0; });
        toggle(x, "Два устройства одновременно", () -> h.dual, on -> { Hub.conn.setDual(on); h.dual = on ? 1 : 0; });
        toggle(x, "Автоответ на звонки", () -> h.autoAnswer, on -> { Hub.conn.setAutoAnswer(on); h.autoAnswer = on ? 1 : 0; });
        TextView fw = note(x, "");
        binders.add(() -> fw.setText(h.fw.isEmpty() ? "" : "Прошивка " + h.fw));
    }
    private void gestures() {
        String[] side = {"Левый наушник", "Правый наушник"};
        int[] col = {PEACH, LAVENDER};
        for (int s = 0; s < 2; s++) {
            LinearLayout t = tile(col[s], side[s]);
            for (int i = 0; i < TAPS.length; i++) {
                final int type = TAP_CODES[i], sd = s, idx = i;
                TextView r = pickRow(t, TAPS[i]);
                r.setOnClickListener(v -> { if (need()) choose(TAPS[idx] + " · " + side[sd], ACTIONS, ACTION_CODES, k -> { Hub.conn.setGesture(type, sd, k); h.gestures[type][sd] = k; refresh(); }); });
                binders.add(() -> r.setText(label(TAPS[idx], h.gestures[type][sd], ACTIONS, ACTION_CODES)));
            }
            final int sd = s;
            TextView c = pickRow(t, "Цикл долгого нажатия");
            c.setOnClickListener(v -> { if (need()) choose("Цикл долгого нажатия", CYCLE, CYCLE_CODES, k -> { Hub.conn.setLongCycle(sd, k); if (sd == 0) h.longL = k; else h.longR = k; refresh(); }); });
            binders.add(() -> c.setText(label("Цикл долгого нажатия", sd == 0 ? h.longL : h.longR, CYCLE, CYCLE_CODES)));
        }
    }
    private TextView pickRow(LinearLayout p, String s) {
        TextView t = tv(this, s, 16, INK, false); t.setMinHeight(dp(64)); t.setGravity(Gravity.CENTER_VERTICAL);
        p.addView(t); return t;
    }
    private static String label(String name, int v, String[] names, int[] codes) {
        String val = "—";
        for (int i = 0; i < codes.length; i++) if (codes[i] == v) val = names[i];
        if (v >= 0 && val.equals("—")) val = "0x" + Integer.toHexString(v);
        return name + "\n" + val;
    }
    private void choose(String title, String[] names, int[] codes, java.util.function.IntConsumer cb) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title)
                .setItems(names, (d, i) -> cb.accept(codes[i])).show();
    }
}
