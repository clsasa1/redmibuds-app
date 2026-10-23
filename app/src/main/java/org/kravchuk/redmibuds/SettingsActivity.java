package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

import static org.kravchuk.redmibuds.Style.*;

/** Every view is created and attached exactly once (helpers attach; never re-add). */
@SuppressLint("MissingPermission")
public class SettingsActivity extends Activity implements DiagRunner.Listener {
    private final Hub h = Hub.I;
    private TextView deviceTxt, progressTxt, resultTxt, primaryBtn, retryBtn, stopBtn;
    private ProgressBar bar;

    private int dpx(float v) { return dp(this, v); }
    private LinearLayout section(LinearLayout root, String title) {
        if (title != null) { TextView t = tv(this, title, 14, Bento.ON_BG_DIM, false); t.setPadding(dpx(16), dpx(22), 0, dpx(8)); root.addView(t); }
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(this, Bento.SURFACE, 20)); c.setPadding(0, dpx(6), 0, dpx(6));
        root.addView(c, new LinearLayout.LayoutParams(-1, -2)); return c;
    }
    private TextView button(LinearLayout p, String s, boolean primary, View.OnClickListener cl) {
        TextView t = Bento.one(this, s, 13, 17, primary ? 0xFFFFFFFF : Bento.BLUE, true);
        t.setGravity(Gravity.CENTER); t.setBackground(round(this, primary ? Bento.BLUE : 0xFF2C2C2E, 26));
        t.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dpx(52)); lp.setMargins(dpx(16), dpx(10), dpx(16), dpx(6));
        p.addView(t, lp); return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG); sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpx(16), dpx(10), dpx(16), dpx(32));
        sv.addView(root); setContentView(sv);

        LinearLayout tr = new LinearLayout(this); tr.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this); back.setImageResource(R.drawable.ic_back); back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setOnClickListener(v -> finish());
        tr.addView(back, new LinearLayout.LayoutParams(dpx(48), dpx(56)));
        TextView ttl = Bento.one(this, "Настройки", 18, 24, TEXT, false); ttl.setPadding(dpx(8), 0, 0, 0);
        tr.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(tr);

        // device
        LinearLayout dc = section(root, "Устройство");
        TextView[] dv = new TextView[1];
        dc.addView(Bento.listRow(this, R.drawable.ic_anc, "Наушники", dv, v -> pick()), new LinearLayout.LayoutParams(-1, dpx(68)));
        deviceTxt = dv[0];
        deviceTxt.setText(h.device == null ? "Выбрать из сопряжённых" : nameOf(h.device) + "  " + h.device.getAddress());
        TextView mh = tv(this, "MAC классического Bluetooth (приложение Xiaomi → «Об наушниках»)", 13, Bento.ON_BG_DIM, false);
        mh.setPadding(dpx(16), dpx(10), dpx(16), dpx(8)); dc.addView(mh);
        EditText mac = field(Devices.HINT_MAC, getSharedPreferences("p", MODE_PRIVATE).getString("mac", ""));
        mac.setSingleLine(true);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dpx(52)); mp.setMargins(dpx(16), 0, dpx(16), 0);
        dc.addView(mac, mp);
        button(dc, "Сохранить MAC и подключиться", false, v -> {
            String m = mac.getText().toString().trim().toUpperCase();
            if (!m.isEmpty() && !android.bluetooth.BluetoothAdapter.checkBluetoothAddress(m)) { Toast.makeText(this, "Неверный MAC", Toast.LENGTH_SHORT).show(); return; }
            getSharedPreferences("p", MODE_PRIVATE).edit().putString("mac", m).apply();
            h.device = m.isEmpty() ? null : Devices.choose(this);
            if (h.device != null) { deviceTxt.setText(nameOf(h.device) + "  " + h.device.getAddress()); Hub.conn.close(); deviceTxt.postDelayed(() -> Hub.conn.connect(h.device), 700); }
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        });

        // diagnostics
        LinearLayout gc = section(root, "Диагностика");
        TextView gd = tv(this, "Проверит подключение RFCOMM MIWEAR, вход и чтение настроек, затем отправит журнал разработчику.", 15, SUB, false);
        gd.setPadding(dpx(16), dpx(10), dpx(16), dpx(2)); gc.addView(gd);
        primaryBtn = button(gc, "Запустить диагностику", true, v -> startDiag());
        stopBtn = button(gc, "Остановить диагностику", false, v -> { DiagRunner.I.cancel(); stopBtn.setEnabled(false); stopBtn.setText("Остановка…"); });
        stopBtn.setVisibility(View.GONE);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); bar.setVisibility(View.GONE);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Bento.BLUE));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dpx(8)); bp.setMargins(dpx(16), dpx(12), dpx(16), 0);
        gc.addView(bar, bp);
        progressTxt = tv(this, "", 15, SUB, false); progressTxt.setVisibility(View.GONE); progressTxt.setPadding(dpx(16), dpx(6), dpx(16), 0); gc.addView(progressTxt);
        resultTxt = tv(this, "", 16, TEXT, true); resultTxt.setVisibility(View.GONE); resultTxt.setPadding(dpx(16), dpx(8), dpx(16), 0); gc.addView(resultTxt);
        retryBtn = button(gc, "Повторить отправку", false, v -> sendNow("retry"));
        retryBtn.setVisibility(View.GONE);

        // app
        LinearLayout rc = section(root, "Приложение");
        rc.addView(Bento.listRow(this, R.drawable.ic_sound, "Отправить журнал", null, v -> sendNow("manual")), new LinearLayout.LayoutParams(-1, dpx(60)));
        TextView[] vv = new TextView[1];
        rc.addView(Bento.listRow(this, R.drawable.ic_settings, "Проверить обновления", vv, v -> Updater.checkInteractive(this)), new LinearLayout.LayoutParams(-1, dpx(64)));
        vv[0].setText("Версия " + Updater.currentVersion(this));

        // developer (collapsed)
        LinearLayout xc = section(root, null);
        ((LinearLayout.LayoutParams) xc.getLayoutParams()).topMargin = dpx(16);
        TextView devHdr = rowText("Для разработчика  ▾"); devHdr.setTextColor(SUB); devHdr.setPadding(dpx(16), 0, dpx(16), 0);
        xc.addView(devHdr);
        LinearLayout devPanel = new LinearLayout(this); devPanel.setOrientation(LinearLayout.VERTICAL);
        devPanel.setVisibility(View.GONE); devPanel.setPadding(dpx(16), 0, dpx(16), dpx(10));
        xc.addView(devPanel);
        add(devPanel, tv(this, "Webhook URL", 14, DIM, false));
        EditText url = add(devPanel, field("https://…", LogSender.prefs(this).getString("webhook_url", "")));
        add(devPanel, tv(this, "Ключ отправителя", 14, DIM, false)).setPadding(0, dpx(10), 0, 0);
        EditText key = add(devPanel, field("Bearer token", LogSender.prefs(this).getString("webhook_key", "")));
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView save = button(devPanel, "Сохранить", false, v -> {
            LogSender.prefs(this).edit().putString("webhook_url", url.getText().toString().trim())
                    .putString("webhook_key", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        });
        ((LinearLayout.LayoutParams) save.getLayoutParams()).setMargins(0, dpx(12), 0, 0);
        add(devPanel, tv(this, "ntfy: " + LogSender.NTFY_TOPIC, 12, DIM, false)).setPadding(0, dpx(8), 0, 0);
        devHdr.setOnClickListener(v -> {
            boolean open = devPanel.getVisibility() != View.VISIBLE;
            devPanel.setVisibility(open ? View.VISIBLE : View.GONE);
            devHdr.setText(open ? "Для разработчика  ▴" : "Для разработчика  ▾");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        DiagRunner d = DiagRunner.I;
        d.listener = this;
        if (d.running) { showRunning(); onProgress(d.pct, d.msg); }
        else if (d.lastResult != null) showResult(!d.lastResult.startsWith("Не"), d.lastResult);
    }
    @Override protected void onPause() { super.onPause(); if (DiagRunner.I.listener == this) DiagRunner.I.listener = null; }

    private void startDiag() {
        if (!DiagRunner.I.start(this, h.device)) { Toast.makeText(this, "Уже выполняется", Toast.LENGTH_SHORT).show(); return; }
        showRunning();
    }
    private void showRunning() {
        primaryBtn.setEnabled(false); primaryBtn.setText("Диагностика…");
        bar.setVisibility(View.VISIBLE); progressTxt.setVisibility(View.VISIBLE);
        resultTxt.setVisibility(View.GONE); retryBtn.setVisibility(View.GONE);
        stopBtn.setVisibility(View.VISIBLE); stopBtn.setEnabled(true); stopBtn.setText("Остановить диагностику");
    }
    private void sendNow(String reason) {
        resultTxt.setVisibility(View.VISIBLE); resultTxt.setTextColor(SUB); resultTxt.setText("Отправка…");
        retryBtn.setVisibility(View.GONE);
        LogSender.send(this, reason, false, this::showResult);
    }
    private void showResult(boolean ok, String msg) {
        resultTxt.setVisibility(View.VISIBLE);
        resultTxt.setTextColor(ok ? 0xFF34C759 : 0xFFFF453A);
        resultTxt.setText(msg);
        retryBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    @Override public void onProgress(int pct, String msg) {
        bar.setProgress(pct);
        progressTxt.setText(pct + "%  ·  " + msg);
    }
    @Override public void onFinished(boolean sent, String msg) {
        primaryBtn.setEnabled(true); primaryBtn.setText("Запустить диагностику");
        stopBtn.setVisibility(View.GONE);
        bar.setProgress(100);
        showResult(sent, msg);
    }

    private void pick() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения Bluetooth", Toast.LENGTH_SHORT).show(); return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        List<BluetoothDevice> l = new ArrayList<>();
        if (bm != null && bm.getAdapter() != null) try { l.addAll(bm.getAdapter().getBondedDevices()); } catch (SecurityException ignored) {}
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = nameOf(l.get(i)) + "\n" + l.get(i).getAddress() + " · " + Devices.type(l.get(i)) + (Devices.hasFd2d(l.get(i)) ? " · MIWEAR" : "");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Сопряжённые устройства")
                .setItems(names, (d, i) -> {
                    h.device = l.get(i);
                    getSharedPreferences("p", MODE_PRIVATE).edit().putString("addr", h.device.getAddress()).apply();
                    deviceTxt.setText(nameOf(h.device) + "  " + h.device.getAddress());
                    Hub.conn.close(); deviceTxt.postDelayed(() -> Hub.conn.connect(h.device), 700);
                }).show();
    }

    private static String nameOf(BluetoothDevice d) {
        try { String n = d.getName(); return n == null ? d.getAddress() : n; } catch (SecurityException e) { return d.getAddress(); }
    }
    private <T extends View> T add(LinearLayout p, T v) { p.addView(v); return v; }
    private TextView rowText(String s) {
        TextView t = tv(this, s, 19, TEXT, false); t.setMaxLines(1); t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setMinHeight(dp(this, 56)); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private EditText field(String hint, String val) {
        EditText e = new EditText(this); e.setTextColor(TEXT); e.setHintTextColor(DIM); e.setTextSize(17);
        e.setHint(hint); e.setText(val); e.setBackground(round(this, PILL, 12));
        e.setPadding(dp(this, 14), dp(this, 14), dp(this, 14), dp(this, 14)); return e;
    }
}
