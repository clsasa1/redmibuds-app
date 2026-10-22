/* SPDX-License-Identifier: AGPL-3.0-or-later. Protocol logic derived from Gadgetbridge (redmibuds), (C) Gadgetbridge contributors. */
package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** RFCOMM/SPP connection + Xiaomi/Redmi auth handshake (port of Gadgetbridge RedmiBudsProtocol). */
@SuppressLint("MissingPermission")
final class BudsConnection {
    static final UUID UUID_CTRL = UUID.fromString("0000fd2d-0000-1000-8000-00805f9b34fb");

    interface Listener {
        void onState(String state);
        void onBattery(int left, int right, int box); // -1 = unknown; +1000 = charging
        void onAnc(int mode, int strength);           // strength -1 unknown
        void onEq(int preset);
        void onInfo(String info);
        void onMethod(String method);
        void onEar(boolean on);
    }

    private volatile Listener l;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BluetoothSocket sock;
    private OutputStream out;
    private volatile boolean running;
    private byte seq = 0;
    boolean authed;

    BudsConnection(Listener l) { this.l = l; }

    void state(String s) { Log2.add("**", s); ui.post(() -> l.onState(s)); }

    // ---- connection strategy ----
    static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final String BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb";
    /** Well-known audio/system profiles that are never the control channel. */
    private static final String[] SKIP = {"1108","110a","110b","110c","110d","110e","110f","1112","111e","111f","1131","1200","1203","1800","1801","184e","184f","1850","1853","184d","1844","1843"};

    android.content.Context ctx;
    private volatile UUID[] sdpUuids = new UUID[0];

    void setContext(android.content.Context c) { ctx = c.getApplicationContext(); }
    void setListener(Listener nl) { l = nl; }

    static boolean wellKnown(UUID u) {
        String s = u.toString().toLowerCase();
        if (!s.endsWith(BASE_SUFFIX) || !s.startsWith("0000")) return false;
        String sh = s.substring(4, 8);
        for (String k : SKIP) if (k.equals(sh)) return true;
        return false;
    }

