/* SPDX-License-Identifier: AGPL-3.0-or-later. Protocol per /workspace/hci/PROTOCOL.md (HCI capture of Xiaomi Earbuds app);
   auth = Gadgetbridge Authentication (SAFER+), (C) Gadgetbridge contributors. */
package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Classic RFCOMM "MIWEAR" (SDP UUID fd2d) transport + MMA framing + auth + controls. */
@SuppressLint("MissingPermission")
final class BudsConnection {
    static final UUID UUID_MIWEAR = UUID.fromString("0000fd2d-0000-1000-8000-00805f9b34fb");
    static final byte OP_DEVICE_INFO = 0x02, OP_ANC = 0x08, OP_RUN_INFO = 0x09, OP_STATUS = 0x0E, OP_RUNINFO_NOTIFY = 0x07,
            OP_AUTH = 0x50, OP_AUTH_CONFIRM = 0x51, OP_SET = (byte) 0xF2, OP_GET = (byte) 0xF3, OP_NOTIFY_CFG = (byte) 0xF4;
    static final int CFG_AUTO_ANSWER = 0x03, CFG_DUAL = 0x04, CFG_GESTURE = 0x02, CFG_EQ = 0x07, CFG_FIND = 0x09, CFG_LONG = 0x0A,
            CFG_STRENGTH = 0x0B, CFG_POSITION = 0x0C, CFG_TIME = 0x28, CFG_ADAPTIVE = 0x29, CFG_VERSION = 0x47;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Context ctx;
    private volatile BluetoothSocket sock;
    private volatile OutputStream out;
    private volatile boolean running;
    private byte seq = 0x10;
    private final ByteArrayOutputStream rx = new ByteArrayOutputStream();
    /** Called (main thread) after auth+initial reads; used by diagnostics. */
    volatile Runnable onReady;

    void setContext(Context c) { ctx = c.getApplicationContext(); }

    private void post(Runnable r) { ui.post(() -> { try { r.run(); } catch (Throwable e) { Log2.add("**", "ui: " + e); } Hub.I.changed(); }); }
    void state(String s) { Log2.add("**", s); post(() -> Hub.I.state = s); }

    boolean isConnected() { BluetoothSocket s = sock; return s != null && s.isConnected(); }

    // ---------- connection ----------
    void connect(BluetoothDevice dev) {
        if (dev == null) return;
        close();
        running = true;
        new Thread(() -> runConnection(dev), "miwear-io").start();
    }

    private void runConnection(BluetoothDevice dev) {
        try {
            String name; try { name = dev.getName(); } catch (SecurityException e) { name = dev.getAddress(); }
            state("Подключение к " + name + "…");
            try { BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter(); if (a != null) a.cancelDiscovery(); } catch (Throwable ignored) {}
            BluetoothSocket s = null;
            String[] ways = {"secure", "insecure", "ch28"};
            for (String w : ways) {
                if (!running) return;
                long t0 = System.currentTimeMillis();
                try {
                    if (w.equals("secure")) s = dev.createRfcommSocketToServiceRecord(UUID_MIWEAR);
                    else if (w.equals("insecure")) s = dev.createInsecureRfcommSocketToServiceRecord(UUID_MIWEAR);
                    else s = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", int.class).invoke(dev, 28);
                    s.connect();
                    Log2.add("OK", "RFCOMM MIWEAR (" + w + ") за " + (System.currentTimeMillis() - t0) + " мс");
                    break;
                } catch (Throwable e) {
                    Throwable c = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
                    Log2.add("FAIL", "RFCOMM MIWEAR (" + w + "): " + c + " (" + (System.currentTimeMillis() - t0) + " мс)");
                    try { if (s != null) s.close(); } catch (Exception ignored) {}
                    s = null;
                }
            }
            if (s == null) { state("Не удалось подключиться"); post(() -> { Hub.I.connected = false; Hub.I.authed = false; }); return; }
            sock = s; out = s.getOutputStream();
            post(() -> { Hub.I.connected = true; Hub.I.authed = false; });
            state("Аутентификация…");
            synchronized (rx) { rx.reset(); }
            byte[] ch = Authentication.getRandomChallenge();
            byte[] p = new byte[17]; p[0] = 1; System.arraycopy(ch, 0, p, 1, 16);
            req(OP_AUTH, p);
            InputStream in = s.getInputStream();
            byte[] buf = new byte[2048];
            while (running) {
                int n = in.read(buf);
                if (n < 0) break;
                byte[] d = java.util.Arrays.copyOf(buf, n);
                Log2.add("RX", Proto.hex(d));
                feed(d);
            }
        } catch (Throwable e) {
            if (running) Log2.add("**", "соединение: " + e);
        } finally {
            boolean wasRunning = running;
            closeSocket();
            post(() -> { Hub.I.connected = false; Hub.I.authed = false; });
            if (wasRunning) state("Нет связи");
        }
    }

