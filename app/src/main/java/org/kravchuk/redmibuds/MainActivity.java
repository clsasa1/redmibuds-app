/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.kravchuk.redmibuds;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity implements BudsConnection.Listener {
    private static final int BG = 0xFF000000, TEXT = 0xFFFFFFFF, SUB = 0xFFB0B0B0,
            ACCENT_BG = 0xFF0A2540, ACCENT = 0xFF4DA3FF, GREEN = 0xFF34C759, GREY = 0xFF666666, BAR_TRACK = 0xFF1A1A1A;

    /** UI order: Off, Transparency, ANC → protocol codes 0, 2, 1 */
    static final String[] ANC_UI = {"Выкл", "Прозрачность", "Шумодав"};
    static final int[] ANC_CODES = {0, 2, 1};
    static final String[] EQ_UI = {"Стандарт", "Бас", "Голос", "Высокие"};
    static final int[] EQ_CODES = {0x00, 0x05, 0x01, 0x06};

    private final Hub h = Hub.I;
    private SharedPreferences prefs;
    private TextView statusTxt;
    private View statusDot;
    private final Style.Ring[] rings = new Style.Ring[3];
    private TextView[] ancSeg = new TextView[3], eqChips = new TextView[4];
    private Switch earSw;
    private boolean earSuppress;
    private TextView banner;
    private static boolean autoTried;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("p", MODE_PRIVATE);
        Hub.conn.setContext(this);
        getWindow().setStatusBarColor(Style.BG); getWindow().setNavigationBarColor(Style.BG);

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Style.BG);
        root.setPadding(dp(18), dp(16), dp(18), dp(16));
        setContentView(root);

        LinearLayout titleRow = hrow(root); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Style.tv(this, "Redmi Buds 8 Pro", 28, Style.TEXT, true);
        title.setMaxLines(1);
        title.setAutoSizeTextTypeUniformWithConfiguration(18, 28, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Style.GearButton gear = new Style.GearButton(this);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        titleRow.addView(gear);
        LinearLayout st = hrow(root); st.setGravity(Gravity.CENTER_VERTICAL); st.setPadding(0, dp(6), 0, 0);
        statusDot = new View(this);
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.OVAL); gd.setColor(GREY);
        statusDot.setBackground(gd);
        st.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusTxt = Style.tv(this, "Нет связи", 18, Style.SUB, false); statusTxt.setPadding(dp(10), 0, 0, 0);
        statusTxt.setMinHeight(dp(32)); statusTxt.setGravity(Gravity.CENTER_VERTICAL);
        st.addView(statusTxt);

        banner = Style.tv(this, "", 17, Style.TEXT, true);
        banner.setBackground(Style.round(this, Style.BLUE, 16)); banner.setPadding(dp(16), dp(14), dp(16), dp(14));
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2); blp.topMargin = dp(12); root.addView(banner, blp);

        Style.BudsArt art = new Style.BudsArt(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, 0, 1f); alp.topMargin = dp(6);
        root.addView(art, alp);

        LinearLayout bats = hrow(root);
        ((LinearLayout.LayoutParams) bats.getLayoutParams()).topMargin = dp(6);
        String[] labels = {"Л", "П", "Кейс"};
        for (int i = 0; i < 3; i++) {
            rings[i] = new Style.Ring(this, labels[i]);
            bats.addView(rings[i], new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // ANC card
        LinearLayout ancCard = Style.card(this, root);
        ancCard.addView(Style.tv(this, "Шумоподавление", 18, Style.TEXT, false));
        LinearLayout seg = hrow(ancCard);
        ((LinearLayout.LayoutParams) seg.getLayoutParams()).topMargin = dp(8);
        seg.setBackground(Style.round(this, Style.PILL, 28));
        for (int i = 0; i < 3; i++) {
            final int ui = i;
            TextView t = Style.pill(this, ANC_UI[i], v -> {
                if (probeBlocked()) return;
                Hub.conn.setAnc(ANC_CODES[ui]); h.anc = ANC_CODES[ui]; render();
            });
            ancSeg[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // EQ card
        LinearLayout eqCard = Style.card(this, root);
        eqCard.addView(Style.tv(this, "Эквалайзер", 18, Style.TEXT, false));
        LinearLayout eq = hrow(eqCard);
        ((LinearLayout.LayoutParams) eq.getLayoutParams()).topMargin = dp(8);
        for (int i = 0; i < 4; i++) {
            final int c = EQ_CODES[i];
            TextView t = Style.pill(this, EQ_UI[i], v -> { if (probeBlocked()) return; Hub.conn.setEq(c); h.eq = c; render(); });
            t.setPadding(dp(4), 0, dp(4), 0);
            eqChips[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.setMarginStart(dp(6));
            eq.addView(t, lp);
        }

        // Ear detection card
        LinearLayout earCard = Style.card(this, root);
        LinearLayout ear = hrow(earCard); ear.setGravity(Gravity.CENTER_VERTICAL); ear.setMinimumHeight(dp(48));
        ear.addView(new Style.EarIcon(this), new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView earT = Style.tv(this, "Обнаружение уха", 18, Style.TEXT, false); earT.setMaxLines(1); earT.setPadding(dp(14), 0, 0, 0);
        ear.addView(earT, new LinearLayout.LayoutParams(0, -2, 1f));
        earSw = new Switch(this);
        earSw.setScaleX(1.25f); earSw.setScaleY(1.25f);
        try {
            earSw.setThumbTintList(android.content.res.ColorStateList.valueOf(Style.TEXT));
            earSw.setTrackTintList(new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Style.BLUE, Style.TRACK}));
        } catch (Throwable ignored) {}
        earSw.setOnCheckedChangeListener((b2, on) -> {
            if (earSuppress) return;
            if (probeBlocked()) { earSuppress = true; earSw.setChecked(h.ear); earSuppress = false; return; }
            Hub.conn.setEarDetection(on); h.ear = on;
        });
        ear.addView(earSw);


        title.setOnClickListener(v -> pickDevice());
        statusTxt.setOnClickListener(v -> {
            if (h.device == null) pickDevice();
            else if (!Hub.conn.isConnected()) connectBle();
        });

        showCrashIfAny();
        ensureUpdateCheck();

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 1);
        else restoreDevice();
    }

    @Override protected void onResume() { super.onResume(); h.ui = this; render(); }
    @Override protected void onPause() { super.onPause(); if (h.ui == this) h.ui = null; }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) restoreDevice();
        else Toast.makeText(this, "Нужно разрешение «Устройства поблизости»", Toast.LENGTH_LONG).show();
    }

    private void ensureUpdateCheck() {
        Updater.check(this, new Updater.Result() {
            public void onNewer(String tag, String url, String notes) {
                banner.setText("Доступно обновление — обновить");
                banner.setVisibility(View.VISIBLE);
                banner.setOnClickListener(v -> Updater.offer(MainActivity.this, tag, url, notes));
            }
            public void onNone(String msg) { Log2.add("UPD", msg); }
        });
    }

    private void showCrashIfAny() {
        java.io.File cf = new java.io.File(getFilesDir(), "crash.txt");
        if (!cf.exists()) return;
        java.io.File prev = new java.io.File(getFilesDir(), "crash_last.txt");
        prev.delete(); cf.renameTo(prev);
        String txt = App.readFile(prev);
        TextView t = new TextView(this); t.setText(txt); t.setTextSize(11); t.setTextIsSelectable(true); t.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView s = new ScrollView(this); s.addView(t);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Прошлый запуск завершился сбоем")
                .setView(s).setPositiveButton("Поделиться", (d, w) -> App.share(this, prev, "Отчёт о сбое"))
                .setNegativeButton("Закрыть", null).show();
    }

    private List<BluetoothDevice> bonded() {
        List<BluetoothDevice> l = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return l;
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        if (bm == null || bm.getAdapter() == null || !bm.getAdapter().isEnabled()) {
            Toast.makeText(this, "Bluetooth выключен", Toast.LENGTH_SHORT).show(); return l;
        }
        try { l.addAll(bm.getAdapter().getBondedDevices()); } catch (SecurityException e) {
            Toast.makeText(this, "Нет разрешения Bluetooth", Toast.LENGTH_SHORT).show();
        }
        return l;
    }

    private void restoreDevice() {
        if (h.device == null) {
            String last = prefs.getString("addr", "");
            for (BluetoothDevice d : bonded()) {
                String n = d.getName() == null ? "" : d.getName().toLowerCase();
                if (d.getAddress().equals(last) || (last.isEmpty() && h.device == null && n.contains("buds"))) h.device = d;
            }
        }
        render(); autoConnect();
    }

    static GattClient gatt(android.content.Context c) { if (Hub.I.gatt == null) Hub.I.gatt = new GattClient(c); return Hub.I.gatt; }
    private void connectBle() {
        if (h.device == null) { pickDevice(); return; }
        Hub.conn.close(); gatt(this).connect(h.device);
    }
    private void autoConnect() {
        if (autoTried || h.device == null || Hub.conn.isConnected() || (h.gatt != null && h.gatt.ready())) return;
        autoTried = true;
        Log2.add("**", "автоподключение BLE к " + h.device.getAddress());
        connectBle();
    }
    private void pickDevice() {
        List<BluetoothDevice> l = bonded();
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = (l.get(i).getName() == null ? "?" : l.get(i).getName()) + "\n" + l.get(i).getAddress();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Сопряжённые устройства")
                .setItems(names, (d, i) -> { h.device = l.get(i); prefs.edit().putString("addr", h.device.getAddress()).apply(); render(); connectBle(); })
                .show();
    }

    private boolean probeBlocked() {
        if (h.gatt != null && h.gatt.probing && Hub.conn.altSender == null) {
            Toast.makeText(this, "Канал управления не найден (идёт проба)", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void render() {
        try { render0(); } catch (Throwable e) { Log2.add("UI", "render: " + e); }
    }
    private void render0() {
        boolean ok = Hub.conn.authed, conn = Hub.conn.isConnected();
        boolean probing = h.gatt != null && h.gatt.probing;
        ((GradientDrawable) statusDot.getBackground()).setColor(ok ? Style.GREEN : conn || probing ? Style.YELLOW : GREY);
        String st;
        if (ok) st = "Подключено";
        else if (probing) st = "Подключение…";
        else if (conn) st = "Подключено";
        else st = "Нет связи";
        statusTxt.setText(st);

        int[] vals = {
                h.left >= 0 ? h.left % 1000 : (h.ble.size() > 0 ? h.ble.get(0) : -1),
                h.right >= 0 ? h.right % 1000 : (h.ble.size() > 1 ? h.ble.get(1) : -1),
                h.box >= 0 ? h.box % 1000 : -1
        };
        for (int i = 0; i < 3; i++) rings[i].set(vals[i]);
        for (int i = 0; i < 3; i++) setSeg(ancSeg[i], h.anc == ANC_CODES[i]);
        for (int i = 0; i < 4; i++) setChip(eqChips[i], h.eq == EQ_CODES[i]);
        if (earSw.isChecked() != h.ear) { earSuppress = true; earSw.setChecked(h.ear); earSuppress = false; }
    }

    private void setSeg(TextView t, boolean on) { Style.setPill(t, on); }
    private void setChip(TextView t, boolean on) { Style.setPill(t, on); }

    @Override public void onState(String s) { render(); }
    @Override public void onBattery(int l, int r, int c) { render(); }
    @Override public void onAnc(int m, int s) { render(); }
    @Override public void onEq(int p) { render(); }
    @Override public void onInfo(String s) {}
    @Override public void onMethod(String m) { render(); }
    @Override public void onEar(boolean on) { render(); }

    // ---- helpers ----
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView tv(String s, float size, int color, boolean med) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (med) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    private LinearLayout hrow(LinearLayout p) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(l, new LinearLayout.LayoutParams(-1, -2)); return l;
    }
    private GradientDrawable pill(int color, float r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g;
    }
    private GradientDrawable strokePill(int stroke, float r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(Color.TRANSPARENT); g.setCornerRadius(dp(r));
        g.setStroke(dp(1), stroke); return g;
    }

    /** Thin blue level bar. */
    static final class LevelBar extends View {
        private int level = -1;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        LevelBar(android.content.Context c) {
            super(c); track.setColor(BAR_TRACK); fill.setColor(ACCENT);
        }
        void setLevel(int v) { level = v; invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            float r = getHeight() / 2f;
            cv.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, track);
            if (level > 0) {
                float w = getWidth() * Math.min(100, level) / 100f;
                cv.drawRoundRect(0, 0, Math.max(w, r * 2), getHeight(), r, r, fill);
            }
        }
    }
}