    /** Runs SDP query and waits (max 8 s) for ACTION_UUID. Logs all UUIDs. */
    UUID[] fetchUuids(BluetoothDevice dev) {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final java.util.List<UUID> got = new java.util.ArrayList<>();
        android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context c, android.content.Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (d == null || !d.getAddress().equals(dev.getAddress())) return;
                android.os.Parcelable[] arr = i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, android.os.Parcelable.class);
                if (arr != null) for (android.os.Parcelable p : arr) got.add(((android.os.ParcelUuid) p).getUuid());
                latch.countDown();
            }
        };
        if (ctx != null) ctx.registerReceiver(r, new android.content.IntentFilter(BluetoothDevice.ACTION_UUID), android.content.Context.RECEIVER_EXPORTED);
        android.os.ParcelUuid[] cached = dev.getUuids();
        Log2.add("SDP", "кэш getUuids(): " + (cached == null ? "null" : cached.length));
        if (cached != null) for (android.os.ParcelUuid p : cached) Log2.add("SDP", "  кэш " + p + (wellKnown(p.getUuid()) ? " (стандартный)" : ""));
        boolean ok = dev.fetchUuidsWithSdp();
        Log2.add("SDP", "fetchUuidsWithSdp() -> " + ok);
        try { latch.await(8, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        try { if (ctx != null) ctx.unregisterReceiver(r); } catch (Exception ignored) {}
        if (got.isEmpty() && cached != null) for (android.os.ParcelUuid p : cached) got.add(p.getUuid());
        Log2.add("SDP", "ACTION_UUID: " + got.size() + " UUID");
        for (UUID u : got) Log2.add("SDP", "  " + u + (wellKnown(u) ? " (стандартный)" : ""));
        sdpUuids = got.toArray(new UUID[0]);
        return sdpUuids;
    }

    /** Candidate strings: "u:<uuid>:s|i" or "c:<n>:s|i". */
    java.util.List<String> candidates(UUID[] sdp) {
        java.util.LinkedHashSet<String> c = new java.util.LinkedHashSet<>();
        java.util.List<UUID> us = new java.util.ArrayList<>();
        for (UUID u : sdp) if (!u.toString().toLowerCase().endsWith(BASE_SUFFIX)) us.add(u);      // vendor 128-bit first
        for (UUID u : sdp) if (!wellKnown(u) && !us.contains(u)) us.add(u);
        if (!us.contains(UUID_CTRL)) us.add(UUID_CTRL);
        if (!us.contains(UUID_SPP)) us.add(UUID_SPP);
        for (UUID u : us) { c.add("u:" + u + ":s"); c.add("u:" + u + ":i"); }
        for (int ch = 1; ch <= 30; ch++) { c.add("c:" + ch + ":i"); c.add("c:" + ch + ":s"); }
        return new java.util.ArrayList<>(c);
    }

    private BluetoothSocket open(BluetoothDevice dev, String cand) throws Exception {
        String[] p = cand.split(":");
        boolean sec = p[p.length - 1].equals("s");
        if (p[0].equals("u")) {
            UUID u = UUID.fromString(p[1]);
            return sec ? dev.createRfcommSocketToServiceRecord(u) : dev.createInsecureRfcommSocketToServiceRecord(u);
        }
        int ch = Integer.parseInt(p[1]);
        java.lang.reflect.Method m = dev.getClass().getMethod(sec ? "createRfcommSocket" : "createInsecureRfcommSocket", int.class);
        return (BluetoothSocket) m.invoke(dev, ch);
    }

    static String describe(String cand) {
        String[] p = cand.split(":");
        String sec = p[p.length - 1].equals("s") ? "secure" : "insecure";
        return p[0].equals("u") ? "UUID " + p[1] + " " + sec : "канал " + p[1] + " " + sec + " (reflection)";
    }

    /** Normal connect: saved method first, then full strategy. diag=true: SDP + all options, reports summary. */
    void connect(BluetoothDevice dev) { start(dev, false); }
    void diagnose(BluetoothDevice dev) { start(dev, true); }

    private void start(BluetoothDevice dev, boolean diag) {
        close();
        running = true;
        new Thread(() -> {
            try {
                state(diag ? "Диагностика: SDP-запрос…" : "Подключение к " + dev.getName() + "…");
                android.content.SharedPreferences sp = ctx == null ? null : ctx.getSharedPreferences("p", 0);
                String saved = sp == null ? null : sp.getString("method_" + dev.getAddress(), null);
                UUID[] sdp = (diag || saved == null) ? fetchUuids(dev) : new UUID[0];
                java.util.List<String> cands = candidates(sdp);
                if (saved != null && !diag) { cands.remove(saved); cands.add(0, saved); Log2.add("**", "сохранённый способ: " + describe(saved)); }
                android.bluetooth.BluetoothAdapter ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                BluetoothSocket ok = null; String okC = null; int n = 0;
                for (String c : cands) {
                    if (!running) return;
                    n++;
                    try { if (ad != null) ad.cancelDiscovery(); } catch (Exception ignored) {}
                    state("Попытка " + n + "/" + cands.size() + ": " + describe(c));
                    BluetoothSocket s = null;
                    long t0 = System.currentTimeMillis();
                    try {
                        s = open(dev, c);
                        s.connect();
                        Log2.add("OK", describe(c) + " — подключено за " + (System.currentTimeMillis() - t0) + " мс");
                        ok = s; okC = c; break;
                    } catch (Throwable e) {
                        Throwable e2 = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
                        Log2.add("FAIL", describe(c) + " — " + e2 + " (" + (System.currentTimeMillis() - t0) + " мс)");
                        try { if (s != null) s.close(); } catch (Exception ignored) {}
                        Thread.sleep(250);
                    }
                }
                if (ok == null) { state("Не удалось подключиться ни одним способом (" + n + " попыток)"); return; }
                if (sp != null) sp.edit().putString("method_" + dev.getAddress(), okC).apply();
                final String okDesc = describe(okC);
                ui.post(() -> l.onMethod(okDesc));
                sock = ok; out = ok.getOutputStream();
                state("Подключено (" + okDesc + "), аутентификация…");
                authed = false;
                byte[] ch = Authentication.getRandomChallenge();
                byte[] p = new byte[17]; p[0] = 1; System.arraycopy(ch, 0, p, 1, 16);
                send(Proto.encode(Proto.PHONE_REQUEST, Proto.OP_AUTH_CHALLENGE, seq++, p));
                readLoop(ok.getInputStream());
            } catch (Exception e) {
                state("Ошибка: " + e.getMessage());
            }
        }, "buds-io").start();
    }

    void forgetMethod(BluetoothDevice dev) { if (ctx != null) ctx.getSharedPreferences("p", 0).edit().remove("method_" + dev.getAddress()).apply(); }

    void close() {
        running = false;
        authed = false;
        try { if (sock != null) sock.close(); } catch (Exception ignored) {}
        sock = null; out = null;
    }

    boolean isConnected() { return (sock != null && sock.isConnected()) || altSender != null; }

    boolean commandsBlocked() {
        GattClient g = Hub.I.gatt;
        return g != null && g.probing && altSender == null;
    }

    synchronized void send(byte[] data) {
        if (commandsBlocked() && altSender == null && out == null) {
            state("Канал управления не найден (идёт проба BLE)");
            Log2.add("**", "команда заблокирована: идёт проба");
            return;
        }
        Log2.add("TX", Proto.hex(data));
        try {
            if (out == null) {
                java.util.function.Consumer<byte[]> a = altSender;
                if (a != null) { a.accept(data); return; }
                GattClient g = Hub.I.gatt;
                if (g != null && g.probing) state("Канал управления не найден (идёт проба BLE)");
                else if (g != null && g.ready()) state("BLE подключено, но канал управления не найден");
                else state("Не подключено");
                return;
            }
            out.write(data); out.flush();
        } catch (Exception e) { state("Ошибка записи: " + e.getMessage()); }
    }

    private void req(byte op, byte[] payload) { send(Proto.encode(Proto.PHONE_REQUEST, op, seq++, payload)); }

    // ---- commands ----
    void setAnc(int mode) { req(Proto.OP_ANC, new byte[]{0x02, 0x04, (byte) mode}); }
    /** Gadgetbridge: 0x00 = wearing detection on, 0x01 = off. */
    void setEarDetection(boolean on) { req(Proto.OP_ANC, new byte[]{0x02, 0x06, (byte) (on ? 0x00 : 0x01)}); }
    /** target 1=ANC, 2=transparency */
    void setStrength(int target, int value) { req(Proto.OP_SET_CONFIG, new byte[]{0x04, 0x00, 0x0b, (byte) target, (byte) value}); }
    void setEq(int preset) { req(Proto.OP_SET_CONFIG, new byte[]{0x03, 0x00, 0x07, (byte) preset}); }
    void refresh() {
        req(Proto.OP_DEVICE_INFO, new byte[]{-1, -1, -1, -1});
        req(Proto.OP_RUN_INFO, new byte[]{-1, -1, -1, -1});
        for (byte c : new byte[]{0x0b, 0x07}) req(Proto.OP_GET_CONFIG, new byte[]{0x00, c});
    }
    void sendRaw(byte[] b) { send(b); }

    // ---- receive ----
    private final ByteArrayOutputStream rxBuf = new ByteArrayOutputStream();

    private void readLoop(InputStream in) throws Exception {
        byte[] tmp = new byte[1024];
        while (running) {
            int n = in.read(tmp);
            if (n < 0) break;
            Log2.add("RX", Proto.hex(tmp, 0, n));
            feed(java.util.Arrays.copyOf(tmp, n));
        }
        state("Отключено");
    }

    /** Alternative transport (BLE GATT) for outgoing frames. */
    volatile java.util.function.Consumer<byte[]> altSender;

    /** Feed raw incoming bytes (any transport); extracts FE DC BA ... frames. */
    synchronized void feed(byte[] data) {
        rxBuf.write(data, 0, data.length);
        byte[] b = rxBuf.toByteArray();
        int pos = 0;
        while (true) {
            int st = findHeader(b, pos);
            if (st < 0) { pos = Math.max(pos, b.length - 2); break; }
            if (b.length - st < 7) { pos = st; break; }
            int len = ((b[st + 5] & 0xFF) << 8) | (b[st + 6] & 0xFF);
            int total = len + 8;
            if (b.length - st < total) { pos = st; break; }
            byte[] f = new byte[total];
            System.arraycopy(b, st, f, 0, total);
            if (f[total - 1] != (byte) 0xEF) Log2.add("**", "нет трейлера EF, кадр: " + Proto.hex(f));
            try { handle(f); } catch (Exception e) { Log2.add("**", "ошибка разбора: " + e); }
            pos = st + total;
        }
        rxBuf.reset();
        if (pos < b.length) rxBuf.write(b, pos, b.length - pos);
    }

    /** Start Redmi auth over current transport. */
    void startAuth() {
        authed = false;
        byte[] ch = Authentication.getRandomChallenge();
        byte[] p = new byte[17]; p[0] = 1; System.arraycopy(ch, 0, p, 1, 16);
        send(Proto.encode(Proto.PHONE_REQUEST, Proto.OP_AUTH_CHALLENGE, seq++, p));
    }
    byte[] deviceInfoReq() { return Proto.encode(Proto.PHONE_REQUEST, Proto.OP_DEVICE_INFO, seq++, new byte[]{-1, -1, -1, -1}); }
    byte[] authStartFrame() {
        byte[] ch = Authentication.getRandomChallenge();
        byte[] p = new byte[17]; p[0] = 1; System.arraycopy(ch, 0, p, 1, 16);
        return Proto.encode(Proto.PHONE_REQUEST, Proto.OP_AUTH_CHALLENGE, seq++, p);
    }
    void authedViaBle() { authed = true; }

    private static int findHeader(byte[] b, int from) {
        for (int i = from; i + 2 < b.length; i++)
            if (b[i] == (byte) 0xFE && b[i + 1] == (byte) 0xDC && b[i + 2] == (byte) 0xBA) return i;
        return -1;
    }

    private void handle(byte[] frame) {
        Proto.Msg m = Proto.parse(frame);
        if (m == null) return;
        Log2.add("..", String.format("type=%02X op=%02X seq=%02X payload=%s", m.type, m.opcode, m.seq, Proto.hex(m.payload)));
        byte[] p = m.payload;
        switch (m.opcode) {
            case Proto.OP_AUTH_CHALLENGE:
                if (m.type == Proto.RESPONSE) {
                    req(Proto.OP_AUTH_CONFIRM, new byte[]{0x01, 0x00});
                } else if (p.length >= 17) {
                    byte[] ch = new byte[16]; System.arraycopy(p, 1, ch, 0, 16);
                    byte[] r = new Authentication().computeChallengeResponse(ch);
                    byte[] pl = new byte[17]; pl[0] = 1; System.arraycopy(r, 0, pl, 1, 16);
                    send(Proto.encode(Proto.RESPONSE, Proto.OP_AUTH_CHALLENGE, m.seq, pl));
                }
                break;
            case Proto.OP_AUTH_CONFIRM:
                if (m.type != Proto.RESPONSE) {
                    send(Proto.encode(Proto.RESPONSE, Proto.OP_AUTH_CONFIRM, m.seq, new byte[]{0x01}));
                    authed = true;
                    state("Аутентифицировано ✓");
                    refresh();
                }
                break;
            case Proto.OP_DEVICE_INFO: decodeTlv(p, true); break;
            case Proto.OP_RUN_INFO: decodeTlv(p, false); break;
            case Proto.OP_REPORT_STATUS: {
                for (int i = 0; i + 1 < p.length; i += (p[i] & 0xFF) + 1) {
                    if (p[i + 1] == 0x00 && i + 4 < p.length) battery(p[i + 2], p[i + 3], p[i + 4]);
                    else if (p[i + 1] == 0x04 && i + 2 < p.length) { int v = p[i + 2]; ui.post(() -> l.onAnc(v, -1)); }
                    if ((p[i] & 0xFF) == 0) break;
                }
                send(Proto.encode(Proto.RESPONSE, Proto.OP_REPORT_STATUS, m.seq, new byte[0]));
                break;
            }
            case Proto.OP_GET_CONFIG: config(p, 0); break;
            case Proto.OP_NOTIFY_CONFIG:
                for (int i = 0; i + 2 < p.length; i += (p[i] & 0xFF) + 1) {
                    if (p[i + 2] == 0x0B && i + 4 < p.length) { int mo = p[i + 3], s = p[i + 4]; ui.post(() -> l.onAnc(mo, s)); }
                    if ((p[i] & 0xFF) == 0) break;
                }
                send(Proto.encode(Proto.RESPONSE, Proto.OP_NOTIFY_CONFIG, m.seq, new byte[0]));
                break;
        }
    }

    private void config(byte[] p, int i) {
        if (p.length < i + 4) return;
        if (p[i + 2] == 0x07) { int v = p[i + 3]; ui.post(() -> l.onEq(v)); }
        if (p[i + 2] == 0x0b && p.length > i + 4) { int t = p[i + 3], v = p[i + 4]; Log2.add("..", "strength target=" + t + " value=" + v); }
    }

    private void decodeTlv(byte[] p, boolean info) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < p.length; ) {
            int len = p[i] & 0xFF, idx = p[i + 1];
            if (info && idx == 0x01 && i + 5 < p.length) sb.append("FW: ").append(v(p[i + 2])).append('.').append(v(p[i + 3])).append(" / ").append(v(p[i + 4])).append('.').append(v(p[i + 5])).append('\n');
            if (info && idx == 0x03 && i + 5 < p.length) sb.append("VID/PID: ").append(Proto.hex(p, i + 2, 4)).append('\n');
            if (info && idx == 0x07 && i + 4 < p.length) battery(p[i + 2], p[i + 3], p[i + 4]);
            if (!info && idx == 0x09 && i + 2 < p.length) { int v = p[i + 2]; ui.post(() -> l.onAnc(v, -1)); }
            if (!info && idx == 0x0A && i + 2 < p.length) { boolean on = p[i + 2] == 0x00; ui.post(() -> l.onEar(on)); }
            if (len == 0) break;
            i += len + 1;
        }
        if (sb.length() > 0) { String s = sb.toString().trim(); ui.post(() -> l.onInfo(s)); }
    }

    private static String v(byte b) { return ((b >> 4) & 0xF) + "." + (b & 0xF); }

    private static int bat(byte b) { return b == (byte) 0xFF ? -1 : (b & 0x7F) + ((b & 0x80) != 0 ? 1000 : 0); }
    private void battery(byte left, byte right, byte box) {
        int a = bat(left), b = bat(right), c = bat(box);
        ui.post(() -> l.onBattery(a, b, c));
    }
}