    void close() { running = false; closeSocket(); }
    private void closeSocket() {
        try { BluetoothSocket s = sock; if (s != null) s.close(); } catch (Exception ignored) {}
        sock = null; out = null;
    }

    // ---------- framing ----------
    synchronized void send(byte[] f) {
        Log2.add("TX", Proto.hex(f));
        OutputStream o = out;
        if (o == null) { state("Нет связи с наушниками"); return; }
        try { o.write(f); o.flush(); } catch (Exception e) { Log2.add("**", "запись: " + e); }
    }
    private void req(byte op, byte[] payload) { send(Proto.encode(Proto.PHONE_REQUEST, op, seq++, payload)); }
    private void ack(byte op, byte s, byte[] payload) { send(Proto.encode(Proto.RESPONSE, op, s, payload)); }

    void feed(byte[] data) {
        synchronized (rx) {
            rx.write(data, 0, data.length);
            byte[] b = rx.toByteArray();
            int pos = 0;
            while (true) {
                int st = -1;
                for (int i = pos; i + 2 < b.length; i++) if (b[i] == (byte) 0xFE && b[i + 1] == (byte) 0xDC && b[i + 2] == (byte) 0xBA) { st = i; break; }
                if (st < 0) { pos = Math.max(pos, b.length - 2); break; }
                if (b.length - st < 7) { pos = st; break; }
                int len = ((b[st + 5] & 0xFF) << 8) | (b[st + 6] & 0xFF);
                int total = len + 8;
                if (b.length - st < total) { pos = st; break; }
                byte[] f = java.util.Arrays.copyOfRange(b, st, st + total);
                try { handle(f); } catch (Throwable e) { Log2.add("**", "разбор " + Proto.hex(f) + ": " + e); }
                pos = st + total;
            }
            rx.reset();
            if (pos < b.length) rx.write(b, pos, b.length - pos);
        }
    }

    private void handle(byte[] f) {
        Proto.Msg m = Proto.parse(f);
        if (m == null) return;
        byte[] p = m.payload;
        Log2.add("..", String.format("type=%02X op=%02X seq=%02X %s", m.type, m.opcode, m.seq, Proto.hex(p)));
        if (m.opcode == OP_AUTH) {
            if (m.type == Proto.RESPONSE) req(OP_AUTH_CONFIRM, new byte[]{0x01, 0x00});
            else if (p.length >= 17) {
                byte[] ch = java.util.Arrays.copyOfRange(p, 1, 17);
                byte[] r = new Authentication().computeChallengeResponse(ch);
                byte[] pl = new byte[17]; pl[0] = 1; System.arraycopy(r, 0, pl, 1, 16);
                ack(OP_AUTH, m.seq, pl);
            }
            return;
        }
        if (m.opcode == OP_AUTH_CONFIRM) {
            if (m.type != Proto.RESPONSE) {
                ack(OP_AUTH_CONFIRM, m.seq, new byte[]{0x01});
                post(() -> Hub.I.authed = true);
                state("Подключено");
                initialReads();
            }
            return;
        }
        switch (m.opcode) {
            case OP_DEVICE_INFO: if (m.type == Proto.RESPONSE) tlv(p, true); break;
            case OP_RUN_INFO: if (m.type == Proto.RESPONSE) tlv(p, false); break;
            case OP_STATUS: status(p); break;
            case OP_RUNINFO_NOTIFY: tlv(p, false); break;
            case OP_GET: case OP_NOTIFY_CFG: if (m.type != Proto.RESPONSE || m.opcode == OP_GET) configs(p); break;
        }
        if (m.type == Proto.EARBUDS_NOTIFY || m.type == Proto.EARBUDS_REQUEST) ack(m.opcode, m.seq, new byte[0]);
    }

