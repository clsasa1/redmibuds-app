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
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

import static org.kravchuk.redmibuds.Bento.*;

/** v0.14 bento main screen; fits without scrolling (weighted hero + battery tiles). */
@SuppressLint("MissingPermission")
public class MainActivity extends Activity implements Hub.Ui {
    static final String[] MODE_UI = {"Прозрачность", "Шумодав", "Выкл"};
    static final String[] MODE_GLYPH = {"☼", "◉", "⏻"};
    static final int[] MODE_CODES = {2, 1, 0};

    private final Hub h = Hub.I;
    private SharedPreferences prefs;
    private TextView statusTxt, banner;
    private final TextView[] batTxt = new TextView[3];
    private final MiniBat[] bats = new MiniBat[3];
    private final ImageView[] modeBtn = new ImageView[3];
    private static boolean autoTried;

    private int dp(float v) { return Bento.dp(this, v); }

    private LinearLayout card(LinearLayout root, float topMargin) {
        LinearLayout c = col(this); c.setBackground(round(this, SURFACE, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(topMargin);
        root.addView(c, lp); return c;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("p", MODE_PRIVATE);
        Hub.conn.setContext(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);

        LinearLayout root = col(this); root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(8), dp(16), dp(14));
        setContentView(root);

        LinearLayout tr = row(this); tr.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = one(this, "REDMI Buds 8 Pro", 18, 26, ON_BG, false);
        title.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        title.setPadding(dp(6), 0, 0, 0);
        tr.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView gear = new ImageView(this); gear.setImageResource(R.drawable.ic_settings);
        gear.setScaleType(ImageView.ScaleType.CENTER_INSIDE); gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        tr.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(tr, new LinearLayout.LayoutParams(-1, dp(64)));

        banner = one(this, "", 12, 15, ON_BG, true);
        banner.setBackground(round(this, BLUE, 14)); banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setVisibility(View.GONE);
        root.addView(banner, new LinearLayout.LayoutParams(-1, -2));

        // illustration: photo of the case, ~45% width, centered in leftover space
        LinearLayout artBox = col(this); artBox.setGravity(Gravity.CENTER);
        ImageView art = new ImageView(this); art.setImageResource(R.drawable.buds_case);
        art.setAdjustViewBounds(true); art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int artW = (int) (getResources().getDisplayMetrics().widthPixels * 0.50f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(artW, 0, 1f); alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.weight = 0; alp.height = -2;
        artBox.addView(art, alp);
        art.setOnClickListener(v -> connect());
        root.addView(artBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        statusTxt = one(this, "Нет связи", 12, 15, ON_BG_DIM, false); statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setPadding(0, dp(6), 0, 0);
        statusTxt.setOnClickListener(v -> connect());
        artBox.addView(statusTxt, new LinearLayout.LayoutParams(-1, -2));
        View gap = new View(this); root.addView(gap, new LinearLayout.LayoutParams(-1, dp(18)));

        // battery card
        LinearLayout bc = card(root, 0);
        LinearLayout br = row(this); br.setPadding(0, dp(16), 0, dp(14));
        bc.addView(br);
        String[] lb = {"Л", "П", "Кейс"};
        for (int i = 0; i < 3; i++) {
            if (i > 0) { View d = new View(this); d.setBackgroundColor(DIVIDER); LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), -1); dl.topMargin = dp(4); dl.bottomMargin = dp(4); br.addView(d, dl); }
            LinearLayout c = col(this); c.setGravity(Gravity.CENTER_HORIZONTAL);
            bats[i] = new MiniBat(this); c.addView(bats[i]);
            batTxt[i] = one(this, "—", 14, 18, ON_BG, false); batTxt[i].setGravity(Gravity.CENTER); batTxt[i].setPadding(0, dp(6), 0, 0);
            c.addView(batTxt[i], new LinearLayout.LayoutParams(-1, -2));
            TextView l = one(this, lb[i], 11, 14, ON_BG_DIM, false); l.setGravity(Gravity.CENTER); l.setPadding(0, dp(2), 0, 0);
            c.addView(l, new LinearLayout.LayoutParams(-1, -2));
            br.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // mode card
        LinearLayout mc = card(root, 14);
        LinearLayout mr = row(this); mr.setPadding(0, dp(18), 0, dp(16)); mc.addView(mr);
        int[] micon = {R.drawable.ic_transp, R.drawable.ic_anc, R.drawable.ic_off};
        for (int i = 0; i < 3; i++) {
            final int code = MODE_CODES[i];
            LinearLayout c = col(this); c.setGravity(Gravity.CENTER_HORIZONTAL);
            modeBtn[i] = new ImageView(this); modeBtn[i].setImageResource(micon[i]);
            modeBtn[i].setScaleType(ImageView.ScaleType.FIT_CENTER); modeBtn[i].setPadding(dp(17), dp(17), dp(17), dp(17));
            c.addView(modeBtn[i], new LinearLayout.LayoutParams(dp(62), dp(62)));
            TextView l = one(this, MODE_UI[i], 11, 15, ON_BG, false); l.setGravity(Gravity.CENTER); l.setPadding(0, dp(8), 0, 0);
            c.addView(l, new LinearLayout.LayoutParams(-1, -2));
            c.setOnClickListener(v -> { if (need()) { Hub.conn.setAnc(code); h.anc = code; refresh(); } });
            c.setOnLongClickListener(v -> { startActivity(new Intent(this, FeatureActivity.class).putExtra("page", "anc")); return true; });
            mr.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // list card
        LinearLayout lc = card(root, 14); lc.setPadding(0, dp(6), 0, dp(6));
        String[] nav = {"Шумоподавление", "Жесты", "Звук и эквалайзер", "Поиск наушников", "Доп. настройки"};
        int[] nicon = {R.drawable.ic_anc, R.drawable.ic_gesture, R.drawable.ic_sound, R.drawable.ic_find, R.drawable.ic_settings};
        String[] keys = {"anc", "gestures", "sound", "find", "extra"};
        for (int i = 0; i < nav.length; i++) lc.addView(Bento.listRow(this, nicon[i], nav[i], null, startPage(keys[i])), new LinearLayout.LayoutParams(-1, dp(58)));

        if (getIntent().getBooleanExtra("demo", false)) {   // preview/screenshot mode only
            h.authed = true; h.connected = true; h.left = 1100; h.right = 1100; h.box = 50; h.anc = 1; h.fw = "";
            h.ancDepth = 12; h.trLevel = 0; h.eq = 0; h.adaptive = 1; h.ear = 1; h.dual = 1; h.autoAnswer = 0; h.longL = 7; h.longR = 7;
            int[][] g = {{-1, -1}, {1, 3}, {2, 4}, {6, 6}, {3, 1}, {11, 11}}; for (int i = 0; i < 6; i++) h.gestures[i] = g[i];
            return;
        }
        showCrashIfAny();
        ensureUpdateCheck();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 1);
        else restoreDevice();
    }

    private View.OnClickListener startPage(String k) { return v -> startActivity(new Intent(this, FeatureActivity.class).putExtra("page", k)); }

    private boolean need() {
        if (h.authed) return true;
        Toast.makeText(this, h.connected ? "Идёт вход…" : "Нет связи с наушниками", Toast.LENGTH_SHORT).show();
        if (!h.connected) connect();
        return false;
    }

    @Override protected void onResume() { super.onResume(); h.uis.addIfAbsent(this); refresh(); }
    @Override protected void onPause() { super.onPause(); h.uis.remove(this); }

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
            Devices.logAll(this);
            String last = prefs.getString("addr", "");
            for (BluetoothDevice d : bonded())
                if (d.getAddress().equals(last) && Devices.isRedmi(d) && Devices.rfcommCapable(d)) h.device = d;
            if (h.device == null && !last.isEmpty()) { Log2.add("DEV", "сохранённое устройство " + last + " не Redmi/не классическое — сброшено"); prefs.edit().remove("addr").apply(); }
            h.device = Devices.choose(this);
        }
        render(); autoConnect();
    }

