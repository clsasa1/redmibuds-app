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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG); sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(this, 20), dp(this, 24), dp(this, 20), dp(this, 32));
        sv.addView(root); setContentView(sv);

        TextView back = add(root, tv(this, "←  Назад", 18, SUB, false));
        back.setMinHeight(dp(this, 48)); back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        add(root, tv(this, "Настройки", 32, TEXT, true));

        // Device card
        LinearLayout dc = card(this, root);
        add(dc, tv(this, "Устройство", 15, DIM, false));
        deviceTxt = add(dc, tv(this, h.device == null ? "Выбрать наушники" : nameOf(h.device), 20, TEXT, true));
        deviceTxt.setMaxLines(1); deviceTxt.setEllipsize(android.text.TextUtils.TruncateAt.END); deviceTxt.setMinHeight(dp(this, 56)); deviceTxt.setGravity(Gravity.CENTER_VERTICAL);
        deviceTxt.setOnClickListener(v -> pick());

        // Diagnostics card
        LinearLayout gc = card(this, root);
        add(gc, tv(this, "Диагностика", 20, TEXT, false));
        add(gc, tv(this, "Проверит RFCOMM MIWEAR, вход и чтение настроек, затем отправит журнал разработчику.", 15, SUB, false)).setPadding(0, dp(this, 6), 0, dp(this, 12));
        primaryBtn = add(gc, pill(this, "Запустить диагностику", v -> startDiag()));
        setPill(primaryBtn, true);
        stopBtn = add(gc, pill(this, "Остановить диагностику", v -> { DiagRunner.I.cancel(); stopBtn.setEnabled(false); stopBtn.setText("Остановка…"); }));
        stopBtn.setVisibility(View.GONE);
        ((LinearLayout.LayoutParams) stopBtn.getLayoutParams()).topMargin = dp(this, 10);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); bar.setVisibility(View.GONE);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(this, 8)); bp.topMargin = dp(this, 14);
        gc.addView(bar, bp);
        progressTxt = add(gc, tv(this, "", 16, SUB, false)); progressTxt.setVisibility(View.GONE); progressTxt.setPadding(0, dp(this, 6), 0, 0);
        resultTxt = add(gc, tv(this, "", 17, TEXT, true)); resultTxt.setVisibility(View.GONE); resultTxt.setPadding(0, dp(this, 10), 0, 0);
        retryBtn = add(gc, pill(this, "Повторить отправку", v -> sendNow("retry")));
        retryBtn.setVisibility(View.GONE);
        ((LinearLayout.LayoutParams) retryBtn.getLayoutParams()).topMargin = dp(this, 10);

        // Rows card
        LinearLayout rc = card(this, root);
        TextView send = add(rc, rowText("Отправить журнал"));
        send.setOnClickListener(v -> sendNow("manual"));
        TextView upd = add(rc, rowText("Проверить обновления"));
        upd.setOnClickListener(v -> Updater.checkInteractive(this));
        TextView ver = add(rc, tv(this, "Версия " + Updater.currentVersion(this), 16, DIM, false));
        ver.setPadding(0, dp(this, 8), 0, 0);

        // Developer (collapsed)
        LinearLayout xc = card(this, root);
        TextView devHdr = add(xc, rowText("Для разработчика  ▾"));
        devHdr.setTextColor(SUB);
        LinearLayout devPanel = new LinearLayout(this); devPanel.setOrientation(LinearLayout.VERTICAL);
        devPanel.setVisibility(View.GONE);
        xc.addView(devPanel);
        add(devPanel, tv(this, "Webhook URL", 14, DIM, false));
        EditText url = add(devPanel, field("https://…", LogSender.prefs(this).getString("webhook_url", "")));
        add(devPanel, tv(this, "Ключ отправителя", 14, DIM, false)).setPadding(0, dp(this, 10), 0, 0);
        EditText key = add(devPanel, field("Bearer token", LogSender.prefs(this).getString("webhook_key", "")));
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView save = add(devPanel, pill(this, "Сохранить", v -> {
            LogSender.prefs(this).edit().putString("webhook_url", url.getText().toString().trim())
                    .putString("webhook_key", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        }));
        ((LinearLayout.LayoutParams) save.getLayoutParams()).topMargin = dp(this, 12);
        add(devPanel, tv(this, "ntfy: " + LogSender.NTFY_TOPIC, 12, DIM, false)).setPadding(0, dp(this, 8), 0, 0);
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
        if (h.device == null) { pick(); return; }
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
        resultTxt.setTextColor(ok ? GREEN : RED);
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
        for (int i = 0; i < l.size(); i++) names[i] = nameOf(l.get(i));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Сопряжённые устройства")
                .setItems(names, (d, i) -> {
                    h.device = l.get(i);
                    getSharedPreferences("p", MODE_PRIVATE).edit().putString("addr", h.device.getAddress()).apply();
                    deviceTxt.setText(nameOf(h.device));
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
