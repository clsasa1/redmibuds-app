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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

import static org.kravchuk.redmibuds.Ui.*;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity implements BudsConnection.Listener {
    static final String[] EQ_NAMES = {"Стандарт", "Баланс", "Высокие", "Бас", "Голос", "Громкость", "Свой"};
    static final int[] EQ_CODES = {0x00, 0x15, 0x06, 0x05, 0x01, 0x07, 0x0A};
    static final String[] ANC_NAMES = {"Выкл", "Шумодав", "Прозрачность"};
    static final String[] ANC_LVL = {"Баланс", "Лёгкий", "Глубокий", "Адаптив"};
    static final String[] TR_LVL = {"Обычный", "Голос", "Окружение"};

    private final Hub h = Hub.I;
    private SharedPreferences prefs;
    private TextView name, status, method, info;
    private View dot;
    private Ring rL, rR, rC;
    private final TextView[] ancSeg = new TextView[3], ancLvl = new TextView[4], trLvl = new TextView[3], eqChips = new TextView[EQ_CODES.length];
    private LinearLayout ancLvlRow, trLvlRow;
    private TextView lvlTitle, banner;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("p", MODE_PRIVATE);
        Hub.conn.setContext(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG); sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(this, 16); root.setPadding(p, dp(this, 24), p, p);
        sv.addView(root); setContentView(sv);

        // top bar
        LinearLayout top = row(this, root, 0); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView appT = new TextView(this); appT.setText("Redmi Buds"); appT.setTextSize(24); appT.setTextColor(TEXT);
        appT.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        top.addView(appT, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView more = new TextView(this); more.setText("⋮"); more.setTextSize(26); more.setTextColor(TEXT);
        more.setPadding(dp(this, 14), 0, dp(this, 6), 0);
        more.setOnClickListener(v -> {
            android.widget.PopupMenu pm = new android.widget.PopupMenu(this, more);
            pm.getMenu().add(0, 1, 0, "Диагностика и журнал");
            pm.getMenu().add(0, 2, 1, "Проверить обновление");
            pm.setOnMenuItemClickListener(it -> {
                if (it.getItemId() == 1) startActivity(new Intent(this, DiagActivity.class)); else Updater.checkInteractive(this);
                return true;
            });
            pm.show();
        });
        top.addView(more);
        banner = new TextView(this); banner.setTextColor(0xFF0F1115); banner.setTextSize(14); banner.setBackground(round(ORANGE, 16, this));
        banner.setPadding(dp(this, 14), dp(this, 10), dp(this, 14), dp(this, 10)); banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2); blp.topMargin = dp(this, 12); root.addView(banner, blp);
        View sp = new View(this); root.addView(sp, new LinearLayout.LayoutParams(1, dp(this, 14)));

        // hero
        LinearLayout hero = card(this, root);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1F3A3A, 0xFF1B1F26});
        hg.setCornerRadius(dp(this, 22)); hero.setBackground(hg);
        name = text(this, hero, "Наушники не выбраны", 22, TEXT, true);
        LinearLayout st = row(this, hero, 10); st.setGravity(Gravity.CENTER_VERTICAL);
        st.setBackground(round(0x33000000, 50, this)); st.setPadding(dp(this, 12), dp(this, 6), dp(this, 14), dp(this, 6));
        ((LinearLayout.LayoutParams) st.getLayoutParams()).width = -2;
        dot = Ui.dot(this, RED); st.addView(dot);
        status = new TextView(this); status.setTextColor(TEXT); status.setTextSize(13); status.setPadding(dp(this, 8), 0, 0, 0); st.addView(status);
        method = text(this, hero, "", 12, SUB, false); method.setPadding(0, dp(this, 6), 0, 0);
        LinearLayout hb = row(this, hero, 14);
        addWeighted(hb, button(this, "Выбрать", 0xFFB0BEC5, v -> pickDevice()), 3);
        addWeighted(hb, button(this, "Подключить", ACCENT, v -> connect()), 3);
        addWeighted(hb, button(this, "Отключить", 0xFF78909C, v -> { Hub.conn.close(); if (h.gatt != null) h.gatt.close(); Hub.I.onState("Отключено"); }), 3);
        LinearLayout hb2 = row(this, hero, 8);
        addWeighted(hb2, button(this, "Подключить по BLE (LE Audio)", ORANGE, v -> connectBle()), 3);

        // battery
        LinearLayout bat = card(this, root);
        header(this, bat, "Батарея");
        LinearLayout br = row(this, bat, 0);
        rL = new Ring(this, "Левый"); rR = new Ring(this, "Правый"); rC = new Ring(this, "Кейс");
        addWeighted(br, rL, 0); addWeighted(br, rR, 0); addWeighted(br, rC, 0);
        info = text(this, bat, "", 12, SUB, false); info.setPadding(0, dp(this, 8), 0, 0);
        TextView rf = chip(this, "↻  Обновить", v -> Hub.conn.refresh());
        LinearLayout rfr = row(this, bat, 10); rfr.addView(rf);

        // ANC
        LinearLayout anc = card(this, root);
        header(this, anc, "Шумоподавление");
        LinearLayout seg = row(this, anc, 0);
        seg.setBackground(round(CARD2, 50, this)); seg.setPadding(dp(this, 4), dp(this, 4), dp(this, 4), dp(this, 4));
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView t = chip(this, ANC_NAMES[i], v -> { Hub.conn.setAnc(m); h.anc = m; render(); });
            ancSeg[i] = t; addWeighted(seg, t, 2);
        }
        lvlTitle = text(this, anc, "", 13, SUB, false); lvlTitle.setPadding(0, dp(this, 14), 0, 0);
        ancLvlRow = row(this, anc, 8);
        for (int i = 0; i < 4; i++) { final int v2 = i; ancLvl[i] = chip(this, ANC_LVL[i], v -> { Hub.conn.setStrength(1, v2); h.ancLevel = v2; render(); }); addWeighted(ancLvlRow, ancLvl[i], 3); }
        trLvlRow = row(this, anc, 8);
        for (int i = 0; i < 3; i++) { final int v2 = i; trLvl[i] = chip(this, TR_LVL[i], v -> { Hub.conn.setStrength(2, v2); h.trLevel = v2; render(); }); addWeighted(trLvlRow, trLvl[i], 3); }

        // EQ
        LinearLayout eq = card(this, root);
        header(this, eq, "Эквалайзер");
        LinearLayout r = null;
        for (int i = 0; i < EQ_CODES.length; i++) {
            if (i % 3 == 0) r = row(this, eq, i == 0 ? 0 : 8);
            final int c = EQ_CODES[i];
            eqChips[i] = chip(this, EQ_NAMES[i], v -> { Hub.conn.setEq(c); h.eq = c; render(); });
            addWeighted(r, eqChips[i], 3);
        }
        if (EQ_CODES.length % 3 != 0) for (int k = EQ_CODES.length % 3; k < 3; k++) addWeighted(r, new View(this), 3);

        LinearLayout ur = row(this, root, 0); ur.setGravity(Gravity.CENTER); ur.addView(chip(this, "Проверить обновление", v -> Updater.checkInteractive(this)));
        text(this, root, "Версия " + Updater.current(this) + ". Протокол на основе Gadgetbridge (AGPL-3.0). Диагностика и журнал — в меню ⋮", 11, SUB, false).setGravity(Gravity.CENTER);

        showCrashIfAny();
        Updater.check(this, new Updater.Result() {
            public void onNewer(String tag, String url, String notes) {
                banner.setText("Доступно обновление " + tag + " — нажмите, чтобы установить");
                banner.setVisibility(View.VISIBLE);
                banner.setOnClickListener(v -> Updater.offer(MainActivity.this, tag, url, notes));
            }
            public void onNone(String msg) { Log2.add("UPD", msg); }
        });
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 1);
        else restoreDevice();
    }

    @Override protected void onResume() { super.onResume(); h.ui = this; render(); }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) restoreDevice();
        else Toast.makeText(this, "Нужно разрешение «Устройства поблизости»", Toast.LENGTH_LONG).show();
    }

    private void showCrashIfAny() {
        java.io.File cf = new java.io.File(getFilesDir(), "crash.txt");
        if (!cf.exists()) return;
        java.io.File prev = new java.io.File(getFilesDir(), "crash_last.txt");
        prev.delete(); cf.renameTo(prev);
        String txt = App.readFile(prev);
        TextView t = new TextView(this); t.setText(txt); t.setTextSize(11); t.setTextIsSelectable(true); t.setPadding(dp(this, 16), dp(this, 8), dp(this, 16), dp(this, 8));
        ScrollView s = new ScrollView(this); s.addView(t);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Прошлый запуск завершился сбоем")
                .setView(s).setPositiveButton("Поделиться", (d, w) -> App.share(this, prev, "Отчёт о сбое"))
                .setNegativeButton("Закрыть", null).show();
    }

    private List<BluetoothDevice> bonded() {
        List<BluetoothDevice> l = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return l;
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        if (bm == null || bm.getAdapter() == null || !bm.getAdapter().isEnabled()) { Toast.makeText(this, "Bluetooth выключен", Toast.LENGTH_SHORT).show(); return l; }
        try { l.addAll(bm.getAdapter().getBondedDevices()); } catch (SecurityException e) { Toast.makeText(this, "Нет разрешения Bluetooth", Toast.LENGTH_SHORT).show(); }
        return l;
    }

    private static boolean autoTried;
    private void restoreDevice() {
        if (h.device != null) { render(); autoConnect(); return; }
        String last = prefs.getString("addr", "");
        for (BluetoothDevice d : bonded()) {
            String n = d.getName() == null ? "" : d.getName().toLowerCase();
            if (d.getAddress().equals(last) || (last.isEmpty() && h.device == null && n.contains("buds"))) h.device = d;
        }
        render();
        autoConnect();
    }

    /** Auto-connect over BLE once per process start. */
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
                .setItems(names, (d, i) -> { h.device = l.get(i); prefs.edit().putString("addr", h.device.getAddress()).apply(); render(); })
                .show();
    }

    private void connect() {
        if (h.device == null) { pickDevice(); return; }
        Hub.conn.connect(h.device);
    }

    static GattClient gatt(android.content.Context c) { if (Hub.I.gatt == null) Hub.I.gatt = new GattClient(c); return Hub.I.gatt; }
    private void connectBle() {
        if (h.device == null) { pickDevice(); return; }
        Hub.conn.close();
        gatt(this).connect(h.device);
    }

    private void render() {
        try { render0(); } catch (Throwable e) { Log2.add("UI", "render: " + e); }
    }
    private void render0() {
        name.setText(h.device == null ? "Наушники не выбраны" : (h.device.getName() == null ? h.device.getAddress() : h.device.getName()));
        status.setText(h.state);
        boolean ok = Hub.conn.authed, conn = Hub.conn.isConnected();
        ((GradientDrawable) dot.getBackground()).setColor(ok ? GREEN : conn ? ORANGE : (h.state.contains("Попытка") || h.state.contains("…")) ? 0xFFFFCA28 : RED);
        method.setText(h.method.isEmpty() ? "" : "Канал: " + h.method);
        info.setText(h.info);
        rL.set(h.left >= 0 ? h.left : h.ble.size() > 0 ? h.ble.get(0) : -1); rR.set(h.right >= 0 ? h.right : h.ble.size() > 1 ? h.ble.get(1) : -1); rC.set(h.box);
        for (int i = 0; i < 3; i++) setChip(ancSeg[i], h.anc == i, i == 1 ? ACCENT : i == 2 ? ORANGE : 0xFFB0BEC5);
        ancLvlRow.setVisibility(h.anc == 1 ? View.VISIBLE : View.GONE);
        trLvlRow.setVisibility(h.anc == 2 ? View.VISIBLE : View.GONE);
        lvlTitle.setVisibility(h.anc == 1 || h.anc == 2 ? View.VISIBLE : View.GONE);
        lvlTitle.setText(h.anc == 1 ? "Уровень шумоподавления" : "Уровень прозрачности");
        for (int i = 0; i < 4; i++) setChip(ancLvl[i], h.ancLevel == i, ACCENT);
        for (int i = 0; i < 3; i++) setChip(trLvl[i], h.trLevel == i, ORANGE);
        for (int i = 0; i < EQ_CODES.length; i++) setChip(eqChips[i], h.eq == EQ_CODES[i], ACCENT);
    }

    @Override protected void onPause() { super.onPause(); if (h.ui == this) h.ui = null; }
    @Override public void onState(String s) { render(); }
    @Override public void onBattery(int l, int r, int c) { render(); }
    @Override public void onAnc(int m, int s) { render(); }
    @Override public void onEq(int p) { render(); }
    @Override public void onInfo(String s) { render(); }
    @Override public void onMethod(String m) { render(); }
}