    private void initialReads() {
        req(OP_DEVICE_INFO, new byte[]{-1, -1, -1, -1});
        req(OP_RUN_INFO, new byte[]{-1, -1, -1, -1});
        long t = System.currentTimeMillis() / 1000;
        set(CFG_TIME, (byte) (t >> 24), (byte) (t >> 16), (byte) (t >> 8), (byte) t);
        get(CFG_VERSION, CFG_STRENGTH, CFG_EQ, CFG_ADAPTIVE, CFG_GESTURE, CFG_LONG, CFG_DUAL, CFG_AUTO_ANSWER);
        ui.postDelayed(() -> { Runnable r = onReady; onReady = null; if (r != null) r.run(); }, 2500);
    }

    // ---------- parsing ----------
    private static int bat(byte b) { return b == (byte) 0xFF ? -1 : (b & 0x7F) + ((b & 0x80) != 0 ? 1000 : 0); }

    private void tlv(byte[] p, boolean info) {
        for (int i = 0; i + 1 < p.length; ) {
            int len = p[i] & 0xFF; if (len == 0) break;
            int id = p[i + 1] & 0xFF, d = i + 2;
            if (info && id == 0x07 && d + 2 < p.length) { int l = bat(p[d]), r = bat(p[d + 1]), c = bat(p[d + 2]); post(() -> { Hub.I.left = l; Hub.I.right = r; Hub.I.box = c; }); }
            if (info && id == 0x01 && d + 1 < p.length) { String v = ver(p[d], p[d + 1]); post(() -> Hub.I.fw = v); }
            if (!info && id == 0x09 && d < p.length) { int v = p[d]; post(() -> Hub.I.anc = v); }
            if (!info && id == 0x0A && d < p.length) { int v = p[d] == 0 ? 1 : 0; post(() -> Hub.I.ear = v); }
            i += len + 1;
        }
    }
    private static String ver(byte a, byte b) { return ((a >> 4) & 0xF) + "." + (a & 0xF) + "." + ((b >> 4) & 0xF) + "." + (b & 0xF); }

    private void status(byte[] p) {
        for (int i = 0; i + 1 < p.length; ) {
            int len = p[i] & 0xFF; if (len == 0) break;
            int id = p[i + 1] & 0xFF, d = i + 2;
            if (id == 0x00 && d + 2 < p.length) { int l = bat(p[d]), r = bat(p[d + 1]), c = bat(p[d + 2]); post(() -> { if (l >= 0) Hub.I.left = l; if (r >= 0) Hub.I.right = r; if (c >= 0) Hub.I.box = c; }); }
            if (id == 0x04 && d < p.length) { int v = p[d]; post(() -> Hub.I.anc = v); }
            i += len + 1;
        }
    }

