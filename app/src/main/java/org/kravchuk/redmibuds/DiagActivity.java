package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import static org.kravchuk.redmibuds.Ui.*;

@SuppressLint("MissingPermission")
public class DiagActivity extends Activity implements BudsConnection.Listener {
    private TextView st;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(this, 16); root.setPadding(p, dp(this, 24), p, p); sv.addView(root); setContentView(sv);
        text(this, root, "Диагностика", 24, TEXT, true).setPadding(0, 0, 0, dp(this, 14));

        LinearLayout c1 = card(this, root);
        header(this, c1, "RFCOMM: поиск канала");
        text(this, c1, "SDP-запрос UUID, затем перебор: нестандартные UUID из SDP, fd2d, SPP 1101 (secure/insecure), каналы RFCOMM 1–30 через reflection. Первый успешный способ запоминается, далее — аутентификация.", 13, SUB, false);
        st = text(this, c1, Hub.I.state, 13, TEXT, false); st.setPadding(0, dp(this, 10), 0, 0);
        LinearLayout r = row(this, c1, 12);
        addWeighted(r, button(this, "Диагностика", ORANGE, v -> {
            if (Hub.I.device == null) { Toast.makeText(this, "Сначала выберите наушники", Toast.LENGTH_SHORT).show(); return; }
            Hub.conn.diagnose(Hub.I.device);
        }), 3);
        addWeighted(r, button(this, "Только SDP", 0xFFB0BEC5, v -> {
            if (Hub.I.device == null) return;
            new Thread(() -> { Hub.conn.fetchUuids(Hub.I.device); runOnUiThread(() -> Toast.makeText(this, "SDP готово — см. журнал", Toast.LENGTH_SHORT).show()); }).start();
        }), 3);
        LinearLayout r2 = row(this, c1, 8);
        addWeighted(r2, chip(this, "Забыть сохранённый способ", v -> { if (Hub.I.device != null) Hub.conn.forgetMethod(Hub.I.device); Toast.makeText(this, "Сброшено", Toast.LENGTH_SHORT).show(); }), 3);

        LinearLayout cg = card(this, root);
        header(this, cg, "BLE GATT");
        text(this, cg, "connectGatt(LE) → при неудаче скан по имени/адресу → MTU 512 → все сервисы/характеристики/дескрипторы в журнал, чтение всех READ, подписка на NOTIFY/INDICATE вендорских сервисов и 2A19, затем проба протокола Redmi (FE DC BA, auth 0x50 + device info 0x02) на каждой вендорской write-характеристике.", 13, SUB, false);
        LinearLayout rg = row(this, cg, 12);
        addWeighted(rg, button(this, "GATT диагностика", ORANGE, v -> {
            if (Hub.I.device == null) { Toast.makeText(this, "Сначала выберите наушники", Toast.LENGTH_SHORT).show(); return; }
            Hub.conn.close(); MainActivity.gatt(this).connect(Hub.I.device);
        }), 3);
        LinearLayout rx = row(this, cg, 8);
        addWeighted(rx, chip(this, "Экспорт GATT карты", v -> {
            GattClient g = Hub.I.gatt;
            String m = g == null ? "GATT не запускался" : g.exportMap();
            try {
                java.io.File f = new java.io.File(getFilesDir(), "gatt_map.txt");
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { o.write(m.getBytes("UTF-8")); }
                Log2.add("GATT", "карта сохранена: " + f.length() + " байт");
                App.share(this, f, "GATT карта");
            } catch (Throwable e) { Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
        }), 3);
        addWeighted(rg, button(this, "Auth по BLE", 0xFFB0BEC5, v -> Hub.conn.startAuth()), 3);
        text(this, cg, "Запись HEX в характеристику:", 13, SUB, false).setPadding(0, dp(this, 12), 0, 0);
        Spinner sp = new Spinner(this); cg.addView(sp);
        LinearLayout rr = row(this, cg, 4);
        addWeighted(rr, chip(this, "↻ Список характеристик", v -> {
            java.util.List<String> n = new java.util.ArrayList<>();
            GattClient g = Hub.I.gatt;
            if (g != null) for (android.bluetooth.BluetoothGattCharacteristic c : g.chars)
                n.add(GattClient.shortName(c.getService().getUuid()) + " / " + GattClient.shortName(c.getUuid()) + " [" + GattClient.props(c.getProperties()) + "]");
            ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, n);
            sp.setAdapter(a);
        }), 3);
        EditText gh = new EditText(this); gh.setTextColor(TEXT); gh.setHintTextColor(SUB); gh.setHint("HEX, напр. FE DC BA C4 02 00 05 00 FF FF FF FF EF"); cg.addView(gh);
        CheckBox nr = new CheckBox(this); nr.setText("Без ответа (WRITE_NR)"); nr.setTextColor(TEXT); cg.addView(nr);
        LinearLayout rw = row(this, cg, 4);
        addWeighted(rw, button(this, "Записать", ACCENT, v -> {
            GattClient g = Hub.I.gatt;
            if (g == null || !g.ready()) { Toast.makeText(this, "GATT не подключён", Toast.LENGTH_SHORT).show(); return; }
            try { g.writeHex(sp.getSelectedItemPosition(), Proto.unhex(gh.getText().toString()), nr.isChecked()); }
            catch (Exception e) { Toast.makeText(this, "Неверный HEX", Toast.LENGTH_SHORT).show(); }
        }), 3);
        LinearLayout rp = row(this, cg, 8);
        addWeighted(rp, button(this, "Ручная проба Redmi", ORANGE, v -> {
            GattClient g = Hub.I.gatt;
            if (g == null || !g.ready()) { Toast.makeText(this, "GATT не подключён", Toast.LENGTH_SHORT).show(); return; }
            g.manualProbe(sp.getSelectedItemPosition(), nr.isChecked());
            Toast.makeText(this, "Проба запущена", Toast.LENGTH_SHORT).show();
        }), 3);

        LinearLayout c2 = card(this, root);
        header(this, c2, "Сырой пакет");
        EditText raw = new EditText(this); raw.setTextColor(TEXT); raw.setHintTextColor(SUB);
        raw.setHint("FE DC BA C4 02 00 05 00 FF FF FF FF EF"); c2.addView(raw);
        LinearLayout r3 = row(this, c2, 8);
        addWeighted(r3, button(this, "Отправить HEX", ACCENT, v -> {
            try { Hub.conn.sendRaw(Proto.unhex(raw.getText().toString())); } catch (Exception e) { Toast.makeText(this, "Неверный HEX", Toast.LENGTH_SHORT).show(); }
        }), 3);

        LinearLayout c3 = card(this, root);
        header(this, c3, "Журнал");
        LinearLayout r4 = row(this, c3, 0);
        addWeighted(r4, button(this, "Открыть журнал TX/RX", ACCENT, v -> startActivity(new Intent(this, LogActivity.class))), 3);
    }
    @Override protected void onResume() { super.onResume(); Hub.I.ui = this; st.setText(Hub.I.state); }
    @Override protected void onPause() { super.onPause(); if (Hub.I.ui == this) Hub.I.ui = null; }
    @Override public void onState(String s) { st.setText(s); }
    @Override public void onBattery(int l, int r, int c) {}
    @Override public void onAnc(int m, int s) {}
    @Override public void onEq(int p) {}
    @Override public void onInfo(String s) {}
    @Override public void onMethod(String m) { st.setText("Успех: " + m); }
}
