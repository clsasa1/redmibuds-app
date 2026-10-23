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
    private final TextView[] modeBtn = new TextView[3];
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
        title.setPadding(dp(4), 0, 0, 0);
        tr.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = tv(this, "⚙", 26, ON_BG, false); gear.setGravity(Gravity.CENTER);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        tr.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(tr, new LinearLayout.LayoutParams(-1, dp(56)));

        banner = one(this, "", 12, 16, ON_BG, true);
        banner.setBackground(round(this, BLUE, 16)); banner.setPadding(dp(14), dp(8), dp(14), dp(8));
        banner.setVisibility(View.GONE);
        root.addView(banner, new LinearLayout.LayoutParams(-1, -2));

        // illustration (shrinks) + status
        CaseArt art = new CaseArt(this);
        art.setOnClickListener(v -> connect());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, 0, 1f); ap.topMargin = dp(8);
        root.addView(art, ap);
        statusTxt = one(this, "Нет связи", 12, 15, ON_BG_DIM, false); statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setPadding(0, dp(8), 0, dp(4));
        statusTxt.setOnClickListener(v -> connect());
        root.addView(statusTxt, new LinearLayout.LayoutParams(-1, -2));

        // battery card
        LinearLayout bc = card(root, 10);
        LinearLayout br = row(this); br.setPadding(0, dp(14), 0, dp(14));
        bc.addView(br);
        String[] lb = {"Л", "П", "Кейс"};
        for (int i = 0; i < 3; i++) {
            if (i > 0) { View d = new View(this); d.setBackgroundColor(DIVIDER); br.addView(d, new LinearLayout.LayoutParams(dp(1), -1)); }
            LinearLayout c = col(this); c.setGravity(Gravity.CENTER_HORIZONTAL);
            bats[i] = new MiniBat(this); c.addView(bats[i]);
            batTxt[i] = one(this, "—", 14, 18, ON_BG, false); batTxt[i].setGravity(Gravity.CENTER); batTxt[i].setPadding(0, dp(4), 0, 0);
            c.addView(batTxt[i], new LinearLayout.LayoutParams(-1, -2));
            TextView l = one(this, lb[i], 11, 14, ON_BG_DIM, false); l.setGravity(Gravity.CENTER);
            c.addView(l, new LinearLayout.LayoutParams(-1, -2));
            br.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // mode card
        LinearLayout mc = card(root, 12);
        LinearLayout mr = row(this); mr.setPadding(0, dp(16), 0, dp(14)); mc.addView(mr);
        for (int i = 0; i < 3; i++) {
            final int code = MODE_CODES[i];
            LinearLayout c = col(this); c.setGravity(Gravity.CENTER_HORIZONTAL);
            modeBtn[i] = icon(this, MODE_GLYPH[i], LIGHT, 0xFF1C1C1E, 56);
            c.addView(modeBtn[i]);
            TextView l = one(this, MODE_UI[i], 11, 15, ON_BG, false); l.setGravity(Gravity.CENTER); l.setPadding(0, dp(8), 0, 0);
            c.addView(l, new LinearLayout.LayoutParams(-1, -2));
            c.setOnClickListener(v -> { if (need()) { Hub.conn.setAnc(code); h.anc = code; refresh(); } });
            c.setOnLongClickListener(v -> { startActivity(new Intent(this, FeatureActivity.class).putExtra("page", "anc")); return true; });
            mr.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        // list card
        LinearLayout lc = card(root, 12); lc.setPadding(0, dp(6), 0, dp(6));
        String[] nav = {"Жесты", "Звук и эквалайзер", "Поиск наушников", "Доп. настройки"};
        String[] glyph = {"☝", "♪", "⌖", "⚙"};
        String[] keys = {"gestures", "sound", "find", "extra"};
        for (int i = 0; i < nav.length; i++) {
            LinearLayout r = row(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(16), 0, dp(16), 0);
            r.addView(icon(this, glyph[i], BLUE, 0xFFFFFFFF, 40));
            TextView t = one(this, nav[i], 13, 17, ON_BG, false); t.setPadding(dp(16), 0, dp(8), 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(tv(this, "›", 22, ON_BG_DIM, false));
            final String k = keys[i];
            r.setOnClickListener(v -> startActivity(new Intent(this, FeatureActivity.class).putExtra("page", k)));
            lc.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));
        }
        statusTxt.setOnLongClickListener(v -> { startActivity(new Intent(this, FeatureActivity.class).putExtra("page", "anc")); return true; });

        showCrashIfAny();
        ensureUpdateCheck();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 1);
        else restoreDevice();
    }

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
                if (d.getAddress().equals(last) && (Devices.hasFd2d(d) || Devices.type(d).equals("CLASSIC"))) h.device = d;
            if (h.device == null) h.device = Devices.choose(this);
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
            android.graphics.drawable.GradientDrawable d = (android.graphics.drawable.GradientDrawable) modeBtn[i].getBackground();
            d.setColor(on ? BLUE : LIGHT);
            d.setStroke(on ? dp(3) : 0, 0xFF0B1F44);
            modeBtn[i].setTextColor(on ? 0xFFFFFFFF : 0xFF1C1C1E);
            modeBtn[i].setPadding(0, 0, 0, 0);
        }
    }
}
