package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

import static org.kravchuk.redmibuds.Ui.*;

@SuppressLint("MissingPermission")
public class SettingsActivity extends Activity implements BudsConnection.Listener {
    private TextView st, deviceTxt;
    private final Hub h = Hub.I;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(this, 16); root.setPadding(p, dp(this, 24), p, p); sv.addView(root); setContentView(sv);

        LinearLayout top = row(this, root, 0); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = chip(this, "← Назад", v -> finish()); top.addView(back);
        text(this, root, "Настройки", 24, TEXT, true).setPadding(0, dp(this, 14), 0, dp(this, 8));

        // Device / connection
        LinearLayout c0 = card(this, root);
        header(this, c0, "Устройство");
        deviceTxt = text(this, c0, h.device == null ? "Не выбрано" : nameOf(h.device), 14, TEXT, false);
        st = text(this, c0, h.state, 13, SUB, false); st.setPadding(0, dp(this, 6), 0, 0);
        LinearLayout r0 = row(this, c0, 12);
        addWeighted(r0, button(this, "Выбрать", 0xFFB0BEC5, v -> pick()), 3);
        addWeighted(r0, button(this, "BLE", ORANGE, v -> {
            if (h.device == null) { pick(); return; }
            Hub.conn.close(); MainActivity.gatt(this).connect(h.device);
        }), 3);
        addWeighted(r0, button(this, "RFCOMM", 0xFF78909C, v -> {
            if (h.device == null) { pick(); return; }
            if (h.gatt != null) h.gatt.close();
            Hub.conn.connect(h.device);
        }), 3);
        LinearLayout r0b = row(this, c0, 8);
        addWeighted(r0b, chip(this, "Отключить", v -> { Hub.conn.close(); if (h.gatt != null) h.gatt.close(); Hub.I.onState("Отключено"); }), 3);
        addWeighted(r0b, chip(this, "Обновить состояние", v -> Hub.conn.refresh()), 3);

        // Logs / update
        LinearLayout c1 = card(this, root);
        header(this, c1, "Журнал и обновления");
        LinearLayout r1 = row(this, c1, 0);
        addWeighted(r1, button(this, "Журнал TX/RX", ACCENT, v -> startActivity(new Intent(this, LogActivity.class))), 3);
        addWeighted(r1, button(this, "Отправить лог", ORANGE, v -> LogSender.send(this, "manual", true)), 3);
        LinearLayout r1b = row(this, c1, 8);
        addWeighted(r1b, button(this, "Проверить обновление", ACCENT, v -> Updater.checkInteractive(this)), 3);
        text(this, c1, "Версия " + Updater.current(this), 12, SUB, false).setPadding(0, dp(this, 10), 0, 0);

