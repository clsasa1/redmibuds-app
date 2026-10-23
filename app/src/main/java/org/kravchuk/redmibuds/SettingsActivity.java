package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("MissingPermission")
public class SettingsActivity extends Activity {
    private static final int BG = 0xFF000000, TEXT = 0xFFFFFFFF, SUB = 0xFFB0B0B0,
            ACCENT = 0xFF4DA3FF, ACCENT_BG = 0xFF0A2540, LINE = 0xFF1A1A1A;

    private final Hub h = Hub.I;
    private TextView deviceTxt, progressTxt, statusLine;
    private ProgressBar bar;
    private View primaryBtn;
    private TextView primaryLabel;
    private LinearLayout devPanel;
    private boolean running;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG); sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int side = dp(24); root.setPadding(side, dp(20), side, dp(40));
        sv.addView(root); setContentView(sv);

        // top
        LinearLayout top = row(root); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("←  Назад", 15, SUB);
        back.setPadding(0, dp(8), dp(16), dp(8));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = label("Настройки", 22, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        top.addView(title, tlp);

        space(root, 32);

        // Device
        label(root, "УСТРОЙСТВО", 12, SUB).setLetterSpacing(0.08f);
        space(root, 12);
        deviceTxt = label(root, h.device == null ? "Наушники не выбраны" : nameOf(h.device), 18, TEXT);
        deviceTxt.setOnClickListener(v -> pick());
        space(root, 8);
        statusLine = label(root, h.state, 13, SUB);
        space(root, 28);

        // Primary diagnostic button
        primaryBtn = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(ACCENT_BG); bg.setCornerRadius(dp(28));
        primaryBtn.setBackground(bg);
        primaryBtn.setPadding(dp(20), dp(18), dp(20), dp(18));
        primaryLabel = label("Запустить диагностику", 16, ACCENT);
        primaryLabel.setGravity(Gravity.CENTER);
        ((FrameLayout) primaryBtn).addView(primaryLabel, new FrameLayout.LayoutParams(-1, -2));
        primaryBtn.setOnClickListener(v -> startFullDiag());
        root.addView(primaryBtn, new LinearLayout.LayoutParams(-1, -2));

        space(root, 16);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); bar.setProgress(0);
        bar.setVisibility(View.GONE);
        try { bar.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN); } catch (Exception ignored) {}
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(4)));
        space(root, 8);
        progressTxt = label(root, "", 13, SUB);
        progressTxt.setVisibility(View.GONE);

        space(root, 40);
        divider(root);
        space(root, 20);

        // Updates
        LinearLayout upd = row(root); upd.setGravity(Gravity.CENTER_VERTICAL);
        upd.setPadding(0, dp(8), 0, dp(8));
        TextView updL = label("Проверить обновления", 16, TEXT);
        upd.addView(updL, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chev = label("›", 22, SUB); upd.addView(chev);
        upd.setOnClickListener(v -> Updater.checkInteractive(this));

        space(root, 12);
        TextView ver = label(root, "Версия " + Updater.current(this), 13, SUB);

        space(root, 40);
        divider(root);
        space(root, 16);

        // Developer collapsed
        LinearLayout devHdr = row(root); devHdr.setGravity(Gravity.CENTER_VERTICAL);
        TextView devTitle = label("Для разработчика", 14, SUB);
        TextView devChevron = label("▾", 14, SUB);
        devHdr.addView(devTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        devHdr.addView(devChevron);
        // row(root) already attached devHdr — do not root.addView(devHdr)

        devPanel = new LinearLayout(this); devPanel.setOrientation(LinearLayout.VERTICAL);
        devPanel.setVisibility(View.GONE);
        root.addView(devPanel);
        space(devPanel, 16);
        label(devPanel, "Webhook URL", 12, SUB);
        EditText url = field(devPanel, "https://…", LogSender.prefs(this).getString("webhook_url", ""));
        space(devPanel, 12);
        label(devPanel, "Ключ отправителя", 12, SUB);
        EditText key = field(devPanel, "Bearer token", LogSender.prefs(this).getString("webhook_key", ""));
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        space(devPanel, 16);
        TextView save = label("Сохранить", 15, ACCENT);
        save.setPadding(0, dp(10), 0, dp(10));
        save.setOnClickListener(v -> {
            LogSender.prefs(this).edit()
                    .putString("webhook_url", url.getText().toString().trim())
                    .putString("webhook_key", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        });
        devPanel.addView(save);
        label(devPanel, "ntfy: " + LogSender.NTFY_TOPIC, 11, 0xFF666666).setPadding(0, dp(8), 0, 0);

        final boolean[] open = {false};
        View.OnClickListener tog = v -> {
            open[0] = !open[0];
            devPanel.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            devChevron.setText(open[0] ? "▴" : "▾");
        };
        devHdr.setOnClickListener(tog);
        devTitle.setOnClickListener(tog);
    }

    private void startFullDiag() {
        if (running) { Toast.makeText(this, "Уже выполняется", Toast.LENGTH_SHORT).show(); return; }
        if (h.device == null) { pick(); return; }
        running = true;
        primaryBtn.setEnabled(false);
        primaryLabel.setText("Диагностика…");
        bar.setVisibility(View.VISIBLE); progressTxt.setVisibility(View.VISIBLE);
        bar.setProgress(0);
        setProg(0, "Подготовка…");
        Log2.add("**", "=== Полная диагностика v" + Updater.current(this) + " ===");

        Hub.conn.close();
        if (h.gatt != null) h.gatt.close();

        // 1) RFCOMM quick (SDP + short tries) → 2) BLE GATT → 3) send log
        Hub.conn.diagnoseRfcommQuick(h.device, (pct, msg) -> runOnUiThread(() -> setProg(pct, msg)), () -> runOnUiThread(() -> {
            setProg(42, "BLE GATT…");
            GattClient g = MainActivity.gatt(this);
            g.onProbeDone = () -> runOnUiThread(this::finishDiag);
            g.connect(h.device);
            // safety timeout 3 min
            primaryBtn.postDelayed(() -> {
                if (running) {
                    Log2.add("**", "таймаут полной диагностики");
                    finishDiag();
                }
            }, 180_000);
        }));
    }

    private void finishDiag() {
        if (!running) return;
        running = false;
        setProg(95, "Отправка журнала…");
        LogSender.send(this, "full-diag", false);
        // toast after a short delay so network can complete
        primaryBtn.postDelayed(() -> {
            setProg(100, "Готово");
            primaryLabel.setText("Запустить диагностику");
            primaryBtn.setEnabled(true);
            Toast.makeText(this, "Журнал отправлен", Toast.LENGTH_LONG).show();
            statusLine.setText(h.state);
        }, 1200);
    }

    private void setProg(int pct, String msg) {
        bar.setProgress(Math.min(100, pct));
        progressTxt.setText(pct + "%  ·  " + msg);
        statusLine.setText(msg);
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
        try { String n = d.getName(); return n == null ? d.getAddress() : n; }
        catch (SecurityException e) { return d.getAddress(); }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String s, float size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private TextView label(LinearLayout p, String s, float size, int color) {
        TextView t = label(s, size, color); p.addView(t); return t;
    }
    private LinearLayout row(LinearLayout p) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(l, new LinearLayout.LayoutParams(-1, -2)); return l;
    }
    private void space(LinearLayout p, int d) {
        View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1, dp(d)));
    }
    private void divider(LinearLayout p) {
        View v = new View(this); v.setBackgroundColor(LINE);
        p.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private EditText field(LinearLayout p, String hint, String val) {
        EditText e = new EditText(this); e.setTextColor(TEXT); e.setHintTextColor(0xFF666666);
        e.setHint(hint); e.setText(val); e.setBackgroundColor(0xFF111111);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        p.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
}
