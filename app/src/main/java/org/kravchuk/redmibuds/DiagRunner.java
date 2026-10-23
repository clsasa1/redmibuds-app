package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Full diagnostics independent of any Activity: own worker thread, per-stage hard timeouts,
 * start ping and ALWAYS a final log send. UI subscribes via listener (may be null).
 */
@SuppressLint("MissingPermission")
final class DiagRunner {
    interface Listener { void onProgress(int pct, String msg); void onFinished(boolean sent, String msg); }

    static final DiagRunner I = new DiagRunner();
    private final Handler main = new Handler(Looper.getMainLooper());
    volatile boolean running;
    volatile Listener listener;
    volatile int pct; volatile String msg = "";
    volatile String lastResult;

    private static final long RFCOMM_TIMEOUT = 60_000, BLE_TIMEOUT = 150_000;

    private void progress(int p, String m) {
        pct = p; msg = m;
        Log2.add("DIAG", p + "% " + m);
        main.post(() -> { Listener l = listener; if (l != null) l.onProgress(p, m); });
    }

    synchronized boolean start(Context c, BluetoothDevice dev) {
        if (running) return false;
        running = true; lastResult = null;
        final Context app = c.getApplicationContext();
        Hub.conn.setContext(app);
        new Thread(() -> run(app, dev), "diag-runner").start();
        return true;
    }

    private void run(Context app, BluetoothDevice dev) {
        String ver = Updater.currentVersion(app);
        try {
            Log2.add("**", "=== Полная диагностика v" + ver + " (" + (dev == null ? "нет устройства" : dev.getAddress()) + ") ===");
            progress(1, "Старт");
            try { LogSender.send(app, "diag started v" + ver, false); } catch (Throwable e) { Log2.add("DIAG", "ping: " + e); }

            // Stage 1: SDP + RFCOMM
            if (dev != null) {
                try {
                    CountDownLatch l1 = new CountDownLatch(1);
                    main.post(() -> {
                        try { Hub.conn.close(); if (Hub.I.gatt != null) Hub.I.gatt.close(); } catch (Throwable ignored) {}
                        try {
                            Hub.conn.diagnoseRfcommQuick(dev, (p, m) -> progress(Math.max(2, p), m), l1::countDown);
                        } catch (Throwable e) { Log2.add("DIAG", "rfcomm start: " + e); l1.countDown(); }
                    });
                    if (!l1.await(RFCOMM_TIMEOUT, TimeUnit.MILLISECONDS)) {
                        Log2.add("DIAG", "RFCOMM: жёсткий таймаут " + RFCOMM_TIMEOUT / 1000 + " с");
                        try { Hub.conn.close(); } catch (Throwable ignored) {}
                    }
                } catch (Throwable e) { Log2.add("DIAG", "RFCOMM этап: " + e); }

                // Stage 2: BLE GATT + probes
                progress(45, "BLE GATT…");
                try {
                    CountDownLatch l2 = new CountDownLatch(1);
                    main.post(() -> {
                        try {
                            GattClient g = MainActivity.gatt(app);
                            g.onProbeDone = l2::countDown;
                            g.connect(dev);
                        } catch (Throwable e) { Log2.add("DIAG", "ble start: " + e); l2.countDown(); }
                    });
                    long t0 = System.currentTimeMillis();
                    while (!l2.await(5, TimeUnit.SECONDS)) {
                        long el = System.currentTimeMillis() - t0;
                        int p = 45 + (int) Math.min(45, el * 45 / BLE_TIMEOUT);
                        progress(p, "BLE: " + Hub.I.state);
                        if (el > BLE_TIMEOUT) { Log2.add("DIAG", "BLE: жёсткий таймаут " + BLE_TIMEOUT / 1000 + " с"); break; }
                    }
                } catch (Throwable e) { Log2.add("DIAG", "BLE этап: " + e); }
            } else Log2.add("DIAG", "устройство не выбрано — только отправка журнала");
        } catch (Throwable e) {
            Log2.add("DIAG", "сбой диагностики: " + e);
        } finally {
            // Stage 3: always send
            progress(95, "Отправка журнала…");
            CountDownLatch l3 = new CountDownLatch(1);
            final String[] res = {"Не отправился: таймаут отправки"};
            final boolean[] ok = {false};
            try {
                LogSender.send(app, "full-diag v" + ver, false, (s, m) -> { ok[0] = s; res[0] = m; l3.countDown(); });
                l3.await(40, TimeUnit.SECONDS);
            } catch (Throwable e) { res[0] = "Не отправился: " + e.getMessage(); }
            lastResult = res[0];
            running = false;
            progress(100, res[0]);
            final boolean fs = ok[0]; final String fm = res[0];
            main.post(() -> { Listener l = listener; if (l != null) l.onFinished(fs, fm); });
        }
    }
}
