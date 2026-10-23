package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** v0.14 diagnostics: SDP UUIDs, RFCOMM MIWEAR connect, auth, settings read; always sends the log (also on cancel). */
@SuppressLint("MissingPermission")
final class DiagRunner {
    interface Listener { void onProgress(int pct, String msg); void onFinished(boolean sent, String msg); }

    static final DiagRunner I = new DiagRunner();
    private final Handler main = new Handler(Looper.getMainLooper());
    volatile boolean running, cancelled;
    volatile Listener listener;
    volatile int pct; volatile String msg = "";
    volatile String lastResult;
    private volatile Thread worker;

    private void progress(int p, String m) {
        pct = p; msg = m;
        Log2.add("DIAG", p + "% " + m);
        main.post(() -> { Listener l = listener; if (l != null) l.onProgress(p, m); });
    }

    synchronized boolean start(Context c, BluetoothDevice dev) {
        if (running) return false;
        running = true; cancelled = false; lastResult = null;
        final Context app = c.getApplicationContext();
        Hub.conn.setContext(app);
        worker = new Thread(() -> run(app, dev), "diag-runner");
        worker.start();
        return true;
    }

    void cancel() {
        if (!running) return;
        cancelled = true;
        Log2.add("DIAG", "остановлено пользователем");
        Thread w = worker; if (w != null) w.interrupt();
    }

    private void sleep(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (cancelled) throw new InterruptedException(); Thread.sleep(100); }
    }

    private void run(Context app, BluetoothDevice dev0) {
        BluetoothDevice dev = dev0;
        String ver = Updater.currentVersion(app);
        try {
            LogSender.send(app, "diag started v" + ver, false, null);
            Devices.logAll(app);
            BluetoothDevice chosen = Devices.choose(app);
            if (chosen != null) dev = chosen;
            if (dev == null) { progress(60, "Наушники не найдены"); return; }
            Log2.add("DIAG", "адрес для RFCOMM: " + Devices.describe(dev));
            for (BluetoothDevice b : Devices.bonded(app)) if (Devices.rfcommCapable(b) && Devices.name(b).toLowerCase().contains("buds"))
                try { b.fetchUuidsWithSdp(); } catch (Throwable ignored) {}
            progress(5, "Устройство " + dev.getAddress() + " [" + Devices.type(dev) + "]");
            try {
                android.os.ParcelUuid[] u = dev.getUuids();
                StringBuilder sb = new StringBuilder();
                if (u != null) for (android.os.ParcelUuid p : u) sb.append(p).append(' ');
                Log2.add("SDP", "UUID (кэш): " + sb);
                boolean has = sb.toString().contains("0000fd2d");
                Log2.add("SDP", has ? "MIWEAR fd2d в SDP есть" : "fd2d в кэше SDP нет (попробуем всё равно)");
                dev.fetchUuidsWithSdp();
            } catch (Throwable e) { Log2.add("SDP", "" + e); }
            sleep(1500);
            for (BluetoothDevice b : Devices.bonded(app)) if (Devices.name(b).toLowerCase().contains("buds")) Log2.add("SDP", "после fetch: " + Devices.describe(b));

            progress(15, "RFCOMM MIWEAR: подключение…");
            final CountDownLatch ready = new CountDownLatch(1);
            Hub.conn.onReady = ready::countDown;
            if (Hub.I.authed) ready.countDown(); else Hub.conn.connect(dev);
            long t0 = System.currentTimeMillis();
            boolean connected = false;
            while (System.currentTimeMillis() - t0 < 30_000) {
                if (Hub.I.connected) { connected = true; if (Hub.I.authed) break; }
                if (connected && !Hub.conn.isConnected()) break;
                sleep(200);
                if (connected) progress(40, "RFCOMM подключён, аутентификация…");
            }
            if (!Hub.I.connected && !Hub.I.authed) { progress(60, "RFCOMM MIWEAR не подключился"); }
            else if (!Hub.I.authed) progress(60, "Аутентификация не прошла");
            else {
                progress(65, "Аутентификация OK, чтение настроек…");
                if (!ready.await(10, TimeUnit.SECONDS)) Log2.add("DIAG", "нет onReady за 10с");
                sleep(2500);
                Hub h = Hub.I;
                StringBuilder g = new StringBuilder();
                for (int t = 1; t < 6; t++) g.append(t).append(':').append(Integer.toHexString(h.gestures[t][0])).append('/').append(Integer.toHexString(h.gestures[t][1])).append(' ');
                Log2.add("DIAG", "fw=" + h.fw + " bat L/R/C=" + h.left + "/" + h.right + "/" + h.box + " anc=" + h.anc
                        + " depth=" + h.ancDepth + " transp=" + h.trLevel + " eq=" + h.eq + " adaptive=" + h.adaptive
                        + " ear=" + h.ear + " dual=" + h.dual + " autoAnswer=" + h.autoAnswer + " long=" + h.longL + "/" + h.longR + " gestures " + g);
                progress(85, "Настройки прочитаны (fw " + h.fw + ")");
            }
        } catch (InterruptedException e) {
            progress(pct, "Остановлено — отправка частичного журнала");
        } catch (Throwable e) {
            Log2.add("DIAG", "ошибка: " + android.util.Log.getStackTraceString(e));
        } finally {
            Hub.conn.onReady = null;
            progress(95, "Отправка журнала…");
            CountDownLatch l3 = new CountDownLatch(1);
            final String[] res = {"Не отправился: таймаут отправки"};
            final boolean[] ok = {false};
            try {
                Thread.interrupted();
                LogSender.send(app, (cancelled ? "partial-diag v" : "full-diag v") + ver, false, (s, m) -> { ok[0] = s; res[0] = m; l3.countDown(); });
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