    private void connect() {
        if (h.device == null) { pickDevice(); return; }
        Hub.conn.connect(h.device);
    }
    private void autoConnect() {
        if (autoTried || h.device == null || Hub.conn.isConnected()) return;
        autoTried = true;
        Log2.add("**", "автоподключение RFCOMM MIWEAR к " + h.device.getAddress());
        connect();
    }
    private void pickDevice() {
        List<BluetoothDevice> l = bonded();
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = Devices.name(l.get(i)) + "\n" + l.get(i).getAddress() + " · " + Devices.type(l.get(i)) + (Devices.hasFd2d(l.get(i)) ? " · MIWEAR" : "");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Сопряжённые устройства")
                .setItems(names, (d, i) -> { h.device = l.get(i); prefs.edit().putString("addr", h.device.getAddress()).apply(); Hub.conn.close(); render(); getWindow().getDecorView().postDelayed(this::connect, 700); })
                .show();
    }

    private void render() { refresh(); }

    @Override public void refresh() {
        statusTxt.setText(h.authed ? "Подключено" + (h.fw.isEmpty() ? "" : " · " + h.fw)
                : h.connected ? "Вход…" : h.device == null ? "Нет связи · нажмите, чтобы подключить" : h.state);
        int[] v = {h.left, h.right, h.box};
        for (int i = 0; i < 3; i++) { batTxt[i].setText(v[i] < 0 ? "—" : (v[i] % 1000) + "%"); bats[i].set(v[i]); }
        for (int i = 0; i < 3; i++) {
            boolean on = h.anc == MODE_CODES[i];
            modeBtn[i].setBackground(Bento.modeCircle(this, on));
            int pd = dp(on ? 18 : 17); modeBtn[i].setPadding(pd, pd, pd, pd);
            modeBtn[i].setColorFilter(on ? 0xFFFFFFFF : 0xFF2C2C2E);
        }
    }
}