    /** Entries "n 00 cfg data…" (n counts bytes after itself). */
    private void configs(byte[] p) {
        for (int i = 0; i + 2 < p.length; ) {
            int n = p[i] & 0xFF; if (n < 2) break;
            int end = Math.min(p.length, i + 1 + n);
            int cfg = p[i + 2] & 0xFF;
            byte[] d = java.util.Arrays.copyOfRange(p, Math.min(i + 3, end), end);
            config(cfg, d);
            i = end;
        }
    }
    private void config(int cfg, byte[] d) {
        if (d.length == 0) return;
        int v0 = d[0] & 0xFF;
        switch (cfg) {
            case CFG_EQ: post(() -> Hub.I.eq = v0); break;
            case CFG_ADAPTIVE: post(() -> Hub.I.adaptive = v0); break;
            case CFG_DUAL: post(() -> Hub.I.dual = v0); break;
            case CFG_AUTO_ANSWER: post(() -> Hub.I.autoAnswer = v0); break;
            case CFG_POSITION: post(() -> Hub.I.position = v0); break;
            case CFG_VERSION: if (d.length >= 2) { String v = ver(d[0], d[1]); post(() -> Hub.I.fw = v); } break;
            case CFG_LONG: if (d.length >= 2) { int l = d[0] & 0xFF, r = d[1] & 0xFF; post(() -> { Hub.I.longL = l; Hub.I.longR = r; }); } break;
            case CFG_STRENGTH: if (d.length >= 2) {
                int mode = v0, s = d[1] & 0xFF;
                post(() -> { if (mode == 1) Hub.I.ancDepth = s; else if (mode == 2) Hub.I.trLevel = s; });
            } break;
            case CFG_FIND: if (d.length >= 2) { int on = v0, side = d[1]; post(() -> Hub.I.find = on == 0 ? 0 : side); } break;
            case CFG_GESTURE:
                for (int i = 0; i + 2 < d.length; i += 3) {
                    int t = d[i] & 0xFF, l = d[i + 1] & 0xFF, r = d[i + 2] & 0xFF;
                    if (t < 6) post(() -> { Hub.I.gestures[t][0] = l; Hub.I.gestures[t][1] = r; });
                }
                break;
        }
    }

    // ---------- commands ----------
    private void set(int cfg, byte... data) {
        byte[] p = new byte[data.length + 3];
        p[0] = (byte) (data.length + 2); p[1] = 0; p[2] = (byte) cfg;
        System.arraycopy(data, 0, p, 3, data.length);
        req(OP_SET, p);
    }
    void get(int... cfgs) {
        byte[] p = new byte[cfgs.length * 2];
        for (int i = 0; i < cfgs.length; i++) { p[2 * i] = 0; p[2 * i + 1] = (byte) cfgs[i]; }
        req(OP_GET, p);
    }
    void setAnc(int mode) { req(OP_ANC, new byte[]{0x02, 0x04, (byte) mode}); }
    void setAncDepth(int v) { set(CFG_STRENGTH, (byte) 1, (byte) v); }
    void setTransparency(int v) { set(CFG_STRENGTH, (byte) 2, (byte) v); }
    void setEq(int preset) { set(CFG_EQ, (byte) preset); }
    void setAdaptive(boolean on) { set(CFG_ADAPTIVE, (byte) (on ? 1 : 0)); }
    void setDual(boolean on) { set(CFG_DUAL, (byte) (on ? 1 : 0)); }
    void setAutoAnswer(boolean on) { set(CFG_AUTO_ANSWER, (byte) (on ? 1 : 0)); }
    void setEarDetection(boolean on) { req(OP_ANC, new byte[]{0x02, 0x06, (byte) (on ? 0x00 : 0x01)}); }
    /** side 1 L, 2 R, 3 both. */
    void find(boolean start, int side) { set(CFG_FIND, (byte) (start ? 1 : 0), (byte) side); }
    /** type 4 single,1 double,2 triple,3 long; side 0 L 1 R. */
    void setGesture(int type, int side, int action) {
        set(CFG_GESTURE, (byte) type, side == 0 ? (byte) action : (byte) 0xFF, side == 1 ? (byte) action : (byte) 0xFF);
    }
    void setLongCycle(int side, int v) { set(CFG_LONG, side == 0 ? (byte) v : (byte) 0xFF, side == 1 ? (byte) v : (byte) 0xFF); }
    void refresh() { if (Hub.I.authed) initialReads(); }
}