        // Webhook
        LinearLayout c = card(this, root);
        header(this, c, "Отправка логов");
        text(this, c, "Основной канал — webhook. Ключ хранится только на устройстве. Если URL пуст — ntfy.", 13, SUB, false);
        text(this, c, "Webhook URL", 13, SUB, false).setPadding(0, dp(this, 12), 0, 0);
        EditText url = new EditText(this); url.setTextColor(TEXT); url.setHintTextColor(SUB);
        url.setHint("https://…"); url.setText(LogSender.prefs(this).getString("webhook_url", "")); c.addView(url);
        text(this, c, "Ключ отправителя", 13, SUB, false).setPadding(0, dp(this, 12), 0, 0);
        EditText key = new EditText(this); key.setTextColor(TEXT); key.setHintTextColor(SUB);
        key.setHint("Bearer token"); key.setText(LogSender.prefs(this).getString("webhook_key", ""));
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        c.addView(key);
        LinearLayout r = row(this, c, 12);
        addWeighted(r, button(this, "Сохранить", ACCENT, v -> {
            LogSender.prefs(this).edit()
                    .putString("webhook_url", url.getText().toString().trim())
                    .putString("webhook_key", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        }), 3);
        addWeighted(r, button(this, "Тест отправки", ORANGE, v -> LogSender.send(this, "settings-test", true)), 3);
        text(this, c, "Запасной ntfy: " + LogSender.NTFY_TOPIC, 11, SUB, false).setPadding(0, dp(this, 10), 0, 0);

        // Diagnostics (was DiagActivity)
        LinearLayout cg = card(this, root);
        header(this, cg, "Диагностика BLE GATT");
        text(this, cg, "Полный перебор RFCOMM, GATT-карта, проба протокола Redmi. Сырые команды и журнал — здесь.", 13, SUB, false);
        LinearLayout rg = row(this, cg, 12);
        addWeighted(rg, button(this, "GATT диагностика", ORANGE, v -> {
            if (h.device == null) { Toast.makeText(this, "Сначала выберите наушники", Toast.LENGTH_SHORT).show(); return; }
            Hub.conn.close(); MainActivity.gatt(this).connect(h.device);
        }), 3);
        addWeighted(rg, button(this, "Auth по BLE", 0xFFB0BEC5, v -> Hub.conn.startAuth()), 3);
        LinearLayout rx = row(this, cg, 8);
        addWeighted(rx, chip(this, "Экспорт GATT карты", v -> {
            GattClient g = h.gatt;
            String m = g == null ? "GATT не запускался" : g.exportMap();
            try {
                java.io.File f = new java.io.File(getFilesDir(), "gatt_map.txt");
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { o.write(m.getBytes("UTF-8")); }
                App.share(this, f, "GATT карта");
            } catch (Throwable e) { Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
        }), 3);

        LinearLayout cr = card(this, root);
        header(this, cr, "RFCOMM");
        LinearLayout rr = row(this, cr, 0);
        addWeighted(rr, button(this, "Диагностика RFCOMM", ORANGE, v -> {
            if (h.device == null) { Toast.makeText(this, "Сначала выберите наушники", Toast.LENGTH_SHORT).show(); return; }
            Hub.conn.diagnose(h.device);
        }), 3);
        addWeighted(rr, chip(this, "Только SDP", v -> {
            if (h.device == null) return;
            new Thread(() -> { Hub.conn.fetchUuids(h.device); runOnUiThread(() -> Toast.makeText(this, "SDP готово — см. журнал", Toast.LENGTH_SHORT).show()); }).start();
        }), 3);
        LinearLayout rr2 = row(this, cr, 8);
        addWeighted(rr2, chip(this, "Забыть способ RFCOMM", v -> { if (h.device != null) Hub.conn.forgetMethod(h.device); Toast.makeText(this, "Сброшено", Toast.LENGTH_SHORT).show(); }), 3);

        // HEX write / manual probe
        LinearLayout c2 = card(this, root);
        header(this, c2, "Запись / ручная проба");
        Spinner sp = new Spinner(this); c2.addView(sp);
        LinearLayout rr3 = row(this, c2, 4);
        addWeighted(rr3, chip(this, "↻ Список характеристик", v -> {
            java.util.List<String> n = new java.util.ArrayList<>();
            GattClient g = h.gatt;
            if (g != null) for (android.bluetooth.BluetoothGattCharacteristic ch : g.chars)
                n.add(GattClient.shortName(ch.getService().getUuid()) + " / " + GattClient.shortName(ch.getUuid()) + " [" + GattClient.props(ch.getProperties()) + "]");
            sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, n));
        }), 3);
        EditText gh = new EditText(this); gh.setTextColor(TEXT); gh.setHintTextColor(SUB);
        gh.setHint("HEX, напр. FE DC BA C4 02 00 05 00 FF FF FF FF EF"); c2.addView(gh);
        CheckBox nr = new CheckBox(this); nr.setText("Без ответа (WRITE_NR)"); nr.setTextColor(TEXT); c2.addView(nr);
        LinearLayout rw = row(this, c2, 4);
        addWeighted(rw, button(this, "Записать", ACCENT, v -> {
            GattClient g = h.gatt;
            if (g == null || !g.ready()) { Toast.makeText(this, "GATT не подключён", Toast.LENGTH_SHORT).show(); return; }
            try { g.writeHex(sp.getSelectedItemPosition(), Proto.unhex(gh.getText().toString()), nr.isChecked()); }
            catch (Exception e) { Toast.makeText(this, "Неверный HEX", Toast.LENGTH_SHORT).show(); }
        }), 3);
        LinearLayout rp = row(this, c2, 8);
        addWeighted(rp, button(this, "Ручная проба Redmi", ORANGE, v -> {
            GattClient g = h.gatt;
            if (g == null || !g.ready()) { Toast.makeText(this, "GATT не подключён", Toast.LENGTH_SHORT).show(); return; }
            g.manualProbe(sp.getSelectedItemPosition(), nr.isChecked());
            Toast.makeText(this, "Проба запущена", Toast.LENGTH_SHORT).show();
        }), 3);

        text(this, root, "Протокол на основе Gadgetbridge (AGPL-3.0).", 11, SUB, false).setGravity(Gravity.CENTER);
    }

    private static String nameOf(BluetoothDevice d) {
        try { String n = d.getName(); return (n == null ? "?" : n) + "\n" + d.getAddress(); }
        catch (SecurityException e) { return d.getAddress(); }
    }

    private void pick() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения Bluetooth", Toast.LENGTH_SHORT).show(); return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        List<BluetoothDevice> l = new ArrayList<>();
        if (bm != null && bm.getAdapter() != null) try { l.addAll(bm.getAdapter().getBondedDevices()); } catch (SecurityException ignored) {}
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = nameOf(l.get(i));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Сопряжённые устройства")
                .setItems(names, (d, i) -> {
                    h.device = l.get(i);
                    getSharedPreferences("p", MODE_PRIVATE).edit().putString("addr", h.device.getAddress()).apply();
                    deviceTxt.setText(nameOf(h.device));
                }).show();
    }

    @Override protected void onResume() { super.onResume(); h.ui = this; st.setText(h.state); if (h.device != null) deviceTxt.setText(nameOf(h.device)); }
    @Override protected void onPause() { super.onPause(); if (h.ui == this) h.ui = null; }
    @Override public void onState(String s) { st.setText(s); }
    @Override public void onBattery(int l, int r, int c) {}
    @Override public void onAnc(int m, int s) {}
    @Override public void onEq(int p) {}
    @Override public void onInfo(String s) {}
    @Override public void onMethod(String m) { st.setText("Успех: " + m); }
    @Override public void onEar(boolean on) {}
}
