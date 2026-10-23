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
    static final String[] MODE_UI = {"Шумоподавление", "Выкл", "Прозрачность"};
    static final int[] MODE_CODES = {1, 0, 2};

    private final Hub h = Hub.I;
    private SharedPreferences prefs;
    private TextView statusTxt, statusSub, banner;
    private View statusDot;
    private final TextView[] batTxt = new TextView[3];
    private final BatBar[] bars = new BatBar[3];
    private final TextView[] modes = new TextView[3];
    private static boolean autoTried;

    private int dp(float v) { return Bento.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("p", MODE_PRIVATE);
        Hub.conn.setContext(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);

        LinearLayout root = col(this); root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(14));
        setContentView(root);

        // title + gear
        LinearLayout tr = row(this); tr.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = one(this, "Redmi Buds 8 Pro", 18, 28, ON_BG, true);
        title.setOnClickListener(v -> pickDevice());
        tr.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = tv(this, "⚙", 24, ON_BG, false);
        gear.setGravity(Gravity.CENTER); gear.setBackground(round(this, SURFACE, 26));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        tr.addView(gear, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(tr, new LinearLayout.LayoutParams(-1, dp(60)));

        banner = one(this, "", 12, 16, INK, true);
        banner.setBackground(round(this, BLUE_SOFT, 16)); banner.setPadding(dp(14), dp(8), dp(14), dp(8));
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.bottomMargin = dp(8);
        root.addView(banner, bp);

        // hero mint tile
        LinearLayout hero = row(this); hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(grad(this, MINT, MINT2, 28)); hero.setPadding(dp(14), dp(12), dp(16), dp(12));
        Style.BudsArt art = new Style.BudsArt(this); art.colors(0xFF2A2725, 0xFF2A2725);
        hero.addView(art, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout hs = col(this); hs.setGravity(Gravity.CENTER_VERTICAL);
        statusTxt = one(this, "Нет связи", 16, 26, INK, true);
        hs.addView(statusTxt);
        LinearLayout sr = row(this); sr.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this); statusDot.setBackground(round(this, 0xFF999999, 5));
        sr.addView(statusDot, new LinearLayout.LayoutParams(dp(9), dp(9)));
        statusSub = one(this, "", 10, 14, INK_DIM, false); statusSub.setPadding(dp(6), 0, 0, 0);
        sr.addView(statusSub, new LinearLayout.LayoutParams(0, -2, 1f));
        hs.addView(sr);
        hero.addView(hs, new LinearLayout.LayoutParams(0, -2, 1.15f));
        hero.setOnClickListener(v -> { if (h.device == null) pickDevice(); else if (!Hub.conn.isConnected()) connect(); });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, 0, 1.1f); hp.bottomMargin = dp(10);
        root.addView(hero, hp);

        // battery tiles
        LinearLayout br = row(this);
        int[] tc = {PEACH, LAVENDER, YELLOW}, bc = {BAT_GREEN, BAT_PURPLE, BAT_YELLOW};
        int[] gc = {0xFFE59B74, 0xFFA58BDB, 0xFFB8A57A};
        for (int i = 0; i < 3; i++) {
            LinearLayout t = col(this); t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setBackground(round(this, tc[i], 26)); t.setPadding(dp(10), dp(12), dp(10), dp(12));
            batTxt[i] = one(this, "—", 14, 24, INK, true); batTxt[i].setGravity(Gravity.CENTER);
            t.addView(batTxt[i], new LinearLayout.LayoutParams(-1, -2));
            t.addView(new Glyph(this, gc[i], i == 2), new LinearLayout.LayoutParams(-1, 0, 1f));
            bars[i] = new BatBar(this, bc[i]);
            t.addView(bars[i], new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            br.addView(t, lp);
        }
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(-1, 0, 1f); brp.bottomMargin = dp(10);
        root.addView(br, brp);

        // mode row
        LinearLayout mr = row(this); mr.setBackground(round(this, 0xFFD9D6CF, 28)); mr.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (int i = 0; i < 3; i++) {
            final int code = MODE_CODES[i];
            modes[i] = pill(this, MODE_UI[i], v -> { if (need()) { Hub.conn.setAnc(code); h.anc = code; refresh(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), i == 0 ? 1.25f : 1f);
            if (i > 0) lp.leftMargin = dp(6);
            mr.addView(modes[i], lp);
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2); mp.bottomMargin = dp(10);
        root.addView(mr, mp);

        // navigation tiles 2x3
        String[] nav = {"Шумоподавление", "Жесты", "Звук", "Поиск наушников", "Доп. настройки"};
        String[] keys = {"anc", "gestures", "sound", "find", "extra"};
        int[] nc = {0xFF33403A, 0xFF3D3547, 0xFF473A33, 0xFF46412F, 0xFF353B47};
        LinearLayout cur = null;
        for (int i = 0; i < nav.length; i++) {
            if (i % 2 == 0) {
                cur = row(this);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(54)); if (i > 0) rp.topMargin = dp(8);
                root.addView(cur, rp);
            }
            TextView t = one(this, nav[i] + "  ›", 12, 16, ON_BG, true);
            t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(16), 0, dp(10), 0);
            t.setBackground(round(this, nc[i], 22));
            final String k = keys[i];
            t.setOnClickListener(v -> startActivity(new Intent(this, FeatureActivity.class).putExtra("page", k)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % 2 == 1) lp.leftMargin = dp(8);
            cur.addView(t, lp);
        }

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
            String last = prefs.getString("addr", "");
            for (BluetoothDevice d : bonded()) {
                String n = d.getName() == null ? "" : d.getName().toLowerCase();
                if (d.getAddress().equals(last) || (last.isEmpty() && h.device == null && n.contains("buds"))) h.device = d;
            }
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
        for (int i = 0; i < l.size(); i++) names[i] = (l.get(i).getName() == null ? "?" : l.get(i).getName()) + "\n" + l.get(i).getAddress();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Сопряжённые устройства")
                .setItems(names, (d, i) -> { h.device = l.get(i); prefs.edit().putString("addr", h.device.getAddress()).apply(); render(); connect(); })
                .show();
    }

    private void render() { refresh(); }

    @Override public void refresh() {
        boolean ok = h.authed;
        ((android.graphics.drawable.GradientDrawable) statusDot.getBackground()).setColor(ok ? 0xFF3FA36B : h.connected ? 0xFFE0A030 : 0xFF999999);
        statusTxt.setText(ok ? "Подключено" : h.connected ? "Вход…" : "Нет связи");
        statusSub.setText(ok ? "Готовы к использованию" + (h.fw.isEmpty() ? "" : " · " + h.fw)
                : h.device == null ? "Нажмите, чтобы выбрать" : h.state);
        String[] lb = {"Л", "П", "Кейс"};
        int[] v = {h.left, h.right, h.box};
        for (int i = 0; i < 3; i++) {
            batTxt[i].setText(lb[i] + " " + (v[i] < 0 ? "—" : (v[i] % 1000) + "%"));
            bars[i].set(v[i]);
        }
        for (int i = 0; i < 3; i++) setPill(modes[i], h.anc == MODE_CODES[i], STONE);
    }
}
